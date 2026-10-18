import { describe, expect, it } from "vitest";
import { errorDeReferencias, hayReferencias, numeroDeGuiaValido, REFERENCIAS_VACIAS, referenciasAApi, TIPOS_DE_DOCUMENTO, type ReferenciasForm } from "./referencias";

const ref = (p: Partial<ReferenciasForm>): ReferenciasForm => ({ ...REFERENCIAS_VACIAS, ...p });

describe("referencias (#300)", () => {
  it("sin nada escrito no hay error ni nada que mandar", () => {
    expect(errorDeReferencias(REFERENCIAS_VACIAS, false)).toBeNull();
    expect(referenciasAApi(REFERENCIAS_VACIAS, false)).toEqual({});
    expect(hayReferencias(REFERENCIAS_VACIAS)).toBe(false);
  });

  it("el número de guía sigue la regla 4006: T###, 0000, EG01–EG04, EG07, G### y V###", () => {
    for (const bueno of ["T001-123", "0001-1", "EG01-45", "EG07-99999999", "G001-8", "V001-45", " T001-123 "]) expect(numeroDeGuiaValido(bueno), bueno).toBe(true);
    for (const malo of ["T001123", "EEEE-1", "EG05-1", "T001-123456789", "t001-1", "T01-1", ""]) expect(numeroDeGuiaValido(malo), malo).toBe(false);
  });

  it("una guía mal escrita, vacía o repetida se dice con su código", () => {
    expect(errorDeReferencias(ref({ guias: [{ tipo: "09", numero: "123" }] }), false)).toMatch(/4006/);
    expect(errorDeReferencias(ref({ guias: [{ tipo: "09", numero: " " }] }), false)).toMatch(/Falta el número de una guía/);
    expect(errorDeReferencias(ref({ guias: [{ tipo: "09", numero: "T001-1" }, { tipo: "09", numero: "T001-1" }] }), false)).toMatch(/2364/);
    // La misma serie-número con otro tipo de guía no es la misma guía.
    expect(errorDeReferencias(ref({ guias: [{ tipo: "09", numero: "T001-1" }, { tipo: "31", numero: "T001-1" }] }), false)).toBeNull();
  });

  it("un documento relacionado: hasta 30 caracteres sin espacios (4010), sin repetir (2365) y 06/07 solo en factura (4009)", () => {
    expect(errorDeReferencias(ref({ documentos: [{ tipo: "05", numero: "SCOP 1" }] }), false)).toMatch(/4010/);
    expect(errorDeReferencias(ref({ documentos: [{ tipo: "05", numero: "S".repeat(31) }] }), false)).toMatch(/4010/);
    expect(errorDeReferencias(ref({ documentos: [{ tipo: "05", numero: "S".repeat(30) }] }), false)).toBeNull();
    expect(errorDeReferencias(ref({ documentos: [{ tipo: "05", numero: "A-1" }, { tipo: "05", numero: "A-1" }] }), false)).toMatch(/2365/);
    expect(errorDeReferencias(ref({ documentos: [{ tipo: "06", numero: "F001-1" }] }), false)).toBeNull();
    expect(errorDeReferencias(ref({ documentos: [{ tipo: "06", numero: "F001-1" }] }), true)).toMatch(/4009/);
    expect(errorDeReferencias(ref({ documentos: [{ tipo: "07", numero: "T001-1" }] }), true)).toMatch(/4009/);
    expect(errorDeReferencias(ref({ documentos: [{ tipo: "99", numero: "X-1" }] }), true)).toBeNull();
    expect(TIPOS_DE_DOCUMENTO.filter((t) => t.soloFactura).map((t) => t.codigo)).toEqual(["06", "07"]);
  });

  it("la orden de compra tiene hasta 20 caracteres y solo es de la factura (4233)", () => {
    expect(errorDeReferencias(ref({ ordenCompra: "O".repeat(21) }), false)).toMatch(/4233/);
    expect(errorDeReferencias(ref({ ordenCompra: "OC-2026-0457" }), false)).toBeNull();
    expect(errorDeReferencias(ref({ ordenCompra: "OC\n1" }), false)).toMatch(/4233/);
    expect(referenciasAApi(ref({ ordenCompra: "OC-1" }), true)).toEqual({});
  });

  it("las observaciones admiten hasta 1000 caracteres", () => {
    expect(errorDeReferencias(ref({ observaciones: "x".repeat(1000) }), false)).toBeNull();
    expect(errorDeReferencias(ref({ observaciones: "x".repeat(1001) }), false)).toMatch(/1000/);
  });

  it("manda solo lo llenado, sin espacios en los extremos, y nada si algo no vale", () => {
    const completo = ref({ ordenCompra: " OC-1 ", guias: [{ tipo: "09", numero: " T001-123 " }], documentos: [{ tipo: "05", numero: "SCOP-1" }], observaciones: " Entrega en almacén\ncentral " });
    expect(referenciasAApi(completo, false)).toEqual({
      orden_compra: "OC-1",
      guias: [{ tipo: "09", numero: "T001-123" }],
      documentos_relacionados: [{ tipo: "05", numero: "SCOP-1" }],
      observaciones: "Entrega en almacén\ncentral",
    });
    expect(hayReferencias(completo)).toBe(true);
    expect(referenciasAApi(ref({ guias: [{ tipo: "09", numero: "mal" }] }), false)).toEqual({});
  });
});
