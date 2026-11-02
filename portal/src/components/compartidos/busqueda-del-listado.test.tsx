import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BusquedaDelListado, ESPERA_DE_BUSQUEDA_MS } from "./busqueda-del-listado";

function campo(valor: string, onCambio: (v: string) => void, extra: { soloDigitos?: boolean } = {}) {
  return <BusquedaDelListado id="b" etiqueta="Cliente" ayuda="ayuda" placeholder="Cliente" valor={valor} onCambio={onCambio} ancho="w-40" maxLength={100} {...extra} />;
}

beforeEach(() => vi.useFakeTimers());
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe("BusquedaDelListado (#326)", () => {
  it("espera una pausa antes de filtrar y manda solo lo último que se tecleó", () => {
    const onCambio = vi.fn();
    render(campo("", onCambio));
    const input = screen.getByLabelText("Cliente");
    fireEvent.change(input, { target: { value: "an" } });
    act(() => void vi.advanceTimersByTime(ESPERA_DE_BUSQUEDA_MS - 1));
    fireEvent.change(input, { target: { value: "andina" } });
    act(() => void vi.advanceTimersByTime(ESPERA_DE_BUSQUEDA_MS - 1));
    expect(onCambio).not.toHaveBeenCalled();
    act(() => void vi.advanceTimersByTime(1));
    expect(onCambio).toHaveBeenCalledTimes(1);
    expect(onCambio).toHaveBeenCalledWith("andina");
  });

  it("no filtra de nuevo si lo escrito es lo que ya está en la URL", () => {
    const onCambio = vi.fn();
    render(campo("andina", onCambio));
    fireEvent.change(screen.getByLabelText("Cliente"), { target: { value: "andina2" } });
    fireEvent.change(screen.getByLabelText("Cliente"), { target: { value: "andina" } });
    act(() => void vi.advanceTimersByTime(ESPERA_DE_BUSQUEDA_MS * 2));
    expect(onCambio).not.toHaveBeenCalled();
  });

  it("sigue a la URL cuando cambia desde fuera, pero no pisa lo tecleado si solo difiere en espacios", () => {
    const onCambio = vi.fn();
    const { rerender } = render(campo("", onCambio));
    rerender(campo("pacífico", onCambio));
    expect(screen.getByLabelText("Cliente")).toHaveValue("pacífico");

    fireEvent.change(screen.getByLabelText("Cliente"), { target: { value: "pacífico " } });
    act(() => void vi.advanceTimersByTime(ESPERA_DE_BUSQUEDA_MS));
    expect(onCambio).toHaveBeenCalledWith("pacífico ");
    // La página recorta los espacios y devuelve «pacífico»: el campo conserva lo tecleado.
    rerender(campo("pacífico", onCambio));
    expect(screen.getByLabelText("Cliente")).toHaveValue("pacífico ");
  });

  it("en el campo de número solo entran dígitos", () => {
    render(campo("", vi.fn(), { soloDigitos: true }));
    fireEvent.change(screen.getByLabelText("Cliente"), { target: { value: "12a3-" } });
    expect(screen.getByLabelText("Cliente")).toHaveValue("123");
  });
});
