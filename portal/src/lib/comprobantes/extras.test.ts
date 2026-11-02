import { describe, expect, it } from "vitest";
import { EXTRAS, type Extra, extrasOfrecidos } from "./extras";

const NINGUNO = Object.fromEntries(EXTRAS.map((e) => [e, false])) as Record<Extra, boolean>;
const con = (...activos: Extra[]) => ({ ...NINGUNO, ...Object.fromEntries(activos.map((e) => [e, true])) });

describe("extrasOfrecidos (#557, #560)", () => {
  it("con las opciones avanzadas, una factura de venta interna ofrece todo, en el orden de la fila", () => {
    expect(extrasOfrecidos({ tipo: "factura", operacion: "0101", avanzadas: true, activos: NINGUNO })).toEqual({ ofrecidos: [...EXTRAS], hayOcultos: false });
  });

  it("sin las opciones avanzadas, una factura ofrece solo crédito, detracción y correo, y dice que hay ocultos", () => {
    expect(extrasOfrecidos({ tipo: "factura", operacion: "0101", avanzadas: false, activos: NINGUNO })).toEqual({ ofrecidos: ["credito", "detraccion", "correo"], hayOcultos: true });
  });

  it("una boleta no ofrece crédito ni retención, con o sin las avanzadas", () => {
    expect(extrasOfrecidos({ tipo: "boleta", operacion: "0101", avanzadas: false, activos: NINGUNO }).ofrecidos).toEqual(["detraccion", "correo"]);
    expect(extrasOfrecidos({ tipo: "boleta", operacion: "0101", avanzadas: true, activos: NINGUNO }).ofrecidos).toEqual(["detraccion", "percepcion", "operacion", "masOpciones", "anticipos", "referencias", "correo"]);
  });

  it("un avanzado que ya viene activo (una copia) se ofrece aunque la preferencia esté apagada", () => {
    const r = extrasOfrecidos({ tipo: "factura", operacion: "0101", avanzadas: false, activos: con("referencias", "masOpciones") });
    expect(r.ofrecidos).toEqual(["credito", "detraccion", "masOpciones", "referencias", "correo"]);
    expect(r.hayOcultos).toBe(true);
  });

  it("una exportación copiada mantiene su tipo de operación y no ofrece lo que es de la venta interna", () => {
    const r = extrasOfrecidos({ tipo: "factura", operacion: "0200", avanzadas: false, activos: con("operacion") });
    expect(r.ofrecidos).toEqual(["credito", "operacion", "correo"]);
    expect(r.hayOcultos).toBe(true);
  });

  it("la detracción activa excluye a la percepción y a la retención; la percepción, a la retención", () => {
    expect(extrasOfrecidos({ tipo: "factura", operacion: "0101", avanzadas: true, activos: con("detraccion") }).ofrecidos).not.toContain("percepcion");
    expect(extrasOfrecidos({ tipo: "factura", operacion: "0101", avanzadas: true, activos: con("detraccion") }).ofrecidos).not.toContain("retencion");
    const conPercepcion = extrasOfrecidos({ tipo: "factura", operacion: "0101", avanzadas: true, activos: con("percepcion") }).ofrecidos;
    expect(conPercepcion).toContain("percepcion");
    expect(conPercepcion).not.toContain("retencion");
  });

  it("con todos los avanzados activos no queda nada oculto aunque la preferencia esté apagada", () => {
    const r = extrasOfrecidos({ tipo: "factura", operacion: "0101", avanzadas: false, activos: con("percepcion", "operacion", "masOpciones", "anticipos", "referencias") });
    // La retención no se ofrece con la percepción activa: no está oculta, no corresponde.
    expect(r.hayOcultos).toBe(false);
  });
});
