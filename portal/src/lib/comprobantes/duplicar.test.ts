import { describe, expect, it, vi } from "vitest";
import type { Comprobante } from "@/lib/api/facturas";
import { alPedirDuplicado, lineaDesde, pedirDuplicado, plantillaDesde, sePuedeDuplicar } from "./duplicar";

const base = {
  id: "f-1", tipo: "01", serie: "F001", numero: 7, fecha_emision: "2026-09-02", moneda: "PEN", tipo_operacion: "0101",
  receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CLIENTE SAC", direccion: "Av. Prueba 123" },
  items: [{ codigo: null, descripcion: "Soporte", unidad: "ZZ", cantidad: 2, precio_unitario: 59, tipo_afectacion_igv: "10" }],
  estado_documento: "ACEPTADO", totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 },
} as unknown as Comprobante;

describe("duplicar (#323)", () => {
  it("copia cliente, moneda y líneas de una factura; la fecha y el número no se copian", () => {
    const p = plantillaDesde(base)!;
    expect(p).toMatchObject({ tipo: "factura", moneda: "PEN", operacion: "0101", cliente: { tipoDoc: "6", numDoc: "20554198211", razonSocial: "CLIENTE SAC", direccion: "Av. Prueba 123", pais: "" } });
    expect(p.lineas).toHaveLength(1);
    expect(p.lineas[0]).toMatchObject({ descripcion: "Soporte", unidad: "ZZ", cantidad: 2, precioUnitario: 59, tipoAfectacionIgv: "10", icbper: false });
    expect(p).not.toHaveProperty("fecha_emision");
    expect(p).not.toHaveProperty("numero");
  });

  it("solo se duplican facturas y boletas, no las notas", () => {
    expect(sePuedeDuplicar({ tipo: "01" })).toBe(true);
    expect(sePuedeDuplicar({ tipo: "03" })).toBe(true);
    expect(sePuedeDuplicar({ tipo: "07" })).toBe(false);
    expect(sePuedeDuplicar({ tipo: "08" })).toBe(false);
    expect(plantillaDesde({ ...base, tipo: "07" })).toBeNull();
  });

  it("una boleta sin documento vuelve sin documento y sin nombre (el formulario pone el habitual)", () => {
    const p = plantillaDesde({ ...base, tipo: "03", receptor: { tipo_doc: "-", num_doc: "-", razon_social: "CLIENTES VARIOS", direccion: null } })!;
    expect(p.tipo).toBe("boleta");
    expect(p.cliente).toMatchObject({ tipoDoc: "-", numDoc: "", razonSocial: "" });
  });

  it("una exportación conserva su operación y el país del cliente; una detracción o percepción vuelve a venta interna", () => {
    const exp = plantillaDesde({ ...base, tipo_operacion: "0200", moneda: "USD", receptor: { tipo_doc: "0", num_doc: "US1", razon_social: "ACME", direccion: null, pais: "US" } })!;
    expect(exp).toMatchObject({ operacion: "0200", moneda: "USD", cliente: { pais: "US" } });
    for (const op of ["1001", "1003", "2001", "0202", "0205", "0301"]) expect(plantillaDesde({ ...base, tipo_operacion: op })!.operacion, op).toBe("0101");
    // El país solo se copia en una exportación.
    expect(plantillaDesde({ ...base, receptor: { ...base.receptor!, pais: "PE" } })!.cliente.pais).toBe("");
  });

  it("copia los ajustes de la línea para no cambiar su importe: descuento, cargo, ISC e ICBPER", () => {
    const l = lineaDesde({
      ...base.items[0],
      descuento: { tipo: "PORCENTAJE", valor: 10, monto: 10, afecta_base_igv: true, codigo: "00" },
      cargos: [{ tipo: "MONTO", valor: 5, monto: 5, afecta_base_igv: false, codigo: "48" }, { tipo: "MONTO", valor: 1, monto: 1, afecta_base_igv: true, codigo: "47" }],
      isc: { sistema: "02", tasa: 0, monto: 4, monto_unitario: 2 },
      icbper: 0.5,
    });
    expect(l.descuento).toEqual({ modo: "porcentaje", valor: "10", afectaBase: true });
    expect(l.cargo).toEqual({ modo: "monto", valor: "5", afectaBase: false });
    expect(l.isc).toEqual({ sistema: "02", tasa: "", montoUnitario: "2", basePvp: "" });
    expect(l.icbper).toBe(true);
    expect(lineaDesde({ ...base.items[0], isc: { sistema: "03", tasa: 30, monto: 3, base_pvp: 3.5 } }).isc).toEqual({ sistema: "03", tasa: "30", montoUnitario: "", basePvp: "3.5" });
  });

  it("el pedido llega a quien escucha y se puede dejar de escuchar", () => {
    const escuchar = vi.fn();
    const baja = alPedirDuplicado(escuchar);
    pedirDuplicado("f-1");
    expect(escuchar).toHaveBeenCalledWith("f-1");
    baja();
    pedirDuplicado("f-2");
    expect(escuchar).toHaveBeenCalledTimes(1);
  });
});
