import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Comprobante } from "@/lib/api/facturas";
import { PAGINAS_MAXIMAS, POR_PAGINA, useAnticiposDisponibles } from "./use-anticipos-disponibles";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const comp = (n: number, extra: Partial<Comprobante> = {}): Comprobante =>
  ({
    id: `f-${n}`, tipo: "01", serie: "F001", numero: n, fecha_emision: "2026-09-01", moneda: "PEN", estado_documento: "ACEPTADO",
    receptor: { tipo_doc: "6", num_doc: "20554198211", razon_social: "X", direccion: null },
    totales: { gravado: 500, exonerado: 0, inafecto: 0, igv: 90, total: 590 }, anticipos: null, ...extra,
  }) as unknown as Comprobante;
const otroCliente = (n: number) => comp(n, { receptor: { tipo_doc: "6", num_doc: "20100066603", razon_social: "Y", direccion: null } });
const sobre = (datos: unknown, ok = true) =>
  Promise.resolve(new Response(JSON.stringify(ok ? { estado: "exito", datos, mensaje: null, codigo: null, errores: null } : { estado: "error", datos: null, mensaje: "x", codigo: "X", errores: null })));

describe("useAnticiposDisponibles (#312)", () => {
  it("sigue pidiendo páginas hasta una corta: un anticipo en la 2.ª página sí se ofrece", async () => {
    const fetch = vi.fn((url: string) => {
      const pagina = Number(new URL(url, "http://x").searchParams.get("pagina"));
      if (pagina === 1) return sobre(Array.from({ length: POR_PAGINA }, (_, i) => otroCliente(1000 + i)));
      return sobre([comp(7)]);
    });
    vi.stubGlobal("fetch", fetch);
    const { result } = renderHook(() => useAnticiposDisponibles(true, "20554198211", "PEN"));
    await waitFor(() => expect(result.current.estado).toBe("listo"));
    expect(result.current.candidatos.map((c) => c.etiqueta)).toEqual(["F001-7"]);
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("no pide más de las páginas máximas", async () => {
    const fetch = vi.fn(() => sobre(Array.from({ length: POR_PAGINA }, (_, i) => otroCliente(i))));
    vi.stubGlobal("fetch", fetch);
    const { result } = renderHook(() => useAnticiposDisponibles(true, "20554198211", "PEN"));
    await waitFor(() => expect(result.current.estado).toBe("listo"));
    expect(fetch).toHaveBeenCalledTimes(PAGINAS_MAXIMAS);
  });

  it("tras un fallo, cerrar y abrir la sección reintenta y el error desaparece", async () => {
    let falla = true;
    vi.stubGlobal("fetch", vi.fn(() => (falla ? sobre(null, false) : sobre([comp(3)]))));
    const { result, rerender } = renderHook(({ activo }) => useAnticiposDisponibles(activo, "20554198211", "PEN"), { initialProps: { activo: true } });
    await waitFor(() => expect(result.current.estado).toBe("error"));

    falla = false;
    rerender({ activo: false });
    expect(result.current.estado).toBe("inactivo");
    act(() => rerender({ activo: true }));
    await waitFor(() => expect(result.current.estado).toBe("listo"));
    expect(result.current.candidatos.map((c) => c.etiqueta)).toEqual(["F001-3"]);
  });

  it("inactivo no pide nada", () => {
    const fetch = vi.fn(() => sobre([]));
    vi.stubGlobal("fetch", fetch);
    const { result } = renderHook(() => useAnticiposDisponibles(false, "20554198211", "PEN"));
    expect(result.current.estado).toBe("inactivo");
    expect(fetch).not.toHaveBeenCalled();
  });
});
