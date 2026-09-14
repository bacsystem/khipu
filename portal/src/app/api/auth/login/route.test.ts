import { NextRequest } from "next/server";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ACCESS, COOKIE_REFRESH } from "@/lib/session";

vi.mock("@/lib/api/auth", () => ({
  login: vi.fn(),
}));

import { login } from "@/lib/api/auth";
import { POST } from "./route";

function postRequest(body: unknown) {
  return new NextRequest("http://localhost/api/auth/login", {
    method: "POST",
    body: JSON.stringify(body),
    headers: { "content-type": "application/json" },
  });
}

describe("POST /api/auth/login", () => {
  it("fija cookies httpOnly y no expone los tokens en el body", async () => {
    vi.mocked(login).mockResolvedValue({
      access: "a1",
      refresh: "r1",
      usuario: { id: "u1", cuenta_id: "c1", email: "a@b.com", rol: "admin" },
    });

    const res = await POST(postRequest({ email: "a@b.com", password: "secreto" }));
    const json = await res.json();

    expect(json.datos.usuario.email).toBe("a@b.com");
    expect(json.datos).not.toHaveProperty("access");
    expect(json.datos).not.toHaveProperty("refresh");
    expect(res.cookies.get(COOKIE_ACCESS)?.value).toBe("a1");
    expect(res.cookies.get(COOKIE_REFRESH)?.value).toBe("r1");
  });

  it("propaga el status y código de ApiError", async () => {
    vi.mocked(login).mockRejectedValue(new ApiError(401, "NO_AUTORIZADO", "Credenciales inválidas"));

    const res = await POST(postRequest({ email: "a@b.com", password: "mala" }));
    const json = await res.json();

    expect(res.status).toBe(401);
    expect(json.codigo).toBe("NO_AUTORIZADO");
  });
});
