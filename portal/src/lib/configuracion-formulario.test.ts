import { describe, expect, it } from "vitest";
import type { BannerConfigurado } from "@/lib/api/admin-configuracion";
import {
  cuerpoDeAviso,
  DIAS_MAX_DE_AVISO,
  estadoDelAviso,
  insertarEnPosicion,
  instanteALima,
  limaAInstante,
  TEXTO_DE_AVISO_MAX,
  valoresDeAviso,
  valoresNuevoAviso,
} from "./configuracion-formulario";

describe("límites", () => {
  it("son los del backend", () => {
    expect(TEXTO_DE_AVISO_MAX).toBe(300);
    expect(DIAS_MAX_DE_AVISO).toBe(90);
  });
});

describe("limaAInstante", () => {
  it("la hora de Lima es UTC−5: las 22:00 de Lima son las 03:00 del día siguiente en UTC", () => {
    expect(limaAInstante("2026-10-15T22:00")).toBe("2026-10-16T03:00:00.000Z");
  });

  it("no depende de la zona del navegador: mediodía de Lima es siempre las 17:00 UTC", () => {
    expect(limaAInstante("2026-01-01T12:00")).toBe("2026-01-01T17:00:00.000Z");
    expect(limaAInstante("2026-07-01T12:00")).toBe("2026-07-01T17:00:00.000Z");
  });

  it("lo que no es una fecha y hora es null", () => {
    for (const mal of ["", "mañana", "2026-10-15", "2026-10-15T22", "2026-10-15 22:00", "2026-13-45T25:61", "2026-10-15T22:00:00", "2026-10-15T22:00Z", "x2026-10-15T22:00", "2026-10-15T22:00x", "99999-10-15T22:00"]) expect(limaAInstante(mal), mal).toBeNull();
  });
});

describe("instanteALima", () => {
  it("el instante en UTC se ve en hora de Lima", () => {
    expect(instanteALima("2026-10-16T03:00:00Z")).toBe("2026-10-15T22:00");
    expect(instanteALima("2026-10-15T17:00:00.000Z")).toBe("2026-10-15T12:00");
  });

  it("cruza el día hacia atrás cuando hace falta", () => {
    expect(instanteALima("2026-10-16T01:30:00Z")).toBe("2026-10-15T20:30");
  });

  it("es la inversa de limaAInstante", () => {
    for (const v of ["2026-10-15T22:00", "2026-01-01T00:00", "2026-12-31T23:59"]) expect(instanteALima(limaAInstante(v) as string)).toBe(v);
  });
});

describe("valores iniciales", () => {
  it("un aviso nuevo empieza ahora, en hora de Lima, y sin texto ni fin", () => {
    expect(valoresNuevoAviso(new Date("2026-10-15T20:00:00Z"))).toEqual({ texto: "", desde: "2026-10-15T15:00", hasta: "" });
  });

  it("uno publicado se edita desde sus datos, en hora de Lima", () => {
    const b: BannerConfigurado = { texto: "Mantenimiento", desde: "2026-10-16T03:00:00Z", hasta: "2026-10-16T08:00:00Z", actualizado_en: "x", vigente_ahora: false };

    expect(valoresDeAviso(b)).toEqual({ texto: "Mantenimiento", desde: "2026-10-15T22:00", hasta: "2026-10-16T03:00" });
  });
});

describe("cuerpoDeAviso", () => {
  it("con texto y fechas arma el cuerpo en instantes UTC, sin recortar el texto (eso lo hace el backend)", () => {
    const r = cuerpoDeAviso({ texto: "  Mantenimiento  ", desde: "2026-10-15T22:00", hasta: "2026-10-16T01:00" });

    expect(r).toEqual({ cuerpo: { texto: "  Mantenimiento  ", desde: "2026-10-16T03:00:00.000Z", hasta: "2026-10-16T06:00:00.000Z" } });
  });

  it("sin texto, o con solo espacios, pide el texto", () => {
    for (const texto of ["", "   ", "\t"]) expect(cuerpoDeAviso({ texto, desde: "2026-10-15T22:00", hasta: "2026-10-16T01:00" }), JSON.stringify(texto)).toEqual({ errores: { texto: "Escribe el texto del aviso." } });
  });

  it("sin fechas pide cada una por separado", () => {
    expect(cuerpoDeAviso({ texto: "x", desde: "", hasta: "2026-10-16T01:00" })).toEqual({ errores: { desde: "Indica desde cuándo se muestra." } });
    expect(cuerpoDeAviso({ texto: "x", desde: "2026-10-15T22:00", hasta: "" })).toEqual({ errores: { hasta: "Indica hasta cuándo se muestra." } });
  });

  it("junta todos los errores a la vez", () => {
    expect(cuerpoDeAviso({ texto: "", desde: "", hasta: "" })).toEqual({
      errores: { texto: "Escribe el texto del aviso.", desde: "Indica desde cuándo se muestra.", hasta: "Indica hasta cuándo se muestra." },
    });
  });

  it("el orden y los límites no se comprueban acá: los pone el backend", () => {
    const r = cuerpoDeAviso({ texto: "x", desde: "2026-10-16T01:00", hasta: "2026-10-15T22:00" });

    expect("cuerpo" in r).toBe(true);
  });
});

describe("estadoDelAviso", () => {
  const ahora = new Date("2026-10-15T20:00:00Z");

  it("si el backend dice que se muestra, está vigente", () => {
    expect(estadoDelAviso({ desde: "2026-10-15T19:00:00Z", vigente_ahora: true }, ahora)).toBe("vigente");
  });

  it("si no se muestra y todavía no empezó, está programado", () => {
    expect(estadoDelAviso({ desde: "2026-10-15T21:00:00Z", vigente_ahora: false }, ahora)).toBe("programado");
  });

  it("si no se muestra y ya empezó, venció", () => {
    expect(estadoDelAviso({ desde: "2026-10-15T10:00:00Z", vigente_ahora: false }, ahora)).toBe("vencido");
  });

  it("empezar justo ahora no es programado", () => {
    expect(estadoDelAviso({ desde: "2026-10-15T20:00:00Z", vigente_ahora: false }, ahora)).toBe("vencido");
  });
});

describe("insertarEnPosicion", () => {
  it("pone la marca donde estaba el cursor y deja el cursor después", () => {
    expect(insertarEnPosicion("Abre aquí", 5, 5, "{enlace}")).toEqual({ texto: "Abre {enlace}aquí", cursor: 13 });
  });

  it("reemplaza lo seleccionado", () => {
    expect(insertarEnPosicion("Abre ESTO ya", 5, 9, "{enlace}")).toEqual({ texto: "Abre {enlace} ya", cursor: 13 });
  });

  it("al principio y al final", () => {
    expect(insertarEnPosicion("hola", 0, 0, "{a}")).toEqual({ texto: "{a}hola", cursor: 3 });
    expect(insertarEnPosicion("hola", 4, 4, "{a}")).toEqual({ texto: "hola{a}", cursor: 7 });
  });

  it("una posición fuera del texto se acota a sus bordes", () => {
    expect(insertarEnPosicion("hola", 99, 120, "{a}")).toEqual({ texto: "hola{a}", cursor: 7 });
    expect(insertarEnPosicion("hola", -5, -1, "{a}")).toEqual({ texto: "{a}hola", cursor: 3 });
  });

  it("un fin antes del inicio no borra nada", () => {
    expect(insertarEnPosicion("hola", 3, 1, "{a}")).toEqual({ texto: "hol{a}a", cursor: 6 });
  });

  it("en un texto vacío es solo la marca", () => {
    expect(insertarEnPosicion("", 0, 0, "{a}")).toEqual({ texto: "{a}", cursor: 3 });
  });
});
