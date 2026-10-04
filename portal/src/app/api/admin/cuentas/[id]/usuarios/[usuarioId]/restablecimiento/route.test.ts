import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-acceso", () => ({ enviarRestablecimiento: vi.fn() }));

import { enviarRestablecimiento } from "@/lib/api/admin-acceso";
import { POST } from "./route";

const CUENTA = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const USUARIO = "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f";
const ENVIADO = { usuario_id: USUARIO, correo: "ana@negocio.pe" };

function peticion(init: { sesion?: boolean; xff?: string } = {}) {
  const headers: Record<string, string> = {};
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest(`http://localhost/api/admin/cuentas/${CUENTA}/usuarios/${USUARIO}/restablecimiento`, { method: "POST", headers });
}

const contexto = (id = CUENTA, usuarioId = USUARIO) => ({ params: Promise.resolve({ id, usuarioId }) });

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(enviarRestablecimiento).mockReset();
});

/** BFF de «mandar el correo de restablecimiento» (#183): el navegador nunca ve el JWT del administrador y los ids no llegan sin validar al backend. */
describe("POST /api/admin/cuentas/[id]/usuarios/[usuarioId]/restablecimiento", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false }), contexto());

    expect(res.status).toBe(401);
    expect(enviarRestablecimiento).not.toHaveBeenCalled();
  });

  it("un id de cuenta o de usuario que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const [cuenta, usuario] of [["../auth/me", USUARIO], [CUENTA, "no-es-un-uuid"], [`${CUENTA}/x`, USUARIO], [CUENTA, ""]]) {
      const res = await POST(peticion(), contexto(cuenta, usuario));
      expect(res.status, `${cuenta} ${usuario}`).toBe(400);
      expect((await res.json()).codigo).toBe("ID_INVALIDO");
    }
    expect(enviarRestablecimiento).not.toHaveBeenCalled();
  });

  it("pide el correo con el JWT del administrador y devuelve a quién se le mandó", async () => {
    vi.mocked(enviarRestablecimiento).mockResolvedValue(ENVIADO);

    const res = await POST(peticion(), contexto());
    const json = await res.json();

    expect(res.status).toBe(200);
    expect(json.datos).toEqual(ENVIADO);
    expect(enviarRestablecimiento).toHaveBeenCalledWith("jwt-admin", CUENTA, USUARIO, {});
  });

  it("manda al backend la IP de confianza ya resuelta, no la cadena que puso el navegador", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(enviarRestablecimiento).mockResolvedValue(ENVIADO);

    await POST(peticion({ xff: "6.6.6.6, 203.0.113.7" }), contexto());

    expect(enviarRestablecimiento).toHaveBeenCalledWith("jwt-admin", CUENTA, USUARIO, { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status y el código del backend (p. ej. un servidor sin correo configurado)", async () => {
    vi.mocked(enviarRestablecimiento).mockRejectedValue(new ApiError(503, "CORREO_NO_CONFIGURADO", "El envío de correos no está habilitado"));

    const res = await POST(peticion(), contexto());

    expect(res.status).toBe(503);
    expect((await res.json()).codigo).toBe("CORREO_NO_CONFIGURADO");
  });

  it("la respuesta no debe quedar en ninguna caché", async () => {
    vi.mocked(enviarRestablecimiento).mockResolvedValue(ENVIADO);

    const res = await POST(peticion(), contexto());

    expect(res.headers.get("cache-control")).toContain("no-store");
  });
});
