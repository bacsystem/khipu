import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useCalculo } from "./use-calculo";

const TOTALES = { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 };
const sobre = (cuerpo: object, status = 200) => Promise.resolve(new Response(JSON.stringify(cuerpo), { status }));

function stub(responder: (cuerpo: unknown) => Promise<Response>) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>((_url, init) => responder(JSON.parse(String(init?.body))));
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("useCalculo (#405)", () => {
  it("sin cuerpo no pregunta nada", () => {
    const fetch = stub(() => sobre({}));
    const { result } = renderHook(() => useCalculo(null));
    expect(result.current).toEqual({ estado: "inactivo" });
    act(() => void vi.advanceTimersByTime(1000));
    expect(fetch).not.toHaveBeenCalled();
  });

  it("espera a que se deje de teclear y pide una sola vez los totales del servidor", async () => {
    const fetch = stub(() => sobre({ estado: "exito", datos: { totales: TOTALES, total_en_soles: 443.21 }, mensaje: null, codigo: null, errores: null }));
    const { result, rerender } = renderHook(({ c }) => useCalculo(c), { initialProps: { c: { items: [1] } as object } });
    expect(result.current.estado).toBe("calculando");
    rerender({ c: { items: [1, 2] } });
    await act(async () => void vi.advanceTimersByTime(400));
    await waitFor(() => expect(result.current.estado).toBe("listo"));
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(String(fetch.mock.calls[0][0])).toBe("/api/proxy/facturas/calcular");
    expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({ items: [1, 2] });
    expect(result.current).toMatchObject({ estado: "listo", totales: TOTALES, totalEnSoles: 443.21, comprobante: { total_en_soles: 443.21 } });
  });

  it("un rechazo de dominio llega con su motivo", async () => {
    stub(() => sobre({ estado: "error", datos: null, mensaje: "3088 - Moneda no soportada: XXX", codigo: "MONEDA_INVALIDA", errores: null }, 422));
    const { result } = renderHook(() => useCalculo({ moneda: "XXX" }));
    await act(async () => void vi.advanceTimersByTime(400));
    await waitFor(() => expect(result.current).toEqual({ estado: "rechazado", mensaje: "3088 - Moneda no soportada: XXX" }));
  });

  it("si no se pudo preguntar (red) el formulario cae a su cálculo local", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new TypeError("Failed to fetch"))));
    const { result } = renderHook(() => useCalculo({ a: 1 }));
    await act(async () => void vi.advanceTimersByTime(400));
    await waitFor(() => expect(result.current).toEqual({ estado: "sin-servicio" }));
  });

  it("la respuesta de un cuerpo anterior no pisa la del actual", async () => {
    let resolver!: (r: Response) => void;
    let llamadas = 0;
    stub(() => (++llamadas === 1 ? new Promise<Response>((r) => (resolver = r)) : sobe(TOTALES, 200)));
    const { result, rerender } = renderHook(({ c }) => useCalculo(c, 10), { initialProps: { c: { n: 1 } as object } });
    await act(async () => void vi.advanceTimersByTime(20));
    rerender({ c: { n: 2 } });
    await act(async () => void vi.advanceTimersByTime(20));
    await waitFor(() => expect(result.current.estado).toBe("listo"));
    await act(async () => resolver(new Response(JSON.stringify({ estado: "exito", datos: { totales: { ...TOTALES, total: 1 } }, mensaje: null, codigo: null, errores: null }))));
    expect(result.current).toMatchObject({ estado: "listo", totales: { total: 118 } });
  });
});

function sobe(totales: typeof TOTALES, status: number) {
  return sobre({ estado: "exito", datos: { totales }, mensaje: null, codigo: null, errores: null }, status);
}
