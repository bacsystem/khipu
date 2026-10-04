import { describe, expect, it } from "vitest";
import { faultEnPalabras, mensajeDeDescarte, mensajeDeReintento } from "./errores-formato";

const etiqueta = (estado: string) => ({ ENVIADO: "Enviado", ANULADO: "Anulado" })[estado] ?? estado;
const NOMBRE = "20100066603-01-F001-7";
const resultado = (estado: string, intentos = 1, fault?: { codigo?: string; mensaje?: string }) => ({ comprobante_id: "c", estado, intentos, fault });

describe("faultEnPalabras", () => {
  it("junta el código y el mensaje", () => {
    expect(faultEnPalabras({ codigo: "0109", mensaje: "El sistema no puede responder" })).toBe("0109 - El sistema no puede responder");
  });

  it("sin código muestra solo el mensaje, y sin mensaje solo el código", () => {
    expect(faultEnPalabras({ mensaje: "INFRA - storage no disponible" })).toBe("INFRA - storage no disponible");
    expect(faultEnPalabras({ codigo: "1033" })).toBe("1033");
  });

  it("sin fault, o con uno vacío, no hay nada que decir", () => {
    expect(faultEnPalabras(undefined)).toBe("");
    expect(faultEnPalabras({})).toBe("");
  });
});

describe("mensajeDeReintento", () => {
  it("un comprobante aceptado, con o sin observaciones, dice que SUNAT lo aceptó", () => {
    expect(mensajeDeReintento(resultado("ACEPTADO"), NOMBRE, etiqueta)).toBe(`${NOMBRE}: SUNAT lo aceptó.`);
    expect(mensajeDeReintento(resultado("ACEPTADO_CON_OBS"), NOMBRE, etiqueta)).toBe(`${NOMBRE}: SUNAT lo aceptó.`);
  });

  it("un rechazo dice el código del fault, y sin código no deja paréntesis vacíos", () => {
    expect(mensajeDeReintento(resultado("RECHAZADO", 1, { codigo: "1033", mensaje: "ya registrado" }), NOMBRE, etiqueta)).toBe(`${NOMBRE}: SUNAT lo rechazó (1033).`);
    expect(mensajeDeReintento(resultado("RECHAZADO"), NOMBRE, etiqueta)).toBe(`${NOMBRE}: SUNAT lo rechazó.`);
  });

  it("si SUNAT vuelve a fallar dice en qué intento va y por qué", () => {
    expect(mensajeDeReintento(resultado("ERROR_ENVIO", 4, { codigo: "0109", mensaje: "El sistema no puede responder" }), NOMBRE, etiqueta)).toBe(
      `${NOMBRE}: SUNAT volvió a fallar (intento 4). 0109 - El sistema no puede responder`,
    );
  });

  it("si vuelve a fallar sin fault no deja un espacio de más", () => {
    expect(mensajeDeReintento(resultado("ERROR_ENVIO", 2), NOMBRE, etiqueta)).toBe(`${NOMBRE}: SUNAT volvió a fallar (intento 2).`);
  });

  it("cualquier otro estado lo dice con su etiqueta", () => {
    expect(mensajeDeReintento(resultado("ENVIADO"), NOMBRE, etiqueta)).toBe(`${NOMBRE} quedó en estado Enviado.`);
  });
});

describe("mensajeDeDescarte", () => {
  it("dice qué comprobante se descartó", () => {
    expect(mensajeDeDescarte(NOMBRE)).toBe(`${NOMBRE} se descartó.`);
  });
});
