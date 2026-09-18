import { describe, expect, it } from "vitest";
import { normalizarComprobante, totalDesdeHeaders, type Comprobante } from "./facturas";

const base = {
  id: "abc",
  tipo: "01",
  serie: "F001",
  numero: 1,
  fecha_emision: "2026-09-01",
  moneda: "PEN",
  estado_documento: "ACEPTADO",
  hash: "h",
  intentos: 1,
  ultimo_error: null,
  cdr: { codigo: "0", descripcion: "ok" },
  totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 },
  enlaces: { xml: "/x", cdr: "/c" },
} as unknown as Partial<Comprobante> & Pick<Comprobante, "id">;

describe("normalizarComprobante", () => {
  it("rellena los campos que un backend anterior no envía", () => {
    const c = normalizarComprobante(base);
    expect(c.items).toEqual([]);
    expect(c.receptor).toBeNull();
    expect(c.tipo_operacion).toBeNull();
    expect(c.nombre_archivo).toBeNull();
    expect(c.cdr?.observaciones).toEqual([]);
    expect(c.forma_pago).toEqual({ tipo: "contado", monto_pendiente: null, cuotas: [] });
  });

  it("conserva los campos cuando vienen", () => {
    const c = normalizarComprobante({
      ...base,
      items: [{ codigo: "A", descripcion: "d", unidad: "ZZ", cantidad: 1, precio_unitario: 2, tipo_afectacion_igv: "10" }],
      receptor: { tipo_doc: "6", num_doc: "20123456789", razon_social: "X", direccion: null },
    });
    expect(c.items).toHaveLength(1);
    expect(c.receptor?.num_doc).toBe("20123456789");
  });
});

describe("totalDesdeHeaders", () => {
  it("usa x-total-count cuando existe y el fallback cuando no", () => {
    expect(totalDesdeHeaders(new Headers({ "x-total-count": "126" }), 2)).toBe(126);
    expect(totalDesdeHeaders(new Headers(), 2)).toBe(2);
  });
});
