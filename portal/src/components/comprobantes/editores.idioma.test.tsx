import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { fijarLocaleDePrueba } from "@/i18n/prueba";
import { CREDITO_VACIO } from "@/lib/comprobantes/credito";
import { PERCEPCION_VACIA } from "@/lib/comprobantes/percepcion";
import { CreditoEditor } from "./credito-editor";
import { EstadoConsultaRuc } from "./estado-consulta-ruc";
import { PercepcionEditor } from "./percepcion-editor";

afterEach(cleanup);

const credito = { montoPendiente: "", cuotas: [{ monto: "50.00", vencimiento: "2026-11-20" }, { monto: "50.00", vencimiento: "2026-12-20" }] };

/** #25: los editores de «Nuevo comprobante» hablan el idioma de la sesión; en castellano dicen lo mismo que antes. */
describe("editores del comprobante según el idioma", () => {
  it("en castellano, el crédito resume las cuotas como siempre", () => {
    render(<CreditoEditor credito={credito} onCambio={() => {}} neto={100} moneda="PEN" emision="2026-10-21" error={null} />);
    expect(screen.getByTestId("credito-resumen")).toHaveTextContent("Las 2 cuotas suman S/ 100.00 de S/ 100.00 pendientes.");
    expect(screen.getByRole("button", { name: "Agregar cuota" })).toBeInTheDocument();
  });

  it("en inglés, el crédito, la percepción y la consulta de RUC se leen en inglés", () => {
    fijarLocaleDePrueba("en");
    render(
      <>
        <CreditoEditor credito={{ ...CREDITO_VACIO, cuotas: credito.cuotas }} onCambio={() => {}} neto={100} moneda="PEN" emision="2026-10-21" error={null} />
        <PercepcionEditor valor={{ ...PERCEPCION_VACIA, activa: true }} onCambio={() => {}} moneda="USD" alCredito={false} montoCalculado={null} />
        <EstadoConsultaRuc consultando={false} resultado={{ tipo: "sin-padron" }} nombreActual="" onReintentar={() => {}} onUsarNombre={() => {}} />
      </>,
    );
    expect(screen.getByRole("button", { name: "Add installment" })).toBeInTheDocument();
    expect(screen.getByTestId("credito-resumen")).toHaveTextContent(/^The 2 installments add up to/);
    expect(screen.getByLabelText("Perception regime")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: /^51 · Perception on domestic sale/ })).toBeInTheDocument();
    expect(screen.getByTestId("percepcion-resumen")).toHaveTextContent("Perception is only reported on documents in soles (2788).");
    expect(screen.getByTestId("consulta-ruc")).toHaveTextContent("RUC lookup is not available right now: type the details in manually.");
  });
});
