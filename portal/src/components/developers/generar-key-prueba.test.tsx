import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { GenerarKeyPrueba } from "./generar-key-prueba";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("GenerarKeyPrueba", () => {
  it("genera una key, la muestra y avisa al padre", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ estado: "exito", datos: { api_key: "fk_test123" }, mensaje: null, codigo: null, errores: null }),
          { status: 201 },
        ),
      ),
    );
    const onGenerada = vi.fn();
    render(<GenerarKeyPrueba onGenerada={onGenerada} />);

    fireEvent.click(screen.getByRole("button", { name: /generar/i }));

    await waitFor(() => expect(screen.getByText("fk_test123")).toBeInTheDocument());
    expect(onGenerada).toHaveBeenCalledWith("fk_test123");
  });

  it("muestra el error si falla", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "x", codigo: "NO_AUTORIZADO", errores: null }), {
          status: 401,
        }),
      ),
    );
    render(<GenerarKeyPrueba onGenerada={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: /generar/i }));

    await waitFor(() => expect(screen.getByText("No autorizado.")).toBeInTheDocument());
  });
});
