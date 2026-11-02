import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fijarLocaleDePrueba } from "@/i18n/prueba";
import { NuevoComprobanteForm } from "./nuevo-comprobante-form";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

const SERIES = [{ tipo: "01", serie: "F001", ultimo_numero: 2, activa: true, establecimiento: "0000" }];

afterEach(cleanup);

describe("NuevoComprobanteForm en inglés (#25)", () => {
  it("muestra los textos del formulario y los catálogos en inglés", () => {
    fijarLocaleDePrueba("en");
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    expect(screen.getByRole("button", { name: "Add item" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Issue invoice" })).toBeInTheDocument();
    expect(screen.getByText("Total to pay")).toBeInTheDocument();
    expect(screen.getByLabelText("Operation type")).toHaveDisplayValue("0101 · Domestic sale");
    expect(screen.getByRole("option", { name: "$ US dollars (USD)" })).toBeInTheDocument();
  });
});
