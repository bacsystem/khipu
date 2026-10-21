import { describe, expect, it } from "vitest";
import { afectaciones, errorDeIsc, gruposAfectacion, incompatibilidadDeTributos, ISC_VACIO, iscAApi, type IscForm, type LineaTributos } from "./tributos";
import es from "@/messages/es.json";

const isc = (p: Partial<IscForm>): IscForm => ({ ...ISC_VACIO, ...p });
const linea = (p: Partial<LineaTributos>): LineaTributos => ({ descripcion: "Bolsa", cantidad: 3, unidad: "NIU", tipoAfectacionIgv: "10", isc: ISC_VACIO, icbper: false, ...p });

describe("afectaciones (catálogo 07)", () => {
  it("ofrece las onerosas y todas las gratuitas, sin la 40 (exportación pide otro tipo de operación)", () => {
    expect(afectaciones(es).map((a) => a.codigo)).toEqual(["10", "20", "30", "17", "11", "12", "13", "14", "15", "16", "21", "31", "32", "33", "34", "35", "36", "37"]);
    expect(gruposAfectacion(es).map((g) => g.grupo)).toEqual(["Onerosas", "Gratuitas gravadas", "Gratuitas exoneradas", "Gratuitas inafectas"]);
  });
});

describe("ISC (catálogo 08)", () => {
  it("sin sistema no hay ISC", () => {
    expect(errorDeIsc(ISC_VACIO, es)).toBeNull();
    expect(iscAApi(ISC_VACIO, es)).toBeUndefined();
  });

  it("cada sistema viaja con los campos que le tocan", () => {
    expect(iscAApi(isc({ sistema: "01", tasa: "35" }), es)).toEqual({ sistema: "01", tasa: 35 });
    expect(iscAApi(isc({ sistema: "02", montoUnitario: "2.25" }), es)).toEqual({ sistema: "02", monto_unitario: 2.25 });
    expect(iscAApi(isc({ sistema: "03", tasa: "30", basePvp: "3.50" }), es)).toEqual({ sistema: "03", tasa: 30, base_pvp: 3.5 });
    // Un campo de otro sistema que quedó escrito no se envía.
    expect(iscAApi(isc({ sistema: "01", tasa: "35", montoUnitario: "9", basePvp: "9" }), es)).toEqual({ sistema: "01", tasa: 35 });
  });

  it("valida como el dominio: positivo y hasta 5 decimales", () => {
    expect(errorDeIsc(isc({ sistema: "01", tasa: "" }), es)).toMatch(/tasa/);
    expect(errorDeIsc(isc({ sistema: "01", tasa: "0" }), es)).toMatch(/tasa/);
    expect(errorDeIsc(isc({ sistema: "01", tasa: "1.123456" }), es)).toMatch(/5 decimales/);
    expect(errorDeIsc(isc({ sistema: "02", montoUnitario: "-1" }), es)).toMatch(/monto por unidad/);
    expect(errorDeIsc(isc({ sistema: "03", tasa: "30", basePvp: "" }), es)).toMatch(/precio de venta al público/);
    expect(iscAApi(isc({ sistema: "01", tasa: "abc" }), es)).toBeUndefined();
  });
});

describe("incompatibilidadDeTributos", () => {
  it("sin conflictos no dice nada", () => {
    expect(incompatibilidadDeTributos([linea({}), linea({ tipoAfectacionIgv: "20" })], undefined, es)).toBeNull();
    expect(incompatibilidadDeTributos([linea({ tipoAfectacionIgv: "17", unidad: "KGM" })], undefined, es)).toBeNull();
  });

  it("el IVAP es de todo el comprobante", () => {
    expect(incompatibilidadDeTributos([linea({ tipoAfectacionIgv: "17" }), linea({ tipoAfectacionIgv: "10" })], undefined, es)).toMatch(/todos sus ítems con IVAP/);
    expect(incompatibilidadDeTributos([linea({ tipoAfectacionIgv: "17", isc: isc({ sistema: "01", tasa: "10" }) })], undefined, es)).toMatch(/IVAP: no lleva ISC ni ICBPER/);
    expect(incompatibilidadDeTributos([linea({ tipoAfectacionIgv: "17", icbper: true })], undefined, es)).toMatch(/IVAP: no lleva ISC ni ICBPER/);
  });

  it("las bolsas del ICBPER son unidades enteras de NIU (2892, 4320)", () => {
    expect(incompatibilidadDeTributos([linea({ icbper: true })], undefined, es)).toBeNull();
    expect(incompatibilidadDeTributos([linea({ icbper: true, unidad: "KGM" })], undefined, es)).toMatch(/NIU/);
    expect(incompatibilidadDeTributos([linea({ icbper: true, cantidad: 2.5 })], undefined, es)).toMatch(/entero de 1 a 99999/);
    expect(incompatibilidadDeTributos([linea({ icbper: true, cantidad: 100000 })], undefined, es)).toMatch(/entero de 1 a 99999/);
  });
});

describe("incompatibilidadDeTributos — NRUS (#297)", () => {
  it("el NRUS (0113) no cobra el ICBPER (3240); la venta interna sí", () => {
    expect(incompatibilidadDeTributos([linea({ icbper: true })], "0113", es)).toMatch(/3240.*NRUS/);
    expect(incompatibilidadDeTributos([linea({ icbper: true })], "0101", es)).toBeNull();
    expect(incompatibilidadDeTributos([linea({})], "0113", es)).toBeNull();
  });
});
