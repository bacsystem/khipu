import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-acciones-empresa", async (original) => ({ ...(await original<typeof import("@/lib/api/admin-acciones-empresa")>()), probarConexionEmpresa: vi.fn() }));

import { probarConexionEmpresa } from "@/lib/api/admin-acciones-empresa";
import { POST } from "./route";

const EMPRESA = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const RESPUESTA = { resultado: "CONECTADO", entorno: "BETA" };

function peticion(init: { sesion?: boolean; xff?: string; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest(`http://localhost/api/admin/empresas/${EMPRESA}/prueba-de-conexion`, { method: "POST", headers, body: init.cuerpo });
}

const contexto = (id = EMPRESA) => ({ params: Promise.resolve({ id }) });

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(probarConexionEmpresa).mockReset();
});

/** BFF de «probar la conexión de una empresa» (#187): el navegador nunca ve el JWT del administrador y los ids no llegan sin validar a la URL del backend. */
describe("POST /api/admin/empresas/[id]/prueba-de-conexion", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false }), contexto());

    expect(res.status).toBe(401);
    expect(probarConexionEmpresa).not.toHaveBeenCalled();
  });

  it("un id que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const malo of ["../auth/me", "no-es-un-uuid", `${EMPRESA}/x`, ""]) {
      const res = await POST(peticion({}), contexto(malo));
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo).toBe("ID_INVALIDO");
    }
    expect(probarConexionEmpresa).not.toHaveBeenCalled();
  });

  it("llama al backend con el JWT del administrador y devuelve su respuesta", async () => {
    vi.mocked(probarConexionEmpresa).mockResolvedValue(RESPUESTA as never);

    const res = await POST(peticion({}), contexto());

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual(RESPUESTA);
    expect(probarConexionEmpresa).toHaveBeenCalledWith("jwt-admin", EMPRESA, {});
  });

  it("manda al backend la IP de confianza ya resuelta, no la cadena que puso el navegador", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(probarConexionEmpresa).mockResolvedValue(RESPUESTA as never);

    await POST(peticion({ xff: "6.6.6.6, 203.0.113.7" }), contexto());

    expect(probarConexionEmpresa).toHaveBeenCalledWith("jwt-admin", EMPRESA, { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status y el código del backend", async () => {
    vi.mocked(probarConexionEmpresa).mockRejectedValue(new ApiError(409, "SOL_NO_CARGADAS", "La empresa no tiene credenciales SOL cargadas"));

    const res = await POST(peticion({}), contexto());

    expect(res.status).toBe(409);
    expect((await res.json()).codigo).toBe("SOL_NO_CARGADAS");
  });

  it("la respuesta no debe quedar en ninguna caché", async () => {
    vi.mocked(probarConexionEmpresa).mockResolvedValue(RESPUESTA as never);

    const res = await POST(peticion({}), contexto());

    expect(res.headers.get("cache-control")).toContain("no-store");
  });
});
