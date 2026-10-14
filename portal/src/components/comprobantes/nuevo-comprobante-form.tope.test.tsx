import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { NuevoComprobanteForm } from "./nuevo-comprobante-form";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));
// El tope real (999) exigiría 998 clics sobre un formulario que se hace más pesado con cada fila: se baja a 3 para probar el freno, y el valor real lo fija limites.test.ts.
vi.mock("@/lib/comprobantes/limites", () => ({ MAX_LINEAS: 3 }));

const SERIES = [{ tipo: "01", serie: "F001", ultimo_numero: 2, activa: true, establecimiento: "0000" }];

afterEach(cleanup);

describe("NuevoComprobanteForm — tope de líneas (#354)", () => {
  /** El número de orden del ítem es de hasta 3 dígitos (2023): al llegar al tope el botón se deshabilita y dice por qué, en vez de dejar emitir una línea de más que el backend rechaza. */
  it("no deja agregar más ítems que el tope", () => {
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    const agregar = screen.getByRole("button", { name: "Agregar ítem" });
    const titulo = () => screen.getByRole("heading", { name: /Ítems/ });
    expect(titulo()).toHaveTextContent("1");
    fireEvent.click(agregar);
    expect(titulo()).toHaveTextContent("2");
    expect(agregar).toBeEnabled();
    fireEvent.click(agregar);
    expect(titulo()).toHaveTextContent("3");
    expect(agregar).toBeDisabled();
    expect(agregar).toHaveAttribute("title", "Un comprobante admite hasta 3 ítems");
    fireEvent.click(agregar);
    expect(titulo()).toHaveTextContent("3");
  });
});
