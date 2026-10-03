import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiKeyRevelada } from "./api-key-revelada";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

function stubPortapapeles(writeText: (t: string) => Promise<void>) {
  vi.stubGlobal("navigator", { clipboard: { writeText } });
}

/** La API key se muestra una sola vez (el backend guarda un hash): el aviso y el copiado son lo que evita perderla. */
describe("ApiKeyRevelada", () => {
  it("muestra la llave completa, seleccionable", () => {
    render(<ApiKeyRevelada apiKey="fk_abc123" />);

    expect(screen.getByTestId("api-key-nueva").textContent).toBe("fk_abc123");
    expect(screen.getByText("Tu API key")).toBeTruthy();
  });

  it("avisa que no volverá a mostrarse y que solo se conserva un hash", () => {
    render(<ApiKeyRevelada apiKey="fk_abc123" />);

    expect(screen.getByText(/no volverá a mostrarse/)).toBeTruthy();
    expect(screen.getByText(/Solo se conserva un hash/)).toBeTruthy();
  });

  it("admite otra etiqueta y otro aviso (el administrador la entrega a un cliente, no la guarda para sí)", () => {
    render(<ApiKeyRevelada apiKey="fk_abc123" etiqueta="API key inicial" aviso={<p>Entrégala al cliente ahora.</p>} />);

    expect(screen.getByText("API key inicial")).toBeTruthy();
    expect(screen.getByText("Entrégala al cliente ahora.")).toBeTruthy();
    expect(screen.queryByText(/no volverá a mostrarse/)).toBeNull();
  });

  it("«Copiar» manda la llave al portapapeles y pasa a «Copiada»", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubPortapapeles(writeText);
    render(<ApiKeyRevelada apiKey="fk_abc123" />);

    fireEvent.click(screen.getByRole("button", { name: "Copiar" }));

    await waitFor(() => expect(screen.getByRole("button", { name: "Copiada" })).toBeTruthy());
    expect(writeText).toHaveBeenCalledWith("fk_abc123");
  });

  it("si el navegador niega el portapapeles, la llave sigue a la vista para copiarla a mano", async () => {
    const writeText = vi.fn().mockRejectedValue(new Error("denegado"));
    stubPortapapeles(writeText);
    render(<ApiKeyRevelada apiKey="fk_abc123" />);

    fireEvent.click(screen.getByRole("button", { name: "Copiar" }));

    await waitFor(() => expect(writeText).toHaveBeenCalled());
    expect(screen.getByRole("button", { name: "Copiar" })).toBeTruthy();
    expect(screen.getByTestId("api-key-nueva").textContent).toBe("fk_abc123");
  });
});
