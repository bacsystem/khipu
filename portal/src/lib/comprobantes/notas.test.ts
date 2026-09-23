import { describe, expect, it } from "vitest";
import type { ItemComprobante } from "@/lib/api/facturas";
import { importeLineaNota, itemParaNota } from "./notas";

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

  it("conserva código SUNAT y GTIN (4331 observa la nota si faltan)", () => {
    const item = itemParaNota(conCargoPorcentaje, 10);
    expect(item.codigo_sunat).toBe("56101500");
    expect(item.gtin).toEqual({ tipo: "GTIN-13", codigo: "7750182000123" });
  });

  it("sin ajustes no manda claves vacías", () => {
    const simple: ItemComprobante = { codigo: null, descripcion: "Servicio", unidad: "ZZ", cantidad: 1, precio_unitario: 118, tipo_afectacion_igv: "10" };
    expect(itemParaNota(simple, 1)).toEqual({ codigo: undefined, descripcion: "Servicio", unidad: "ZZ", cantidad: 1, precio_unitario: 118, tipo_afectacion_igv: "10", descuento: undefined, cargos: undefined, isc: undefined, icbper: undefined, codigo_sunat: undefined, gtin: undefined });
  });
});

describe("importeLineaNota", () => {
  it("con la cantidad facturada es el precio de venta que calculó el backend (cargos incluidos)", () => {
    expect(importeLineaNota(conCargoPorcentaje, 10)).toBe(1298);
  });

  it("con menos cantidad, un cargo en porcentaje entra en proporción y con su IGV", () => {
    // 5 × 118 = 590, más la mitad del cargo (50) con IGV (59) = 649.
    expect(importeLineaNota(conCargoPorcentaje, 5)).toBe(649);
  });

  it("con menos cantidad, un cargo de monto fijo no entra (no viaja)", () => {
    expect(importeLineaNota(conCargoMonto, 5)).toBe(590);
  });

  it("un cargo que no afecta la base (48) entra sin IGV", () => {
    const con48 = { ...conCargoPorcentaje, cargos: [{ tipo: "PORCENTAJE" as const, valor: 10, monto: 100, afecta_base_igv: false, codigo: "48" }], valor_venta: 1000, igv: 180, precio_venta: 1280 };
    expect(importeLineaNota(con48, 5)).toBe(640);
  });

  it("sin precio_venta (backend anterior) reconstruye el importe también con la cantidad facturada", () => {
    const { precio_venta: _omitido, ...sinPrecioVenta } = conCargoMonto;
    void _omitido;
    expect(importeLineaNota(sinPrecioVenta, 10)).toBe(1298);
  });
});
