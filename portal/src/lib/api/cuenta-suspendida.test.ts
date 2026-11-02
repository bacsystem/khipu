import { describe, expect, it } from "vitest";
import { esCuentaSuspendida } from "./cuenta-suspendida";
import { ApiError } from "./types";

describe("esCuentaSuspendida (#182)", () => {
  it("reconoce el 403 CUENTA_SUSPENDIDA del backend", () => {
    expect(esCuentaSuspendida(new ApiError(403, "CUENTA_SUSPENDIDA", "Tu cuenta está suspendida"))).toBe(true);
  });

  it("un 403 por otra causa no es una suspensión", () => {
    expect(esCuentaSuspendida(new ApiError(403, "EMPRESA_AJENA", "La empresa no pertenece a tu cuenta"))).toBe(false);
    expect(esCuentaSuspendida(new ApiError(403, "CORREO_SIN_VERIFICAR", "Verifica tu correo"))).toBe(false);
    expect(esCuentaSuspendida(new ApiError(403, null, "Prohibido"))).toBe(false);
  });

  it("el mismo código con otro estado HTTP tampoco: lo decide el par status y código", () => {
    expect(esCuentaSuspendida(new ApiError(401, "CUENTA_SUSPENDIDA", "x"))).toBe(false);
    expect(esCuentaSuspendida(new ApiError(500, "CUENTA_SUSPENDIDA", "x"))).toBe(false);
  });

  it("lo que no es un error de la API no cuenta", () => {
    expect(esCuentaSuspendida(new Error("CUENTA_SUSPENDIDA"))).toBe(false);
    expect(esCuentaSuspendida(null)).toBe(false);
    expect(esCuentaSuspendida({ status: 403, codigo: "CUENTA_SUSPENDIDA" })).toBe(false);
    expect(esCuentaSuspendida(undefined)).toBe(false);
  });
});
