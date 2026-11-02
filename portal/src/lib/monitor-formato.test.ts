import { describe, expect, it } from "vitest";
import { duracionEnPalabras, formatearHoraConSegundos, formatearHoraDeLima, formatearTasa, proporcionDeBarra } from "./monitor-formato";

describe("formatearHoraDeLima", () => {
  it("muestra la hora de Lima (UTC-5), no la del navegador ni la de UTC", () => {
    expect(formatearHoraDeLima("2026-10-15T15:20:00Z")).toBe("10:20");
    expect(formatearHoraDeLima("2026-10-15T05:00:00Z")).toBe("00:00");
    expect(formatearHoraDeLima("2026-10-15T04:59:00Z")).toBe("23:59");
  });

  it("la medianoche es 00:00 y no 24:00", () => {
    expect(formatearHoraDeLima("2026-10-16T05:00:00Z")).toBe("00:00");
  });

  it("un instante ilegible se devuelve tal cual", () => {
    expect(formatearHoraDeLima("ayer")).toBe("ayer");
    expect(formatearHoraDeLima("")).toBe("");
  });
});

describe("formatearHoraConSegundos", () => {
  it("agrega los segundos, en hora de Lima", () => {
    expect(formatearHoraConSegundos("2026-10-15T15:20:35Z")).toBe("10:20:35");
    expect(formatearHoraConSegundos("2026-10-15T05:00:07Z")).toBe("00:00:07");
  });

  it("un instante ilegible se devuelve tal cual", () => {
    expect(formatearHoraConSegundos("ayer")).toBe("ayer");
  });
});

describe("formatearTasa", () => {
  it("es un porcentaje con un decimal", () => {
    expect(formatearTasa(0.0291)).toBe("2.9 %");
    expect(formatearTasa(0.25)).toBe("25.0 %");
    expect(formatearTasa(1)).toBe("100.0 %");
  });

  it("cero es 0.0 %: sin rechazos es una buena noticia, no un dato que falta", () => {
    expect(formatearTasa(0)).toBe("0.0 %");
  });

  it("ausente es un guion y no 0 %: sin resueltos no hay tasa que mostrar", () => {
    expect(formatearTasa(undefined)).toBe("—");
  });
});

describe("duracionEnPalabras", () => {
  it("menos de un minuto va en segundos", () => {
    expect(duracionEnPalabras(0)).toBe("0 s");
    expect(duracionEnPalabras(45)).toBe("45 s");
    expect(duracionEnPalabras(59)).toBe("59 s");
  });

  it("de un minuto a una hora va en minutos, redondeando hacia abajo", () => {
    expect(duracionEnPalabras(60)).toBe("1 min");
    expect(duracionEnPalabras(420)).toBe("7 min");
    expect(duracionEnPalabras(119)).toBe("1 min");
    expect(duracionEnPalabras(3599)).toBe("59 min");
  });

  it("de una hora en adelante va en horas y minutos, sin minutos si no sobran", () => {
    expect(duracionEnPalabras(3600)).toBe("1 h");
    expect(duracionEnPalabras(3900)).toBe("1 h 5 min");
    expect(duracionEnPalabras(7200)).toBe("2 h");
    expect(duracionEnPalabras(90_000)).toBe("25 h");
  });

  it("nunca es negativo ni tiene decimales", () => {
    expect(duracionEnPalabras(-30)).toBe("0 s");
    expect(duracionEnPalabras(12.9)).toBe("12 s");
  });
});

describe("proporcionDeBarra", () => {
  it("es la fracción del máximo", () => {
    expect(proporcionDeBarra(5, 10)).toBe(0.5);
    expect(proporcionDeBarra(10, 10)).toBe(1);
    expect(proporcionDeBarra(0, 10)).toBe(0);
  });

  it("sin máximo, o con uno negativo, es cero y nunca NaN", () => {
    expect(proporcionDeBarra(0, 0)).toBe(0);
    expect(proporcionDeBarra(5, 0)).toBe(0);
    expect(proporcionDeBarra(5, -1)).toBe(0);
  });

  it("se queda entre 0 y 1", () => {
    expect(proporcionDeBarra(20, 10)).toBe(1);
    expect(proporcionDeBarra(-3, 10)).toBe(0);
  });
});
