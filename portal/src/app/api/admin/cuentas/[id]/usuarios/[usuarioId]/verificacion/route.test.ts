import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-acceso", () => ({ reenviarVerificacion: vi.fn() }));

import { reenviarVerificacion } from "@/lib/api/admin-acceso";
import { POST } from "./route";

const CUENTA = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const USUARIO = "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f";
const ENVIADO = { usuario_id: USUARIO, correo: "ana@negocio.pe" };

function peticion(init: { sesion?: boolean; xff?: string } = {}) {
  const headers: Record<string, string> = {};
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest(`http://localhost/api/admin/cuentas/${CUENTA}/usuarios/${USUARIO}/verificacion`, { method: "POST", headers });
}

const contexto = (id = CUENTA, usuarioId = USUARIO) => ({ params: Promise.resolve({ id, usuarioId }) });

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(reenviarVerificacion).mockReset();
});

/** BFF de «reenviar la verificación de correo» (#183): mismas garantías que el restablecimiento. */
describe("POST /api/admin/cuentas/[id]/usuarios/[usuarioId]/verificacion", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false }), contexto());

    expect(res.status).toBe(401);
    expect(reenviarVerificacion).not.toHaveBeenCalled();
  });

  it("un id que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const [cuenta, usuario] of [["../auth/me", USUARIO], [CUENTA, "no-es-un-uuid"], [CUENTA, ""]]) {
      const res = await POST(peticion(), contexto(cuenta, usuario));
      expect(res.status, `${cuenta} ${usuario}`).toBe(400);
    }
    expect(reenviarVerificacion).not.toHaveBeenCalled();
  });

  it("reenvía con el JWT del administrador y devuelve a quién se le mandó", async () => {
    vi.mocked(reenviarVerificacion).mockResolvedValue(ENVIADO);

    const res = await POST(peticion(), contexto());

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual(ENVIADO);
    expect(reenviarVerificacion).toHaveBeenCalledWith("jwt-admin", CUENTA, USUARIO, {});
  });

  it("manda al backend la IP de confianza ya resuelta", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(reenviarVerificacion).mockResolvedValue(ENVIADO);

    await POST(peticion({ xff: "6.6.6.6, 203.0.113.7" }), contexto());

    expect(reenviarVerificacion).toHaveBeenCalledWith("jwt-admin", CUENTA, USUARIO, { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status y el código del backend (p. ej. un correo ya verificado)", async () => {
    vi.mocked(reenviarVerificacion).mockRejectedValue(new ApiError(409, "CORREO_YA_VERIFICADO", "El correo ya está verificado"));

    const res = await POST(peticion(), contexto());

    expect(res.status).toBe(409);
    expect((await res.json()).codigo).toBe("CORREO_YA_VERIFICADO");
  });
});
