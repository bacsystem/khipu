import { describe, expect, it, vi } from "vitest";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";
import { COOKIE_ACCESS, COOKIE_EMPRESA, COOKIE_REFRESH } from "@/lib/session";
import { POST } from "./route";

describe("POST /api/soporte/salir (#184)", () => {
  it("borra las tres cookies del cliente", async () => {
    const res = await POST();

    for (const nombre of [COOKIE_ACCESS, COOKIE_REFRESH, COOKIE_EMPRESA]) {
      expect(res.cookies.get(nombre)?.value, nombre).toBe("");
      expect(res.cookies.get(nombre)?.maxAge, nombre).toBe(0);
    }
  });

  /** Es a donde se vuelve: la sesión del administrador vive en otra cookie y salir del modo soporte no la toca. */
  it("no toca la cookie de la sesión del administrador", async () => {
    const res = await POST();

    expect(res.cookies.get(COOKIE_ADMIN_ACCESS)).toBeUndefined();
  });

  it("no llama al backend: una sesión de soporte no puede escribir, ni siquiera cerrarse allí", async () => {
    const fetch = vi.fn();
    vi.stubGlobal("fetch", fetch);

    await POST();

    expect(fetch).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });

  it("responde éxito y no se guarda en ninguna caché", async () => {
    const res = await POST();

    expect(res.status).toBe(200);
    expect((await res.json()).estado).toBe("exito");
    expect(res.headers.get("cache-control")).toContain("no-store");
  });
});
