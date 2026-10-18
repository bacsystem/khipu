import { describe, expect, it } from "vitest";
import { avisoDeDetraccion, CODIGOS_SIN_FORMULARIO, DETRACCION_VACIA, detraccionAApi, errorDeDetraccion, operacionDeDetraccion, PORCENTAJE_SUGERIDO, type DetraccionForm } from "./detraccion";

const det = (p: Partial<DetraccionForm>): DetraccionForm => ({ ...DETRACCION_VACIA, activa: true, codigo: "022", porcentaje: "12", cuenta: "00-000-123456", ...p });

describe("detracción (#288)", () => {
  it("sin activar no hay nada que decir ni que mandar", () => {
    expect(errorDeDetraccion(DETRACCION_VACIA, "PEN")).toBeNull();
    expect(detraccionAApi(DETRACCION_VACIA, "PEN")).toBeUndefined();
  });

  it("pide el bien o servicio, un porcentaje válido y la cuenta del Banco de la Nación", () => {
    expect(errorDeDetraccion(det({ codigo: "" }), "PEN")).toMatch(/catálogo 54/);
    expect(errorDeDetraccion(det({ porcentaje: "" }), "PEN")).toMatch(/porcentaje/);
    expect(errorDeDetraccion(det({ porcentaje: "0" }), "PEN")).toMatch(/porcentaje/);
    expect(errorDeDetraccion(det({ porcentaje: "101" }), "PEN")).toMatch(/porcentaje/);
    expect(errorDeDetraccion(det({ porcentaje: "1.234567" }), "PEN")).toMatch(/5 decimales/);
    expect(errorDeDetraccion(det({ cuenta: "  " }), "PEN")).toMatch(/cuenta de detracciones/);
    expect(errorDeDetraccion(det({}), "PEN")).toBeNull();
  });

  it("fuera de soles el monto a depositar se indica en soles (3208)", () => {
    expect(errorDeDetraccion(det({}), "USD")).toMatch(/en soles/);
    expect(errorDeDetraccion(det({ montoEnSoles: "1416" }), "USD")).toBeNull();
    expect(detraccionAApi(det({ montoEnSoles: "1416.50" }), "USD")).toMatchObject({ monto: 1416.5 });
  });

  it("manda lo que pide la API y deja el monto al backend cuando la factura es en soles", () => {
    expect(detraccionAApi(det({ cuenta: " 00-000-123456 " }), "PEN")).toEqual({ codigo_bien_servicio: "022", porcentaje: 12, medio_pago: "001", cuenta_banco_nacion: "00-000-123456", monto: undefined });
  });

  it("el tipo de operación lo fija el código: 1003 para el transporte de pasajeros y 1001 para el resto", () => {
    expect(operacionDeDetraccion("028")).toBe("1003");
    expect(operacionDeDetraccion("022")).toBe("1001");
    expect(operacionDeDetraccion("037")).toBe("1001");
  });

  it("los códigos que piden datos por ítem (004, 027) no se ofrecen en el formulario", () => {
    expect([...CODIGOS_SIN_FORMULARIO].sort()).toEqual(["004", "027"]);
    expect(PORCENTAJE_SUGERIDO["004"]).toBeUndefined();
    expect(PORCENTAJE_SUGERIDO["022"]).toBe(12);
    expect(PORCENTAJE_SUGERIDO["019"]).toBe(10);
  });

  it("avisa cuando una factura en soles pasa de S/ 700 sin detracción, y solo entonces", () => {
    expect(avisoDeDetraccion({ activa: false, moneda: "PEN", total: 701, boleta: false })).toBe(true);
    expect(avisoDeDetraccion({ activa: false, moneda: "PEN", total: 700, boleta: false })).toBe(false);
    expect(avisoDeDetraccion({ activa: true, moneda: "PEN", total: 5000, boleta: false })).toBe(false);
    expect(avisoDeDetraccion({ activa: false, moneda: "USD", total: 5000, boleta: false })).toBe(false);
    expect(avisoDeDetraccion({ activa: false, moneda: "PEN", total: 5000, boleta: true })).toBe(false);
  });
});
