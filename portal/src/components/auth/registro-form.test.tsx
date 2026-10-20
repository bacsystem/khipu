import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const push = vi.hoisted(() => vi.fn());
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh: vi.fn() }) }));

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
  fireEvent.change(screen.getByLabelText("Nombre de la cuenta"), { target: { value: "Mi negocio" } });
  fireEvent.change(screen.getByLabelText("Celular (Perú)"), { target: { value: "987654321" } });
  fireEvent.change(screen.getByLabelText("Correo electrónico"), { target: { value: "ana@negocio.pe" } });
  fireEvent.change(screen.getByLabelText("Contraseña"), { target: { value: "Segura123" } });
}

const casilla = () => screen.getByRole("checkbox", { name: /Acepto los términos del servicio y la política de privacidad/ });
const enviar = () => fireEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));

describe("RegistroForm: términos y privacidad (#342)", () => {
  it("la casilla empieza sin marcar y enlaza los dos textos en otra pestaña", () => {
    render(<RegistroForm />);

    expect((casilla() as HTMLInputElement).checked).toBe(false);
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
