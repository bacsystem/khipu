import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { DocumentosDeAgente, ImportesDeAgente } from "./agente-detalle";
import { esDeAgente, etiquetasTipo, TIPOS_FILTRABLES, type ComprobanteDeAgente } from "@/lib/api/facturas";
import { puedeRecuperarCdr } from "@/lib/comprobantes/consultas-sunat";
import es from "@/messages/es.json";

/** #40: la ficha de una retención o una percepción muestra los documentos pagados o cobrados y sus totales en soles, no las líneas de venta que no tiene. */
const agente: ComprobanteDeAgente = {
  regimen: "01",
  descripcion_regimen: "Tasa 3%",
  tasa: 3,
  importe_total: 91.74,
  redondeo: 0,
  importe_neto: 2966.26,
  documentos: [
    { tipo: "01", numero: "F001-123", fecha_emision: "2026-09-01", importe_total: 1180, moneda: "PEN", pago: { numero: 1, fecha: "2026-09-12", importe: 1180 }, tipo_cambio: null, importe_agente: 35.4, neto: 1144.6 },
    { tipo: "01", numero: "E001-45", fecha_emision: "2026-08-20", importe_total: 1000, moneda: "USD", pago: { numero: 2, fecha: "2026-09-10", importe: 500 }, tipo_cambio: { factor: 3.756, fecha: "2026-09-10" }, importe_agente: 56.34, neto: 1821.66 },
  ],
};

describe("detalle de una retención o una percepción", () => {
  it("lista cada documento con su pago, lo retenido y el neto", () => {
    render(<DocumentosDeAgente agente={agente} retencion />);
    expect(screen.getByText("Documentos pagados al proveedor")).toBeTruthy();
    expect(screen.getByText(/Tasa 3%/)).toBeTruthy();
    expect(screen.getByText("F001-123")).toBeTruthy();
    expect(screen.getByText("E001-45")).toBeTruthy();
    expect(screen.getByText("Retenido")).toBeTruthy();
    expect(screen.getAllByRole("row")).toHaveLength(3);
  });

  it("en la percepción, lo cobrado y lo percibido", () => {
    render(<DocumentosDeAgente agente={agente} retencion={false} />);
    expect(screen.getByText("Documentos cobrados al cliente")).toBeTruthy();
    expect(screen.getByText("Percibido")).toBeTruthy();
  });

  it("los totales son lo retenido y lo pagado", () => {
    render(<ImportesDeAgente agente={agente} retencion />);
    expect(screen.getByText("Importe total retenido")).toBeTruthy();
    expect(screen.getByText("Importe total pagado al proveedor")).toBeTruthy();
    expect(screen.queryByText("Redondeo")).toBeNull();
  });

  it("los tipos 20 y 40 tienen etiqueta, se filtran y no consultan getStatusCdr", () => {
    expect(etiquetasTipo(es)["20"]).toBe("Retención");
    expect(etiquetasTipo(es)["40"]).toBe("Percepción");
    expect(TIPOS_FILTRABLES).toContain("20");
    expect(TIPOS_FILTRABLES).toContain("40");
    expect(esDeAgente({ tipo: "20" })).toBe(true);
    expect(esDeAgente({ tipo: "01" })).toBe(false);
    expect(puedeRecuperarCdr({ tipo: "20", estado_documento: "ENVIADO", nota: null })).toBe(false);
    expect(puedeRecuperarCdr({ tipo: "01", estado_documento: "ENVIADO", nota: null })).toBe(true);
  });
});
