import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CambiarContrasenaDialog } from "./perfil-usuario";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

function stubFetch(status: number, cuerpo: object) {
  const fn = vi.fn(async () => new Response(JSON.stringify({ datos: null, mensaje: null, codigo: null, errores: null, ...cuerpo }), { status }));
  vi.stubGlobal("fetch", fn);
  return fn;
}

/** El diálogo «Cambiar contraseña» del menú de usuario (#105): pide el enlace con el correo de la sesión y dice qué pasó, también cuando falla. */
describe("CambiarContrasenaDialog", () => {
  it("con el 202 del BFF confirma a qué correo se mandó el enlace", async () => {
    const fetch = stubFetch(202, { estado: "exito" });
    render(<CambiarContrasenaDialog email="ana@empresa.pe" abierto onOpenChange={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Enviar enlace" }));

    // Mientras se envía, el spinner del botón también es un `status`: se busca el que lleva la confirmación.
    const confirmacion = await screen.findByText(/Enlace enviado a/);
    expect(confirmacion.closest("[role=status]")?.textContent).toContain("Enlace enviado a ana@empresa.pe");
    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/auth/recuperar");
    expect(JSON.parse(init.body as string)).toEqual({ email: "ana@empresa.pe" });
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("si el pedido falla lo anuncia como alerta y deja reintentar, en vez de quedarse mudo", async () => {
    stubFetch(502, { estado: "error", codigo: "ERROR_INTERNO", mensaje: "x" });
    render(<CambiarContrasenaDialog email="ana@empresa.pe" abierto onOpenChange={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Enviar enlace" }));

    expect((await screen.findByRole("alert")).textContent).not.toBe("");
    expect(screen.queryByText(/Enlace enviado a/)).toBeNull();
    expect((screen.getByRole("button", { name: "Enviar enlace" }) as HTMLButtonElement).disabled).toBe(false);
  });
});
