import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CLAVE_ULTIMA_ACTIVIDAD, CLAVE_ULTIMO_KEEP_ALIVE } from "@/lib/sesion/inactividad";

const push = vi.hoisted(() => vi.fn());
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh: vi.fn() }), useSearchParams: () => new URLSearchParams() }));

import { LoginForm } from "./login-form";

afterEach(() => {
  cleanup();
  push.mockClear();
  window.localStorage.clear();
  vi.unstubAllGlobals();
});

function stubFetch(status: number, cuerpo: object) {
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ datos: null, mensaje: null, codigo: null, errores: null, ...cuerpo }), { status })));
}

function enviar() {
  fireEvent.change(screen.getByLabelText("Correo electrónico"), { target: { value: "ana@negocio.pe" } });
  fireEvent.change(screen.getByLabelText("Contraseña"), { target: { value: "Segura123" } });
  fireEvent.click(screen.getByRole("button", { name: "Iniciar sesión" }));
}

describe("LoginForm: la inactividad de la sesión anterior (#637)", () => {
  it("al iniciar sesión se reinician las marcas: la actividad de ayer no cierra la sesión nueva", async () => {
    window.localStorage.setItem(CLAVE_ULTIMA_ACTIVIDAD, "1");
    window.localStorage.setItem(CLAVE_ULTIMO_KEEP_ALIVE, "1");
    stubFetch(200, { estado: "exito", datos: { usuario: {} } });
    render(<LoginForm />);

    enviar();

    await waitFor(() => expect(push).toHaveBeenCalled());
    expect(window.localStorage.getItem(CLAVE_ULTIMA_ACTIVIDAD)).toBeNull();
    expect(window.localStorage.getItem(CLAVE_ULTIMO_KEEP_ALIVE)).toBeNull();
  });

  it("si el login falla no toca las marcas", async () => {
    window.localStorage.setItem(CLAVE_ULTIMA_ACTIVIDAD, "1");
    stubFetch(401, { estado: "error", codigo: "CREDENCIALES_INVALIDAS" });
    render(<LoginForm />);

    enviar();

    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
    expect(window.localStorage.getItem(CLAVE_ULTIMA_ACTIVIDAD)).toBe("1");
    expect(push).not.toHaveBeenCalled();
  });
});
