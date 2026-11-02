import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS, COOKIE_ADMIN_DESAFIO } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-auth", () => ({
  loginAdministrador: vi.fn(),
}));

import { loginAdministrador } from "@/lib/api/admin-auth";
import { POST } from "./route";

function postRequest(body: unknown, headers: Record<string, string> = {}) {
  return new NextRequest("http://localhost/api/admin/auth/login", {
    method: "POST",
    body: JSON.stringify(body),
    headers: { "content-type": "application/json", ...headers },
  });
}

afterEach(() => {
  vi.unstubAllEnvs();
});

const credenciales = { email: "ana@khipu.pe", password: "Segura123" };
const desafio = { desafio: "desafio-1", paso: "VERIFICAR_SEGUNDO_FACTOR" as const };

describe("POST /api/admin/auth/login: IP del cliente (#208)", () => {
  it("manda al backend la IP de confianza ya resuelta, no la cadena del navegador", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(loginAdministrador).mockResolvedValue(desafio);

    await POST(postRequest(credenciales, { "x-forwarded-for": "6.6.6.6, 203.0.113.7" }));

    expect(loginAdministrador).toHaveBeenCalledWith("ana@khipu.pe", "Segura123", { "X-Forwarded-For": "203.0.113.7" });
  });

  it("sin saltos de confianza no manda ninguna IP", async () => {
    vi.mocked(loginAdministrador).mockResolvedValue(desafio);

    await POST(postRequest(credenciales, { "x-forwarded-for": "6.6.6.6" }));

    expect(loginAdministrador).toHaveBeenCalledWith("ana@khipu.pe", "Segura123", {});
  });
});

/** #177: la contraseña sola no da sesión. */
describe("POST /api/admin/auth/login", () => {
  it("guarda el desafío en una cookie httpOnly limitada al login y responde solo el paso", async () => {
    vi.mocked(loginAdministrador).mockResolvedValue(desafio);

    const res = await POST(postRequest(credenciales));
    const json = await res.json();

    expect(json.datos).toEqual({ paso: "VERIFICAR_SEGUNDO_FACTOR" });
    const cookie = res.cookies.get(COOKIE_ADMIN_DESAFIO);
    expect(cookie?.value).toBe("desafio-1");
    expect(cookie?.httpOnly).toBe(true);
    expect(cookie?.path).toBe("/api/admin/auth");
    expect(cookie?.maxAge).toBe(300);
  });

  it("no abre sesión: la cookie de acceso queda vacía, aunque hubiera una anterior", async () => {
    vi.mocked(loginAdministrador).mockResolvedValue(desafio);

    const res = await POST(postRequest(credenciales));

    expect(res.cookies.get(COOKIE_ADMIN_ACCESS)?.value).toBe("");
    expect(res.cookies.get(COOKIE_ADMIN_ACCESS)?.maxAge).toBe(0);
  });

  it("propaga el status y código de ApiError, sin desafío", async () => {
    vi.mocked(loginAdministrador).mockRejectedValue(new ApiError(401, "CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos"));

    const res = await POST(postRequest({ email: "ana@khipu.pe", password: "mala" }));
    const json = await res.json();

    expect(res.status).toBe(401);
    expect(json.codigo).toBe("CREDENCIALES_INVALIDAS");
    expect(res.cookies.get(COOKIE_ADMIN_DESAFIO)).toBeUndefined();
  });
});
