import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, CODIGO_COMUNICACION } from "@/lib/api/types";
import { COOKIE_ACCESS, COOKIE_REFRESH } from "@/lib/session";

vi.mock("@/lib/api/auth", () => ({ refrescar: vi.fn() }));

import { refrescar } from "@/lib/api/auth";
import { POST } from "./route";

function jwtCon(exp: number): string {
  const payload = btoa(JSON.stringify({ exp })).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return `header.${payload}.firma`;
}

const VIGENTE = () => jwtCon(Math.floor(Date.now() / 1000) + 900);
const VENCIDO = () => jwtCon(Math.floor(Date.now() / 1000) - 60);

function peticion(cookies: Record<string, string>) {
  const cookie = Object.entries(cookies).map(([k, v]) => `${k}=${v}`).join("; ");
  return new NextRequest("http://localhost/api/auth/sesion", { method: "POST", headers: cookie ? { cookie } : undefined });
}

const TOKENS = { access: "a2", refresh: "r2", usuario: {} } as never;

afterEach(() => vi.mocked(refrescar).mockReset());

/** El keep-alive del corte por inactividad (#637): el navegador nunca ve el JWT y solo toca el backend si el access venció. */
describe("POST /api/auth/sesion", () => {
  it("con el access vigente responde 204 sin llamar al backend ni tocar las cookies", async () => {
    const res = await POST(peticion({ [COOKIE_ACCESS]: VIGENTE(), [COOKIE_REFRESH]: "r1" }));

    expect(res.status).toBe(204);
    expect(refrescar).not.toHaveBeenCalled();
    expect(res.cookies.get(COOKIE_ACCESS)).toBeUndefined();
  });

  it("con el access vencido refresca con el refresh, escribe las cookies nuevas y responde 204", async () => {
    vi.mocked(refrescar).mockResolvedValue(TOKENS);

    const res = await POST(peticion({ [COOKIE_ACCESS]: VENCIDO(), [COOKIE_REFRESH]: "r1" }));

    expect(res.status).toBe(204);
    expect(refrescar).toHaveBeenCalledWith("r1");
    expect(res.cookies.get(COOKIE_ACCESS)?.value).toBe("a2");
    expect(res.cookies.get(COOKIE_REFRESH)?.value).toBe("r2");
  });

  it("sin access (venció la cookie) pero con refresh, también refresca", async () => {
    vi.mocked(refrescar).mockResolvedValue(TOKENS);

    const res = await POST(peticion({ [COOKIE_REFRESH]: "r1" }));

    expect(res.status).toBe(204);
    expect(res.cookies.get(COOKIE_ACCESS)?.value).toBe("a2");
  });

  it("si el refresh se rechaza con 401 limpia la sesión y responde 401", async () => {
    vi.mocked(refrescar).mockRejectedValue(new ApiError(401, "SESION_INVALIDA", "Sesión inválida"));

    const res = await POST(peticion({ [COOKIE_ACCESS]: VENCIDO(), [COOKIE_REFRESH]: "r1" }));

    expect(res.status).toBe(401);
    expect(res.cookies.get(COOKIE_ACCESS)?.value).toBe("");
    expect(res.cookies.get(COOKIE_REFRESH)?.value).toBe("");
  });

  it("una cuenta suspendida no es una sesión muerta: responde 403 con su código y no limpia las cookies", async () => {
    vi.mocked(refrescar).mockRejectedValue(new ApiError(403, "CUENTA_SUSPENDIDA", "Cuenta suspendida"));

    const res = await POST(peticion({ [COOKIE_ACCESS]: VENCIDO(), [COOKIE_REFRESH]: "r1" }));

    expect(res.status).toBe(403);
    expect((await res.json()).codigo).toBe("CUENTA_SUSPENDIDA");
    expect(res.cookies.get(COOKIE_ACCESS)).toBeUndefined();
    expect(res.cookies.get(COOKIE_REFRESH)).toBeUndefined();
  });

  it.each([
    ["un corte de red", new ApiError(502, CODIGO_COMUNICACION, "sin conexión")],
    ["un 500 del backend", new ApiError(500, "ERROR_INTERNO", "falló")],
    ["un error inesperado", new Error("boom")],
    ["un 429 por demasiados intentos", new ApiError(429, "DEMASIADOS_INTENTOS", "espera un momento")],
  ])("%s no limpia las cookies: la sesión sigue y se reintenta en la ventana siguiente", async (_titulo, error) => {
    vi.mocked(refrescar).mockRejectedValue(error);

    const res = await POST(peticion({ [COOKIE_ACCESS]: VENCIDO(), [COOKIE_REFRESH]: "r1" }));

    expect(res.status).toBeGreaterThanOrEqual(429);
    expect(res.status).not.toBe(401);
    expect(res.cookies.get(COOKIE_ACCESS)).toBeUndefined();
    expect(res.cookies.get(COOKIE_REFRESH)).toBeUndefined();
  });

  it("sin cookies responde 401 y no llama al backend", async () => {
    const res = await POST(peticion({}));

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("NO_AUTORIZADO");
    expect(refrescar).not.toHaveBeenCalled();
  });

  it("con el access vencido y sin refresh (una sesión de soporte que venció) responde 401", async () => {
    const res = await POST(peticion({ [COOKIE_ACCESS]: VENCIDO() }));

    expect(res.status).toBe(401);
    expect(refrescar).not.toHaveBeenCalled();
  });

  it("con un access vigente y sin refresh (una sesión de soporte) responde 204", async () => {
    const res = await POST(peticion({ [COOKIE_ACCESS]: VIGENTE() }));

    expect(res.status).toBe(204);
  });
});
