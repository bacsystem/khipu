import { renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { usePreferenciasDeEmision } from "./use-preferencias-de-emision";

function stub(respuesta: () => Promise<Response>) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(() => respuesta());
  vi.stubGlobal("fetch", fetch);
  return fetch;
}
const sobre = (cuerpo: object, status = 200) => Promise.resolve(new Response(JSON.stringify(cuerpo), { status }));

afterEach(() => vi.unstubAllGlobals());

describe("usePreferenciasDeEmision", () => {
  it("lee la preferencia por el proxy; mientras carga vale false", async () => {
    const fetch = stub(() => sobre({ estado: "exito", datos: { opciones_avanzadas: true }, mensaje: null, codigo: null, errores: null }));
    const { result } = renderHook(() => usePreferenciasDeEmision());
    expect(result.current).toEqual({ opcionesAvanzadas: false, cargando: true });

    await waitFor(() => expect(result.current.cargando).toBe(false));
    expect(result.current.opcionesAvanzadas).toBe(true);
    expect(fetch.mock.calls[0][0]).toBe("/api/proxy/empresa/preferencias-emision");
    expect(fetch.mock.calls[0][1]?.method).toBe("GET");
  });

  it("si no se puede leer, queda en false (el formulario simple)", async () => {
    stub(() => sobre({ estado: "error", datos: null, mensaje: "x", codigo: "ERROR_INTERNO", errores: null }, 500));
    const { result } = renderHook(() => usePreferenciasDeEmision());
    await waitFor(() => expect(result.current.cargando).toBe(false));
    expect(result.current.opcionesAvanzadas).toBe(false);
  });

  it("con la red caída, también false", async () => {
    stub(() => Promise.reject(new TypeError("Failed to fetch")));
    const { result } = renderHook(() => usePreferenciasDeEmision());
    await waitFor(() => expect(result.current.cargando).toBe(false));
    expect(result.current.opcionesAvanzadas).toBe(false);
  });
});
