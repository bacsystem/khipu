import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CLAVE_ULTIMA_ACTIVIDAD, CLAVE_ULTIMO_KEEP_ALIVE } from "@/lib/sesion/inactividad";

const push = vi.hoisted(() => vi.fn());
const consulta = vi.hoisted(() => ({ actual: "" }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh: vi.fn() }), useSearchParams: () => new URLSearchParams(consulta.actual) }));

import { fijarLocaleDePrueba } from "@/i18n/prueba";
import { LoginForm } from "./login-form";

afterEach(() => {
  cleanup();
  push.mockClear();
  consulta.actual = "";
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

/** #637: el portal cierra la sesión por inactividad y lleva a `/login?motivo=inactividad`; el login lo explica, sin repetir lo que dice la URL. */
describe("LoginForm: el motivo del cierre (#637)", () => {
  it("con ?motivo=inactividad avisa en español que se cerró la sesión por inactividad", () => {
    consulta.actual = "motivo=inactividad";
    render(<LoginForm />);

    expect(screen.getByText("Cerramos tu sesión por inactividad. Vuelve a ingresar.")).toBeInTheDocument();
  });

  it("y en inglés con el locale en inglés", () => {
    fijarLocaleDePrueba("en");
    consulta.actual = "motivo=inactividad";
    render(<LoginForm />);

    expect(screen.getByText("We signed you out due to inactivity. Please sign in again.")).toBeInTheDocument();
  });

  it("sin motivo no hay aviso", () => {
    render(<LoginForm />);

    expect(screen.queryByText(/inactividad/)).toBeNull();
  });

  it("otro valor de motivo no se muestra: el login solo compara con el literal y nunca refleja la URL", () => {
    consulta.actual = "motivo=%3Cb%3Ehackeado%3C%2Fb%3E";
    const { container } = render(<LoginForm />);

    expect(container.textContent).not.toContain("hackeado");
    expect(screen.queryByText(/inactividad/)).toBeNull();
  });
});
