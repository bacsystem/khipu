import { describe, expect, it } from "vitest";
import { bajaVencida, etiquetaDeBaja } from "./baja-etiqueta";
import es from "@/messages/es.json";

describe("etiquetaDeBaja", () => {
  it("una baja aceptada anula el comprobante", () => {
    expect(etiquetaDeBaja({ estado: "ACEPTADA", condicion: "BAJA" }, "boleta", es)).toBe("Aceptada: comprobante anulado");
    // Sin condición (respuestas viejas) es una baja.
    expect(etiquetaDeBaja({ estado: "ACEPTADA" }, "boleta", es)).toBe("Aceptada: comprobante anulado");
  });

  it("un alta aceptada informa la boleta o la nota en vez de anularla", () => {
    expect(etiquetaDeBaja({ estado: "ACEPTADA", condicion: "ALTA" }, "boleta", es)).toBe("Aceptado: boleta informada a SUNAT");
    expect(etiquetaDeBaja({ estado: "ACEPTADA", condicion: "ALTA" }, "nota", es)).toBe("Aceptado: nota informada a SUNAT");
    expect(etiquetaDeBaja({ estado: "RECHAZADA", condicion: "ALTA" }, "boleta", es)).toBe("Rechazado por SUNAT: la boleta no quedó informada");
  });

  it("los estados en curso dicen lo mismo en baja y en alta", () => {
    for (const condicion of ["BAJA", "ALTA"] as const) {
      expect(etiquetaDeBaja({ estado: "ENVIADA", condicion }, "boleta", es)).toBe("Enviada: SUNAT la está procesando");
      expect(etiquetaDeBaja({ estado: "ERROR_ENVIO", condicion }, "boleta", es)).toBe("Error de envío: se reintentará");
      expect(etiquetaDeBaja({ estado: "GENERADA", condicion }, "boleta", es)).toBe("Generada, pendiente de envío");
    }
  });

  /** La rechazada con 2957 es una baja pedida pasados los 7 días; un alta con 2957 no existe. */
  it("una baja rechazada con 2957 está vencida, y una rechazada por otro código no", () => {
    expect(bajaVencida({ estado: "RECHAZADA", condicion: "BAJA", cdr: { codigo: "2957" } })).toBe(true);
    expect(etiquetaDeBaja({ estado: "RECHAZADA", condicion: "BAJA", cdr: { codigo: "2957" } }, "boleta", es)).toBe("Vencida: pasaron los 7 días para anularla");
    expect(bajaVencida({ estado: "RECHAZADA", condicion: "BAJA", cdr: { codigo: "2323" } })).toBe(false);
    expect(bajaVencida({ estado: "RECHAZADA", condicion: "ALTA", cdr: { codigo: "2957" } })).toBe(false);
    expect(bajaVencida({ estado: "ACEPTADA", condicion: "BAJA", cdr: { codigo: "2957" } })).toBe(false);
    expect(etiquetaDeBaja({ estado: "RECHAZADA", condicion: "BAJA", cdr: { codigo: "2323" } }, "boleta", es)).toBe("Rechazada por SUNAT");
  });
});
