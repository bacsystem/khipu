import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ValidezButton, type DatosDeValidez } from "./validez-button";

const DATOS: DatosDeValidez = { ruc: "20123456786", tipo: "01", serie: "F001", numero: 7, tipoDocReceptor: "6", numDocReceptor: "20554198211", fecha: "2026-09-02", monto: 59 };

function stub(cuerpo: object, status = 200) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(() => Promise.resolve(new Response(JSON.stringify(cuerpo), { status })));
  vi.stubGlobal("fetch", fetch);
  return fetch;
}
const exito = (datos: object) => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("ValidezButton (#315)", () => {
  it("consulta por los datos del comprobante: emisor, tipo, serie, número, receptor, fecha y monto con 2 decimales", async () => {
    const fetch = stub(exito({ estado: "ACEPTADO", codigo: "0001", mensaje: "ok" }));
    render(<ValidezButton datos={DATOS} />);
    fireEvent.click(screen.getByRole("button", { name: "Consultar validez en SUNAT" }));
    expect(await screen.findByTestId("validez-resultado")).toHaveTextContent("Aceptado por SUNAT (0001)");
    const url = new URL(String(fetch.mock.calls[0][0]), "http://x");
    expect(url.pathname).toBe("/api/proxy/consultas/validez");
    expect(Object.fromEntries(url.searchParams)).toEqual({ ruc: "20123456786", tipo: "01", serie: "F001", numero: "7", tipo_doc_receptor: "6", num_doc_receptor: "20554198211", fecha: "2026-09-02", monto: "59.00" });
  });

  it("sin receptor identificado no manda sus criterios", async () => {
    const fetch = stub(exito({ estado: "ACEPTADO", codigo: "0001", mensaje: "ok" }));
    render(<ValidezButton datos={{ ...DATOS, tipoDocReceptor: undefined, numDocReceptor: undefined }} />);
    fireEvent.click(screen.getByRole("button", { name: "Consultar validez en SUNAT" }));
    await screen.findByTestId("validez-resultado");
    const q = new URL(String(fetch.mock.calls[0][0]), "http://x").searchParams;
    expect(q.has("tipo_doc_receptor")).toBe(false);
    expect(q.has("num_doc_receptor")).toBe(false);
  });

  it.each([
    ["RECHAZADO", /rechazado \(0002\)/],
    ["DE_BAJA", /dado de baja \(0003\)/],
    ["NO_EXISTE", /no tiene este comprobante \(0011\)/],
    ["AJENO", /otro emisor \(0012\)/],
  ])("dice en palabras el estado %s", async (estado, texto) => {
    stub(exito({ estado, codigo: "x", mensaje: "m" }));
    render(<ValidezButton datos={DATOS} />);
    fireEvent.click(screen.getByRole("button", { name: "Consultar validez en SUNAT" }));
    expect(await screen.findByTestId("validez-resultado")).toHaveTextContent(texto);
  });

  it.each([
    ["ACEPTADO", "Aceptado por SUNAT."],
    ["DE_BAJA", "anulado"],
    ["NO_EXISTE", "no tiene informado"],
    ["AUTORIZADO", "autorizado por imprenta"],
    ["NO_AUTORIZADO", "no autorizado por imprenta"],
  ])("por la API REST (#318) dice el estado %s sin los códigos de SOAP", async (estado, texto) => {
    stub(exito({ estado, codigo: "1", mensaje: "m", fuente: "API_REST" }));
    render(<ValidezButton datos={DATOS} />);
    fireEvent.click(screen.getByRole("button", { name: "Consultar validez en SUNAT" }));
    const r = await screen.findByTestId("validez-resultado");
    expect(r).toHaveTextContent(texto);
    expect(r).not.toHaveTextContent("0001");
  });

  it("por la API REST muestra las observaciones de SUNAT, y si cayó a SOAP dice por qué (#318)", async () => {
    stub(exito({ estado: "ACEPTADO", codigo: "1", mensaje: "m", fuente: "API_REST", observaciones: ["- Fecha de emisión ingresada no coincide con la registrada en la SUNAT."] }));
    render(<ValidezButton datos={DATOS} />);
    fireEvent.click(screen.getByRole("button", { name: "Consultar validez en SUNAT" }));
    expect(await screen.findByTestId("validez-resultado")).toHaveTextContent("Observaciones: - Fecha de emisión ingresada no coincide");
    cleanup();

    stub(exito({ estado: "ACEPTADO", codigo: "0001", mensaje: "m", fuente: "SOAP", aviso: "La API de SUNAT no respondió (HTTP 503): se usó billValidService con la clave SOL" }));
    render(<ValidezButton datos={DATOS} />);
    fireEvent.click(screen.getByRole("button", { name: "Consultar validez en SUNAT" }));
    const r = await screen.findByTestId("validez-resultado");
    expect(r).toHaveTextContent("Aceptado por SUNAT (0001).");
    expect(r).toHaveTextContent("se usó billValidService");
  });

  it("un error de la consulta (sin credenciales API ni SOL, por ejemplo) se muestra como alerta", async () => {
    stub({ estado: "error", datos: null, mensaje: "Para consultar la validez en SUNAT configure las credenciales API SUNAT o las credenciales SOL de la empresa", codigo: "CREDENCIALES_SUNAT_NO_CARGADAS", errores: null }, 422);
    render(<ValidezButton datos={DATOS} />);
    fireEvent.click(screen.getByRole("button", { name: "Consultar validez en SUNAT" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("credenciales SOL");
  });
});
