import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { sinEnvioImplicito } from "./formularios";

afterEach(cleanup);

function formulario(alEnviar = vi.fn()) {
  render(
    <form onKeyDown={sinEnvioImplicito} onSubmit={(e) => { e.preventDefault(); alEnviar(); }} data-testid="f">
      <input aria-label="Nombre" required />
      <input aria-label="Opcional" />
      <textarea aria-label="Notas" />
      <button type="button">Cancelar</button>
      <button type="submit">Emitir</button>
    </form>,
  );
  return alEnviar;
}

describe("sinEnvioImplicito (#117)", () => {
  it("Enter sobre un campo no envía y, si el formulario está incompleto, muestra qué falta", () => {
    const enviar = formulario();
    const form = screen.getByTestId("f") as HTMLFormElement;
    const reportar = vi.spyOn(form, "reportValidity").mockReturnValue(false);

    const seguir = fireEvent.keyDown(screen.getByLabelText("Opcional"), { key: "Enter" });

    expect(seguir).toBe(false); // preventDefault: el navegador no envía
    expect(reportar).toHaveBeenCalledTimes(1);
    expect(enviar).not.toHaveBeenCalled();
  });

  it("con el formulario completo tampoco envía: pasa el foco al botón de emitir", () => {
    const enviar = formulario();
    fireEvent.change(screen.getByLabelText("Nombre"), { target: { value: "Ana" } });
    const form = screen.getByTestId("f") as HTMLFormElement;
    const reportar = vi.spyOn(form, "reportValidity");

    fireEvent.keyDown(screen.getByLabelText("Nombre"), { key: "Enter" });

    expect(reportar).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Emitir" })).toHaveFocus();
    expect(enviar).not.toHaveBeenCalled();
  });

  it("en un botón conserva su Enter, y en un textarea es un salto de línea", () => {
    formulario();
    expect(fireEvent.keyDown(screen.getByRole("button", { name: "Cancelar" }), { key: "Enter" })).toBe(true);
    expect(fireEvent.keyDown(screen.getByRole("button", { name: "Emitir" }), { key: "Enter" })).toBe(true);
    expect(fireEvent.keyDown(screen.getByLabelText("Notas"), { key: "Enter" })).toBe(true);
  });

  it("otras teclas no se tocan", () => {
    formulario();
    expect(fireEvent.keyDown(screen.getByLabelText("Nombre"), { key: "a" })).toBe(true);
  });
});
