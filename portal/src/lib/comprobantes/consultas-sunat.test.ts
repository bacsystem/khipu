import { describe, expect, it } from "vitest";
import type { Comprobante } from "@/lib/api/facturas";
import { datosDeValidez, puedeRecuperarCdr, rucDelEmisor } from "./consultas-sunat";

type Datos = Parameters<typeof datosDeValidez>[0];
const base = (extra: Partial<Datos> = {}): Datos => ({
  tipo: "01", serie: "F001", numero: 7, fecha_emision: "2026-09-02", estado_documento: "ENVIADO", nombre_archivo: "20123456786-01-F001-00000007",
  receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "X", direccion: null }, totales: { total: 59 } as Comprobante["totales"], ...extra,
});

describe("consultas a SUNAT desde la ficha (#315)", () => {
  it("recuperar la constancia: solo facturas y sus notas, solo enviadas o con error de envío", () => {
    const c = (extra: Partial<Parameters<typeof puedeRecuperarCdr>[0]>) => puedeRecuperarCdr({ tipo: "01", estado_documento: "ENVIADO", nota: null, ...extra });
    expect(c({})).toBe(true);
    expect(c({ estado_documento: "ERROR_ENVIO" })).toBe(true);
    for (const estado of ["ACEPTADO", "RECHAZADO", "FIRMADO", "RECIBIDO", "ANULADO"] as const) expect(c({ estado_documento: estado }), estado).toBe(false);
    // Una nota de factura sí; una boleta y una nota de boleta no (`Comprobante.esDeBoleta`).
    expect(c({ tipo: "07", nota: { tipo_afectado: "01" } as never })).toBe(true);
    expect(c({ tipo: "03" })).toBe(false);
    expect(c({ tipo: "07", nota: { tipo_afectado: "03" } as never })).toBe(false);
    expect(c({ tipo: "08", nota: { tipo_afectado: "03" } as never })).toBe(false);
  });

  it("el RUC del emisor sale del nombre de archivo y solo si son 11 dígitos", () => {
    expect(rucDelEmisor("20123456786-01-F001-00000007")).toBe("20123456786");
    for (const mal of [null, undefined, "", "F001-00000007", "2012345678-01-F001-1", "2012345678a-01-F001-1"]) expect(rucDelEmisor(mal), String(mal)).toBeNull();
  });

  it("la validez lleva los datos del comprobante y el comprador identificado", () => {
    expect(datosDeValidez(base())).toEqual({ ruc: "20123456786", tipo: "01", serie: "F001", numero: 7, tipoDocReceptor: "6", numDocReceptor: "20554198211", fecha: "2026-09-02", monto: 59 });
  });

  it("un comprador sin documento («-») o sin receptor no manda sus criterios", () => {
    for (const receptor of [{ tipo_doc: "-", num_doc: "-", razon_social: "CLIENTES VARIOS", direccion: null }, null]) {
      const d = datosDeValidez(base({ tipo: "03", receptor }))!;
      expect(d.tipoDocReceptor).toBeUndefined();
      expect(d.numDocReceptor).toBeUndefined();
    }
  });

  it("no se ofrece si todavía no salió hacia SUNAT o no se conoce el emisor", () => {
    for (const estado of ["RECIBIDO", "FIRMADO", "INVALIDO", "DESCARTADO"] as const) expect(datosDeValidez(base({ estado_documento: estado })), estado).toBeNull();
    expect(datosDeValidez(base({ nombre_archivo: null }))).toBeNull();
    expect(datosDeValidez(base({ estado_documento: "ACEPTADO" }))).not.toBeNull();
  });
});
