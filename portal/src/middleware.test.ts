import { readdirSync } from "node:fs";
import path from "node:path";
import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ACCESS, COOKIE_REFRESH } from "@/lib/session";

vi.mock("@/lib/api/auth", () => ({
  refrescar: vi.fn(),
}));

import { refrescar } from "@/lib/api/auth";
import { config, middleware } from "./middleware";

function jwtCon(exp: number): string {
  const payload = btoa(JSON.stringify({ exp }))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
  return `header.${payload}.firma`;
}

function requestCon(cookies: Record<string, string>) {
  const cookie = Object.entries(cookies)
    .map(([k, v]) => `${k}=${v}`)
    .join("; ");
  return new NextRequest("http://localhost/comprobantes", { headers: cookie ? { cookie } : undefined });
}

afterEach(() => {
  vi.mocked(refrescar).mockReset();
});

describe("middleware", () => {
  it("redirige a /login cuando no hay cookie de sesión", async () => {
    const res = await middleware(requestCon({}));

    expect(res.status).toBe(307);
    const location = new URL(res.headers.get("location")!);
    expect(location.pathname).toBe("/login");
    expect(location.searchParams.get("next")).toBe("/comprobantes");
  });

  it("deja pasar sin refrescar cuando el access no está vencido", async () => {
    const access = jwtCon(Math.floor(Date.now() / 1000) + 900);

    const res = await middleware(requestCon({ [COOKIE_ACCESS]: access, [COOKIE_REFRESH]: "r1" }));

    expect(res.status).toBe(200);
    expect(refrescar).not.toHaveBeenCalled();
  });

  it("refresca cuando el access está vencido y fija las cookies nuevas", async () => {
    const access = jwtCon(Math.floor(Date.now() / 1000) - 60);
    vi.mocked(refrescar).mockResolvedValue({
      access: "a2",
      refresh: "r2",
      usuario: { id: "u1", cuenta_id: "c1", email: "a@b.com", rol: "admin" },
    });

    const res = await middleware(requestCon({ [COOKIE_ACCESS]: access, [COOKIE_REFRESH]: "r1" }));

    expect(refrescar).toHaveBeenCalledWith("r1");
    expect(res.status).toBe(200);
    expect(res.cookies.get(COOKIE_ACCESS)?.value).toBe("a2");
  });

  it("refresca cuando no hay cookie de access pero sí de refresh", async () => {
    vi.mocked(refrescar).mockResolvedValue({
      access: "a2",
      refresh: "r2",
      usuario: { id: "u1", cuenta_id: "c1", email: "a@b.com", rol: "admin" },
    });

    const res = await middleware(requestCon({ [COOKIE_REFRESH]: "r1" }));

    expect(res.status).toBe(200);
    expect(res.cookies.get(COOKIE_ACCESS)?.value).toBe("a2");
  });

  it("si el refresh falla, redirige a /login y limpia la sesión", async () => {
    const access = jwtCon(Math.floor(Date.now() / 1000) - 60);
    vi.mocked(refrescar).mockRejectedValue(new ApiError(401, "SESION_INVALIDA", "Sesión vencida"));

    const res = await middleware(requestCon({ [COOKIE_ACCESS]: access, [COOKIE_REFRESH]: "r1" }));

    expect(res.status).toBe(307);
    expect(res.cookies.get(COOKIE_REFRESH)?.value).toBe("");
  });

  // El matcher tiene que ser un literal para que Next lo analice en build, así que no se puede
  // derivar del árbol de rutas. Esta comparación es lo que impide que una página privada nueva se
  // quede sin refresco proactivo del access token (el síntoma es una expulsión al login a los 15
  // minutos, con la sesión todavía viva).
  it("cubre todas las páginas de app/(privado)", () => {
    const privado = path.join(__dirname, "app", "(privado)");
    const paginas = readdirSync(privado, { withFileTypes: true })
      .filter((entrada) => entrada.isDirectory())
      .map((entrada) => entrada.name);

    expect(paginas.length).toBeGreaterThan(0);
    for (const pagina of paginas) {
      expect(config.matcher).toContain(`/${pagina}/:path*`);
    }
  });
});
