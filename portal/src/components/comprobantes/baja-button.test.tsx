import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { BajaButton } from "./baja-button";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn(), push: vi.fn() }) }));
vi.mock("@/lib/api/browser", () => ({ apiRequest: vi.fn() }));

afterEach(cleanup);

function abrir(boleta: boolean) {
  render(<BajaButton id="c-1" numero={boleta ? "B001-21" : "F001-21"} fechaEmision="2026-09-01" boleta={boleta} />);
  fireEvent.click(screen.getByTestId("dar-de-baja"));
  return screen.getByTestId("baja-confirmacion").textContent ?? "";
}

/**
 * #362 H8: el resumen diario (RC) con que se anula una boleta no lleva el motivo (BajaController: queda en khipu), así que el diálogo no puede decir
 * que SUNAT lo ve; y la anulación solo cabe si el comprobante no se entregó al adquirente (RS 097-2012 art. 14): si se entregó, corresponde una nota de crédito.
 */
describe("BajaButton: textos de la baja", () => {
  it("una factura dice que el motivo va en el XML y lo ve SUNAT", () => {
    const texto = abrir(false);

    expect(texto).toContain("Va en el XML y lo ve SUNAT");
    expect(texto).not.toContain("art. 14");
  });

  it("una boleta dice que el motivo queda en khipu y que SUNAT no lo ve", () => {
    const texto = abrir(true);

    expect(texto).not.toContain("Va en el XML");
    expect(texto).not.toContain("lo ve SUNAT.");
    expect(texto).toContain("Queda solo en khipu");
    expect(texto).toContain("el resumen diario no lo lleva");
  });

  it("una boleta avisa que solo se anula lo no entregado al adquirente y que si ya se entregó va una nota de crédito", () => {
    const texto = abrir(true);

    expect(texto).toContain("no se entregó al adquirente");
    expect(texto).toContain("RS 097-2012, art. 14");
    expect(texto).toContain("nota de crédito");
  });
});
