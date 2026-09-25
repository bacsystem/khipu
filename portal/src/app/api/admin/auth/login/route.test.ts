import { NextRequest } from "next/server";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-auth", () => ({
  loginAdministrador: vi.fn(),
}));

import { loginAdministrador } from "@/lib/api/admin-auth";
import { POST } from "./route";

function postRequest(body: unknown) {
  return new NextRequest("http://localhost/api/admin/auth/login", {
    method: "POST",
    body: JSON.stringify(body),
    headers: { "content-type": "application/json" },
  });
}

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
