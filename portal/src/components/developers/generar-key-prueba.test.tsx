import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { GenerarKeyPrueba } from "./generar-key-prueba";

afterEach(() => {
  cleanup();
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

  /** F2: la misma ruta que «Crear API key»; con el plan lleno el desarrollador ve qué plan tiene y cómo liberar un lugar, no un texto genérico. */
  it("si el plan ya no deja más API keys muestra lo que dice el backend", async () => {
    const mensaje = "Tu plan Gratis incluye 1 API key y ya tienes 1 API key activa. Revoca una que ya no uses, o pide un cambio de plan";
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(JSON.stringify({ estado: "error", datos: null, mensaje, codigo: "CUPO_DEL_PLAN", errores: null }), { status: 409 })),
    );
    const onGenerada = vi.fn();
    render(<GenerarKeyPrueba onGenerada={onGenerada} />);

    fireEvent.click(screen.getByRole("button", { name: /generar/i }));

    await waitFor(() => expect(screen.getByText(mensaje)).toBeInTheDocument());
    expect(onGenerada).not.toHaveBeenCalled();
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

  it("el error se anuncia con rol de alerta", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "x", codigo: "NO_AUTORIZADO", errores: null }), { status: 401 })));
    render(<GenerarKeyPrueba onGenerada={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: /generar/i }));

    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });
});
