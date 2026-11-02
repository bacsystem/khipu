import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-acciones-empresa", async (original) => ({ ...(await original<typeof import("@/lib/api/admin-acciones-empresa")>()), cambiarEntornoEmpresa: vi.fn() }));

import { cambiarEntornoEmpresa } from "@/lib/api/admin-acciones-empresa";
import { POST } from "./route";

const EMPRESA = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const RESPUESTA = { empresa_id: EMPRESA, desde: "BETA", hacia: "PRODUCCION" };

function peticion(init: { sesion?: boolean; xff?: string; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest(`http://localhost/api/admin/empresas/${EMPRESA}/entorno`, { method: "POST", headers, body: init.cuerpo });
}

const contexto = (id = EMPRESA) => ({ params: Promise.resolve({ id }) });

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(cambiarEntornoEmpresa).mockReset();
});

/** BFF de «cambiar el entorno de una empresa» (#187): el navegador nunca ve el JWT del administrador y los ids no llegan sin validar a la URL del backend. */
describe("POST /api/admin/empresas/[id]/entorno", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false, cuerpo: JSON.stringify({ entorno: "PRODUCCION" }) }), contexto());

    expect(res.status).toBe(401);
    expect(cambiarEntornoEmpresa).not.toHaveBeenCalled();
  });

  it("un id que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const malo of ["../auth/me", "no-es-un-uuid", `${EMPRESA}/x`, ""]) {
      const res = await POST(peticion({ cuerpo: JSON.stringify({ entorno: "PRODUCCION" }) }), contexto(malo));
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo).toBe("ID_INVALIDO");
    }
    expect(cambiarEntornoEmpresa).not.toHaveBeenCalled();
  });

  it("llama al backend con el JWT del administrador y devuelve su respuesta", async () => {
    vi.mocked(cambiarEntornoEmpresa).mockResolvedValue(RESPUESTA as never);

    const res = await POST(peticion({ cuerpo: JSON.stringify({ entorno: "PRODUCCION" }) }), contexto());

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual(RESPUESTA);
    expect(cambiarEntornoEmpresa).toHaveBeenCalledWith("jwt-admin", EMPRESA, "PRODUCCION", {});
  });

  it("manda al backend la IP de confianza ya resuelta, no la cadena que puso el navegador", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(cambiarEntornoEmpresa).mockResolvedValue(RESPUESTA as never);

    await POST(peticion({ xff: "6.6.6.6, 203.0.113.7", cuerpo: JSON.stringify({ entorno: "PRODUCCION" }) }), contexto());

    expect(cambiarEntornoEmpresa).toHaveBeenCalledWith("jwt-admin", EMPRESA, "PRODUCCION", { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status y el código del backend", async () => {
    vi.mocked(cambiarEntornoEmpresa).mockRejectedValue(new ApiError(409, "EMPRESA_CON_ENVIOS_PENDIENTES", "La empresa tiene envíos pendientes"));

    const res = await POST(peticion({ cuerpo: JSON.stringify({ entorno: "PRODUCCION" }) }), contexto());

    expect(res.status).toBe(409);
    expect((await res.json()).codigo).toBe("EMPRESA_CON_ENVIOS_PENDIENTES");
  });

  it("la respuesta no debe quedar en ninguna caché", async () => {
    vi.mocked(cambiarEntornoEmpresa).mockResolvedValue(RESPUESTA as never);

    const res = await POST(peticion({ cuerpo: JSON.stringify({ entorno: "PRODUCCION" }) }), contexto());

    expect(res.headers.get("cache-control")).toContain("no-store");
  });

  it("un entorno que no existe se rechaza con 422 sin llamar al backend", async () => {
    for (const malo of [{ entorno: "PRUEBAS" }, { entorno: "beta" }, { entorno: 1 }, {}]) {
      const res = await POST(peticion({ cuerpo: JSON.stringify(malo) }), contexto());
      expect(res.status, JSON.stringify(malo)).toBe(422);
      expect((await res.json()).codigo).toBe("ENTORNO_INVALIDO");
    }
    expect(cambiarEntornoEmpresa).not.toHaveBeenCalled();
  });

  it("un cuerpo que no es JSON, o ausente, responde 400 sin llamar al backend", async () => {
    expect((await POST(peticion({ cuerpo: "{no es json" }), contexto())).status).toBe(400);
    expect((await POST(peticion(), contexto())).status).toBe(400);
    expect(cambiarEntornoEmpresa).not.toHaveBeenCalled();
  });
});
