import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import type { PeriodoDelResumen, ResumenDeFacturas } from "@/lib/api/facturas";
import { periodoEnPalabras, ResumenDeComprobantes } from "./resumen-de-comprobantes";
import es from "@/messages/es.json";

afterEach(cleanup);

const MES: PeriodoDelResumen = { desde: "2026-10-01", hasta: "2026-10-04", esElMesEnCurso: true };
const resumen = (extra: Partial<ResumenDeFacturas> = {}): ResumenDeFacturas => ({
  emitidos: 40,
  aceptados_con_cdr: 30,
  en_camino: 5,
  atencion_requerida: { total: 0, rechazados: 0, errores_de_envio: 0, fuera_de_plazo: 0, boletas_sin_enviar: 0 },
  facturado: [{ moneda: "PEN", total: 12345.6 }],
  ...extra,
});

const mostrar = (r: ResumenDeFacturas | null, p: PeriodoDelResumen = MES) => render(<ResumenDeComprobantes resumen={r} periodo={p} />);
// La tarjeta entera de la métrica (la etiqueta, el valor y la ayuda son hermanos dentro de ella).
const metrica = (etiqueta: string) => screen.getByText(etiqueta).closest("[data-metrica]") as HTMLElement;

describe("periodoEnPalabras", () => {
  it("dice el período como se lo ve en la franja", () => {
    expect(periodoEnPalabras(MES, es)).toBe("Este mes");
    expect(periodoEnPalabras({ desde: "2026-08-01", hasta: "2026-08-31", esElMesEnCurso: false }, es)).toBe("1 Ago – 31 Ago");
    expect(periodoEnPalabras({ desde: "2026-08-15", hasta: "2026-08-15", esElMesEnCurso: false }, es)).toBe("15 Ago");
    expect(periodoEnPalabras({ desde: "2026-08-15", esElMesEnCurso: false }, es)).toBe("Desde el 15 Ago");
    expect(periodoEnPalabras({ hasta: "2026-08-15", esElMesEnCurso: false }, es)).toBe("Hasta el 15 Ago");
    expect(periodoEnPalabras({ esElMesEnCurso: false }, es)).toBe("Todo el historial");
  });
});

describe("ResumenDeComprobantes (#15)", () => {
  it("muestra lo emitido, lo aceptado con su porcentaje y el período en cada indicador que lo necesita", () => {
    mostrar(resumen());

    expect(within(metrica("Emitidos en el período")).getByTestId("metrica-emitidos").textContent).toBe("40");
    expect(within(metrica("Emitidos en el período")).getByText("Este mes")).toBeTruthy();
    expect(within(metrica("Aceptados con CDR")).getByTestId("metrica-aceptados").textContent).toBe("30");
    expect(within(metrica("Aceptados con CDR")).getByText("75 % de lo emitido")).toBeTruthy();
  });

  it("el porcentaje de aceptados se redondea hacia abajo y sin emitidos no se divide", () => {
    mostrar(resumen({ emitidos: 3, aceptados_con_cdr: 2 }));
    expect(screen.getByText("66 % de lo emitido")).toBeTruthy();
    cleanup();

    mostrar(resumen({ emitidos: 0, aceptados_con_cdr: 0 }));
    expect(screen.getByText("Sin comprobantes")).toBeTruthy();
    expect(screen.queryByText(/NaN|Infinity/)).toBeNull();
  });

  it("el total facturado se dice por moneda, con su símbolo y sus miles", () => {
    mostrar(resumen({ facturado: [{ moneda: "PEN", total: 12345.6 }, { moneda: "USD", total: 500 }] }));

    const montos = screen.getAllByTestId("metrica-facturado");
    expect(montos.map((m) => m.textContent)).toEqual(["S/ 12,345.60", "$ 500.00"]);
    expect(montos.map((m) => m.getAttribute("data-moneda"))).toEqual(["PEN", "USD"]);
    expect(within(metrica("Total facturado")).getByText("Neto de notas de crédito · Este mes")).toBeTruthy();
  });

  it("sin nada facturado dice cero soles y no un guion", () => {
    mostrar(resumen({ facturado: [] }));

    expect(screen.getByTestId("metrica-facturado").textContent).toBe("S/ 0.00");
  });

  it("un neto negativo se ve negativo", () => {
    mostrar(resumen({ facturado: [{ moneda: "PEN", total: -150 }] }));

    expect(screen.getByTestId("metrica-facturado").textContent).toBe("S/ -150.00");
  });

  it("la atención requerida dice cuántos y de qué clases, y solo las que hay", () => {
    mostrar(resumen({ atencion_requerida: { total: 10, rechazados: 3, errores_de_envio: 2, fuera_de_plazo: 1, boletas_sin_enviar: 4 } }));

    expect(screen.getByTestId("metrica-atencion").textContent).toBe("10");
    expect(screen.getByTestId("metrica-atencion-detalle").textContent).toBe("3 rechazados · 2 con error de envío · 1 fuera de plazo · 4 boletas sin enviar");
  });

  /** #356: una boleta que quedó FIRMADA sin enviar desde el 5.º día es atención requerida, y se dice con sus palabras, en singular y plural. */
  it("las boletas sin enviar se dicen aparte, en singular y plural", () => {
    mostrar(resumen({ atencion_requerida: { total: 1, rechazados: 0, errores_de_envio: 0, fuera_de_plazo: 0, boletas_sin_enviar: 1 } }));
    expect(screen.getByTestId("metrica-atencion-detalle").textContent).toBe("1 boleta sin enviar");
    expect(screen.getByTestId("metrica-atencion").className).toContain("text-destructive");
    cleanup();
    mostrar(resumen({ atencion_requerida: { total: 2, rechazados: 0, errores_de_envio: 0, fuera_de_plazo: 0, boletas_sin_enviar: 2 } }));
    expect(screen.getByTestId("metrica-atencion-detalle").textContent).toBe("2 boletas sin enviar");
  });

  it("un rechazado se dice en singular y una clase en cero no aparece", () => {
    mostrar(resumen({ atencion_requerida: { total: 1, rechazados: 1, errores_de_envio: 0, fuera_de_plazo: 0, boletas_sin_enviar: 0 } }));

    expect(screen.getByTestId("metrica-atencion-detalle").textContent).toBe("1 rechazado");
  });

  it("sin atención requerida dice que todo está en orden y no se pinta de alerta", () => {
    mostrar(resumen());

    expect(screen.getByTestId("metrica-atencion").textContent).toBe("0");
    expect(screen.getByTestId("metrica-atencion").className).not.toContain("text-destructive");
    expect(screen.getByTestId("metrica-atencion-detalle").textContent).toBe("Todo en orden");
  });

  it("con atención requerida el número se pinta de alerta", () => {
    mostrar(resumen({ atencion_requerida: { total: 2, rechazados: 0, errores_de_envio: 2, fuera_de_plazo: 0, boletas_sin_enviar: 0 } }));

    expect(screen.getByTestId("metrica-atencion").className).toContain("text-destructive");
  });

  it("el período de otro rango se dice con sus fechas en cada indicador", () => {
    mostrar(resumen(), { desde: "2026-08-01", hasta: "2026-08-31", esElMesEnCurso: false });

    expect(within(metrica("Emitidos en el período")).getByText("1 Ago – 31 Ago")).toBeTruthy();
    expect(within(metrica("Total facturado")).getByText("Neto de notas de crédito · 1 Ago – 31 Ago")).toBeTruthy();
  });

  it("la franja marca si el resumen cargó", () => {
    mostrar(resumen());

    expect(screen.getByTestId("resumen-de-comprobantes").getAttribute("data-cargado")).toBe("true");
  });

  /** Si el resumen no cargó, las cuatro quedan atenuadas con un guion y lo dicen: no se muestra un cero que parezca un dato. */
  it("sin resumen (no cargó) las cuatro métricas quedan atenuadas con un guion y dicen por qué", () => {
    mostrar(null);

    expect(screen.getByTestId("resumen-de-comprobantes").getAttribute("data-cargado")).toBe("false");
    for (const etiqueta of ["Total facturado", "Emitidos en el período", "Aceptados con CDR", "Atención requerida"]) {
      const m = metrica(etiqueta);
      expect(m.className).toContain("opacity-60");
      expect(within(m).getByText("—")).toBeTruthy();
      expect(within(m).getByText("No se pudo cargar el resumen")).toBeTruthy();
    }
    expect(screen.queryByTestId("metrica-emitidos")).toBeNull();
  });

  it("con resumen ninguna métrica queda atenuada", () => {
    mostrar(resumen());

    for (const etiqueta of ["Total facturado", "Emitidos en el período", "Aceptados con CDR", "Atención requerida"]) expect(metrica(etiqueta).className).not.toContain("opacity-60");
  });
});
