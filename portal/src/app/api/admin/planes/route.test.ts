import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";
import { ApiError } from "@/lib/api/types";

vi.mock("@/lib/api/admin-planes", () => ({ crearPlan: vi.fn() }));

import { crearPlan } from "@/lib/api/admin-planes";
import { POST } from "./route";

const CUERPO = { nombre: "Estudio", precio_mensual: 49.9, limites: { rucs: 2 } };

function peticion(init: { sesion?: boolean; xff?: string; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest("http://localhost/api/admin/planes", { method: "POST", headers, body: init.cuerpo ?? JSON.stringify(CUERPO) });
}

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(crearPlan).mockReset();
});

/** BFF de «crear un plan» (#190). */
describe("POST /api/admin/planes", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await POST(peticion({ sesion: false }));

    expect(res.status).toBe(401);
    expect(crearPlan).not.toHaveBeenCalled();
  });

  it("reenvía el cuerpo tal cual con el JWT del administrador y responde 201 sin caché", async () => {
    vi.mocked(crearPlan).mockResolvedValue({ id: "p1" } as never);

    const res = await POST(peticion());

    expect(res.status).toBe(201);
    expect((await res.json()).datos).toEqual({ id: "p1" });
    expect(crearPlan).toHaveBeenCalledWith("jwt-admin", CUERPO, {});
    expect(res.headers.get("cache-control")).toContain("no-store");
  });

  it("un cuerpo que no es un objeto JSON responde 400 sin llamar al backend", async () => {
    for (const malo of ["{no es json", "[1]", "42"]) {
      const res = await POST(peticion({ cuerpo: malo }));
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo, malo).toBe("JSON_INVALIDO");
    }
    expect(crearPlan).not.toHaveBeenCalled();
  });

  it("manda al backend la IP de confianza ya resuelta, no la cadena que puso el navegador", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(crearPlan).mockResolvedValue({} as never);

    await POST(peticion({ xff: "6.6.6.6, 203.0.113.7" }));

    expect(crearPlan).toHaveBeenCalledWith("jwt-admin", CUERPO, { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status y el código del backend (p. ej. un nombre repetido)", async () => {
    vi.mocked(crearPlan).mockRejectedValue(new ApiError(409, "NOMBRE_DUPLICADO", "Ya existe un plan llamado «Estudio»"));

    const res = await POST(peticion());
    const json = await res.json();

    expect(res.status).toBe(409);
    expect(json.codigo).toBe("NOMBRE_DUPLICADO");
  });
});
