import { describe, expect, it } from "vitest";
import { PRIVACIDAD } from "./privacidad";
import { TERMINOS } from "./terminos";
import { revisadoPorLegal, VERSION_PRIVACIDAD, VERSION_TERMINOS, VERSION_TEXTOS_LEGALES } from "./version";

const FECHA = /^\d{4}-\d{2}-\d{2}$/;

/**
 * #342: lo que acepta el usuario es `VERSION_TEXTOS_LEGALES`. Si un texto cambia de versión y esta no sube, nadie vuelve a aceptar; por eso es
 * la más reciente de las dos. Que sea la misma que `app.terminos-version` del backend lo comprueba `TerminosVersionTest` del backend.
 */
/** #580: el aviso de borrador lo quita `REVISADO_POR_LEGAL=true` en el entorno; ante cualquier duda, se sigue avisando. */
describe("revisadoPorLegal", () => {
  it("solo true los da por revisados, sin importar mayúsculas ni espacios", () => {
    for (const v of ["true", "TRUE", " True "]) expect(revisadoPorLegal(v)).toBe(true);
  });

  it("vacía, ausente o cualquier otro valor deja el aviso", () => {
    for (const v of [undefined, "", "false", "1", "si", "yes", "truee"]) expect(revisadoPorLegal(v)).toBe(false);
  });

  it("lee la variable de entorno al llamarse, no al cargar el módulo", () => {
    const antes = process.env.REVISADO_POR_LEGAL;
    try {
      process.env.REVISADO_POR_LEGAL = "true";
      expect(revisadoPorLegal()).toBe(true);
      process.env.REVISADO_POR_LEGAL = "";
      expect(revisadoPorLegal()).toBe(false);
    } finally {
      if (antes === undefined) delete process.env.REVISADO_POR_LEGAL;
      else process.env.REVISADO_POR_LEGAL = antes;
    }
  });
});

describe("versiones de los textos legales", () => {
  it("son fechas AAAA-MM-DD válidas", () => {
    for (const v of [VERSION_TERMINOS, VERSION_PRIVACIDAD, VERSION_TEXTOS_LEGALES]) {
      expect(v).toMatch(FECHA);
      expect(new Date(`${v}T00:00:00Z`).toISOString().slice(0, 10)).toBe(v);
    }
  });

  it("la versión que se acepta es la más reciente de los dos textos", () => {
    expect(VERSION_TEXTOS_LEGALES).toBe([VERSION_TERMINOS, VERSION_PRIVACIDAD].sort().at(-1));
  });

  it("cada documento lleva su versión", () => {
    expect(TERMINOS.version).toBe(VERSION_TERMINOS);
    expect(PRIVACIDAD.version).toBe(VERSION_PRIVACIDAD);
  });

  it("cada documento tiene secciones con título único y algo de texto", () => {
    for (const doc of [TERMINOS, PRIVACIDAD]) {
      expect(doc.secciones.length).toBeGreaterThan(0);
      const titulos = doc.secciones.map((s) => s.titulo);
      expect(new Set(titulos).size).toBe(titulos.length);
      for (const s of doc.secciones) expect(s.parrafos.length + (s.lista?.length ?? 0)).toBeGreaterThan(0);
    }
  });

  /** Lo mínimo que la Ley 29733 pide informar: si el borrador lo pierde al editarse, que se note. */
  it("la política de privacidad habla de lo que exige la Ley 29733", () => {
    const texto = PRIVACIDAD.secciones.map((s) => [s.titulo, ...s.parrafos, ...(s.lista ?? []), ...(s.cierre ?? [])].join(" ")).join(" ");
    for (const tema of ["Responsable", "finalidad|Para qué", "SUNAT", "Transferencia internacional", "cinco años", "ARCO", "Autoridad Nacional de Protección de Datos Personales"])
      expect(texto).toMatch(new RegExp(tema, "i"));
  });
});
