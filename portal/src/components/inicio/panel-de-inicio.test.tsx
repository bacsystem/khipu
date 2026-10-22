import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import type { ResumenDeFacturas } from "@/lib/api/facturas";
import { alertasDelPanel, serieDiaria } from "@/lib/inicio/panel";
import { AlertasDelPanel } from "./alertas-del-panel";
import { descripcionDelDia, GraficoDeEmision, resumenDelGrafico } from "./grafico-de-emision";
import { KpisDelMes } from "./kpis-del-mes";

afterEach(cleanup);

const resumen = (extra: Partial<ResumenDeFacturas> = {}): ResumenDeFacturas => ({
  emitidos: 5,
  aceptados_con_cdr: 3,
  en_camino: 1,
  atencion_requerida: { total: 1, rechazados: 1, errores_de_envio: 0, fuera_de_plazo: 0, boletas_sin_enviar: 0 },
  facturado: [{ moneda: "PEN", total: 418 }],
  ...extra,
});
const metrica = (etiqueta: string) => screen.getByText(etiqueta, { exact: true }).parentElement as HTMLElement;
const MES = { desde: "2026-10-01", hasta: "2026-10-05" };

describe("KpisDelMes (#24)", () => {
  it("dice lo facturado, lo emitido, lo aceptado, lo pendiente y lo que pide atención", () => {
    render(<KpisDelMes resumen={resumen()} anterior={null} />);

    expect(screen.getByTestId("kpi-facturado").textContent).toBe("S/ 418.00");
    expect(screen.getByTestId("kpi-emitidos").textContent).toBe("5");
    expect(screen.getByTestId("kpi-aceptados").textContent).toBe("3");
    expect(within(metrica("Aceptados con CDR")).getByText("60 % de lo emitido")).toBeTruthy();
    expect(screen.getByTestId("kpi-pendientes").textContent).toBe("1");
    expect(screen.getByTestId("kpi-atencion").textContent).toBe("1");
    expect(screen.getByTestId("kpi-atencion").className).toContain("text-destructive");
    expect(screen.getByTestId("kpi-atencion-detalle").textContent).toBe("1 rechazado");
  });

  it("compara lo facturado y lo emitido con el mismo tramo del mes anterior", () => {
    render(<KpisDelMes resumen={resumen()} anterior={resumen({ emitidos: 4, facturado: [{ moneda: "PEN", total: 200 }] })} />);

    expect(screen.getByTestId("kpi-facturado-variacion").textContent).toBe("+109 % vs. el mes pasado");
    expect(screen.getByTestId("kpi-emitidos-variacion").textContent).toBe("+25 % vs. el mes pasado");
  });

  it("sin mes anterior (no cargó o fue cero) no inventa un porcentaje", () => {
    render(<KpisDelMes resumen={resumen()} anterior={resumen({ emitidos: 0, facturado: [] })} />);

    expect(screen.getByTestId("kpi-facturado-variacion").textContent).toBe("Neto de notas de crédito");
    expect(screen.getByTestId("kpi-emitidos-variacion").textContent).toBe("Este mes");
    expect(screen.queryByText(/NaN|Infinity/)).toBeNull();
  });

  it("con varias monedas cada una va con su símbolo y no se compara una sola", () => {
    render(<KpisDelMes resumen={resumen({ facturado: [{ moneda: "PEN", total: 1234.5 }, { moneda: "USD", total: 50 }] })} anterior={resumen()} />);

    expect(screen.getAllByTestId("kpi-facturado").map((m) => m.textContent)).toEqual(["S/ 1,234.50", "$ 50.00"]);
    expect(screen.getByTestId("kpi-facturado-variacion").textContent).toBe("Neto, por moneda");
  });

  it("sin nada facturado dice cero soles", () => {
    render(<KpisDelMes resumen={resumen({ facturado: [] })} anterior={null} />);
    expect(screen.getByTestId("kpi-facturado").textContent).toBe("S/ 0.00");
  });

  it("si el resumen no cargó, los cinco quedan atenuados con un guion y lo dicen", () => {
    render(<KpisDelMes resumen={null} anterior={null} />);

    expect(screen.getByTestId("kpis-del-mes").getAttribute("data-cargado")).toBe("false");
    for (const etiqueta of ["Facturado", "Emitidos", "Aceptados con CDR", "Pendientes", "Atención requerida"]) {
      expect(metrica(etiqueta).className).toContain("opacity-60");
      expect(within(metrica(etiqueta)).getByText("—")).toBeTruthy();
    }
    expect(screen.getAllByText("No se pudo cargar el resumen del mes")).toHaveLength(5);
  });
});

describe("GraficoDeEmision (#24)", () => {
  const serie = serieDiaria({ dias: [{ fecha: "2026-10-02", emitidos: 2, facturado: [{ moneda: "PEN", total: 236 }] }, { fecha: "2026-10-04", emitidos: 4, facturado: [{ moneda: "PEN", total: 100 }, { moneda: "USD", total: 50.5 }] }] }, MES);

  it("dibuja un SVG accesible con un día por punto, los vacíos sin barra, y su descripción", () => {
    render(<GraficoDeEmision serie={serie} titulo="Emisión por día" />);

    const svg = screen.getByRole("img", { name: /Emisión por día/ });
    expect(svg.getAttribute("aria-labelledby")).toBe("grafico-emision-titulo grafico-emision-desc");
    expect(screen.getByText(resumenDelGrafico(serie))).toBeTruthy();
    const dias = screen.getAllByTestId("grafico-emision-dia");
    expect(dias.map((d) => d.getAttribute("data-emitidos"))).toEqual(["0", "2", "0", "4", "0"]);
    expect(dias.map((d) => d.querySelectorAll("rect").length)).toEqual([1, 2, 1, 2, 1]);
    expect(screen.getByTestId("grafico-emision-maximo").textContent).toBe("4");
  });

  it("la barra más alta llena el alto y las demás en proporción, con el color de la paleta de gráficos", () => {
    render(<GraficoDeEmision serie={serie} titulo="Emisión por día" />);

    const barras = screen.getAllByTestId("grafico-emision-dia").map((d) => d.querySelectorAll("rect")[1]).filter(Boolean) as SVGRectElement[];
    expect(barras.map((b) => b.getAttribute("height"))).toEqual(["50", "100"]);
    expect(barras.every((b) => b.getAttribute("class")?.includes("fill-chart-1"))).toBe(true);
  });

  it("cada día dice en su title lo emitido y lo facturado por moneda", () => {
    render(<GraficoDeEmision serie={serie} titulo="Emisión por día" />);

    const titulos = screen.getAllByTestId("grafico-emision-dia").map((d) => d.querySelector("title")?.textContent);
    expect(titulos[1]).toBe("2 Oct 2026: 2 comprobantes · S/ 236.00");
    expect(titulos[3]).toBe("4 Oct 2026: 4 comprobantes · S/ 100.00 · $ 50.50");
    expect(titulos[0]).toBe("1 Oct 2026: 0 comprobantes");
    expect(descripcionDelDia({ fecha: "2026-10-09", emitidos: 1, facturado: {} })).toBe("9 Oct 2026: 1 comprobante");
  });

  it("trae la misma información como tabla, con una columna por moneda", () => {
    render(<GraficoDeEmision serie={serie} titulo="Emisión por día" />);

    const tabla = screen.getByTestId("grafico-emision-tabla");
    expect(within(tabla).getAllByRole("columnheader").map((c) => c.textContent)).toEqual(["Día", "Emitidos", "Facturado PEN", "Facturado USD"]);
    const filas = within(tabla).getAllByRole("row").slice(1);
    expect(filas).toHaveLength(5);
    expect(within(filas[3]).getAllByRole("cell").map((c) => c.textContent)).toEqual(["4 Oct 2026", "4", "S/ 100.00", "$ 50.50"]);
    expect(within(filas[0]).getAllByRole("cell").map((c) => c.textContent)).toEqual(["1 Oct 2026", "0", "S/ 0.00", "$ 0.00"]);
  });

  it("el resumen para lectores de pantalla dice cuántos, en cuántos días y el pico", () => {
    expect(resumenDelGrafico(serie)).toBe("6 comprobantes emitidos en 2 de 5 días, del 1 Oct 2026 al 5 Oct 2026. El día de más emisión fue el 4 Oct 2026, con 4.");
  });

  it("sin emisión en el mes no dibuja barras y lo dice", () => {
    render(<GraficoDeEmision serie={serieDiaria({ dias: [] }, MES)} titulo="Emisión por día" />);

    expect(screen.getByTestId("grafico-emision-vacio").textContent).toContain("Todavía no hay comprobantes emitidos este mes");
    expect(screen.queryByRole("img")).toBeNull();
  });

  it("si no cargó lo dice y no dibuja nada", () => {
    render(<GraficoDeEmision serie={null} titulo="Emisión por día" />);

    expect(screen.getByTestId("grafico-emision").getAttribute("data-cargado")).toBe("false");
    expect(screen.getByTestId("grafico-emision-error").textContent).toContain("No se pudo cargar la emisión por día");
    expect(screen.queryByRole("img")).toBeNull();
  });
});

describe("AlertasDelPanel (#24)", () => {
  const HOY = "2026-10-20";

  it("cada aviso lleva su título, su explicación y el enlace a donde se resuelve", () => {
    const alertas = alertasDelPanel(
      { tiene_certificado: true, certificado_vigencia_hasta: "2026-10-30", tiene_credenciales_sol: true, credenciales_sol_rechazadas: null },
      { atencion_requerida: { total: 1, rechazados: 0, errores_de_envio: 0, fuera_de_plazo: 1, boletas_sin_enviar: 0 } },
      { desde: "2026-10-01", hasta: HOY },
      HOY,
    );
    render(<AlertasDelPanel alertas={alertas} incompleto={false} />);

    const avisos = screen.getAllByTestId("alerta-del-panel");
    expect(avisos.map((a) => a.getAttribute("data-alerta"))).toEqual(["fuera-de-plazo", "certificado-por-vencer"]);
    expect(within(avisos[0]).getByRole("alert")).toBeTruthy();
    expect(within(avisos[0]).getByRole("link", { name: "Ver fuera de plazo" }).getAttribute("href")).toBe("/comprobantes?estado=FUERA_DE_PLAZO&desde=2026-10-01&hasta=2026-10-20");
    expect(within(avisos[1]).getByRole("status").textContent).toContain("El certificado digital vence en 10 días");
    expect(within(avisos[1]).getByRole("link", { name: "Renovar certificado" }).getAttribute("href")).toBe("/empresa?seccion=certificado");
    expect(screen.queryByTestId("alertas-sin-avisos")).toBeNull();
  });

  it("sin avisos lo dice en una línea", () => {
    render(<AlertasDelPanel alertas={[]} incompleto={false} />);
    expect(screen.getByTestId("alertas-sin-avisos").textContent).toContain("Sin avisos");
  });

  it("con pasos pendientes en la guía tampoco afirma que todo está en orden: lo dice la guía", () => {
    render(<AlertasDelPanel alertas={[]} incompleto={false} guiaPendiente />);
    expect(screen.queryByTestId("alertas-sin-avisos")).toBeNull();
  });

  it("si algo no se pudo revisar no afirma que todo está en orden", () => {
    render(<AlertasDelPanel alertas={[]} incompleto />);
    expect(screen.queryByTestId("alertas-sin-avisos")).toBeNull();
    expect(screen.getByTestId("alertas-incompletas").textContent).toContain("No se pudo revisar todo");
  });
});
