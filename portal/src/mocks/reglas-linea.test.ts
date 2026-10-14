import { describe, expect, it } from "vitest";
import { MAX_LINEAS, rechazoPorLineaMock, rechazoPorTopeDeLineasMock, rechazoPorNrusMock, rechazoPorValorDeVentaMock, type LineaMock } from "./reglas-linea";

/**
 * #354: el mock responde lo mismo que `Item.exigirValidoParaFactura`, `Comprobante` e `ItemCalculado`: mismo código SUNAT al inicio del mensaje y mismo texto,
 * porque el e2e del portal afirma sobre ellos.
 */
const linea = (extra: Partial<LineaMock> = {}): LineaMock => ({ descripcion: "Pan", unidad: "NIU", cantidad: 1, precio_unitario: 10, tipo_afectacion_igv: "10", ...extra });

describe("tope de líneas (2023 / 2137)", () => {
  it("999 pasan y 1000 se rechazan, con el texto del backend", () => {
    expect(MAX_LINEAS).toBe(999);
    expect(rechazoPorTopeDeLineasMock(Array(999).fill(linea()), "factura")).toBeNull();
    expect(rechazoPorTopeDeLineasMock(Array(1000).fill(linea()), "factura")).toBe(
      "2023 - Una factura admite hasta 999 líneas (el número de orden del ítem es de hasta 3 dígitos); recibidas 1000: divida la operación en más de un comprobante");
    expect(rechazoPorTopeDeLineasMock(Array(1000).fill(linea()), "boleta")).toMatch(/^2023 - Una boleta admite hasta 999 líneas/);
    expect(rechazoPorTopeDeLineasMock(Array(1000).fill(linea()), "nota")).toMatch(/^2137 - Una nota admite hasta 999 líneas/);
    expect(rechazoPorTopeDeLineasMock(Array(999).fill(linea()), "nota")).toBeNull();
  });
});

describe("ICBPER (2892, 4320)", () => {
  it("la cantidad de bolsas es un entero de 1 a 99999, sin redondear", () => {
    for (const cantidad of [1.5, 0.5, 100000, 123456]) {
      expect(rechazoPorLineaMock(linea({ icbper: true, cantidad })), String(cantidad)).toBe(
        `2892 - La cantidad de bolsas del ICBPER es un entero de hasta 5 dígitos (1 a 99999); recibida ${cantidad}`);
    }
    for (const cantidad of [1, 2, 99999, 3.0]) expect(rechazoPorLineaMock(linea({ icbper: true, cantidad }))).toBeNull();
  });

  it("la unidad es NIU", () => {
    for (const unidad of ["ZZ", "KGM"]) {
      expect(rechazoPorLineaMock(linea({ icbper: true, unidad }))).toBe(`4320 - Un ítem con ICBPER va en unidades (unidad NIU, una bolsa por unidad); recibida ${unidad}`);
    }
  });

  it("sin icbper la cantidad puede ser fraccionaria y de otra unidad", () => {
    expect(rechazoPorLineaMock(linea({ unidad: "KGM", cantidad: 1.5 }))).toBeNull();
    expect(rechazoPorLineaMock(linea({ icbper: false, cantidad: 123456 }))).toBeNull();
  });
});

describe("precio (2369, 2367)", () => {
  it("en cero se rechaza; en una gratuita, como valor referencial", () => {
    expect(rechazoPorLineaMock(linea({ precio_unitario: 0 }))).toBe("2369 - El precio unitario debe ser mayor que cero");
    expect(rechazoPorLineaMock(linea({ precio_unitario: 0, tipo_afectacion_igv: "20" }))).toBe("2369 - El precio unitario debe ser mayor que cero");
    for (const af of ["11", "15", "21", "32", "37"]) {
      expect(rechazoPorLineaMock(linea({ precio_unitario: 0, tipo_afectacion_igv: af })), af).toBe("2367 - El valor referencial unitario de una operación gratuita debe ser mayor que cero");
    }
    expect(rechazoPorLineaMock(linea({ precio_unitario: 100, tipo_afectacion_igv: "15" }))).toBeNull();
  });

  /** H4 (revisión de #354): el negativo ya no es de Bean Validation sino del dominio, con la misma regla que el cero (`Item.exigirValido`). */
  it("negativo lleva la misma regla que el cero: 2369, o 2367 en una gratuita", () => {
    expect(rechazoPorLineaMock(linea({ precio_unitario: -1 }))).toBe("2369 - El precio unitario no puede ser negativo");
    expect(rechazoPorLineaMock(linea({ precio_unitario: -1, tipo_afectacion_igv: "15" }))).toBe("2367 - El precio unitario no puede ser negativo");
  });
});

describe("NRUS con ICBPER (3240)", () => {
  it("solo la boleta 0113 con una línea con icbper", () => {
    const mensaje = "3240 - El impuesto ICBPER no aplica para el NRUS (tipo de operación 0113): quite el icbper de las líneas o emita con el tipo de operación 0101";
    expect(rechazoPorNrusMock("0113", [linea(), linea({ icbper: true })])).toBe(mensaje);
    expect(rechazoPorNrusMock("0113", [linea()])).toBeNull();
    expect(rechazoPorNrusMock("0101", [linea({ icbper: true })])).toBeNull();
    expect(rechazoPorNrusMock(undefined, [linea({ icbper: true })])).toBeNull();
  });
});

describe("valor de venta de la línea (2370)", () => {
  it("redondea a 0.00 o queda negativo al separar el ICBPER", () => {
    expect(rechazoPorValorDeVentaMock(linea({ precio_unitario: 0.004 }), 18, "2026-09-13")).toBe("2370 - El valor de venta de la línea «Pan» debe ser mayor que 0.00: suba el precio o la cantidad");
    expect(rechazoPorValorDeVentaMock(linea({ precio_unitario: 0.01 }), 18, "2026-09-13")).toBeNull();
    // la gravada separa el IGV antes de redondear (0.005 / 1.18 → 0.00); la exonerada no tiene IGV que separar (0.005 → 0.01)
    expect(rechazoPorValorDeVentaMock(linea({ precio_unitario: 0.005 }), 18, "2026-09-13")).toMatch(/^2370 - /);
    expect(rechazoPorValorDeVentaMock(linea({ precio_unitario: 0.005, tipo_afectacion_igv: "20" }), 18, "2026-09-13")).toBeNull();
    for (const precio of [0.5, 0.4]) {
      expect(rechazoPorValorDeVentaMock(linea({ icbper: true, cantidad: 2, precio_unitario: precio }), 18, "2026-09-13"), String(precio)).toBe(
        "2370 - El valor de venta de la línea «Pan» debe ser mayor que 0.00: el precio_unitario incluye el ICBPER (S/ 0.50 por bolsa) y debe superarlo");
    }
    expect(rechazoPorValorDeVentaMock(linea({ icbper: true, cantidad: 2, precio_unitario: 0.6 }), 18, "2026-09-13")).toBeNull();
  });

  /** H6 (revisión de #354): solo un cargo que afecta la base (47) suma al valor de venta; el 48 se cobra aparte y no salva la línea. */
  it("un cargo con monto que afecta la base salva la línea; uno sin IGV o en porcentaje no", () => {
    const chica = { precio_unitario: 0.004 };
    expect(rechazoPorValorDeVentaMock(linea({ ...chica, cargos: [{ monto: 5 }] }), 18, "2026-09-13")).toBeNull();
    expect(rechazoPorValorDeVentaMock(linea({ ...chica, cargos: [{ monto: 5, afecta_base_igv: true }] }), 18, "2026-09-13")).toBeNull();
    expect(rechazoPorValorDeVentaMock(linea({ ...chica, cargos: [{ monto: 5, afecta_base_igv: false }] }), 18, "2026-09-13")).toMatch(/^2370 - /);
    expect(rechazoPorValorDeVentaMock(linea({ ...chica, cargos: [{ porcentaje: 10 }] }), 18, "2026-09-13")).toMatch(/^2370 - /);
  });

  it("la tasa del ICBPER sale del año de emisión, con el formato del backend", () => {
    expect(rechazoPorValorDeVentaMock(linea({ icbper: true, precio_unitario: 0.1 }), 18, "2019-05-01")).toContain("(S/ 0.10 por bolsa)");
    expect(rechazoPorValorDeVentaMock(linea({ icbper: true, precio_unitario: 0.2 }), 18, "2020-05-01")).toContain("(S/ 0.2 por bolsa)");
  });
});
