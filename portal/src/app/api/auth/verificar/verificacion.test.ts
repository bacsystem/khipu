import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ACCESS } from "@/lib/session";

vi.mock("@/lib/api/auth", () => ({
  verificarCorreo: vi.fn(),
  reenviarVerificacion: vi.fn(),
}));

import { reenviarVerificacion, verificarCorreo } from "@/lib/api/auth";
import { POST as verificar } from "./route";
import { POST as reenviar } from "../verificacion/route";

beforeEach(() => vi.clearAllMocks());

/** #22: el enlace del correo se abre sin sesión; reenviar, en cambio, es del usuario de la sesión. */
describe("POST /api/auth/verificar", () => {
  const pedir = (token: string) =>
    new NextRequest("http://localhost/api/auth/verificar", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ token }) });

  it("verifica con el token, sin pedir sesión", async () => {
    vi.mocked(verificarCorreo).mockResolvedValue(undefined);

    const res = await verificar(pedir("tok-1"));

    expect(res.status).toBe(200);
    expect(verificarCorreo).toHaveBeenCalledWith("tok-1");
  });

  it("un enlace vencido o usado devuelve el error del backend", async () => {
    vi.mocked(verificarCorreo).mockRejectedValue(new ApiError(422, "TOKEN_INVALIDO", "El enlace venció"));

    const res = await verificar(pedir("viejo"));

    expect(res.status).toBe(422);
    expect((await res.json()).codigo).toBe("TOKEN_INVALIDO");
  });
});

describe("POST /api/auth/verificacion", () => {
  const pedir = (conSesion: boolean) =>
    new NextRequest("http://localhost/api/auth/verificacion", {
      method: "POST",
      headers: conSesion ? { cookie: `${COOKIE_ACCESS}=jwt-usuario` } : {},
    });

  it("reenvía con el JWT de la cookie", async () => {
    vi.mocked(reenviarVerificacion).mockResolvedValue(undefined);

    const res = await reenviar(pedir(true));

    expect(res.status).toBe(202);
    expect(reenviarVerificacion).toHaveBeenCalledWith("jwt-usuario");
  });

  it("sin sesión responde 401 sin llamar al backend", async () => {
    const res = await reenviar(pedir(false));

    expect(res.status).toBe(401);
    expect(reenviarVerificacion).not.toHaveBeenCalled();
  });

  it("con el correo ya verificado devuelve el 409 del backend", async () => {
    vi.mocked(reenviarVerificacion).mockRejectedValue(new ApiError(409, "CORREO_YA_VERIFICADO", "ya"));

    const res = await reenviar(pedir(true));

    expect(res.status).toBe(409);
    expect((await res.json()).codigo).toBe("CORREO_YA_VERIFICADO");
  });
});
