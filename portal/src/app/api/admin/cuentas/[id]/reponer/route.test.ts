import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-baja", () => ({ reponerCuenta: vi.fn() }));

import { reponerCuenta } from "@/lib/api/admin-baja";
import { POST } from "./route";

const ID = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const REPUESTA = { cuenta_id: ID };

function peticion(init: { sesion?: boolean; xff?: string } = {}) {
  const headers: Record<string, string> = {};
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest(`http://localhost/api/admin/cuentas/${ID}/reponer`, { method: "POST", headers });
}

const contexto = (id = ID) => ({ params: Promise.resolve({ id }) });

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(reponerCuenta).mockReset();
});

/** BFF de «reponer una cuenta» (#201): mismas garantías que dar de baja. */
describe("POST /api/admin/cuentas/[id]/reponer", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false }), contexto());

    expect(res.status).toBe(401);
    expect(reponerCuenta).not.toHaveBeenCalled();
  });

  it("un id que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const malo of ["../auth/me", "no-es-un-uuid", `${ID}/x`, ""]) {
      const res = await POST(peticion(), contexto(malo));
      expect(res.status, malo).toBe(400);
    }
    expect(reponerCuenta).not.toHaveBeenCalled();
  });

  it("repone con el JWT del administrador y devuelve cómo quedó", async () => {
    vi.mocked(reponerCuenta).mockResolvedValue(REPUESTA as never);

    const res = await POST(peticion(), contexto());
    const json = await res.json();

    expect(res.status).toBe(200);
    expect(json.datos).toEqual(REPUESTA);
    expect(reponerCuenta).toHaveBeenCalledWith("jwt-admin", ID, {});
  });

  it("manda al backend la IP de confianza ya resuelta", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(reponerCuenta).mockResolvedValue(REPUESTA as never);

    await POST(peticion({ xff: "6.6.6.6, 203.0.113.7" }), contexto());

    expect(reponerCuenta).toHaveBeenCalledWith("jwt-admin", ID, { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status y el código del backend (p. ej. una cuenta que no estaba dada de baja)", async () => {
    vi.mocked(reponerCuenta).mockRejectedValue(new ApiError(409, "CUENTA_NO_DE_BAJA", "La cuenta no está dada de baja"));

    const res = await POST(peticion(), contexto());

    expect(res.status).toBe(409);
    expect((await res.json()).codigo).toBe("CUENTA_NO_DE_BAJA");
  });
});
