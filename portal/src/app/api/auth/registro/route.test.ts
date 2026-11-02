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
      usuario: { id: "u1", cuenta_id: "c1", email: "a@b.com", rol: "admin", correo_verificado: true },
    });

    const res = await POST(postRequest({ nombre: "Mi cuenta", email: "a@b.com", password: "secreto1" }));
    const json = await res.json();

    expect(json.datos.usuario.email).toBe("a@b.com");
    expect(json.datos).not.toHaveProperty("access");
    expect(res.cookies.get(COOKIE_ACCESS)?.value).toBe("a1");
    expect(res.cookies.get(COOKIE_REFRESH)?.value).toBe("r1");
  });

  /** #342: el backend es quien decide; el BFF solo no deja que un «sí» que no sea `true` (p. ej. `"true"` o `1`) pase como aceptación. */
  it("manda la aceptación de los términos al backend, y solo un true cuenta", async () => {
    vi.mocked(registrar).mockResolvedValue({
      access: "a1",
      refresh: "r1",
      usuario: { id: "u1", cuenta_id: "c1", email: "a@b.com", rol: "admin", correo_verificado: false, terminos_pendientes: false },
    });
    const datos = { nombre: "Mi cuenta", email: "a@b.com", password: "secreto1", telefono: "987654321" };

    await POST(postRequest({ ...datos, acepta_terminos: true }));
    expect(registrar).toHaveBeenLastCalledWith({ ...datos, acepta_terminos: true });

    for (const valor of [undefined, false, "true", 1]) {
      await POST(postRequest({ ...datos, acepta_terminos: valor }));
      expect(registrar).toHaveBeenLastCalledWith({ ...datos, acepta_terminos: false });
    }
  });

  it("sin aceptar los términos devuelve el 400 del backend", async () => {
    vi.mocked(registrar).mockRejectedValue(new ApiError(400, "TERMINOS_NO_ACEPTADOS", "Para crear la cuenta tienes que aceptar los términos"));

    const res = await POST(postRequest({ nombre: "Mi cuenta", email: "a@b.com", password: "secreto1", telefono: "987654321", acepta_terminos: false }));

    expect(res.status).toBe(400);
    expect((await res.json()).codigo).toBe("TERMINOS_NO_ACEPTADOS");
    expect(res.cookies.get(COOKIE_ACCESS)).toBeUndefined();
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
      usuario: { id: "u1", cuenta_id: "c1", email: "a@b.com", rol: "admin", correo_verificado: true },
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
