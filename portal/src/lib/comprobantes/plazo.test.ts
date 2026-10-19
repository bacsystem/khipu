import { describe, expect, it } from "vitest";
import { avisoDePlazo, fechaMinimaDeEmision, fechaMinimaDeNota, soloPorResumen } from "./plazo";

/** #292: espeja `PlazoEnvio` del backend. Factura: 3 días. Boleta: sola hasta el 5.º día y por resumen diario hasta el 7.º. */
describe("plazo de envío", () => {
  it("la factura admite hasta 3 días atrás y la boleta hasta 7", () => {
    expect(fechaMinimaDeEmision("factura", "2026-10-10")).toBe("2026-10-07");
    expect(fechaMinimaDeEmision("boleta", "2026-10-10")).toBe("2026-10-03");
  });

  it("la boleta va sola hasta el 5.º día; el 6.º y el 7.º, en el resumen diario", () => {
    expect(soloPorResumen("boleta", "2026-10-05", "2026-10-10")).toBe(false);
    expect(soloPorResumen("boleta", "2026-10-04", "2026-10-10")).toBe(true);
    expect(soloPorResumen("boleta", "2026-10-03", "2026-10-10")).toBe(true);
  });

  it("una factura nunca va en un resumen", () => {
    expect(soloPorResumen("factura", "2026-10-04", "2026-10-10")).toBe(false);
  });
});

/**
 * #356: lo de boleta que sigue sin informarse pide atención desde el 5.º día. Espeja `Comprobante.porInformar` y `porVencerElPlazo` del backend. La ficha avisa con esto, y el
 * texto cambia según lo que le queda: el último día del envío individual, solo el resumen diario, el último día del resumen, o ya pasado.
 */
describe("avisoDePlazo", () => {
  const boleta = (estado_documento: string, fecha_emision = "2026-10-10") => ({ tipo: "03", estado_documento, fecha_emision, nota: null });

  it("antes del 5.º día no avisa", () => {
    expect(avisoDePlazo(boleta("FIRMADO"), "2026-10-14")).toBeNull();
  });

  it("el 5.º día es el último del envío individual", () => {
    expect(avisoDePlazo(boleta("FIRMADO"), "2026-10-15")).toEqual({ fase: "ultimo-dia-individual", limite: "2026-10-17", diasRestantes: 2 });
  });

  it("el 6.º día ya solo va en el resumen diario, y dice cuántos días quedan", () => {
    expect(avisoDePlazo(boleta("FIRMADO"), "2026-10-16")).toEqual({ fase: "solo-resumen", limite: "2026-10-17", diasRestantes: 1 });
  });

  it("el 7.º día es el último en plazo; pasado, se informa fuera de plazo", () => {
    expect(avisoDePlazo(boleta("ERROR_ENVIO"), "2026-10-17")).toEqual({ fase: "ultimo-dia-resumen", limite: "2026-10-17", diasRestantes: 0 });
    expect(avisoDePlazo(boleta("ERROR_ENVIO"), "2026-10-20")).toEqual({ fase: "fuera-de-plazo", limite: "2026-10-17", diasRestantes: -3 });
  });

  it("solo lo que sigue por enviar: FIRMADO o con el envío fallando", () => {
    for (const estado of ["ENVIADO", "ACEPTADO", "ACEPTADO_CON_OBS", "RECHAZADO", "ANULADO", "FUERA_DE_PLAZO", "DESCARTADO", "RECIBIDO", "INVALIDO"])
      expect(avisoDePlazo(boleta(estado), "2026-10-16"), estado).toBeNull();
  });

  it("una nota sobre boleta sigue a la boleta; la NC 10 y la ND 03 no van en el resumen", () => {
    const nota = (tipo: string, motivo: string) => ({ tipo, estado_documento: "FIRMADO", fecha_emision: "2026-10-10", nota: { tipo_afectado: "03", motivo } });

    expect(avisoDePlazo(nota("07", "07"), "2026-10-16")?.fase).toBe("solo-resumen");
    expect(avisoDePlazo(nota("08", "01"), "2026-10-16")?.fase).toBe("solo-resumen");
    expect(avisoDePlazo(nota("07", "10"), "2026-10-15")).toBeNull();
    expect(avisoDePlazo(nota("08", "03"), "2026-10-15")).toBeNull();
  });

  it("una factura, o una nota sobre factura, nunca", () => {
    expect(avisoDePlazo({ tipo: "01", estado_documento: "FIRMADO", fecha_emision: "2026-10-01", nota: null }, "2026-10-20")).toBeNull();
    expect(avisoDePlazo({ tipo: "07", estado_documento: "FIRMADO", fecha_emision: "2026-10-01", nota: { tipo_afectado: "01", motivo: "01" } }, "2026-10-20")).toBeNull();
  });
});

describe("fechaMinimaDeNota (#314)", () => {
  const hoy = "2026-10-18";
  it("la nota de una factura hasta el 3.er día; la de una boleta hasta el 7.º en el resumen diario", () => {
    expect(fechaMinimaDeNota({ tipo: "07", sobreBoleta: false, motivo: "01" }, hoy)).toBe("2026-10-15");
    expect(fechaMinimaDeNota({ tipo: "07", sobreBoleta: true, motivo: "01" }, hoy)).toBe("2026-10-11");
    expect(fechaMinimaDeNota({ tipo: "08", sobreBoleta: true, motivo: "01" }, hoy)).toBe("2026-10-11");
  });
  it("la NC 10 y la ND 03 sobre boleta no van en el resumen: hasta el 5.º día", () => {
    expect(fechaMinimaDeNota({ tipo: "07", sobreBoleta: true, motivo: "10" }, hoy)).toBe("2026-10-13");
    expect(fechaMinimaDeNota({ tipo: "08", sobreBoleta: true, motivo: "03" }, hoy)).toBe("2026-10-13");
    // El mismo motivo en una nota de factura no cambia: el 3.º.
    expect(fechaMinimaDeNota({ tipo: "07", sobreBoleta: false, motivo: "10" }, hoy)).toBe("2026-10-15");
  });
});
