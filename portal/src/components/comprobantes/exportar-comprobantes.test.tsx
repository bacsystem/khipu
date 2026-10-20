import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { EXPORTACION_MAXIMA, ExportarComprobantes, hrefDeExportacion, nombreDelArchivo } from "./exportar-comprobantes";

const abrir = () => fireEvent.click(screen.getByRole("button", { name: "Exportar" }));

let creadas: string[];
beforeEach(() => {
  creadas = [];
  vi.stubGlobal("URL", Object.assign(URL, { createObjectURL: vi.fn(() => "blob:x"), revokeObjectURL: vi.fn() }));
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
    creadas.push(this.download);
  });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function stub(respuesta: () => Promise<Response>) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(() => respuesta());
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

describe("ExportarComprobantes (#321)", () => {
  it("la exportación lleva los filtros activos de la tabla y el formato; el separador solo en CSV y si no es la coma", () => {
    const href = new URL(hrefDeExportacion({ estado: "ACEPTADO", desde: "2026-09-01", hasta: "2026-09-30", serie: "F001", tipo: "07", cliente: "andina", numero: 7 }, "xlsx", "punto_y_coma"), "http://x");
    expect(href.pathname).toBe("/api/proxy/facturas/exportacion");
    expect(Object.fromEntries(href.searchParams)).toEqual({ estado: "ACEPTADO", desde: "2026-09-01", hasta: "2026-09-30", serie: "F001", tipo: "07", cliente: "andina", numero: "7", formato: "xlsx" });
    expect(hrefDeExportacion({}, "csv")).toBe("/api/proxy/facturas/exportacion?formato=csv");
    expect(hrefDeExportacion({}, "csv", "punto_y_coma")).toBe("/api/proxy/facturas/exportacion?formato=csv&separador=punto_y_coma");
  });

  it("el nombre del archivo sale de Content-Disposition", () => {
    expect(nombreDelArchivo('attachment; filename="comprobantes-2026-09-01_2026-09-30.xlsx"', "xlsx")).toBe("comprobantes-2026-09-01_2026-09-30.xlsx");
    expect(nombreDelArchivo(null, "csv")).toBe("comprobantes.csv");
  });

  it("descarga en Excel con el nombre que propone la API, mostrando que está en curso", async () => {
    let soltar: (r: Response) => void = () => {};
    const fetch = stub(() => new Promise((r) => (soltar = r)));
    render(<ExportarComprobantes filtros={{ desde: "2026-09-01" }} total={12} />);
    abrir();
    expect(screen.getByTestId("exportar-comprobantes")).toHaveTextContent("12 que muestra la lista");
    fireEvent.click(screen.getByTestId("exportar-descargar"));

    expect(await screen.findByRole("button", { name: "Exportando…" })).toBeDisabled();
    expect(fetch.mock.calls[0][0]).toBe("/api/proxy/facturas/exportacion?desde=2026-09-01&formato=xlsx");
    soltar(new Response(new Blob(["PK"]), { status: 200, headers: { "content-disposition": 'attachment; filename="comprobantes-2026-09-01_2026-10-20.xlsx"' } }));

    await waitFor(() => expect(creadas).toEqual(["comprobantes-2026-09-01_2026-10-20.xlsx"]));
    expect(screen.queryByTestId("exportar-comprobantes")).not.toBeInTheDocument();
  });

  it("en CSV ofrece el separador y lo manda", async () => {
    const fetch = stub(() => Promise.resolve(new Response("﻿tipo\r\n", { status: 200 })));
    render(<ExportarComprobantes filtros={{}} total={1} />);
    abrir();
    fireEvent.click(screen.getByLabelText(/CSV/));
    fireEvent.change(screen.getByLabelText("Separador"), { target: { value: "punto_y_coma" } });
    fireEvent.click(screen.getByTestId("exportar-descargar"));

    await waitFor(() => expect(creadas).toEqual(["comprobantes.csv"]));
    expect(fetch.mock.calls[0][0]).toBe("/api/proxy/facturas/exportacion?formato=csv&separador=punto_y_coma");
  });

  it("un error de la API se muestra y no baja nada", async () => {
    stub(() => Promise.resolve(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "Hay 6000 comprobantes con esos filtros y una exportación admite hasta 5000", codigo: "EXPORTACION_EXCEDE_EL_TOPE", errores: null }), { status: 422 })));
    render(<ExportarComprobantes filtros={{}} total={10} />);
    abrir();
    fireEvent.click(screen.getByTestId("exportar-descargar"));

    expect(await screen.findByTestId("exportar-error")).toHaveTextContent("Hay 6000 comprobantes");
    expect(creadas).toEqual([]);
    expect(screen.getByTestId("exportar-descargar")).toBeEnabled();
  });

  it("sin conexión lo dice", async () => {
    stub(() => Promise.reject(new TypeError("red")));
    render(<ExportarComprobantes filtros={{}} total={10} />);
    abrir();
    fireEvent.click(screen.getByTestId("exportar-descargar"));
    expect(await screen.findByTestId("exportar-error")).toHaveTextContent("revisa tu conexión");
  });

  it("por encima del tope no se intenta y dice cuántos hay", () => {
    const fetch = stub(() => Promise.resolve(new Response("")));
    render(<ExportarComprobantes filtros={{}} total={EXPORTACION_MAXIMA + 1} />);
    abrir();
    expect(screen.getByTestId("exportar-motivo")).toHaveTextContent("una exportación admite hasta 5,000");
    expect(screen.getByTestId("exportar-descargar")).toBeDisabled();
    expect(fetch).not.toHaveBeenCalled();
  });
});
