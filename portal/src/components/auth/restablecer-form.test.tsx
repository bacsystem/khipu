import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RestablecerForm } from "./restablecer-form";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

function stubFetch(status: number, cuerpo: object) {
  const fn = vi.fn(async () => new Response(JSON.stringify({ datos: null, mensaje: null, codigo: null, errores: null, ...cuerpo }), { status }));
  vi.stubGlobal("fetch", fn);
  return fn;
}

function elegirPassword(valor: string) {
  fireEvent.change(screen.getByLabelText(/contraseña/i), { target: { value: valor } });
  fireEvent.click(screen.getByRole("button"));
}

/**
 * La invitación del alta asistida (#188) reutiliza el enlace de restablecer con `?invitacion=1`: el cliente no «restablece» nada, crea
 * la contraseña con la que va a entrar por primera vez.
 */
describe("RestablecerForm", () => {
  it("sin invitación conserva los textos de restablecer", () => {
    render(<RestablecerForm token="tok" />);

    expect(screen.getByLabelText("Nueva contraseña")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Restablecer contraseña" })).toBeTruthy();
  });

  it("con invitación habla de crear la contraseña, no de restablecerla", () => {
    render(<RestablecerForm token="tok" invitacion />);

    expect(screen.getByLabelText("Tu contraseña")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Crear contraseña" })).toBeTruthy();
    expect(screen.queryByText(/Restablecer/)).toBeNull();
  });

  it("manda el token y la contraseña al mismo endpoint, con o sin invitación", async () => {
    const fetch = stubFetch(200, { estado: "exito" });
    render(<RestablecerForm token="tok-123" invitacion />);

    elegirPassword("Segura123");

    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/auth/restablecer");
    expect(JSON.parse(init.body as string)).toEqual({ token: "tok-123", password: "Segura123" });
  });

  it("al terminar una invitación dice que la contraseña quedó lista y lleva a iniciar sesión", async () => {
    stubFetch(200, { estado: "exito" });
    render(<RestablecerForm token="tok" invitacion />);

    elegirPassword("Segura123");

    await waitFor(() => expect(screen.getByText(/Tu contraseña quedó lista/)).toBeTruthy());
    expect(screen.getByText("Ir a iniciar sesión").closest("a")?.getAttribute("href")).toBe("/login");
  });

  it("una invitación vencida o ya usada dice cómo pedir otro enlace, no «solicita uno nuevo» a secas", async () => {
    stubFetch(422, { estado: "error", codigo: "TOKEN_INVALIDO", mensaje: "Enlace de recuperación inválido o vencido" });
    render(<RestablecerForm token="tok" invitacion />);

    elegirPassword("Segura123");

    await waitFor(() => expect(screen.getByText(/invitación es inválido o venció/)).toBeTruthy());
    expect(screen.getByText(/¿Olvidaste tu contraseña\?/)).toBeTruthy();
  });

  it("un enlace de restablecer vencido conserva su mensaje de siempre", async () => {
    stubFetch(422, { estado: "error", codigo: "TOKEN_INVALIDO", mensaje: "x" });
    render(<RestablecerForm token="tok" />);

    elegirPassword("Segura123");

    await waitFor(() => expect(screen.getByText("El enlace de recuperación es inválido o venció. Solicita uno nuevo.")).toBeTruthy());
  });
});
