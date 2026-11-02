import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { useForm } from "react-hook-form";
import { afterEach, describe, expect, it } from "vitest";
import { FormField } from "./form-field";

afterEach(cleanup);

function Campo({ error, hint, filtrar }: { error?: string; hint?: string; filtrar?: (v: string) => string }) {
  const { register } = useForm<{ celular: string }>();
  return <FormField id="celular" label="Celular" register={register("celular")} error={error} hint={hint} filtrar={filtrar} />;
}

/**
 * Los formularios de acceso (login, registro, recuperar, restablecer) usan este campo con las mismas piezas que el panel (`Campo` y `Entrada`): lo que
 * se prueba es lo que el panel promete (etiqueta atada al control, error anunciado y ligado al input).
 */
describe("FormField", () => {
  it("ata la etiqueta al control", () => {
    render(<Campo />);

    expect(screen.getByLabelText("Celular").getAttribute("id")).toBe("celular");
  });

  it("con error lo anuncia como alerta, lo liga al input y marca el campo inválido", () => {
    render(<Campo error="Ingresa tu celular" />);

    const input = screen.getByLabelText("Celular");
    const alerta = screen.getByRole("alert");
    expect(alerta.textContent).toBe("Ingresa tu celular");
    expect(input.getAttribute("aria-describedby")).toBe(alerta.getAttribute("id"));
    expect(input.getAttribute("aria-invalid")).toBe("true");
  });

  it("sin error muestra la ayuda, la liga al input y no marca el campo inválido", () => {
    render(<Campo hint="9 dígitos" />);

    const input = screen.getByLabelText("Celular");
    expect(screen.queryByRole("alert")).toBeNull();
    expect(input.getAttribute("aria-describedby")).toBe(screen.getByText("9 dígitos").getAttribute("id"));
    expect(input.getAttribute("aria-invalid")).toBeNull();
  });

  it("el error manda sobre la ayuda: nunca se muestran los dos", () => {
    render(<Campo error="Ingresa tu celular" hint="9 dígitos" />);

    expect(screen.queryByText("9 dígitos")).toBeNull();
    expect(screen.getByRole("alert").textContent).toBe("Ingresa tu celular");
  });

  it("corrige lo tecleado con `filtrar` antes de que llegue al formulario", () => {
    render(<Campo filtrar={(v) => v.replace(/\D/g, "")} />);

    const input = screen.getByLabelText("Celular") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "98a7b6" } });

    expect(input.value).toBe("9876");
  });
});
