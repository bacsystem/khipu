import { describe, expect, it } from "vitest";
import { enlaceASeccion, seccionesPendientes, seccionInicial } from "./secciones";

const lista = { tiene_certificado: true, certificado_vigencia_hasta: "2027-01-01", tiene_credenciales_sol: true, credenciales_sol_rechazadas: null };
const HOY = "2026-10-09";

/** #276: «Fiscal & certificado» en pestañas. Se abre en la que hace falta y se puede enlazar a una con `?seccion=`. */
describe("seccionesPendientes", () => {
  it("con certificado vigente y credenciales SOL no hay pendientes", () => {
    expect(seccionesPendientes(lista, HOY)).toEqual([]);
  });

  it("sin certificado, o vencido, el certificado está pendiente", () => {
    expect(seccionesPendientes({ ...lista, tiene_certificado: false, certificado_vigencia_hasta: null }, HOY)).toEqual(["certificado"]);
    expect(seccionesPendientes({ ...lista, certificado_vigencia_hasta: "2026-10-08" }, HOY)).toEqual(["certificado"]);
  });

  it("sin credenciales SOL, o con las que SUNAT rechazó, SOL está pendiente", () => {
    expect(seccionesPendientes({ ...lista, tiene_credenciales_sol: false }, HOY)).toEqual(["sol"]);
    expect(seccionesPendientes({ ...lista, credenciales_sol_rechazadas: { desde: "2026-10-08T10:00:00Z", motivo: "0102" } }, HOY)).toEqual(["sol"]);
  });
});

describe("seccionInicial", () => {
  it("la pedida en la URL manda, aunque haya otra pendiente", () => {
    expect(seccionInicial("pdf", { ...lista, tiene_certificado: false }, HOY)).toBe("pdf");
  });

  it("sin pedirla, abre la primera pendiente: el certificado antes que SOL", () => {
    expect(seccionInicial(undefined, { ...lista, tiene_certificado: false, tiene_credenciales_sol: false }, HOY)).toBe("certificado");
    expect(seccionInicial(undefined, { ...lista, tiene_credenciales_sol: false }, HOY)).toBe("sol");
  });

  it("sin pendientes, o con una sección que no existe, abre «Datos fiscales»", () => {
    expect(seccionInicial(undefined, lista, HOY)).toBe("datos");
    expect(seccionInicial("inventada", lista, HOY)).toBe("datos");
  });
});

describe("enlaceASeccion", () => {
  it("lleva a la pestaña con `?seccion=`", () => {
    expect(enlaceASeccion("certificado")).toBe("/empresa?seccion=certificado");
    expect(enlaceASeccion("sol")).toBe("/empresa?seccion=sol");
  });
});
