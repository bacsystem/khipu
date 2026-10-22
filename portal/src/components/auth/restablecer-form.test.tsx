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

  it("mientras envía, el botón queda ocupado con el texto de espera, como los del panel (#12)", async () => {
    let responder: (r: Response) => void = () => undefined;
    vi.stubGlobal("fetch", vi.fn(() => new Promise<Response>((resolve) => (responder = resolve))));
    render(<RestablecerForm token="tok" />);

    elegirPassword("Segura123");

    const boton = await screen.findByRole("button", { name: /Restableciendo…/ });
    expect(boton.getAttribute("aria-busy")).toBe("true");
    expect((boton as HTMLButtonElement).disabled).toBe(true);
    responder(new Response(JSON.stringify({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null }), { status: 200 }));
    // El éxito también es la `Alerta` del panel (tono ok, `role="status"`).
    await waitFor(() => expect(screen.getByRole("status").textContent).toContain("Tu contraseña se actualizó"));
  });

  it("el error del envío llega en la alerta del panel, no en un texto suelto (#12)", async () => {
    stubFetch(422, { estado: "error", codigo: "TOKEN_INVALIDO", mensaje: "x" });
    render(<RestablecerForm token="tok" />);

    elegirPassword("Segura123");

    const alerta = await screen.findByRole("alert");
    expect(alerta.textContent).toContain("El enlace de recuperación es inválido o venció");
    // La `Alerta` de tono error del panel: banda con borde e icono.
    expect(alerta.className).toContain("border-destructive-border");
    expect(alerta.querySelector("svg")).not.toBeNull();
  });

  it("un enlace de restablecer vencido conserva su mensaje de siempre", async () => {
    stubFetch(422, { estado: "error", codigo: "TOKEN_INVALIDO", mensaje: "x" });
    render(<RestablecerForm token="tok" />);

    elegirPassword("Segura123");

    await waitFor(() => expect(screen.getByText("El enlace de recuperación es inválido o venció. Solicita uno nuevo.")).toBeTruthy());
  });
});
