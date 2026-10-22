import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { EnvioAlClienteForm } from "./envio-al-cliente-form";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh }) }));

function stub(respuesta: () => Promise<Response>) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(() => respuesta());
  vi.stubGlobal("fetch", fetch);
  return fetch;
}
const sobre = (cuerpo: object, status = 200) => Promise.resolve(new Response(JSON.stringify(cuerpo), { status }));
const casilla = () => screen.getByRole("checkbox", { name: /Enviar automáticamente el comprobante al cliente/ });
const guardar = () => screen.getByRole("button", { name: "Guardar preferencia" });

afterEach(() => {
  cleanup();
  refresh.mockClear();
  vi.unstubAllGlobals();
});

describe("EnvioAlClienteForm (#325)", () => {
  it("activa el envío automático con PUT y lo confirma", async () => {
    const fetch = stub(() => sobre({ estado: "exito", datos: { enviar_automaticamente: true, correo_disponible: true }, mensaje: null, codigo: null, errores: null }));
    render(<EnvioAlClienteForm inicial={{ enviar_automaticamente: false, correo_disponible: true }} />);
    expect(casilla()).not.toBeChecked();
    expect(guardar()).toBeDisabled();

    fireEvent.click(casilla());
    fireEvent.click(guardar());

    expect(await screen.findByText("Envío automático activado.")).toBeInTheDocument();
    expect(fetch.mock.calls[0][0]).toBe("/api/proxy/empresa/envio-al-cliente");
    expect(fetch.mock.calls[0][1]?.method).toBe("PUT");
    expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({ enviar_automaticamente: true });
    expect(refresh).toHaveBeenCalledTimes(1);
    // Ya guardado: no queda nada que guardar.
    expect(guardar()).toBeDisabled();
  });

  it("sin correo en el servidor lo dice y no deja activarlo", () => {
    render(<EnvioAlClienteForm inicial={{ enviar_automaticamente: false, correo_disponible: false }} />);
    expect(screen.getByText("El servidor no tiene correo configurado")).toBeInTheDocument();
    expect(casilla()).toBeDisabled();
  });

  it("sin correo en el servidor, una preferencia activada sí se puede desactivar", async () => {
    const fetch = stub(() => sobre({ estado: "exito", datos: { enviar_automaticamente: false, correo_disponible: false }, mensaje: null, codigo: null, errores: null }));
    render(<EnvioAlClienteForm inicial={{ enviar_automaticamente: true, correo_disponible: false }} />);
    expect(casilla()).toBeEnabled();

    fireEvent.click(casilla());
    fireEvent.click(guardar());

    expect(await screen.findByText("Envío automático desactivado.")).toBeInTheDocument();
    expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({ enviar_automaticamente: false });
  });

  it("un rechazo del backend (503 CORREO_NO_CONFIGURADO) se muestra y no refresca", async () => {
    stub(() => sobre({ estado: "error", datos: null, mensaje: "El envío de correos no está habilitado en el servidor", codigo: "CORREO_NO_CONFIGURADO", errores: null }, 503));
    render(<EnvioAlClienteForm inicial={{ enviar_automaticamente: false, correo_disponible: true }} />);

    fireEvent.click(casilla());
    fireEvent.click(guardar());

    await waitFor(() => expect(screen.getByText("El envío de correos no está habilitado en el servidor")).toBeInTheDocument());
    expect(refresh).not.toHaveBeenCalled();
  });
});
