import { describe, expect, it } from "vitest";
import { bajaVencida, etiquetaDeBaja } from "./baja-etiqueta";

describe("etiquetaDeBaja", () => {
  it("una baja aceptada anula el comprobante", () => {
    expect(etiquetaDeBaja({ estado: "ACEPTADA", condicion: "BAJA" }, "boleta")).toBe("Aceptada: comprobante anulado");
    // Sin condición (respuestas viejas) es una baja.
    expect(etiquetaDeBaja({ estado: "ACEPTADA" }, "boleta")).toBe("Aceptada: comprobante anulado");
  });

  it("un alta aceptada informa la boleta o la nota en vez de anularla", () => {
    expect(etiquetaDeBaja({ estado: "ACEPTADA", condicion: "ALTA" }, "boleta")).toBe("Aceptado: boleta informada a SUNAT");
    expect(etiquetaDeBaja({ estado: "ACEPTADA", condicion: "ALTA" }, "nota")).toBe("Aceptado: nota informada a SUNAT");
    expect(etiquetaDeBaja({ estado: "RECHAZADA", condicion: "ALTA" }, "boleta")).toBe("Rechazado por SUNAT: la boleta no quedó informada");
  });

  it("los estados en curso dicen lo mismo en baja y en alta", () => {
    for (const condicion of ["BAJA", "ALTA"] as const) {
      expect(etiquetaDeBaja({ estado: "ENVIADA", condicion }, "boleta")).toBe("Enviada: SUNAT la está procesando");
      expect(etiquetaDeBaja({ estado: "ERROR_ENVIO", condicion }, "boleta")).toBe("Error de envío: se reintentará");
      expect(etiquetaDeBaja({ estado: "GENERADA", condicion }, "boleta")).toBe("Generada, pendiente de envío");
    }
    expect(etiquetaDeBaja({ estado: "DESCARTADA", condicion: "BAJA" }, "boleta")).toBe("Reemplazada por un resumen nuevo");
  });

  /** #338: la rechazada con 2957 es la que khipu cerró sin enviar porque la baja se pasó de plazo; un alta con 2957 no existe. */
  it("una baja rechazada con 2957 está vencida, y una rechazada por otro código no", () => {
    expect(bajaVencida({ estado: "RECHAZADA", condicion: "BAJA", cdr: { codigo: "2957" } })).toBe(true);
    expect(etiquetaDeBaja({ estado: "RECHAZADA", condicion: "BAJA", cdr: { codigo: "2957" } }, "boleta")).toBe("Vencida: pasaron los 7 días para anularla");
    expect(bajaVencida({ estado: "RECHAZADA", condicion: "BAJA", cdr: { codigo: "2323" } })).toBe(false);
    expect(bajaVencida({ estado: "RECHAZADA", condicion: "ALTA", cdr: { codigo: "2957" } })).toBe(false);
    expect(bajaVencida({ estado: "ACEPTADA", condicion: "BAJA", cdr: { codigo: "2957" } })).toBe(false);
    expect(etiquetaDeBaja({ estado: "RECHAZADA", condicion: "BAJA", cdr: { codigo: "2323" } }, "boleta")).toBe("Rechazada por SUNAT");
  });
});
