import { cleanup, render, screen } from "@testing-library/react";
import { SelectCampo } from "khipu-design-system/formularios/select-campo";
import { useState } from "react";
import { afterEach, describe, expect, it } from "vitest";
import { elegirOpcion, opcionesDe } from "./select-de-prueba";

function Prueba({ inicial = "" }: { inicial?: string }) {
  const [valor, setValor] = useState(inicial);
  return (
    <form>
      <label htmlFor="p-moneda">Moneda</label>
      <SelectCampo
        id="p-moneda"
        nombre="moneda"
        valor={valor}
        onCambio={setValor}
        placeholder="Elige una"
        opciones={[
          { value: "PEN", label: "Soles", grupo: "Locales" },
          { value: "USD", label: "Dólares", grupo: "Extranjeras" },
          { value: "EUR", label: "Euros", grupo: "Extranjeras" },
        ]}
      />
      <output data-testid="valor">{valor}</output>
    </form>
  );
}

describe("elegirOpcion (helper de los combos del DS)", () => {
  afterEach(cleanup);

  it("elige por el texto de la opción y el combo se nombra por su <label>", async () => {
    render(<Prueba />);
    const combo = screen.getByRole("combobox", { name: "Moneda" });
    expect(combo).toHaveTextContent("Elige una");
    await elegirOpcion(combo, "Dólares");
    expect(screen.getByTestId("valor")).toHaveTextContent("USD");
    expect(combo).toHaveTextContent("Dólares");
    expect(screen.getByLabelText("Moneda")).toBe(combo);
  });

  it("lista las opciones en orden, con sus grupos", async () => {
    render(<Prueba inicial="PEN" />);
    expect(await opcionesDe(screen.getByRole("combobox", { name: "Moneda" }))).toEqual(["Soles", "Dólares", "Euros"]);
    expect(screen.queryByRole("listbox")).toBeNull();
  });

  it("lleva el valor en el input oculto para un <form> nativo", async () => {
    const { container } = render(<Prueba inicial="EUR" />);
    expect(container.querySelector<HTMLInputElement>('input[name="moneda"]')?.value).toBe("EUR");
  });
});
