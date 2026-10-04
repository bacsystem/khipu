import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-acciones-empresa", async (original) => ({ ...(await original<typeof import("@/lib/api/admin-acciones-empresa")>()), revocarApiKeyEmpresa: vi.fn() }));

import { revocarApiKeyEmpresa } from "@/lib/api/admin-acciones-empresa";
import { POST } from "./route";

const EMPRESA = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const KEY = "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f";
const RESPUESTA = { api_key_id: KEY, revocada_en: "2026-10-03T09:00:00Z" };

function peticion(init: { sesion?: boolean; xff?: string; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest(`http://localhost/api/admin/empresas/${EMPRESA}/api-keys/${KEY}/revocar`, { method: "POST", headers, body: init.cuerpo });
}

const contexto = (id = EMPRESA, apiKeyId = KEY) => ({ params: Promise.resolve({ id, apiKeyId }) });

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(revocarApiKeyEmpresa).mockReset();
});

/** BFF de «revocar una API key» (#187): el navegador nunca ve el JWT del administrador y los ids no llegan sin validar a la URL del backend. */
describe("POST /api/admin/empresas/[id]/api-keys/[apiKeyId]/revocar", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false }), contexto());

    expect(res.status).toBe(401);
    expect(revocarApiKeyEmpresa).not.toHaveBeenCalled();
  });

  it("un id que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const malo of ["../auth/me", "no-es-un-uuid", `${EMPRESA}/x`, ""]) {
      const res = await POST(peticion({}), contexto(malo, KEY));
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo).toBe("ID_INVALIDO");
    }
    expect(revocarApiKeyEmpresa).not.toHaveBeenCalled();
  });

  it("llama al backend con el JWT del administrador y devuelve su respuesta", async () => {
    vi.mocked(revocarApiKeyEmpresa).mockResolvedValue(RESPUESTA as never);

    const res = await POST(peticion({}), contexto());

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual(RESPUESTA);
    expect(revocarApiKeyEmpresa).toHaveBeenCalledWith("jwt-admin", EMPRESA, KEY, {});
  });

  it("manda al backend la IP de confianza ya resuelta, no la cadena que puso el navegador", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(revocarApiKeyEmpresa).mockResolvedValue(RESPUESTA as never);

    await POST(peticion({ xff: "6.6.6.6, 203.0.113.7" }), contexto());

    expect(revocarApiKeyEmpresa).toHaveBeenCalledWith("jwt-admin", EMPRESA, KEY, { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status y el código del backend", async () => {
    vi.mocked(revocarApiKeyEmpresa).mockRejectedValue(new ApiError(409, "API_KEY_YA_REVOCADA", "La API key ya estaba revocada"));

    const res = await POST(peticion({}), contexto());

    expect(res.status).toBe(409);
    expect((await res.json()).codigo).toBe("API_KEY_YA_REVOCADA");
  });

  it("la respuesta no debe quedar en ninguna caché", async () => {
    vi.mocked(revocarApiKeyEmpresa).mockResolvedValue(RESPUESTA as never);

    const res = await POST(peticion({}), contexto());

    expect(res.headers.get("cache-control")).toContain("no-store");
  });

  it("un id de key que no es un UUID responde 400 sin llamar al backend", async () => {
    const res = await POST(peticion(), contexto(EMPRESA, "../x"));

    expect(res.status).toBe(400);
    expect(revocarApiKeyEmpresa).not.toHaveBeenCalled();
  });
});
