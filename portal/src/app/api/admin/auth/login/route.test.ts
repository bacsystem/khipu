import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

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

describe("POST /api/admin/auth/login: IP del cliente (#208)", () => {
  const sesion = { access_token: "jwt-admin", administrador: { id: "a1", email: "ana@khipu.pe" } };
  const credenciales = { email: "ana@khipu.pe", password: "Segura123" };

  it("manda al backend la IP de confianza ya resuelta, no la cadena del navegador (la bitácora del login, #177, la necesitará)", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(loginAdministrador).mockResolvedValue(sesion);

    await POST(postRequest(credenciales, { "x-forwarded-for": "6.6.6.6, 203.0.113.7" }));

    expect(loginAdministrador).toHaveBeenCalledWith("ana@khipu.pe", "Segura123", { "X-Forwarded-For": "203.0.113.7" });
  });

  it("sin saltos de confianza no manda ninguna IP", async () => {
    vi.mocked(loginAdministrador).mockResolvedValue(sesion);

    await POST(postRequest(credenciales, { "x-forwarded-for": "6.6.6.6" }));

    expect(loginAdministrador).toHaveBeenCalledWith("ana@khipu.pe", "Segura123", {});
  });
});

describe("POST /api/admin/auth/login", () => {
  it("fija la cookie httpOnly y no expone el token en el body", async () => {
    vi.mocked(loginAdministrador).mockResolvedValue({
      access_token: "jwt-admin",
      administrador: { id: "a1", email: "ana@khipu.pe" },
    });

    const res = await POST(postRequest({ email: "ana@khipu.pe", password: "Segura123" }));
    const json = await res.json();

    expect(json.datos.administrador.email).toBe("ana@khipu.pe");
    expect(json.datos).not.toHaveProperty("access_token");
    expect(res.cookies.get(COOKIE_ADMIN_ACCESS)?.value).toBe("jwt-admin");
  });

  it("propaga el status y código de ApiError", async () => {
    vi.mocked(loginAdministrador).mockRejectedValue(new ApiError(401, "CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos"));

    const res = await POST(postRequest({ email: "ana@khipu.pe", password: "mala" }));
    const json = await res.json();

    expect(res.status).toBe(401);
    expect(json.codigo).toBe("CREDENCIALES_INVALIDAS");
  });
});
