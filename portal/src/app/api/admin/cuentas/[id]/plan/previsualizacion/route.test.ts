import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";
import { ApiError } from "@/lib/api/types";

vi.mock("@/lib/api/admin-plan-de-cuenta", () => ({ previsualizarCambioDePlan: vi.fn() }));

import { previsualizarCambioDePlan } from "@/lib/api/admin-plan-de-cuenta";
import { GET } from "./route";

const ID = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const PLAN = "1c2d3e4f-0f1c-4b53-9a1e-2f6f6d0c7a22";

function peticion(init: { sesion?: boolean; xff?: string; query?: string } = {}) {
  const headers: Record<string, string> = {};
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest(`http://localhost/api/admin/cuentas/${ID}/plan/previsualizacion${init.query ?? `?plan_id=${PLAN}`}`, { headers });
}

const contexto = (id = ID) => ({ params: Promise.resolve({ id }) });

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(previsualizarCambioDePlan).mockReset();
});

/** BFF de la previsualización de un cambio de plan (#191): solo lectura, pero con la misma disciplina de sesión y de ids. */
describe("GET /api/admin/cuentas/[id]/plan/previsualizacion", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await GET(peticion({ sesion: false }), contexto());

    expect(res.status).toBe(401);
    expect(previsualizarCambioDePlan).not.toHaveBeenCalled();
  });

  it("un id de cuenta que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const malo of ["../auth/me", "no-es-un-uuid", ""]) {
      const res = await GET(peticion(), contexto(malo));
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo, malo).toBe("ID_INVALIDO");
    }
    expect(previsualizarCambioDePlan).not.toHaveBeenCalled();
  });

  it("un plan que falta o que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const query of ["", "?plan_id=", "?plan_id=no-es-un-uuid", `?plan_id=${PLAN}/x`, "?otro=1"]) {
      const res = await GET(peticion({ query }), contexto());
      expect(res.status, query).toBe(400);
      expect((await res.json()).codigo, query).toBe("PLAN_INVALIDO");
    }
    expect(previsualizarCambioDePlan).not.toHaveBeenCalled();
  });

  it("pide la previsualización con el JWT, la cuenta y el plan, y no deja la respuesta en caché", async () => {
    vi.mocked(previsualizarCambioDePlan).mockResolvedValue({ direccion: "SUBIDA" } as never);

    const res = await GET(peticion(), contexto());

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual({ direccion: "SUBIDA" });
    expect(previsualizarCambioDePlan).toHaveBeenCalledWith("jwt-admin", ID, PLAN, {});
    expect(res.headers.get("cache-control")).toContain("no-store");
  });

  it("manda al backend la IP de confianza ya resuelta", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(previsualizarCambioDePlan).mockResolvedValue({} as never);

    await GET(peticion({ xff: "6.6.6.6, 203.0.113.7" }), contexto());

    expect(previsualizarCambioDePlan).toHaveBeenCalledWith("jwt-admin", ID, PLAN, { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status y el código del backend (p. ej. un plan fuera de la oferta)", async () => {
    vi.mocked(previsualizarCambioDePlan).mockRejectedValue(new ApiError(409, "PLAN_INACTIVO", "El plan está fuera de la oferta"));

    const res = await GET(peticion(), contexto());

    expect(res.status).toBe(409);
    expect((await res.json()).codigo).toBe("PLAN_INACTIVO");
  });
});
