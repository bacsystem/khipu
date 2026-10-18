import { describe, expect, it } from "vitest";
import { documentosDeExportacion, errorDeExportacion, esExportacion, EXPORTACION_SIN_RUC, OPERACIONES } from "./operaciones";
import { CODIGOS_PAIS, listarPaises } from "./paises";

const datos = (p: Partial<Parameters<typeof errorDeExportacion>[0]>) => ({ operacion: "0200", incoterm: "", paisDeUso: "", paisDelComprador: "US", ...p });

describe("operaciones (#311)", () => {
  it("ofrece la venta interna (con el NRUS, solo en boleta) y la exportación, sin el hospedaje ni el paquete turístico (0202, 0205)", () => {
    expect(OPERACIONES.map((o) => o.codigo)).toEqual(["0101", "0113", "0200", "0201", "0203", "0204", "0206", "0207", "0208"]);
    expect(esExportacion("0200")).toBe(true);
    expect(esExportacion("0208")).toBe(true);
    expect(esExportacion("0101")).toBe(false);
    expect(esExportacion("1001")).toBe(false);
  });

  it("en una factura de exportación el RUC solo vale donde la regla lo permite (2800); en una boleta nunca", () => {
    for (const op of EXPORTACION_SIN_RUC) expect(documentosDeExportacion(op, false)).not.toContain("6");
    expect(documentosDeExportacion("0203", false)).toContain("6");
    expect(documentosDeExportacion("0203", true)).not.toContain("6");
    expect(documentosDeExportacion("0200", false)).toEqual(["0", "1", "4", "7", "A", "B", "C", "D", "E", "G"]);
  });
});

describe("errorDeExportacion (#311)", () => {
  it("una venta interna no tiene nada que decir", () => {
    expect(errorDeExportacion(datos({ operacion: "0101", paisDelComprador: "" }), false)).toBeNull();
  });

  it("la factura de exportación lleva el país del cliente; la boleta puede no llevarlo", () => {
    expect(errorDeExportacion(datos({ paisDelComprador: "" }), false)).toMatch(/país/);
    expect(errorDeExportacion(datos({ paisDelComprador: "" }), true)).toBeNull();
    expect(errorDeExportacion(datos({}), false)).toBeNull();
  });

  it("los servicios 0201 y 0208 piden el país de uso, distinto de Perú", () => {
    expect(errorDeExportacion(datos({ operacion: "0201" }), false)).toMatch(/3098/);
    expect(errorDeExportacion(datos({ operacion: "0208", paisDeUso: "PE" }), false)).toMatch(/3099/);
    expect(errorDeExportacion(datos({ operacion: "0208", paisDeUso: "CL" }), false)).toBeNull();
    expect(errorDeExportacion(datos({ operacion: "0203" }), false)).toBeNull();
  });
});

describe("países (catálogo 04)", () => {
  it("son los 249 de ISO 3166-1, sin repetir", () => {
    expect(CODIGOS_PAIS).toHaveLength(249);
    expect(new Set(CODIGOS_PAIS).size).toBe(249);
  });

  it("se listan con su nombre en español y los habituales primero", () => {
    const lista = listarPaises();
    expect(lista).toHaveLength(249);
    expect(lista[0].codigo).toBe("US");
    expect(lista.find((p) => p.codigo === "PE")?.nombre).toBe("Perú");
    expect(lista.find((p) => p.codigo === "DE")?.nombre).toBe("Alemania");
  });
});
