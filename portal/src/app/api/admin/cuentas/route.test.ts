import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-alta", () => ({
  altaAsistida: vi.fn(),
}));

import { altaAsistida } from "@/lib/api/admin-alta";
import { POST } from "./route";

const CUERPO = {
  nombre: "Comercial Andina",
  email: "ana@andina.pe",
  telefono: "987654321",
  empresa: { ruc: "20100066603", razon_social: "COMERCIAL ANDINA SAC", entorno: "BETA" },
  serie: { tipo: "01", serie: "F001" },
};

const CREADA = {
  cuenta_id: "c1",
  tenant_id: "t1",
  ruc: "20100066603",
  api_key: "fk_secreta",
  serie: { tipo: "01", serie: "F001" },
  invitacion_enviada: true,
};

function peticion(init: { sesion?: boolean; xff?: string; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest("http://localhost/api/admin/cuentas", { method: "POST", headers, body: init.cuerpo ?? JSON.stringify(CUERPO) });
}

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(altaAsistida).mockReset();
});

/** BFF del alta asistida (#188): el navegador nunca ve el JWT del administrador, y la API key viaja una sola vez en esta respuesta. */
describe("POST /api/admin/cuentas", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false }));

    expect(res.status).toBe(401);
    expect(altaAsistida).not.toHaveBeenCalled();
  });

  it("reenvía el cuerpo con el JWT del administrador y devuelve el alta con 201", async () => {
    vi.mocked(altaAsistida).mockResolvedValue(CREADA);

    const res = await POST(peticion());
    const json = await res.json();

    expect(res.status).toBe(201);
    expect(json.estado).toBe("exito");
    expect(json.datos).toEqual(CREADA);
    expect(altaAsistida).toHaveBeenCalledWith("jwt-admin", CUERPO, {});
  });

  it("la respuesta lleva la API key: no debe quedar en ninguna caché", async () => {
    vi.mocked(altaAsistida).mockResolvedValue(CREADA);

    const res = await POST(peticion());

    expect(res.headers.get("cache-control")).toContain("no-store");
  });

  it("manda al backend la IP de confianza ya resuelta, no la cadena que puso el navegador", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(altaAsistida).mockResolvedValue(CREADA);

    await POST(peticion({ xff: "6.6.6.6, 203.0.113.7" }));

    expect(altaAsistida).toHaveBeenCalledWith("jwt-admin", CUERPO, { "X-Forwarded-For": "203.0.113.7" });
  });

  it("sin saltos de confianza no reenvía ninguna IP", async () => {
    vi.mocked(altaAsistida).mockResolvedValue(CREADA);

    await POST(peticion({ xff: "6.6.6.6" }));

    expect(altaAsistida).toHaveBeenCalledWith("jwt-admin", CUERPO, {});
  });

  it("propaga el status y el código del backend (p. ej. un correo ya registrado)", async () => {
    vi.mocked(altaAsistida).mockRejectedValue(new ApiError(409, "DUPLICADO", "Ya existe una cuenta con ese correo"));

    const res = await POST(peticion());
    const json = await res.json();

    expect(res.status).toBe(409);
    expect(json.codigo).toBe("DUPLICADO");
    expect(json.mensaje).toBe("Ya existe una cuenta con ese correo");
  });

  it("un cuerpo que no es JSON responde 400 sin llamar al backend", async () => {
    const res = await POST(peticion({ cuerpo: "{no es json" }));

    expect(res.status).toBe(400);
    expect((await res.json()).codigo).toBe("JSON_INVALIDO");
    expect(altaAsistida).not.toHaveBeenCalled();
  });
});
