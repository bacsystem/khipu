import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CLAVE_ULTIMA_ACTIVIDAD, CLAVE_ULTIMO_KEEP_ALIVE } from "@/lib/sesion/inactividad";
import { avisarSesionTerminada, leerSesionTerminada, montarDialogo } from "@/lib/sesion/sesion-terminada";

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

/** El estado que dejó la sesión anterior: un 401 definitivo con el diálogo montado. */
function estarTerminada() {
  montarDialogo();
  avisarSesionTerminada();
}

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

  /** #654: «Volver a ingresar» manda la ruta con su consulta en `next`; el login la valida con `rutaSegura` y vuelve a ella. */
  it("tras iniciar sesión vuelve a la ruta de next, con su consulta", async () => {
    consulta.actual = "next=%2Fcomprobantes%3Festado%3DACEPTADO";
    stubFetch(200, { estado: "exito", datos: { usuario: {} } });
    render(<LoginForm />);

    enviar();

    await waitFor(() => expect(push).toHaveBeenCalledWith("/comprobantes?estado=ACEPTADO"));
  });

  it.each([["//evil.com"], ["https://evil.com"], ["/\\evil.com"]])("un next que sale del portal (%s) va al inicio", async (next) => {
    consulta.actual = `next=${encodeURIComponent(next)}`;
    stubFetch(200, { estado: "exito", datos: { usuario: {} } });
    render(<LoginForm />);

    enviar();

    await waitFor(() => expect(push).toHaveBeenCalledWith("/inicio"));
  });

  /** #654: la navegación del cliente no recarga la página; sin esto el estado de la sesión anterior abriría «Tu sesión terminó» sobre la nueva. */
  it("una sesión nueva empieza de cero: borra el estado de «la sesión terminó»", async () => {
    estarTerminada();
    stubFetch(200, { estado: "exito", datos: { usuario: {} } });
    render(<LoginForm />);

    enviar();

    await waitFor(() => expect(push).toHaveBeenCalled());
    expect(leerSesionTerminada()).toBe(false);
  });

  it("si el login falla no toca el estado de «la sesión terminó»", async () => {
    estarTerminada();
    stubFetch(401, { estado: "error", codigo: "CREDENCIALES_INVALIDAS" });
    render(<LoginForm />);

    enviar();

    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
    expect(leerSesionTerminada()).toBe(true);
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
    expect(screen.queryByText(/otra pestaña/)).toBeNull();
  });

  /** #653: las demás pestañas caen acá cuando la persona cerró la sesión a propósito en otra. */
  it("con ?motivo=otra-pestana avisa que cerró la sesión en otra pestaña, y no habla de inactividad", () => {
    consulta.actual = "motivo=otra-pestana";
    render(<LoginForm />);

    expect(screen.getByText("Cerraste sesión en otra pestaña.")).toBeInTheDocument();
    expect(screen.queryByText(/inactividad/)).toBeNull();
  });

  it("el aviso de otra pestaña también sale en inglés", () => {
    fijarLocaleDePrueba("en");
    consulta.actual = "motivo=otra-pestana";
    render(<LoginForm />);

    expect(screen.getByText("You signed out in another tab.")).toBeInTheDocument();
  });

  it("el motivo de inactividad no muestra el de otra pestaña", () => {
    consulta.actual = "motivo=inactividad";
    render(<LoginForm />);

    expect(screen.queryByText(/otra pestaña/)).toBeNull();
  });

  it("otro valor de motivo no se muestra: el login solo compara con el literal y nunca refleja la URL", () => {
    consulta.actual = "motivo=%3Cb%3Ehackeado%3C%2Fb%3E";
    const { container } = render(<LoginForm />);

    expect(container.textContent).not.toContain("hackeado");
    expect(screen.queryByText(/inactividad/)).toBeNull();
    expect(screen.queryByText(/otra pestaña/)).toBeNull();
  });

  it("un motivo que solo empieza con el literal tampoco se muestra", () => {
    consulta.actual = "motivo=otra-pestana%3Cb%3Ex%3C%2Fb%3E";
    const { container } = render(<LoginForm />);

    expect(container.textContent).not.toContain("Cerraste sesión");
    expect(container.querySelector("b")).toBeNull();
  });
});
