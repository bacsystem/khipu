import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SoloLectura } from "@/lib/solo-lectura";
import { PreferenciasEmisionForm } from "./preferencias-emision-form";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh }) }));

function stub(respuesta: () => Promise<Response>) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(() => respuesta());
  vi.stubGlobal("fetch", fetch);
  return fetch;
}
const sobre = (cuerpo: object, status = 200) => Promise.resolve(new Response(JSON.stringify(cuerpo), { status }));
const exito = (opciones_avanzadas: boolean) => sobre({ estado: "exito", datos: { opciones_avanzadas }, mensaje: null, codigo: null, errores: null });
/** El `Interruptor` del design system (base-ui) es un `role="switch"`. */
const interruptor = () => screen.getByRole("switch", { name: /Mostrar opciones avanzadas al emitir/ });
const guardar = () => screen.getByRole("button", { name: "Guardar preferencia" });

afterEach(() => {
  cleanup();
  refresh.mockClear();
  vi.unstubAllGlobals();
});

describe("PreferenciasEmisionForm", () => {
  it("muestra el estado guardado y explica qué oculta y qué queda siempre", () => {
    render(<PreferenciasEmisionForm inicial={{ opciones_avanzadas: false }} />);
    expect(interruptor()).not.toBeChecked();
    expect(screen.getByText(/anticipos, la percepción, la retención/)).toBeInTheDocument();
    expect(screen.getByText(/El crédito, la detracción y el correo del cliente están siempre/)).toBeInTheDocument();
    // Nada cambió: no hay nada que guardar.
    expect(guardar()).toBeDisabled();
  });

  it("encendida de antes, se ve encendida", () => {
    render(<PreferenciasEmisionForm inicial={{ opciones_avanzadas: true }} />);
    expect(interruptor()).toBeChecked();
  });

  it("la enciende con PUT, lo confirma y refresca", async () => {
    const fetch = stub(() => exito(true));
    render(<PreferenciasEmisionForm inicial={{ opciones_avanzadas: false }} />);

    fireEvent.click(interruptor());
    expect(interruptor()).toBeChecked();
    fireEvent.click(guardar());

    expect(await screen.findByText("Opciones avanzadas activadas.")).toBeInTheDocument();
    expect(fetch.mock.calls[0][0]).toBe("/api/proxy/empresa/preferencias-emision");
    expect(fetch.mock.calls[0][1]?.method).toBe("PUT");
    expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({ opciones_avanzadas: true });
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(guardar()).toBeDisabled();
  });

  it("la apaga con PUT", async () => {
    const fetch = stub(() => exito(false));
    render(<PreferenciasEmisionForm inicial={{ opciones_avanzadas: true }} />);

    fireEvent.click(interruptor());
    fireEvent.click(guardar());

    expect(await screen.findByText("Opciones avanzadas desactivadas.")).toBeInTheDocument();
    expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({ opciones_avanzadas: false });
  });

  it("un rechazo del backend se muestra, no refresca y deja el cambio sin guardar", async () => {
    stub(() => sobre({ estado: "error", datos: null, mensaje: "Solo un administrador", codigo: "ROL_INSUFICIENTE", errores: null }, 403));
    render(<PreferenciasEmisionForm inicial={{ opciones_avanzadas: false }} />);

    fireEvent.click(interruptor());
    fireEvent.click(guardar());

    await waitFor(() => expect(screen.getByText("Solo un administrador")).toBeInTheDocument());
    expect(refresh).not.toHaveBeenCalled();
    expect(guardar()).toBeEnabled();
  });

  it.each(["EMISOR", "LECTURA"])("para %s se ve pero no se puede cambiar ni guardar", (rol) => {
    const fetch = stub(() => exito(true));
    render(
      <SoloLectura activa={false} rol={rol}>
        <PreferenciasEmisionForm inicial={{ opciones_avanzadas: false }} />
      </SoloLectura>,
    );
    expect(interruptor()).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByText("Solo un administrador puede cambiarlo.")).toBeInTheDocument();

    fireEvent.click(interruptor());
    expect(interruptor()).not.toBeChecked();
    expect(guardar()).toBeDisabled();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("un ADMIN la puede cambiar", () => {
    render(
      <SoloLectura activa={false} rol="ADMIN">
        <PreferenciasEmisionForm inicial={{ opciones_avanzadas: false }} />
      </SoloLectura>,
    );
    expect(interruptor()).not.toHaveAttribute("aria-disabled", "true");
    expect(screen.queryByText("Solo un administrador puede cambiarlo.")).not.toBeInTheDocument();
  });
});
