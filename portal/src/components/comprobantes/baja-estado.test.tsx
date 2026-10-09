import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Baja } from "@/lib/api/facturas";
import { BajaEstado } from "./baja-estado";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn() }) }));

const BASE: Baja = {
  id: "b-1", identificador: "RA-20260918-1", comprobante: "F001-125", tipo_comprobante: "01", fecha_generacion: "2026-09-18", motivo: "Error en el RUC",
  estado: "ACEPTADA", ticket: "1789768174685", cdr: { codigo: "0", descripcion: "aceptada", observaciones: [] }, intentos: 1, ultimo_error: null,
};

afterEach(cleanup);

describe("BajaEstado", () => {
  it("una baja aceptada dice que el comprobante quedó anulado", () => {
    render(<BajaEstado baja={BASE} />);
    expect(screen.getByTestId("baja")).toHaveTextContent("Comunicación de baja RA-20260918-1");
    expect(screen.getByRole("status")).toHaveTextContent("Aceptada: comprobante anulado");
  });

  /** 274-H1: el resumen diario también informa una boleta que pasó el envío individual. Aceptado, la boleta quedó informada, no anulada. */
  it("un resumen de alta aceptado dice que la boleta quedó informada, no anulada", () => {
    render(<BajaEstado baja={{ ...BASE, identificador: "RC-20260918-2", comprobante: "B001-7", tipo_comprobante: "03", condicion: "ALTA", motivo: "Pasó el envío individual" }} />);
    expect(screen.getByTestId("baja")).toHaveTextContent("Resumen diario RC-20260918-2 (alta)");
    expect(screen.getByRole("status")).toHaveTextContent("Aceptado: boleta informada a SUNAT");
    expect(screen.getByTestId("baja")).not.toHaveTextContent("anulado");
  });
});
