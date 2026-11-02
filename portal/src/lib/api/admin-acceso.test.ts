import { afterEach, describe, expect, it, vi } from "vitest";
import { enviarRestablecimiento, reenviarVerificacion } from "./admin-acceso";
import { ApiError } from "./types";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const CUENTA = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const USUARIO = "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f";

function stubFetch(status: number, cuerpo: object) {
  const fn = vi.fn(async () => new Response(JSON.stringify({ mensaje: null, codigo: null, errores: null, datos: null, ...cuerpo }), { status }));
  vi.stubGlobal("fetch", fn);
  return fn;
}

function ultimaLlamada(fn: ReturnType<typeof vi.fn>) {
  const [url, init] = fn.mock.calls[0] as unknown as [string, RequestInit];
  return { url, init, headers: new Headers(init.headers) };
}

/** Solo desde el servidor: el JWT del administrador y la IP resuelta salen del BFF; el navegador nunca los ve (#183). */
describe.each([
  ["enviarRestablecimiento", enviarRestablecimiento, "restablecimiento"],
  ["reenviarVerificacion", reenviarVerificacion, "verificacion"],
])("%s", (_nombre, llamar, ruta) => {
  it(`hace POST a /v1/admin/cuentas/{cuenta}/usuarios/{usuario}/${ruta} con el JWT del administrador`, async () => {
    vi.stubEnv("API_BASE_URL", "http://backend.test");
    const fetch = stubFetch(200, { estado: "exito", datos: { usuario_id: USUARIO, correo: "ana@negocio.pe" } });

    const r = await llamar("jwt-admin", CUENTA, USUARIO);

    const { url, init, headers } = ultimaLlamada(fetch);
    expect(url).toBe(`http://backend.test/v1/admin/cuentas/${CUENTA}/usuarios/${USUARIO}/${ruta}`);
    expect(init.method).toBe("POST");
    expect(headers.get("authorization")).toBe("Bearer jwt-admin");
    expect(r).toEqual({ usuario_id: USUARIO, correo: "ana@negocio.pe" });
  });

  it("manda la IP ya resuelta para que la bitácora registre la del administrador", async () => {
    const fetch = stubFetch(200, { estado: "exito", datos: { usuario_id: USUARIO, correo: "ana@negocio.pe" } });

    await llamar("jwt-admin", CUENTA, USUARIO, { "X-Forwarded-For": "203.0.113.7" });

    expect(ultimaLlamada(fetch).headers.get("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("un error del backend llega como ApiError con su status y su código", async () => {
    stubFetch(503, { estado: "error", codigo: "CORREO_NO_CONFIGURADO", mensaje: "El envío de correos no está habilitado" });

    await expect(llamar("jwt-admin", CUENTA, USUARIO)).rejects.toMatchObject({ status: 503, codigo: "CORREO_NO_CONFIGURADO" });
    await expect(llamar("jwt-admin", CUENTA, USUARIO)).rejects.toBeInstanceOf(ApiError);
  });
});
