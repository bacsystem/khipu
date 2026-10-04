import { describe, expect, it } from "vitest";
import { intentoPara } from "./idempotencia";

describe("intentoPara (#115)", () => {
  let n = 0;
  const nueva = () => `clave-${++n}`;

  it("el primer intento estrena clave", () => {
    expect(intentoPara(null, '{"a":1}', nueva)).toEqual({ contenido: '{"a":1}', clave: "clave-1" });
  });

  it("repetir el mismo contenido reusa la clave: es un reintento, no otra factura", () => {
    const primero = intentoPara(null, '{"a":1}', nueva);
    expect(intentoPara(primero, '{"a":1}', nueva)).toBe(primero);
  });

  it("si el contenido cambió es otra factura y lleva otra clave", () => {
    const primero = intentoPara(null, '{"a":1}', nueva);
    const otro = intentoPara(primero, '{"a":2}', nueva);
    expect(otro.clave).not.toBe(primero.clave);
    expect(otro.contenido).toBe('{"a":2}');
  });

  it("por defecto la clave es un UUID", () => {
    expect(intentoPara(null, "x").clave).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });
});
