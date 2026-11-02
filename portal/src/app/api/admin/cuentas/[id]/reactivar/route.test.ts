import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-suspension", () => ({ reactivarCuenta: vi.fn() }));

import { reactivarCuenta } from "@/lib/api/admin-suspension";
import { POST } from "./route";

const ID = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const ACTIVA = { cuenta_id: ID, estado: "ACTIVA" };

function peticion(init: { sesion?: boolean; xff?: string } = {}) {
  const headers: Record<string, string> = {};
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest(`http://localhost/api/admin/cuentas/${ID}/reactivar`, { method: "POST", headers });
}

const contexto = (id = ID) => ({ params: Promise.resolve({ id }) });

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(reactivarCuenta).mockReset();
});

/** BFF de «reactivar una cuenta» (#182): mismas garantías que suspender. */
describe("POST /api/admin/cuentas/[id]/reactivar", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false }), contexto());

    expect(res.status).toBe(401);
    expect(reactivarCuenta).not.toHaveBeenCalled();
  });

  it("un id que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const malo of ["../auth/me", "no-es-un-uuid", `${ID}/x`, ""]) {
      const res = await POST(peticion(), contexto(malo));
      expect(res.status, malo).toBe(400);
    }
    expect(reactivarCuenta).not.toHaveBeenCalled();
  });

  it("reactiva con el JWT del administrador y devuelve el nuevo estado", async () => {
    vi.mocked(reactivarCuenta).mockResolvedValue(ACTIVA as never);

    const res = await POST(peticion(), contexto());
    const json = await res.json();

    expect(res.status).toBe(200);
    expect(json.datos).toEqual(ACTIVA);
    expect(reactivarCuenta).toHaveBeenCalledWith("jwt-admin", ID, {});
  });

  it("manda al backend la IP de confianza ya resuelta", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(reactivarCuenta).mockResolvedValue(ACTIVA as never);

    await POST(peticion({ xff: "6.6.6.6, 203.0.113.7" }), contexto());

    expect(reactivarCuenta).toHaveBeenCalledWith("jwt-admin", ID, { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status y el código del backend (p. ej. una cuenta que no estaba suspendida)", async () => {
    vi.mocked(reactivarCuenta).mockRejectedValue(new ApiError(409, "CUENTA_NO_SUSPENDIDA", "La cuenta no está suspendida"));

    const res = await POST(peticion(), contexto());

    expect(res.status).toBe(409);
    expect((await res.json()).codigo).toBe("CUENTA_NO_SUSPENDIDA");
  });
});
