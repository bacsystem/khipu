import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RecuperarCdrButton } from "./recuperar-cdr-button";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh }) }));

function stub(respuesta: () => Promise<Response>) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(() => respuesta());
  vi.stubGlobal("fetch", fetch);
  return fetch;
}
const sobre = (cuerpo: object, status = 200) => Promise.resolve(new Response(JSON.stringify(cuerpo), { status }));

afterEach(() => {
  cleanup();
  refresh.mockClear();
  vi.unstubAllGlobals();
});

describe("RecuperarCdrButton (#315)", () => {
  it("pide la constancia a SUNAT y, si el comprobante cambió de estado, refresca la ficha", async () => {
    const fetch = stub(() => sobre({ estado: "exito", datos: { estado_documento: "ACEPTADO" }, mensaje: null, codigo: null, errores: null }));
    render(<RecuperarCdrButton id="f-1" estado="ENVIADO" />);
    fireEvent.click(screen.getByRole("button", { name: "Recuperar constancia de SUNAT" }));
    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(fetch.mock.calls[0][0]).toBe("/api/proxy/facturas/f-1/cdr/recuperar");
    expect(fetch.mock.calls[0][1]?.method).toBe("POST");
  });

  it("si SUNAT todavía no la tiene (el comprobante vuelve igual) lo dice y no refresca", async () => {
    stub(() => sobre({ estado: "exito", datos: { estado_documento: "ENVIADO" }, mensaje: null, codigo: null, errores: null }));
    render(<RecuperarCdrButton id="f-1" estado="ENVIADO" />);
    fireEvent.click(screen.getByRole("button", { name: "Recuperar constancia de SUNAT" }));
    expect(await screen.findByTestId("recuperar-cdr-mensaje")).toHaveTextContent(/todavía no tiene la constancia/);
    expect(refresh).not.toHaveBeenCalled();
  });

  it("un rechazo del backend (409 CDR_YA_DISPONIBLE) se muestra tal cual", async () => {
    stub(() => sobre({ estado: "error", datos: null, mensaje: "El comprobante ya tiene su constancia de SUNAT", codigo: "CDR_YA_DISPONIBLE", errores: null }, 409));
    render(<RecuperarCdrButton id="f-1" estado="ENVIADO" />);
    fireEvent.click(screen.getByRole("button", { name: "Recuperar constancia de SUNAT" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("ya tiene su constancia");
    expect(screen.getByRole("button", { name: "Recuperar constancia de SUNAT" })).toBeEnabled();
  });

  it("con la red cortada avisa y deja reintentar", async () => {
    stub(() => Promise.reject(new TypeError("Failed to fetch")));
    render(<RecuperarCdrButton id="f-1" estado="ENVIADO" />);
    fireEvent.click(screen.getByRole("button", { name: "Recuperar constancia de SUNAT" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/Se cortó la conexión/);
    expect(screen.getByRole("button", { name: "Recuperar constancia de SUNAT" })).toBeEnabled();
  });

  it("un segundo clic mientras consulta no manda otra petición", async () => {
    let resolver: (r: Response) => void = () => {};
    const fetch = stub(() => new Promise<Response>((r) => (resolver = r)));
    render(<RecuperarCdrButton id="f-1" estado="ENVIADO" />);
    const boton = screen.getByRole("button", { name: "Recuperar constancia de SUNAT" });
    fireEvent.click(boton);
    fireEvent.click(boton);
    expect(fetch).toHaveBeenCalledTimes(1);
    resolver(new Response(JSON.stringify({ estado: "exito", datos: { estado_documento: "ENVIADO" }, mensaje: null, codigo: null, errores: null })));
    await screen.findByTestId("recuperar-cdr-mensaje");
  });
});
