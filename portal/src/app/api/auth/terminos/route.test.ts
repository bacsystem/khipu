import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ACCESS, COOKIE_REFRESH } from "@/lib/session";

vi.mock("@/lib/api/auth", () => ({ aceptarTerminos: vi.fn(), refrescar: vi.fn() }));

import { aceptarTerminos, refrescar } from "@/lib/api/auth";
import { POST } from "./route";

function postRequest(cookie?: string) {
  return new NextRequest("http://localhost/api/auth/terminos", { method: "POST", headers: cookie ? { cookie } : {} });
}

const ACEPTACION = { version: "2026-10-21", aceptada_en: "2026-10-21T15:00:00Z" };
const TOKENS = { access: "jwt-nuevo", refresh: "r-nuevo", usuario: { id: "u1", cuenta_id: "c1", email: "a@b.pe", rol: "ADMIN", correo_verificado: true } };

afterEach(() => {
  vi.mocked(aceptarTerminos).mockReset();
  vi.mocked(refrescar).mockReset();
});

describe("POST /api/auth/terminos (#342)", () => {
  it("acepta con el access de la cookie y devuelve lo guardado", async () => {
    vi.mocked(aceptarTerminos).mockResolvedValue(ACEPTACION);

    const res = await POST(postRequest(`${COOKIE_ACCESS}=jwt-1; ${COOKIE_REFRESH}=r-1`));

    expect(res.status).toBe(200);
    expect(aceptarTerminos).toHaveBeenCalledWith("jwt-1");
    expect(refrescar).not.toHaveBeenCalled();
    expect((await res.json()).datos).toEqual(ACEPTACION);
  });

  it("sin sesión es 401 y no llama al backend", async () => {
    const res = await POST(postRequest());

    expect(res.status).toBe(401);
    expect(aceptarTerminos).not.toHaveBeenCalled();
  });

  it("con el access vencido renueva una vez, reintenta y deja las cookies nuevas", async () => {
    vi.mocked(aceptarTerminos).mockRejectedValueOnce(new ApiError(401, "NO_AUTORIZADO", "Token inválido o expirado")).mockResolvedValueOnce(ACEPTACION);
    vi.mocked(refrescar).mockResolvedValue(TOKENS);

    const res = await POST(postRequest(`${COOKIE_ACCESS}=jwt-vencido; ${COOKIE_REFRESH}=r-1`));

    expect(res.status).toBe(200);
    expect(refrescar).toHaveBeenCalledWith("r-1");
    expect(aceptarTerminos).toHaveBeenLastCalledWith("jwt-nuevo");
    expect(res.cookies.get(COOKIE_ACCESS)?.value).toBe("jwt-nuevo");
    expect(res.cookies.get(COOKIE_REFRESH)?.value).toBe("r-nuevo");
  });

  it("sin access pero con refresh también renueva antes de aceptar", async () => {
    vi.mocked(aceptarTerminos).mockResolvedValue(ACEPTACION);
    vi.mocked(refrescar).mockResolvedValue(TOKENS);

    const res = await POST(postRequest(`${COOKIE_REFRESH}=r-1`));

    expect(res.status).toBe(200);
    expect(aceptarTerminos).toHaveBeenCalledTimes(1);
    expect(aceptarTerminos).toHaveBeenCalledWith("jwt-nuevo");
  });

  it("si el refresh también falla, devuelve su error sin reintentar más", async () => {
    vi.mocked(aceptarTerminos).mockRejectedValue(new ApiError(401, "NO_AUTORIZADO", "Token inválido o expirado"));
    vi.mocked(refrescar).mockRejectedValue(new ApiError(401, "SESION_INVALIDA", "Sesión expirada o inválida"));

    const res = await POST(postRequest(`${COOKIE_ACCESS}=jwt-vencido; ${COOKIE_REFRESH}=r-muerto`));

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("SESION_INVALIDA");
    expect(aceptarTerminos).toHaveBeenCalledTimes(1);
  });

  it("propaga otro rechazo del backend sin renovar, p. ej. una sesión de soporte", async () => {
    vi.mocked(aceptarTerminos).mockRejectedValue(new ApiError(403, "SOPORTE_SOLO_LECTURA", "solo se puede mirar"));

    const res = await POST(postRequest(`${COOKIE_ACCESS}=jwt-soporte; ${COOKIE_REFRESH}=r-1`));

    expect(res.status).toBe(403);
    expect((await res.json()).codigo).toBe("SOPORTE_SOLO_LECTURA");
    expect(refrescar).not.toHaveBeenCalled();
  });
});
