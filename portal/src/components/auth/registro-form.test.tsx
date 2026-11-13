import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const push = vi.hoisted(() => vi.fn());
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh: vi.fn() }) }));

import { CLAVE_ULTIMA_ACTIVIDAD, CLAVE_ULTIMO_KEEP_ALIVE } from "@/lib/sesion/inactividad";
import { avisarSesionTerminada, leerSesionTerminada, montarDialogo } from "@/lib/sesion/sesion-terminada";
import { RegistroForm } from "./registro-form";

afterEach(() => {
  cleanup();
  push.mockClear();
  vi.unstubAllGlobals();
});

function stubFetch(status: number, cuerpo: object) {
  const fn = vi.fn(async () => new Response(JSON.stringify({ datos: null, mensaje: null, codigo: null, errores: null, ...cuerpo }), { status }));
  vi.stubGlobal("fetch", fn);
  return fn;
}

function llenar() {
  fireEvent.change(screen.getByLabelText("Tu nombre o el de tu negocio"), { target: { value: "Mi negocio" } });
  fireEvent.change(screen.getByLabelText("Celular (Perú)"), { target: { value: "987654321" } });
  fireEvent.change(screen.getByLabelText("Correo electrónico"), { target: { value: "ana@negocio.pe" } });
  fireEvent.change(screen.getByLabelText("Contraseña"), { target: { value: "Segura123" } });
}

const casilla = () => screen.getByRole("checkbox", { name: /Acepto los términos del servicio y la política de privacidad/ });
const enviar = () => fireEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));

describe("RegistroForm: términos y privacidad (#342)", () => {
  it("#580: el aviso de borrador sigue a `revisado`, que le pasa la página; sin él, se avisa", () => {
    render(<RegistroForm />);
    expect(screen.getByTestId("borrador-legal-casilla")).toBeTruthy();
    cleanup();

    render(<RegistroForm revisado />);
    expect(screen.queryByTestId("borrador-legal-casilla")).toBeNull();
  });

  it("la casilla empieza sin marcar y enlaza los dos textos en otra pestaña", () => {
    render(<RegistroForm />);

    expect(casilla()).not.toBeChecked();
    expect(screen.getByRole("link", { name: "términos del servicio" }).getAttribute("href")).toBe("/terminos");
    expect(screen.getByRole("link", { name: "política de privacidad" }).getAttribute("href")).toBe("/privacidad");
    expect(screen.getByRole("link", { name: "términos del servicio" }).getAttribute("target")).toBe("_blank");
  });

  it("sin marcarla no se registra: el error queda atado a la casilla", async () => {
    const fetch = stubFetch(201, { estado: "exito", datos: { usuario: {} } });
    render(<RegistroForm />);

    llenar();
    enviar();

    await waitFor(() => expect(casilla().getAttribute("aria-invalid")).toBe("true"));
    expect(screen.getByRole("alert").textContent).toContain("tienes que aceptar los términos");
    expect(casilla().getAttribute("aria-describedby")).toContain("acepta_terminos-error");
    expect(fetch).not.toHaveBeenCalled();
  });

  it("marcada, manda acepta_terminos: true con el resto y va al onboarding", async () => {
    const fetch = stubFetch(201, { estado: "exito", datos: { usuario: {} } });
    render(<RegistroForm />);

    llenar();
    fireEvent.click(casilla());
    enviar();

    await waitFor(() => expect(push).toHaveBeenCalledWith("/onboarding"));
    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/auth/registro");
    expect(JSON.parse(init.body as string)).toEqual({ nombre: "Mi negocio", telefono: "987654321", email: "ana@negocio.pe", password: "Segura123", acepta_terminos: true });
  });

  it("una cuenta nueva no hereda la inactividad de la sesión anterior (#637): al registrarse se reinician las marcas", async () => {
    window.localStorage.setItem(CLAVE_ULTIMA_ACTIVIDAD, "1");
    window.localStorage.setItem(CLAVE_ULTIMO_KEEP_ALIVE, "1");
    stubFetch(201, { estado: "exito", datos: { usuario: {} } });
    render(<RegistroForm />);

    llenar();
    fireEvent.click(casilla());
    enviar();

    await waitFor(() => expect(push).toHaveBeenCalledWith("/onboarding"));
    expect(window.localStorage.getItem(CLAVE_ULTIMA_ACTIVIDAD)).toBeNull();
    expect(window.localStorage.getItem(CLAVE_ULTIMO_KEEP_ALIVE)).toBeNull();
  });

  /** #654: la navegación del cliente no recarga la página; el estado de la sesión anterior abriría «Tu sesión terminó» sobre la cuenta recién creada. */
  it("una cuenta nueva empieza de cero: borra el estado de «la sesión terminó»", async () => {
    montarDialogo();
    avisarSesionTerminada();
    stubFetch(201, { estado: "exito", datos: { usuario: {} } });
    render(<RegistroForm />);

    llenar();
    fireEvent.click(casilla());
    enviar();

    await waitFor(() => expect(push).toHaveBeenCalledWith("/onboarding"));
    expect(leerSesionTerminada()).toBe(false);
  });

  it("si el registro falla no toca el estado de «la sesión terminó»", async () => {
    montarDialogo();
    avisarSesionTerminada();
    stubFetch(400, { estado: "error", codigo: "TERMINOS_NO_ACEPTADOS", mensaje: "x" });
    render(<RegistroForm />);

    llenar();
    fireEvent.click(casilla());
    enviar();

    await waitFor(() => expect(screen.getByRole("alert")).toBeTruthy());
    expect(leerSesionTerminada()).toBe(true);
  });

  it("si el backend igual lo rechaza, muestra su mensaje", async () => {
    stubFetch(400, { estado: "error", codigo: "TERMINOS_NO_ACEPTADOS", mensaje: "x" });
    render(<RegistroForm />);

    llenar();
    fireEvent.click(casilla());
    enviar();

    await waitFor(() => expect(screen.getByText("Para crear la cuenta tienes que aceptar los términos del servicio y la política de privacidad.")).toBeTruthy());
    expect(push).not.toHaveBeenCalled();
  });
});

/** #581: el plan que eligió en la página de precios se ve en el registro, con lo que pasa con él, y viaja con el registro. */
describe("RegistroForm: plan elegido (#581)", () => {
  it("dice qué plan eligió, a qué precio, y que la cuenta empieza gratis", () => {
    render(<RegistroForm plan={{ nombre: "Negocio", precio: 69 }} />);

    expect(screen.getByText("Elegiste el plan Negocio · S/ 69/mes, IGV incluido")).toBeTruthy();
    expect(screen.getByTestId("plan-elegido").textContent).toContain("Tu cuenta empieza en el plan gratuito y te escribimos para activar Negocio");
  });

  it("manda el plan con el registro; sin plan elegido no manda la clave", async () => {
    const fetch = stubFetch(201, { estado: "exito", datos: { usuario: {} } });
    render(<RegistroForm plan={{ nombre: "Negocio", precio: 69 }} />);
    llenar();
    fireEvent.click(casilla());
    enviar();
    await waitFor(() => expect(push).toHaveBeenCalledWith("/onboarding"));
    expect(JSON.parse((fetch.mock.calls[0] as unknown as [string, RequestInit])[1].body as string).plan).toBe("Negocio");

    cleanup();
    render(<RegistroForm />);
    expect(screen.queryByTestId("plan-elegido")).toBeNull();
  });

  it("el nombre dice qué dato va y para qué se usa", () => {
    render(<RegistroForm />);
    const nombre = screen.getByLabelText("Tu nombre o el de tu negocio");
    const ayuda = document.getElementById(nombre.getAttribute("aria-describedby")?.split(" ")[0] ?? "");
    expect(ayuda?.textContent).toContain("La razón social de tu empresa te la pedimos después");
  });
});
