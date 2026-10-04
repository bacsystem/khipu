import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-errores", () => ({ reintentarEnvio: vi.fn() }));

import { reintentarEnvio } from "@/lib/api/admin-errores";
import { POST } from "./route";

const ID = "00000000-0000-4000-d000-000000000101";
const RESULTADO = { comprobante_id: ID, estado: "ERROR_ENVIO", intentos: 4, fault: { codigo: "0000", mensaje: "SUNAT respondió HTTP 503" } };

function peticion(init: { sesion?: boolean; xff?: string } = {}) {
  const headers: Record<string, string> = {};
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest(`http://localhost/api/admin/comprobantes/${ID}/reintento`, { method: "POST", headers });
}

const contexto = (id = ID) => ({ params: Promise.resolve({ id }) });

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(reintentarEnvio).mockReset();
});

/** BFF de «reintentar el envío» (#196): reenvía a SUNAT de verdad, así que el JWT nunca llega al navegador y el id no llega sin validar a la URL del backend. */
describe("POST /api/admin/comprobantes/[id]/reintento", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false }), contexto());

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("NO_AUTORIZADO");
    expect(reintentarEnvio).not.toHaveBeenCalled();
  });

  it("un id que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const malo of ["../auth/me", "no-es-un-uuid", `${ID}/x`, ""]) {
      const res = await POST(peticion(), contexto(malo));
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo).toBe("ID_INVALIDO");
    }
    expect(reintentarEnvio).not.toHaveBeenCalled();
  });

  it("reintenta con el JWT del administrador y devuelve cómo quedó, sin caché", async () => {
    vi.mocked(reintentarEnvio).mockResolvedValue(RESULTADO as never);

    const res = await POST(peticion(), contexto());

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual(RESULTADO);
    expect(reintentarEnvio).toHaveBeenCalledWith("jwt-admin", ID, {});
    expect(res.headers.get("cache-control")).toContain("no-store");
  });

  it("manda al backend la IP de confianza ya resuelta, no la cadena que puso el navegador", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(reintentarEnvio).mockResolvedValue(RESULTADO as never);

    await POST(peticion({ xff: "6.6.6.6, 203.0.113.7" }), contexto());

    expect(reintentarEnvio).toHaveBeenCalledWith("jwt-admin", ID, { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status y el código del backend (un estado que no admite el envío, o el plazo vencido)", async () => {
    vi.mocked(reintentarEnvio).mockRejectedValue(new ApiError(409, "ESTADO_NO_ENVIABLE", "El comprobante está en estado ACEPTADO"));
    const res = await POST(peticion(), contexto());
    expect(res.status).toBe(409);
    expect((await res.json()).codigo).toBe("ESTADO_NO_ENVIABLE");

    vi.mocked(reintentarEnvio).mockRejectedValue(new ApiError(409, "FUERA_DE_PLAZO", "2108 - fuera de plazo"));
    expect((await (await POST(peticion(), contexto())).json()).codigo).toBe("FUERA_DE_PLAZO");
  });

  it("un fallo que no es del backend responde 502", async () => {
    vi.mocked(reintentarEnvio).mockRejectedValue(new Error("caído"));

    expect((await POST(peticion(), contexto())).status).toBe(502);
  });
});
