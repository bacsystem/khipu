import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/types";
import { COOKIE_ADMIN_ACCESS, COOKIE_ADMIN_DESAFIO } from "@/lib/admin-session";

vi.mock("@/lib/api/admin-auth", () => ({
  configurarSegundoFactor: vi.fn(),
  confirmarSegundoFactor: vi.fn(),
  verificarSegundoFactor: vi.fn(),
}));

import { configurarSegundoFactor, confirmarSegundoFactor, verificarSegundoFactor } from "@/lib/api/admin-auth";
import { POST as configurar } from "./configurar/route";
import { POST as confirmar } from "./confirmar/route";
import { POST as verificar } from "./verificar/route";

function postRequest(ruta: string, body: unknown, { desafio = "desafio-1", headers = {} }: { desafio?: string | null; headers?: Record<string, string> } = {}) {
  return new NextRequest(`http://localhost/api/admin/auth/segundo-factor/${ruta}`, {
    method: "POST",
    body: JSON.stringify(body),
    headers: { "content-type": "application/json", ...(desafio ? { cookie: `${COOKIE_ADMIN_DESAFIO}=${desafio}` } : {}), ...headers },
  });
}

const administrador = { id: "a1", email: "ana@khipu.pe" };

beforeEach(() => vi.clearAllMocks());
afterEach(() => vi.unstubAllEnvs());

describe("sin la cookie del desafío", () => {
  it("los tres pasos responden 401 SESION_INVALIDA sin llamar al backend", async () => {
    for (const [paso, post] of [["configurar", configurar], ["confirmar", confirmar], ["verificar", verificar]] as const) {
      const res = await post(postRequest(paso, { codigo: "123456" }, { desafio: null }));
      expect(res.status, paso).toBe(401);
      expect((await res.json()).codigo, paso).toBe("SESION_INVALIDA");
    }
    expect(configurarSegundoFactor).not.toHaveBeenCalled();
    expect(confirmarSegundoFactor).not.toHaveBeenCalled();
    expect(verificarSegundoFactor).not.toHaveBeenCalled();
  });
});

describe("POST /api/admin/auth/segundo-factor/configurar", () => {
  it("usa el desafío de la cookie y devuelve el QR y el secreto", async () => {
    vi.mocked(configurarSegundoFactor).mockResolvedValue({ secreto: "SECRETO", uri: "otpauth://totp/x", qr_png: "iVBOR" });

    const res = await configurar(postRequest("configurar", {}));

    expect(configurarSegundoFactor).toHaveBeenCalledWith("desafio-1");
    expect((await res.json()).datos).toEqual({ secreto: "SECRETO", uri: "otpauth://totp/x", qr_png: "iVBOR" });
  });
});

describe("POST /api/admin/auth/segundo-factor/confirmar", () => {
  it("abre la sesión con la vida que dice el backend, borra el desafío y pasa los códigos sin el token", async () => {
    vi.stubEnv("TRUSTED_PROXY_HOPS", "1");
    vi.mocked(confirmarSegundoFactor).mockResolvedValue({ access_token: "jwt-admin", expira_en: 900, administrador, codigos_recuperacion: ["AAAAA-BBBBB"] });

    const res = await confirmar(postRequest("confirmar", { codigo: "123456" }, { headers: { "x-forwarded-for": "6.6.6.6, 203.0.113.7" } }));
    const json = await res.json();

    expect(confirmarSegundoFactor).toHaveBeenCalledWith("desafio-1", "123456", { "X-Forwarded-For": "203.0.113.7" });
    expect(json.datos).toEqual({ administrador, codigos_recuperacion: ["AAAAA-BBBBB"] });
    expect(JSON.stringify(json)).not.toContain("jwt-admin");
    expect(res.cookies.get(COOKIE_ADMIN_ACCESS)?.value).toBe("jwt-admin");
    expect(res.cookies.get(COOKIE_ADMIN_ACCESS)?.maxAge).toBe(900);
    expect(res.cookies.get(COOKIE_ADMIN_DESAFIO)?.maxAge).toBe(0);
  });

  it("un código equivocado propaga el error y deja el desafío para reintentar", async () => {
    vi.mocked(confirmarSegundoFactor).mockRejectedValue(new ApiError(401, "CODIGO_INVALIDO", "El código no es válido"));

    const res = await confirmar(postRequest("confirmar", { codigo: "000000" }));

    expect(res.status).toBe(401);
    expect((await res.json()).codigo).toBe("CODIGO_INVALIDO");
    expect(res.cookies.get(COOKIE_ADMIN_DESAFIO)).toBeUndefined();
    expect(res.cookies.get(COOKIE_ADMIN_ACCESS)).toBeUndefined();
  });
});

describe("POST /api/admin/auth/segundo-factor/verificar", () => {
  it("abre la sesión con la vida que dice el backend y borra el desafío", async () => {
    vi.mocked(verificarSegundoFactor).mockResolvedValue({ access_token: "jwt-admin", expira_en: 600, administrador });

    const res = await verificar(postRequest("verificar", { codigo: "654321" }));
    const json = await res.json();

    expect(verificarSegundoFactor).toHaveBeenCalledWith("desafio-1", "654321", {});
    expect(json.datos).toEqual({ administrador });
    expect(res.cookies.get(COOKIE_ADMIN_ACCESS)?.value).toBe("jwt-admin");
    expect(res.cookies.get(COOKIE_ADMIN_ACCESS)?.maxAge).toBe(600);
    expect(res.cookies.get(COOKIE_ADMIN_DESAFIO)?.maxAge).toBe(0);
  });

  it("el bloqueo por demasiados intentos llega con su 429", async () => {
    vi.mocked(verificarSegundoFactor).mockRejectedValue(new ApiError(429, "DEMASIADOS_INTENTOS", "Espera unos minutos"));

    const res = await verificar(postRequest("verificar", { codigo: "000000" }));

    expect(res.status).toBe(429);
    expect((await res.json()).codigo).toBe("DEMASIADOS_INTENTOS");
  });
});
