import { afterEach, describe, expect, it, vi } from "vitest";
import { altaAsistida, type AltaAsistida } from "./admin-alta";
import { ApiError } from "./types";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const CUERPO: AltaAsistida = {
  nombre: "Comercial Andina",
  email: "ana@andina.pe",
  telefono: "987654321",
  empresa: { ruc: "20100066603", razon_social: "COMERCIAL ANDINA SAC", entorno: "BETA" },
  serie: { tipo: "01", serie: "F001" },
};

const CREADA = { cuenta_id: "c1", tenant_id: "t1", ruc: "20100066603", api_key: "fk_x", serie: { tipo: "01", serie: "F001" }, invitacion_enviada: true };

function stubFetch(status: number, cuerpo: object) {
  const fn = vi.fn(async () => new Response(JSON.stringify({ mensaje: null, codigo: null, errores: null, datos: null, ...cuerpo }), { status }));
  vi.stubGlobal("fetch", fn);
  return fn;
}

function ultimaLlamada(fn: ReturnType<typeof vi.fn>) {
  const [url, init] = fn.mock.calls[0] as unknown as [string, RequestInit];
  return { url, init, headers: new Headers(init.headers) };
}

/** Solo desde el servidor: el JWT del administrador y la IP resuelta salen del BFF; el navegador nunca los ve. */
describe("altaAsistida", () => {
  it("hace POST a /v1/admin/cuentas con el cuerpo en JSON y el JWT del administrador", async () => {
    vi.stubEnv("API_BASE_URL", "http://backend.test");
    const fetch = stubFetch(201, { estado: "exito", datos: CREADA });

    const r = await altaAsistida("jwt-admin", CUERPO);

    const { url, init, headers } = ultimaLlamada(fetch);
    expect(url).toBe("http://backend.test/v1/admin/cuentas");
    expect(init.method).toBe("POST");
    expect(headers.get("authorization")).toBe("Bearer jwt-admin");
    expect(headers.get("content-type")).toBe("application/json");
    expect(JSON.parse(init.body as string)).toEqual(CUERPO);
    expect(r).toEqual(CREADA);
  });

  it("agrega la IP ya resuelta del administrador cuando el BFF la tiene, para que la bitácora la registre", async () => {
    const fetch = stubFetch(201, { estado: "exito", datos: CREADA });

    await altaAsistida("jwt-admin", CUERPO, { "X-Forwarded-For": "203.0.113.7" });

    const { headers } = ultimaLlamada(fetch);
    expect(headers.get("x-forwarded-for")).toBe("203.0.113.7");
    expect(headers.get("authorization")).toBe("Bearer jwt-admin");
  });

  it("sin IP resuelta no manda ninguna cabecera de proxy", async () => {
    const fetch = stubFetch(201, { estado: "exito", datos: CREADA });

    await altaAsistida("jwt-admin", CUERPO);

    expect(ultimaLlamada(fetch).headers.get("x-forwarded-for")).toBeNull();
  });

  it("un rechazo del backend se lanza como ApiError con su status, código y mensaje", async () => {
    stubFetch(409, { estado: "error", codigo: "DUPLICADO", mensaje: "Ya existe una empresa con RUC 20100066603" });

    const error = await altaAsistida("jwt-admin", CUERPO).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(409);
    expect(error.codigo).toBe("DUPLICADO");
    expect(error.message).toBe("Ya existe una empresa con RUC 20100066603");
  });
});
