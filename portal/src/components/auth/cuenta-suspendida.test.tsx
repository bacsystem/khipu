import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CuentaSuspendida } from "./cuenta-suspendida";

const asignar = vi.fn();

afterEach(() => {
  cleanup();
  asignar.mockClear();
  vi.unstubAllGlobals();
});

function salida() {
  vi.stubGlobal("location", { assign: asignar });
}

describe("CuentaSuspendida (#182)", () => {
  it("dice que no se borró nada y qué hacer, sin ofrecer reintentar", () => {
    render(<CuentaSuspendida />);

    const texto = screen.getByTestId("cuenta-suspendida").textContent ?? "";
    expect(texto).toContain("No se borró nada");
    expect(texto).toContain("contacta a soporte");
    expect(screen.getAllByRole("button")).toHaveLength(1);
  });

  it("cerrar sesión llama al logout y lleva al login", async () => {
    salida();
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    render(<CuentaSuspendida />);

    fireEvent.click(screen.getByRole("button", { name: /Cerrar sesión/ }));

    await waitFor(() => expect(asignar).toHaveBeenCalledWith("/login"));
    expect(fetchMock).toHaveBeenCalledWith("/api/auth/logout", { method: "POST" });
  });

  /** Un corte de red no debe dejar al usuario sin salida: el login se muestra igual. */
  it("si el logout falla por la red, igual lleva al login", async () => {
    salida();
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("red caída")));
    render(<CuentaSuspendida />);

    fireEvent.click(screen.getByRole("button", { name: /Cerrar sesión/ }));

    await waitFor(() => expect(asignar).toHaveBeenCalledWith("/login"));
  });

  it("el botón se deshabilita al pulsarlo: un doble clic no manda dos logout", async () => {
    salida();
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    render(<CuentaSuspendida />);
    const boton = screen.getByRole("button", { name: /Cerrar sesión/ });

    fireEvent.click(boton);
    fireEvent.click(boton);

    await waitFor(() => expect(asignar).toHaveBeenCalled());
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
