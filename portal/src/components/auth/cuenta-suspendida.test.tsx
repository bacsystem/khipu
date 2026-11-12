import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CLAVE_ULTIMA_ACTIVIDAD, CLAVE_ULTIMO_KEEP_ALIVE } from "@/lib/sesion/inactividad";
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

  it("cerrar sesión borra las marcas de inactividad: la sesión siguiente empieza de cero (#637)", async () => {
    salida();
    window.localStorage.setItem(CLAVE_ULTIMA_ACTIVIDAD, "1");
    window.localStorage.setItem(CLAVE_ULTIMO_KEEP_ALIVE, "1");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 200 })));
    render(<CuentaSuspendida />);

    fireEvent.click(screen.getByRole("button", { name: /Cerrar sesión/ }));

    await waitFor(() => expect(asignar).toHaveBeenCalledWith("/login"));
    expect(window.localStorage.getItem(CLAVE_ULTIMA_ACTIVIDAD)).toBeNull();
    expect(window.localStorage.getItem(CLAVE_ULTIMO_KEEP_ALIVE)).toBeNull();
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
