import { NextRequest } from "next/server";
import { describe, expect, it, vi } from "vitest";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";
import { ApiError } from "@/lib/api/types";
import { leerCuerpoDePlan, sobrePlan } from "./comun";

const ID = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";

function peticion(init: { sesion?: boolean; xff?: string; cuerpo?: string } = {}) {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (init.sesion !== false) headers.cookie = `${COOKIE_ADMIN_ACCESS}=jwt-admin`;
  if (init.xff !== undefined) headers["x-forwarded-for"] = init.xff;
  return new NextRequest(`http://localhost/api/admin/planes/${ID}`, { method: "POST", headers, body: init.cuerpo });
}

const contexto = (id = ID) => ({ params: Promise.resolve({ id }) });

/** El BFF de planes (#190): el navegador nunca ve el JWT del administrador y el id no llega sin validar a la URL del backend. */
describe("sobrePlan", () => {
  it("sin sesión de administrador responde 401 y no llama al backend", async () => {
    const hacer = vi.fn();

    const res = await sobrePlan(peticion({ sesion: false }), contexto(), hacer);

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("NO_AUTORIZADO");
    expect(hacer).not.toHaveBeenCalled();
  });

  /** Lo que llega por la URL no se pega en la del backend: `../` o un espacio no son un id de plan. */
  it("un id que no es un UUID responde 400 sin llamar al backend", async () => {
    const hacer = vi.fn();
    for (const malo of ["../auth/me", "no-es-un-uuid", `${ID}/x`, ""]) {
      const res = await sobrePlan(peticion(), contexto(malo), hacer);
      expect(res.status, malo).toBe(400);
      expect((await res.json()).codigo).toBe("ID_INVALIDO");
    }
    expect(hacer).not.toHaveBeenCalled();
  });

  it("llama con el JWT, el id y la IP de confianza ya resuelta, y devuelve los datos sin caché", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    const hacer = vi.fn().mockResolvedValue({ id: ID });

    const res = await sobrePlan(peticion({ xff: "6.6.6.6, 203.0.113.7" }), contexto(), hacer);

    expect(res.status).toBe(200);
    expect((await res.json()).datos).toEqual({ id: ID });
    expect(hacer).toHaveBeenCalledWith("jwt-admin", ID, { "X-Forwarded-For": "203.0.113.7" });
    expect(res.headers.get("cache-control")).toContain("no-store");
    vi.unstubAllEnvs();
  });

  it("propaga el status y el código del backend", async () => {
    const hacer = vi.fn().mockRejectedValue(new ApiError(409, "PLAN_EN_USO", "lo tienen 2 cuentas"));

    const res = await sobrePlan(peticion(), contexto(), hacer);
    const json = await res.json();

    expect(res.status).toBe(409);
    expect(json.codigo).toBe("PLAN_EN_USO");
    expect(json.mensaje).toBe("lo tienen 2 cuentas");
  });
});

describe("leerCuerpoDePlan", () => {
  it("un objeto JSON se devuelve tal cual, sin tocarlo (las reglas las pone el backend)", async () => {
    const cuerpo = { nombre: "Estudio", precio_mensual: 49.9, limites: { rucs: 2 } };

    const r = await leerCuerpoDePlan(peticion({ cuerpo: JSON.stringify(cuerpo) }));

    expect(r).toEqual({ cuerpo });
  });

  it("lo que no es un objeto JSON se rechaza con 400 antes de llegar al backend", async () => {
    for (const malo of ["{no es json", "", "[1,2]", "42", '"texto"', "null"]) {
      const r = await leerCuerpoDePlan(peticion({ cuerpo: malo }));
      expect("error" in r, malo).toBe(true);
      if ("error" in r) {
        expect(r.error.status, malo).toBe(400);
        expect((await r.error.json()).codigo, malo).toBe("JSON_INVALIDO");
      }
    }
  });
});
