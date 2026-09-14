import { NextRequest } from "next/server";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ACCESS, COOKIE_EMPRESA, COOKIE_REFRESH } from "@/lib/session";

vi.mock("@/lib/api/auth", () => ({
  registrar: vi.fn(),
}));

import { registrar } from "@/lib/api/auth";
import { POST } from "./route";

function postRequest(body: unknown, cookie?: string) {
  return new NextRequest("http://localhost/api/auth/registro", {
    method: "POST",
    body: JSON.stringify(body),
    headers: { "content-type": "application/json", ...(cookie ? { cookie } : {}) },
  });
}

describe("POST /api/auth/registro", () => {
  it("fija cookies httpOnly y no expone los tokens en el body", async () => {
    vi.mocked(registrar).mockResolvedValue({
      access: "a1",
      refresh: "r1",
      usuario: { id: "u1", cuenta_id: "c1", email: "a@b.com", rol: "admin" },
    });

    const res = await POST(postRequest({ nombre: "Mi cuenta", email: "a@b.com", password: "secreto1" }));
    const json = await res.json();

    expect(json.datos.usuario.email).toBe("a@b.com");
    expect(json.datos).not.toHaveProperty("access");
    expect(res.cookies.get(COOKIE_ACCESS)?.value).toBe("a1");
    expect(res.cookies.get(COOKIE_REFRESH)?.value).toBe("r1");
  });

  it("propaga el status y código de ApiError", async () => {
    vi.mocked(registrar).mockRejectedValue(new ApiError(409, "CORREO_YA_REGISTRADO", "Ese correo ya está en uso"));

    const res = await POST(postRequest({ nombre: "Mi cuenta", email: "a@b.com", password: "secreto1" }));
    const json = await res.json();

    expect(res.status).toBe(409);
    expect(json.codigo).toBe("CORREO_YA_REGISTRADO");
  });

  it("limpia una cookie de empresa ajena heredada de una sesión anterior en el mismo navegador", async () => {
    vi.mocked(registrar).mockResolvedValue({
      access: "a1",
      refresh: "r1",
      usuario: { id: "u1", cuenta_id: "c1", email: "a@b.com", rol: "admin" },
    });

    const res = await POST(
      postRequest(
        { nombre: "Mi cuenta", email: "a@b.com", password: "secreto1" },
        `${COOKIE_EMPRESA}=empresa-de-otra-cuenta`,
      ),
    );

    expect(res.cookies.get(COOKIE_EMPRESA)?.value).toBe("");
  });
});
