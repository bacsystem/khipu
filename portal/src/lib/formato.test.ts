import { describe, expect, it } from "vitest";
import { diasEntre, formatearFecha, formatearFechaDeLima, formatearFechaHora, formatearMes, formatearMonto, formatearNumero, inicioDelProximoCiclo, sumarDias, ultimoDiaCubierto, venceDesdeFechaDeLima } from "./formato";

describe("formatearNumero", () => {
  it("usa separador de miles y siempre 2 decimales", () => {
    expect(formatearNumero(1234.5)).toBe("1,234.50");
    expect(formatearNumero(0)).toBe("0.00");
    expect(formatearNumero(1000000)).toBe("1,000,000.00");
  });

  it("acepta valores que llegan como string desde la API", () => {
    expect(formatearNumero("12.3" as unknown as number)).toBe("12.30");
  });
});

describe("formatearMonto", () => {
  it("antepone el símbolo de la moneda conocida", () => {
    expect(formatearMonto("PEN", 2000.01)).toBe("S/ 2,000.01");
    expect(formatearMonto("USD", 5)).toBe("$ 5.00");
  });

  it("cubre las tres monedas que acepta la API con su símbolo", () => {
    // EUR llegó tarde al mapa: el formulario de emisión lo ofrecía y el pie de totales mostraba "EUR 2,500.01"
    // mientras el campo de precio mostraba "€ 2,500.01" para el mismo importe.
    expect(formatearMonto("PEN", 1)).toBe("S/ 1.00");
    expect(formatearMonto("USD", 1)).toBe("$ 1.00");
    expect(formatearMonto("EUR", 1)).toBe("€ 1.00");
  });

  it("usa el código ISO cuando la moneda no tiene símbolo", () => {
    expect(formatearMonto("CLP", 1)).toBe("CLP 1.00");
  });
});

describe("formatearFecha", () => {
  it("convierte ISO a día + mes abreviado + año", () => {
    expect(formatearFecha("2026-09-14")).toBe("14 Set 2026");
    expect(formatearFecha("2026-01-05")).toBe("5 Ene 2026");
  });

  it("devuelve el valor original si no es una fecha ISO", () => {
    expect(formatearFecha("hoy")).toBe("hoy");
  });
});

describe("formatearFechaHora", () => {
  it("convierte un instante UTC a fecha y hora de Lima (UTC-5)", () => {
    expect(formatearFechaHora("2026-09-15T15:32:00Z")).toBe("15 Set 2026, 10:32");
    expect(formatearFechaHora("2026-01-01T02:05:00Z")).toBe("31 Dic 2025, 21:05");
  });

  it("devuelve el texto tal cual si no es una fecha válida", () => {
    expect(formatearFechaHora("no-es-fecha")).toBe("no-es-fecha");
  });
});

describe("diasEntre", () => {
  it("cuenta días calendario entre dos fechas ISO, sin zona horaria", () => {
    expect(diasEntre("2026-09-11", "2026-09-18")).toBe(7);
    expect(diasEntre("2026-09-18", "2026-09-18")).toBe(0);
    expect(diasEntre("2026-12-31", "2027-01-01")).toBe(1);
  });

  it("es negativo cuando la segunda fecha es anterior", () => {
    expect(diasEntre("2026-09-18", "2026-09-15")).toBe(-3);
  });
});

describe("sumarDias", () => {
  it("suma y resta días calendario cruzando mes y año", () => {
    expect(sumarDias("2026-09-18", 3)).toBe("2026-09-21");
    expect(sumarDias("2026-09-01", -3)).toBe("2026-08-29");
    expect(sumarDias("2027-01-01", -1)).toBe("2026-12-31");
  });

  it("cuenta el 29 de febrero en año bisiesto", () => {
    expect(sumarDias("2028-02-28", 1)).toBe("2028-02-29");
    expect(sumarDias("2027-02-28", 1)).toBe("2027-03-01");
  });

  it("es el inverso de diasEntre", () => {
    expect(diasEntre("2026-09-18", sumarDias("2026-09-18", -3))).toBe(-3);
  });

  it("con una fecha ilegible devuelve la entrada en vez de lanzar", () => {
    // Sin la guarda, `toISOString()` sobre un Invalid Date lanza RangeError y se cae el render entero.
    expect(sumarDias("", -3)).toBe("");
    expect(sumarDias("no-es-fecha", -3)).toBe("no-es-fecha");
  });
});

describe("formatearFechaDeLima", () => {
  it("da la fecha de Lima del instante, no la de UTC", () => {
    expect(formatearFechaDeLima("2026-11-01T05:00:00Z")).toBe("1 Nov 2026");
    // 03:00 UTC del 1 de noviembre todavía es el 31 de octubre en Lima.
    expect(formatearFechaDeLima("2026-11-01T03:00:00Z")).toBe("31 Oct 2026");
  });

  it("con un instante ilegible devuelve la entrada en vez de lanzar", () => {
    expect(formatearFechaDeLima("no-es-fecha")).toBe("no-es-fecha");
  });
});

/** La misma regla que aplica el backend (CicloMensual): el mes calendario en Lima. */
describe("inicioDelProximoCiclo (#190)", () => {
  it("es la medianoche del día 1 del mes que viene, hora de Lima (05:00Z)", () => {
    expect(inicioDelProximoCiclo(new Date("2026-10-15T15:00:00Z"))).toBe("2026-11-01T05:00:00.000Z");
  });

  it("usa el mes de Lima, no el de UTC: a 01:00Z del 1 de noviembre todavía es octubre", () => {
    expect(inicioDelProximoCiclo(new Date("2026-11-01T01:00:00Z"))).toBe("2026-11-01T05:00:00.000Z");
  });

  it("justo en el inicio de un ciclo ya es el ciclo nuevo", () => {
    expect(inicioDelProximoCiclo(new Date("2026-11-01T05:00:00Z"))).toBe("2026-12-01T05:00:00.000Z");
    expect(inicioDelProximoCiclo(new Date("2026-11-01T04:59:59Z"))).toBe("2026-11-01T05:00:00.000Z");
  });

  it("diciembre salta al año siguiente", () => {
    expect(inicioDelProximoCiclo(new Date("2026-12-20T12:00:00Z"))).toBe("2027-01-01T05:00:00.000Z");
  });
});

describe("venceDesdeFechaDeLima y ultimoDiaCubierto (#191)", () => {
  it("«pagado hasta el 31 de octubre» vence a la medianoche del 1 de noviembre en Lima (05:00Z)", () => {
    expect(venceDesdeFechaDeLima("2026-10-31")).toBe("2026-11-01T05:00:00.000Z");
  });

  it("cruza el fin de mes y de año", () => {
    expect(venceDesdeFechaDeLima("2026-09-30")).toBe("2026-10-01T05:00:00.000Z");
    expect(venceDesdeFechaDeLima("2026-12-31")).toBe("2027-01-01T05:00:00.000Z");
    expect(venceDesdeFechaDeLima("2028-02-28")).toBe("2028-02-29T05:00:00.000Z");
    expect(venceDesdeFechaDeLima("2027-02-28")).toBe("2027-03-01T05:00:00.000Z");
  });

  it("el último día cubierto es el día anterior al vencimiento, en hora de Lima", () => {
    expect(ultimoDiaCubierto("2026-11-01T05:00:00Z")).toBe("2026-10-31");
    expect(ultimoDiaCubierto("2027-01-01T05:00:00Z")).toBe("2026-12-31");
  });

  it("son inversas una de la otra", () => {
    for (const dia of ["2026-10-31", "2026-02-28", "2028-02-29", "2026-12-31"]) expect(ultimoDiaCubierto(venceDesdeFechaDeLima(dia))).toBe(dia);
  });

  it("con un vencimiento ilegible devuelve la entrada en vez de lanzar", () => {
    expect(ultimoDiaCubierto("no-es-fecha")).toBe("no-es-fecha");
  });
});

describe("formatearMes", () => {
  it("«2026-09» es «Set 2026»", () => {
    expect(formatearMes("2026-09")).toBe("Set 2026");
    expect(formatearMes("2027-01")).toBe("Ene 2027");
    expect(formatearMes("2026-12")).toBe("Dic 2026");
  });

  it("con algo que no es un mes devuelve la entrada", () => {
    for (const malo of ["", "2026", "2026-13", "2026-00", "octubre"]) expect(formatearMes(malo), malo).toBe(malo);
  });
});
