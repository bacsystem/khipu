import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { afterEach, describe, expect, it } from "vitest";
import { StepperNumerico } from "khipu-design-system/formularios/stepper-numerico";

afterEach(cleanup);

// base-ui deja además un `<input type="number">` oculto para la validación nativa: el que se teclea es el de texto con teclado decimal.
const campoVisible = () => document.querySelector<HTMLInputElement>("input[inputmode]")!;

function Con({ inicial = 3 }: { inicial?: number | null }) {
  const [v, setV] = useState<number | null>(inicial);
  return (
    <>
      <StepperNumerico id="cant" valor={v} onCambio={setV} min={0} paso="any" />
      <output data-testid="valor">{v === null ? "" : v}</output>
    </>
  );
}

/**
 * Estas pruebas viven en el portal aunque el componente sea del paquete `khipu-design-system`: el paquete no tiene suite propia, y aquí fijan lo que el portal
 * decidió en #117 y no debe cambiar al subir la versión. La decisión: los botones ± quedan FUERA del orden de Tab (`tabindex="-1"`) pero con nombre accesible,
 * porque el campo ya se cambia con las flechas del teclado y dos paradas de Tab más por cada línea de un comprobante hacían la emisión lenta con teclado.
 */
describe("StepperNumerico — accesibilidad por teclado (#117)", () => {
  it("el campo se cambia con las flechas sin pasar por los botones", () => {
    render(<Con />);
    const campo = campoVisible();
    campo.focus();
    fireEvent.keyDown(campo, { key: "ArrowUp" });
    expect(screen.getByTestId("valor")).toHaveTextContent("4");
    fireEvent.keyDown(campo, { key: "ArrowDown" });
    fireEvent.keyDown(campo, { key: "ArrowDown" });
    expect(screen.getByTestId("valor")).toHaveTextContent("2");
  });

  it("también se teclea, y no baja del mínimo", () => {
    render(<Con inicial={1} />);
    const campo = campoVisible();
    fireEvent.change(campo, { target: { value: "7.5" } });
    fireEvent.blur(campo);
    expect(screen.getByTestId("valor")).toHaveTextContent("7.5");
    fireEvent.keyDown(campo, { key: "ArrowDown" });
    fireEvent.keyDown(campo, { key: "ArrowDown" });
    fireEvent.keyDown(campo, { key: "ArrowDown" });
    fireEvent.keyDown(campo, { key: "ArrowDown" });
    fireEvent.keyDown(campo, { key: "ArrowDown" });
    fireEvent.keyDown(campo, { key: "ArrowDown" });
    fireEvent.keyDown(campo, { key: "ArrowDown" });
    fireEvent.keyDown(campo, { key: "ArrowDown" });
    expect(Number(screen.getByTestId("valor").textContent)).toBeGreaterThanOrEqual(0);
  });

  it("los botones ± tienen nombre accesible aunque no estén en el orden de Tab (decisión #117, descrita arriba)", () => {
    render(<Con />);
    for (const nombre of ["Disminuir", "Aumentar"]) {
      const boton = screen.getByRole("button", { name: nombre });
      expect(boton).toBeInTheDocument();
      expect(boton.getAttribute("tabindex")).toBe("-1");
    }
  });
});
