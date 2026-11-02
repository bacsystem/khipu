import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fijarLocaleDePrueba } from "@/i18n/prueba";
import { opcionesDe } from "@/test/select-de-prueba";
import { NuevoComprobanteForm } from "./nuevo-comprobante-form";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

const SERIES = [{ tipo: "01", serie: "F001", ultimo_numero: 2, activa: true, establecimiento: "0000" }];

afterEach(cleanup);

describe("NuevoComprobanteForm en inglés (#25)", () => {
  it("muestra los textos del formulario y los catálogos en inglés", async () => {
    fijarLocaleDePrueba("en");
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    expect(screen.getByRole("button", { name: "Add item" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Issue invoice" })).toBeInTheDocument();
    expect(screen.getByText("Total to pay")).toBeInTheDocument();
    // #557: lo poco frecuente va a pedido, con sus «+» también traducidos.
    expect(screen.getByText("Add:")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Operation type" }));
    expect(screen.getByLabelText("Operation type")).toHaveTextContent("0101 · Domestic sale");
    expect(screen.getByRole("button", { name: "Remove “Operation type”" })).toBeInTheDocument();
    expect(await opcionesDe(screen.getByLabelText("Currency"))).toContain("$ US dollars (USD)");
  });
});
