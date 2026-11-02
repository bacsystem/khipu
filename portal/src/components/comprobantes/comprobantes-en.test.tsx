import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fijarLocaleDePrueba } from "@/i18n/prueba";
import type { Baja } from "@/lib/api/facturas";
import { BajaEstado } from "./baja-estado";
import { EnvioAlClienteEstado } from "./envio-al-cliente-estado";
import { EstadoBadge } from "./estado-badge";
import { ResumenDeComprobantes } from "./resumen-de-comprobantes";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn() }) }));

afterEach(cleanup);

/** #25: la ficha y el listado de comprobantes con la sesión en inglés. */
describe("comprobantes en inglés", () => {
  it("la franja de métricas dice el período y los indicadores en inglés", () => {
    fijarLocaleDePrueba("en");
    render(
      <ResumenDeComprobantes
        periodo={{ desde: "2026-08-01", hasta: "2026-08-31", esElMesEnCurso: false }}
        resumen={{
          emitidos: 4,
          aceptados_con_cdr: 3,
          en_camino: 0,
          atencion_requerida: { total: 0, rechazados: 0, errores_de_envio: 0, fuera_de_plazo: 0, boletas_sin_enviar: 0 },
          facturado: [],
        }}
      />,
    );
    expect(screen.getByLabelText("Period summary")).toBeTruthy();
    expect(screen.getByText("Issued in the period")).toBeTruthy();
    expect(screen.getByText("Aug 1 – Aug 31")).toBeTruthy();
    expect(screen.getByText("75 % of issued")).toBeTruthy();
  });

  it("el estado del comprobante y el de su baja salen en inglés", () => {
    fijarLocaleDePrueba("en");
    const baja: Baja = {
      id: "b-1", identificador: "RA-20260918-1", comprobante: "F001-125", tipo_comprobante: "01", fecha_generacion: "2026-09-18", motivo: "Wrong RUC",
      estado: "ACEPTADA", ticket: "1789768174685", cdr: { codigo: "0", descripcion: "aceptada", observaciones: [] }, intentos: 1, ultimo_error: null,
    };
    render(
      <>
        <EstadoBadge estado="ACEPTADO_CON_OBS" />
        <BajaEstado baja={baja} />
      </>,
    );
    expect(screen.getByText("Accepted with obs.")).toBeTruthy();
    expect(screen.getByTestId("baja")).toHaveTextContent("Voiding notice RA-20260918-1");
    expect(screen.getByRole("status")).toHaveTextContent("Accepted: document voided");
  });

  it("el envío automático al cliente pendiente cuenta los intentos en inglés", () => {
    fijarLocaleDePrueba("en");
    render(<EnvioAlClienteEstado envio={{ destinatario: "a@b.pe", estado: "PENDIENTE", intentos: 2 }} />);
    expect(screen.getByText("Sending to customer pending")).toBeTruthy();
    expect(screen.getByText("2 attempts failed; it retries automatically.")).toBeTruthy();
  });
});
