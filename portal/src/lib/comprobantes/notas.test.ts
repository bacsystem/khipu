import { describe, expect, it } from "vitest";
import type { ItemComprobante } from "@/lib/api/facturas";
import { afectacionPredominante, importeLineaNota, impuestoRedondeaACero, itemParaNota, lineaRedondeaACero, topePorTributo } from "./notas";

// 10 mesas a 118 con IGV, cargo 47 del 10 % (flete, paga IGV): valor 1000 + 100, IGV 198, paga 1298.
const conCargoPorcentaje: ItemComprobante = {
  codigo: "MESA", descripcion: "Mesa de trabajo", unidad: "NIU", cantidad: 10, precio_unitario: 118, tipo_afectacion_igv: "10",
  valor_venta: 1100, igv: 198, precio_venta: 1298,
  cargos: [{ tipo: "PORCENTAJE", valor: 10, monto: 100, afecta_base_igv: true, codigo: "47" }],
  codigo_sunat: "56101500", gtin: { tipo: "GTIN-13", codigo: "7750182000123" },
};
// Igual pero con el flete como monto fijo de 100 (no escala con la cantidad).
const conCargoMonto: ItemComprobante = { ...conCargoPorcentaje, cargos: [{ tipo: "MONTO", valor: 100, monto: 100, afecta_base_igv: true, codigo: "47" }] };

describe("itemParaNota", () => {
  it("reenvía los cargos de línea con la forma del request (porcentaje o monto), no la de la respuesta", () => {
    expect(itemParaNota(conCargoPorcentaje, 10).cargos).toEqual([{ porcentaje: 10, afecta_base_igv: true }]);
    expect(itemParaNota(conCargoMonto, 10).cargos).toEqual([{ monto: 100, afecta_base_igv: true }]);
  });

  it("un cargo en porcentaje acompaña a la cantidad parcial; uno de monto fijo solo va con la cantidad facturada", () => {
    expect(itemParaNota(conCargoPorcentaje, 3).cargos).toEqual([{ porcentaje: 10, afecta_base_igv: true }]);
    expect(itemParaNota(conCargoMonto, 3).cargos).toBeUndefined();
  });

  it("aplica la misma regla al descuento de línea", () => {
    const conDescuento = { ...conCargoPorcentaje, cargos: null, descuento: { tipo: "PORCENTAJE" as const, valor: 5, monto: 50, afecta_base_igv: true, codigo: "00" } };
    expect(itemParaNota(conDescuento, 3).descuento).toEqual({ porcentaje: 5, afecta_base_igv: true });
    expect(itemParaNota({ ...conDescuento, descuento: { ...conDescuento.descuento, tipo: "MONTO" } }, 3).descuento).toBeUndefined();
  });

  it("el ISC viaja como lo exige el dominio: tasa en 01, monto_unitario en 02, tasa y base_pvp en 03", () => {
    const base = { ...conCargoPorcentaje, cargos: null };
    expect(itemParaNota({ ...base, isc: { sistema: "01", tasa: 35, monto: 350 } }, 10).isc).toEqual({ sistema: "01", tasa: 35 });
    expect(itemParaNota({ ...base, isc: { sistema: "02", tasa: 12.5, monto: 22.5, monto_unitario: 2.25 } }, 10).isc).toEqual({ sistema: "02", monto_unitario: 2.25 });
    expect(itemParaNota({ ...base, isc: { sistema: "03", tasa: 17, monto: 5.95, base_pvp: 3.5 } }, 10).isc).toEqual({ sistema: "03", tasa: 17, base_pvp: 3.5 });
  });

  it("conserva código SUNAT y GTIN, que van al XML de la nota igual que en la factura", () => {
    const item = itemParaNota(conCargoPorcentaje, 10);
    expect(item.codigo_sunat).toBe("56101500");
    expect(item.gtin).toEqual({ tipo: "GTIN-13", codigo: "7750182000123" });
  });

  it("el ICBPER viaja como bandera: sin él la NC sobre bolsas saldría sin el tributo 7152 y acreditaría de menos", () => {
    const bolsas: ItemComprobante = { codigo: null, descripcion: "Bolsa", unidad: "NIU", cantidad: 2, precio_unitario: 0.618, tipo_afectacion_igv: "10", icbper: 1 };
    expect(itemParaNota(bolsas, 2).icbper).toBe(true);
    expect(itemParaNota({ ...bolsas, icbper: 0 }, 2).icbper).toBeUndefined();
  });

  it("sin ajustes no manda claves vacías", () => {
    const simple: ItemComprobante = { codigo: null, descripcion: "Servicio", unidad: "ZZ", cantidad: 1, precio_unitario: 118, tipo_afectacion_igv: "10" };
    expect(itemParaNota(simple, 1)).toEqual({ codigo: undefined, descripcion: "Servicio", unidad: "ZZ", cantidad: 1, precio_unitario: 118, tipo_afectacion_igv: "10", descuento: undefined, cargos: undefined, isc: undefined, icbper: undefined, codigo_sunat: undefined, gtin: undefined });
  });
});

describe("importeLineaNota", () => {
  it("con la cantidad facturada es el precio de venta que calculó el backend, aunque no coincida con precio × cantidad", () => {
    expect(importeLineaNota(conCargoPorcentaje, 10)).toBe(1298);
    // ISC 01 al 35 % + cargo 47 del 10 %: el dominio da 1100.02 (ISC sobre el valor con cargo, IGV sobre valor + ISC);
    // precio × cantidad daría 1000.
    expect(importeLineaNota(conIscYCargo, 10)).toBe(1100.02);
  });

  it("con menos cantidad prorratea el precio de venta: un cargo en porcentaje entra en proporción y con su IGV", () => {
    // 5 × 118 = 590, más la mitad del cargo (50) con IGV (59) = 649.
    expect(importeLineaNota(conCargoPorcentaje, 5)).toBe(649);
  });

  it("con menos cantidad, un cargo de monto fijo no entra (no viaja): se resta con su IGV antes de prorratear", () => {
    // (1298 − 100 × 1.18) / 2 = 590.
    expect(importeLineaNota(conCargoMonto, 5)).toBe(590);
  });

  it("un cargo que no afecta la base (48) entra sin IGV", () => {
    const con48 = { ...conCargoPorcentaje, cargos: [{ tipo: "PORCENTAJE" as const, valor: 10, monto: 100, afecta_base_igv: false, codigo: "48" }], valor_venta: 1000, igv: 180, precio_venta: 1280 };
    expect(importeLineaNota(con48, 5)).toBe(640);
    const con48Fijo = { ...con48, cargos: [{ ...con48.cargos[0], tipo: "MONTO" as const }] };
    expect(importeLineaNota(con48Fijo, 5)).toBe(590);
  });

  it("con menos cantidad, un descuento de monto fijo no viaja: se devuelve con su IGV antes de prorratear", () => {
    // 10 × 118 con descuento 00 de 100 (afecta la base): paga 1180 − 118 = 1062. Devolver 3 acredita (1062 + 118) × 0.3 = 354.
    const conDescuentoFijo: ItemComprobante = { ...conCargoPorcentaje, cargos: null, valor_venta: 900, igv: 162, precio_venta: 1062, descuento: { tipo: "MONTO", valor: 100, monto: 100, afecta_base_igv: true, codigo: "00" } };
    expect(importeLineaNota(conDescuentoFijo, 3)).toBe(354);
    // En porcentaje acompaña a la cantidad: 1062 × 0.3 = 318.60.
    expect(importeLineaNota({ ...conDescuentoFijo, descuento: { ...conDescuentoFijo.descuento!, tipo: "PORCENTAJE", valor: 10 } }, 3)).toBe(318.6);
  });

  it("una gratuita no se cobra, tampoco en parcial: el precio unitario es solo el valor referencial", () => {
    // La recert #3 midió 300 para 3 de 10 unidades a 100 de valor referencial: el tope bloqueaba NC legítimas.
    const gratuita: ItemComprobante = { codigo: null, descripcion: "Muestra", unidad: "NIU", cantidad: 10, precio_unitario: 100, tipo_afectacion_igv: "11", valor_venta: 1000, igv: 180, precio_venta: 0, gratuita: true };
    expect(importeLineaNota(gratuita, 10)).toBe(0);
    expect(importeLineaNota(gratuita, 3)).toBe(0);
  });

  it("con ISC (01) y un cargo en porcentaje en la misma línea, la mitad es la mitad del dominio (±0.01), no un 2 % menos", () => {
    // Dominio para 5 unidades: baseBruta 313.87 + cargo 31.39 → valor 345.26, ISC 120.84, IGV 83.90 → 550.00.
    // La reconstrucción desde el precio unitario daba 539.02.
    expect(Math.abs(importeLineaNota(conIscYCargo, 5) - 550)).toBeLessThanOrEqual(0.01);
    // Y con un cargo de monto fijo que no viaja, se descuenta con el ISC y el IGV que arrastraba.
    const conIscYCargoFijo = { ...conIscYCargo, cargos: [{ tipo: "MONTO" as const, valor: 62.78, monto: 62.78, afecta_base_igv: true, codigo: "47" }] };
    // 62.78 × 1.35 × 1.18 = 100.01 → (1100.02 − 100.01) / 2 = 500.01 (el dominio sin cargo: 500.00).
    expect(Math.abs(importeLineaNota(conIscYCargoFijo, 5) - 500)).toBeLessThanOrEqual(0.01);
  });

  it("sin precio_venta (backend anterior) queda precio × cantidad como aproximación", () => {
    const { precio_venta: _omitido, ...sinPrecioVenta } = conCargoMonto;
    void _omitido;
    expect(importeLineaNota(sinPrecioVenta, 10)).toBe(1180);
  });
});

// Línea consistente con `ItemCalculado`: precio 100 con todo incluido, cantidad 10, ISC 01 al 35 %, cargo 47 del 10 %:
// baseBruta 627.75, cargo 62.78 → valor 690.53, ISC 241.69, IGV 167.80 → precio de venta 1100.02.
const conIscYCargo: ItemComprobante = {
  codigo: null, descripcion: "Pisco", unidad: "NIU", cantidad: 10, precio_unitario: 100, tipo_afectacion_igv: "10",
  valor_venta: 690.53, igv: 167.8, precio_venta: 1100.02,
  isc: { sistema: "01", tasa: 35, monto: 241.69, base: 690.53 },
  cargos: [{ tipo: "PORCENTAJE", valor: 10, monto: 62.78, afecta_base_igv: true, codigo: "47" }],
};

describe("lineaRedondeaACero", () => {
  it("un cargo en porcentaje que redondea a 0.00 sobre la base de la cantidad parcial (2955)", () => {
    // 0.0004 × 118 = 0.05 de importe (no es cero), pero la base sin IGV es 0.04 y el 10 % es 0.004 → 0.00.
    expect(lineaRedondeaACero(conCargoPorcentaje, 0.0004)).toBe(true);
    expect(lineaRedondeaACero(conCargoPorcentaje, 0.01)).toBe(false);
    // Un cargo de monto fijo no viaja en parcial, así que no cuenta.
    expect(lineaRedondeaACero(conCargoMonto, 0.0004)).toBe(false);
  });

  it("un descuento en porcentaje que redondea a 0.00 no bloquea: el dominio lo acepta", () => {
    const conDescuento: ItemComprobante = { ...conCargoPorcentaje, cargos: null, valor_venta: 900, igv: 162, precio_venta: 1062, descuento: { tipo: "PORCENTAJE", valor: 10, monto: 100, afecta_base_igv: true, codigo: "00" } };
    expect(lineaRedondeaACero(conDescuento, 0.0004)).toBe(false);
  });

  it("una línea gratuita no cuenta: su importe es 0 por definición y SUNAT lo exige así (2640/3224)", () => {
    // Recert #10: con la bonificación dentro, la NC parcial entera quedaba bloqueada con un consejo imposible de cumplir.
    const bonificacion: ItemComprobante = { codigo: null, descripcion: "Muestra sin costo", unidad: "NIU", cantidad: 5, precio_unitario: 100, tipo_afectacion_igv: "11", valor_venta: 500, igv: 90, precio_venta: 0, gratuita: true };
    expect(lineaRedondeaACero(bonificacion, 5)).toBe(false);
    expect(lineaRedondeaACero(bonificacion, 1)).toBe(false);
    expect(lineaRedondeaACero({ ...bonificacion, tipo_afectacion_igv: "21" }, 1)).toBe(false);
    expect(lineaRedondeaACero({ ...bonificacion, tipo_afectacion_igv: "31" }, 1)).toBe(false);
  });

  it("una línea cuyo importe redondea a 0.00 (2367/2369)", () => {
    const simple: ItemComprobante = { codigo: null, descripcion: "Servicio", unidad: "ZZ", cantidad: 1, precio_unitario: 118, tipo_afectacion_igv: "10", precio_venta: 118 };
    expect(lineaRedondeaACero(simple, 0.00001)).toBe(true);
    expect(lineaRedondeaACero(simple, 0.001)).toBe(false);
  });
});

describe("afectacionPredominante", () => {
  const a = (...lineas: Array<[string, number]>) => lineas.map(([c, precio]) => ({ tipo_afectacion_igv: c, cantidad: 1, precio_unitario: precio, precio_venta: precio }));
  it("gravada si la factura tiene alguna línea gravada; si no, exonerada o inafecta según cuál pese más", () => {
    expect(afectacionPredominante(a(["10", 1], ["20", 1000]))).toBe("10");
    expect(afectacionPredominante(a(["20", 1], ["21", 1]))).toBe("20");
    expect(afectacionPredominante(a(["30", 1], ["31", 1]))).toBe("30");
    // Mezcla sin gravadas: nunca «10» (la nota llevaría IGV contra un gravado de 0 → 3503 siempre); gana la que más pesa.
    expect(afectacionPredominante(a(["20", 100], ["30", 300]))).toBe("30");
    expect(afectacionPredominante(a(["20", 300], ["30", 100]))).toBe("20");
  });
});

describe("topePorTributo", () => {
  // f-cargos: gravado 1140.32 + IGV 206.39 = 1346.71, por debajo del total 1353 (el resto es ISC): el límite real (3503).
  const totales = { gravado: 1140.32, igv: 206.39, exonerado: 0, inafecto: 0, total: 1353 };
  it("gravada: gravado + IGV; exonerada/inafecta: su base; exportación e IVAP: el total", () => {
    expect(topePorTributo(totales, "10")).toBe(1346.71);
    expect(topePorTributo({ ...totales, exonerado: 200 }, "20")).toBe(200);
    expect(topePorTributo({ ...totales, inafecto: 50 }, "30")).toBe(50);
    // Exportación: la base 9995, no el total (un cargo 48 o un descuento global lo separan). IVAP: base + IVAP.
    expect(topePorTributo({ ...totales, exportacion: 1000, total: 1050 }, "40")).toBe(1000);
    expect(topePorTributo(totales, "40")).toBe(1353); // backend anterior sin `exportacion`: el total
    // Total distinto de base + IVAP (p. ej. con un cargo sin IGV) para que «devolver el total» no pase por casualidad.
    expect(topePorTributo({ gravado: 100, igv: 0, ivap: 4, exonerado: 0, inafecto: 0, total: 110 }, "17")).toBe(104);
  });
});

describe("impuestoRedondeaACero (3111)", () => {
  it("solo el IVAP al 4 %: importes con impuesto entre 0.07 y 0.12 tienen base > 0.06 e IVAP 0.00", () => {
    expect(impuestoRedondeaACero("17", 0.10)).toBe(true);
    expect(impuestoRedondeaACero("17", 0.12)).toBe(true);
    expect(impuestoRedondeaACero("17", 0.13)).toBe(false); // base 0.125 → 0.005 → 0.01
    expect(impuestoRedondeaACero("17", 0.06)).toBe(false); // base 0.0577 ≤ 0.06: SUNAT no lo exige
    expect(impuestoRedondeaACero("10", 0.10)).toBe(false); // IGV 18 %: base 0.0847 → 0.02
  });
  it("una línea parcial IVAP con importe en esa ventana no deja emitir", () => {
    const arroz: ItemComprobante = { codigo: null, descripcion: "Arroz", unidad: "NIU", cantidad: 10, precio_unitario: 10.4, tipo_afectacion_igv: "17", valor_venta: 100, igv: 4, precio_venta: 104 };
    expect(lineaRedondeaACero(arroz, 0.0096)).toBe(true); // 0.10
    expect(lineaRedondeaACero(arroz, 0.0125)).toBe(false); // 0.13
  });
});
