import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";
import { ApiError } from "@/lib/api/types";

vi.mock("@/lib/api/admin-planes", () => ({ editarPlan: vi.fn(), eliminarPlan: vi.fn() }));

import { editarPlan, eliminarPlan } from "@/lib/api/admin-planes";
import { DELETE, PUT } from "./route";

const ID = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const CUERPO = { nombre: "Estudio", precio_mensual: 49.9, limites: { rucs: 2 } };

function peticion(metodo: "PUT" | "DELETE", init: { sesion?: boolean; xff?: string; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  const body = metodo === "PUT" ? (init.cuerpo ?? JSON.stringify(CUERPO)) : undefined;
  return new NextRequest(`http://localhost/api/admin/planes/${ID}`, { method: metodo, headers, body });
}

const contexto = (id = ID) => ({ params: Promise.resolve({ id }) });

afterEach(() => {
  vi.unstubAllEnvs();
  vi.mocked(editarPlan).mockReset();
  vi.mocked(eliminarPlan).mockReset();
});

/** BFF de «editar un plan» (#190). */
describe("PUT /api/admin/planes/[id]", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const res = await PUT(peticion("PUT", { sesion: false }), contexto());

    expect(res.status).toBe(401);
    expect(editarPlan).not.toHaveBeenCalled();
  });

  it("un id que no es un UUID responde 400 sin llamar al backend", async () => {
    for (const malo of ["../auth/me", "no-es-un-uuid", `${ID}/x`, ""]) {
      const res = await PUT(peticion("PUT"), contexto(malo));
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo, malo).toBe("ID_INVALIDO");
    }
    expect(editarPlan).not.toHaveBeenCalled();
  });

  it("un cuerpo que no es un objeto JSON responde 400 sin llamar al backend", async () => {
    for (const malo of ["{no es json", "[1]", ""]) {
      const res = await PUT(peticion("PUT", { cuerpo: malo }), contexto());
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo, malo).toBe("JSON_INVALIDO");
    }
    expect(editarPlan).not.toHaveBeenCalled();
  });

  it("reenvía el id y el cuerpo con el JWT del administrador y responde sin caché", async () => {
    vi.mocked(editarPlan).mockResolvedValue({ id: ID } as never);

    const res = await PUT(peticion("PUT"), contexto());

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual({ id: ID });
    expect(editarPlan).toHaveBeenCalledWith("jwt-admin", ID, CUERPO, {});
    expect(res.headers.get("cache-control")).toContain("no-store");
  });

  it("manda al backend la IP de confianza ya resuelta", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(editarPlan).mockResolvedValue({} as never);

    await PUT(peticion("PUT", { xff: "6.6.6.6, 203.0.113.7" }), contexto());

    expect(editarPlan).toHaveBeenCalledWith("jwt-admin", ID, CUERPO, { "X-Forwarded-For": "203.0.113.7" });
  });

  it("propaga el status y el código del backend", async () => {
    vi.mocked(editarPlan).mockRejectedValue(new ApiError(404, "NO_ENCONTRADO", "El plan no existe"));

    const res = await PUT(peticion("PUT"), contexto());

    expect(res.status).toBe(404);
    expect((await res.json()).codigo).toBe("NO_ENCONTRADO");
  });
});

describe("DELETE /api/admin/planes/[id]", () => {
  it("llama a eliminar con el JWT y el id", async () => {
    vi.mocked(eliminarPlan).mockResolvedValue(null as never);

    const res = await DELETE(peticion("DELETE"), contexto());

    expect(res.status).toBe(200);
    expect(eliminarPlan).toHaveBeenCalledWith("jwt-admin", ID, {});
  });

  it("sin sesión responde 401 y con un plan en uso propaga el 409", async () => {
    expect((await DELETE(peticion("DELETE", { sesion: false }), contexto())).status).toBe(401);
    expect(eliminarPlan).not.toHaveBeenCalled();

    vi.mocked(eliminarPlan).mockRejectedValue(new ApiError(409, "PLAN_EN_USO", "lo tienen 2 cuentas"));
    const res = await DELETE(peticion("DELETE"), contexto());

    expect(res.status).toBe(409);
    expect((await res.json()).codigo).toBe("PLAN_EN_USO");
  });
});
