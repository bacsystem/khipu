import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";
import { ApiError } from "@/lib/api/types";

vi.mock("@/lib/api/admin-plan-de-cuenta", () => ({ cambiarPlanDeCuenta: vi.fn() }));

import { cambiarPlanDeCuenta } from "@/lib/api/admin-plan-de-cuenta";
import { POST } from "./route";

const ID = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const CUERPO = { plan_id: "1c2d3e4f-0f1c-4b53-9a1e-2f6f6d0c7a22", vence_en: "2026-11-01T05:00:00.000Z", dias_de_gracia: 5 };

function peticion(init: { sesion?: boolean; xff?: string; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest(`http://localhost/api/admin/cuentas/${ID}/plan`, { method: "POST", headers, body: init.cuerpo ?? JSON.stringify(CUERPO) });
}

const contexto = (id = ID) => ({ params: Promise.resolve({ id }) });

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(cambiarPlanDeCuenta).mockReset();
});

/** BFF de «cambiar el plan de una cuenta» (#191): el navegador nunca ve el JWT del administrador y el id no llega sin validar a la URL del backend. */
describe("POST /api/admin/cuentas/[id]/plan", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false }), contexto());

    expect(res.status).toBe(401);
    expect(cambiarPlanDeCuenta).not.toHaveBeenCalled();
  });

  it("un id que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const malo of ["../auth/me", "no-es-un-uuid", `${ID}/x`, ""]) {
      const res = await POST(peticion(), contexto(malo));
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo, malo).toBe("ID_INVALIDO");
    }
    expect(cambiarPlanDeCuenta).not.toHaveBeenCalled();
  });

  it("un cuerpo que no es un objeto JSON responde 400 sin llamar al backend", async () => {
    for (const malo of ["{no es json", "[1]", "42", ""]) {
      const res = await POST(peticion({ cuerpo: malo }), contexto());
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo, malo).toBe("JSON_INVALIDO");
    }
    expect(cambiarPlanDeCuenta).not.toHaveBeenCalled();
  });

  it("reenvía el cuerpo tal cual con el JWT del administrador y devuelve el plan sin caché", async () => {
    vi.mocked(cambiarPlanDeCuenta).mockResolvedValue({ cuenta_id: ID } as never);

    const res = await POST(peticion(), contexto());

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual({ cuenta_id: ID });
    expect(cambiarPlanDeCuenta).toHaveBeenCalledWith("jwt-admin", ID, CUERPO, {});
    expect(res.headers.get("cache-control")).toContain("no-store");
  });

  it("manda al backend la IP de confianza ya resuelta, no la cadena que puso el navegador", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(cambiarPlanDeCuenta).mockResolvedValue({} as never);

    await POST(peticion({ xff: "6.6.6.6, 203.0.113.7" }), contexto());

    expect(cambiarPlanDeCuenta).toHaveBeenCalledWith("jwt-admin", ID, CUERPO, { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status y el código del backend (p. ej. otro administrador cambió el plan)", async () => {
    vi.mocked(cambiarPlanDeCuenta).mockRejectedValue(new ApiError(409, "CAMBIO_CONCURRENTE", "Otro administrador cambió el plan"));

    const res = await POST(peticion(), contexto());
    const json = await res.json();

    expect(res.status).toBe(409);
    expect(json.codigo).toBe("CAMBIO_CONCURRENTE");
    expect(json.mensaje).toBe("Otro administrador cambió el plan");
  });
});
