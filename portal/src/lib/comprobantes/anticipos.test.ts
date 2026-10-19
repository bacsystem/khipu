import { describe, expect, it } from "vitest";
import type { Comprobante } from "@/lib/api/facturas";
import { afectacionPorDefecto, anticipoInicial, anticiposAApi, candidatosDeAnticipo, errorDeAnticipos } from "./anticipos";

type Parcial = Partial<Comprobante> & { serie: string; numero: number };
const comp = (p: Parcial): Comprobante =>
  ({
    id: `${p.serie}-${p.numero}`, tipo: "01", fecha_emision: "2026-09-01", moneda: "PEN", estado_documento: "ACEPTADO", receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "X", direccion: null },
    totales: { gravado: 1000, exonerado: 0, inafecto: 0, igv: 180, total: 1180 }, anticipos: null, ...p,
  }) as unknown as Comprobante;

const CLIENTE = { numDoc: "20554198211" };

describe("anticipos (#312)", () => {
  it("ofrece las facturas y boletas aceptadas del mismo cliente y moneda, con lo que queda por descontar", () => {
    const lista = [
      comp({ serie: "F001", numero: 1 }),
      comp({ serie: "B001", numero: 2, tipo: "03" }),
      comp({ serie: "F001", numero: 3, estado_documento: "RECHAZADO" }),
      comp({ serie: "F001", numero: 4, receptor: { tipo_doc: "6", num_doc: "20100066603", razon_social: "Otro", direccion: null } }),
      comp({ serie: "F001", numero: 5, moneda: "USD" }),
      comp({ serie: "FC01", numero: 6, tipo: "07" }),
      comp({ serie: "F001", numero: 7, estado_documento: "ACEPTADO_CON_OBS" }),
    ];
    expect(candidatosDeAnticipo(lista, CLIENTE, "PEN").map((c) => c.etiqueta)).toEqual(["F001-1", "B001-2", "F001-7"]);
    expect(candidatosDeAnticipo(lista, { numDoc: "" }, "PEN")).toEqual([]);
  });

  it("descuenta lo ya regularizado por otras facturas, salvo las rechazadas, y oculta el anticipo agotado", () => {
    const final = (n: number, estado: Comprobante["estado_documento"], monto: number) =>
      comp({ serie: "F002", numero: n, estado_documento: estado, anticipos: [{ comprobante: "F001-1", serie: "F001", numero: 1, monto, importe_pagado: monto * 1.18, afectacion: "gravado", codigo_sunat: "04", fecha_pago: null }] });
    const base = comp({ serie: "F001", numero: 1 });
    const [c] = candidatosDeAnticipo([base, final(10, "ACEPTADO", 300), final(11, "RECHAZADO", 500)], CLIENTE, "PEN").filter((x) => x.etiqueta === "F001-1");
    expect(c.regularizado.gravado).toBe(300);
    expect(c.disponible.gravado).toBe(700);
    expect(candidatosDeAnticipo([base, final(10, "ACEPTADO", 1000)], CLIENTE, "PEN").map((x) => x.etiqueta)).not.toContain("F001-1");
  });

  /** Revisión de #312 (H1): `montoRegularizado` del backend suma todas las afectaciones y solo deja fuera las finales rechazadas o inválidas. */
  it("espeja al backend: una final dada de baja sigue contando, y lo regularizado en una afectación resta en las demás", () => {
    const final = (n: number, estado: Comprobante["estado_documento"], afectacion: "gravado" | "exonerado", monto: number) =>
      comp({ serie: "F002", numero: n, estado_documento: estado, anticipos: [{ comprobante: "F001-1", serie: "F001", numero: 1, monto, importe_pagado: monto, afectacion, codigo_sunat: "04", fecha_pago: null }] });
    const mixto = comp({ serie: "F001", numero: 1, totales: { gravado: 1000, exonerado: 400, inafecto: 0, igv: 180, total: 1580 } });
    const [c] = candidatosDeAnticipo([mixto, final(10, "ANULADO", "gravado", 300), final(11, "INVALIDO", "gravado", 500)], CLIENTE, "PEN");
    expect(c.disponible.gravado).toBe(700); // la anulada cuenta (300), la inválida no
    const [d] = candidatosDeAnticipo([mixto, final(12, "ACEPTADO", "exonerado", 400)], CLIENTE, "PEN");
    expect(d.disponible.gravado).toBe(600); // 1000 − los 400 regularizados en exonerado: lo que el backend compararía
    expect(d.disponible.exonerado).toBe(0);
    expect(errorDeAnticipos([{ serie: "F001", numero: 1, monto: "601", afectacion: "gravado" }], [d])).toMatch(/solo tiene 600\.00 por descontar en gravado \(ya se regularizaron 400\.00\)/);
  });

  it("elige la afectación con algo disponible y propone descontarlo todo", () => {
    const [c] = candidatosDeAnticipo([comp({ serie: "F001", numero: 1, totales: { gravado: 0, exonerado: 400, inafecto: 0, igv: 0, total: 400 } })], CLIENTE, "PEN");
    expect(afectacionPorDefecto(c)).toBe("exonerado");
    expect(anticipoInicial(c)).toEqual({ serie: "F001", numero: 1, monto: "400.00", afectacion: "exonerado" });
  });

  it("valida el monto, que no pase de lo disponible y que no se repita el anticipo", () => {
    const [c] = candidatosDeAnticipo([comp({ serie: "F001", numero: 1 })], CLIENTE, "PEN");
    const a = (monto: string) => ({ serie: "F001", numero: 1, monto, afectacion: "gravado" as const });
    expect(errorDeAnticipos([a("1000")], [c])).toBeNull();
    for (const mal of ["", "0", "-5", "abc", "10.123"]) expect(errorDeAnticipos([a(mal)], [c]), mal).toMatch(/mayor que cero/);
    expect(errorDeAnticipos([a("1000.01")], [c])).toMatch(/solo tiene 1000\.00 por descontar en gravado/);
    expect(errorDeAnticipos([a("500"), a("400")], [c])).toMatch(/3215/);
  });

  it("manda serie, número, monto y afectación; nada si algo no vale", () => {
    const [c] = candidatosDeAnticipo([comp({ serie: "F001", numero: 1 })], CLIENTE, "PEN");
    expect(anticiposAApi([{ serie: "F001", numero: 1, monto: "500.50", afectacion: "gravado" }], [c])).toEqual([{ serie: "F001", numero: 1, monto: 500.5, afectacion: "gravado" }]);
    expect(anticiposAApi([], [c])).toBeUndefined();
    expect(anticiposAApi([{ serie: "F001", numero: 1, monto: "9999", afectacion: "gravado" }], [c])).toBeUndefined();
  });
});
