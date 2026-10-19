import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ProductoGuardado } from "@/lib/api/productos";
import { NuevoComprobanteForm } from "./nuevo-comprobante-form";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

const SERIES = [
  { tipo: "01", serie: "F001", ultimo_numero: 2, activa: true, establecimiento: "0000" },
  { tipo: "03", serie: "B001", ultimo_numero: 9, activa: true, establecimiento: "0000" },
];

const CONSULTORIA: ProductoGuardado = { id: "p-1", codigo: "SRV-001", descripcion: "Consultoría tributaria", unidad: "ZZ", tipo_afectacion_igv: "10", precio_unitario: 118, codigo_sunat: "80101500", creado_en: "2026-09-05T15:00:00Z", actualizado_en: "2026-09-05T15:00:00Z" };
const LIBRO: ProductoGuardado = { id: "p-2", descripcion: "Libro técnico", unidad: "NIU", tipo_afectacion_igv: "20", precio_unitario: 50, creado_en: "2026-09-06T15:00:00Z", actualizado_en: "2026-09-06T15:00:00Z" };
const LICENCIA: ProductoGuardado = { id: "p-3", descripcion: "Licencia para el exterior", unidad: "ZZ", tipo_afectacion_igv: "40", precio_unitario: 1200, creado_en: "2026-09-07T15:00:00Z", actualizado_en: "2026-09-07T15:00:00Z" };

const sobre = (datos: unknown, status = 200, cabeceras: Record<string, string> = {}) =>
  new Response(JSON.stringify({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }), { status, headers: cabeceras });

/** Contesta la búsqueda de productos con `encontrados`, el cálculo y la emisión como un backend sano. */
function stubBackend(encontrados: ProductoGuardado[]) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>((url) => {
    const u = String(url);
    if (u.includes("/api/proxy/productos?")) return Promise.resolve(sobre(encontrados, 200, { "x-total-count": String(encontrados.length) }));
    if (u.includes("/api/proxy/clientes")) return Promise.resolve(sobre([], 200, { "x-total-count": "0" }));
    if (u.includes("/facturas/calcular")) return Promise.resolve(sobre({ totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 }, total_en_soles: null }));
    return Promise.resolve(sobre({ id: "c-1" }, 201));
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

const emisiones = (fetch: ReturnType<typeof stubBackend>) =>
  fetch.mock.calls.filter(([url, init]) => String(url).includes("/facturas") && !String(url).includes("/calcular") && init?.method === "POST").map(([, init]) => JSON.parse(String(init?.body)));

async function elegir(texto: string, nombre: string) {
  fireEvent.change(screen.getByLabelText("Buscar un producto guardado"), { target: { value: texto } });
  fireEvent.mouseDown(await screen.findByRole("option", { name: new RegExp(nombre) }));
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("NuevoComprobanteForm — productos guardados (#320)", () => {
  it("elegir un producto llena la línea vacía con su descripción, unidad, afectación y precio, y viaja con su código interno y el de SUNAT", async () => {
    const fetch = stubBackend([CONSULTORIA]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });

    await elegir("consult", "Consultoría");

    expect((screen.getByLabelText("Descripción") as HTMLInputElement).value).toBe("Consultoría tributaria");
    expect((screen.getByLabelText(/Precio unit\./) as HTMLInputElement).value).toBe("118.00");
    expect(screen.getAllByLabelText("Descripción")).toHaveLength(1);
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));

    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].items).toEqual([expect.objectContaining({ codigo: "SRV-001", codigo_sunat: "80101500", descripcion: "Consultoría tributaria", unidad: "ZZ", tipo_afectacion_igv: "10", precio_unitario: 118 })]);
  });

  it("un segundo producto se agrega como otra línea y conserva su propia afectación", async () => {
    const fetch = stubBackend([LIBRO]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: "Algo que ya estaba" } });
    fireEvent.change(screen.getByLabelText(/Precio unit\./), { target: { value: "10" } });
    fireEvent.blur(screen.getByLabelText(/Precio unit\./));

    await elegir("libro", "Libro técnico");

    expect(screen.getAllByLabelText("Descripción")).toHaveLength(2);
    expect((screen.getAllByLabelText("Descripción")[1] as HTMLInputElement).value).toBe("Libro técnico");
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].items).toHaveLength(2);
    expect(emisiones(fetch)[0].items[1]).toMatchObject({ descripcion: "Libro técnico", tipo_afectacion_igv: "20", precio_unitario: 50 });
    expect(emisiones(fetch)[0].items[1].codigo).toBeUndefined();
  });

  it("un producto de exportación no se agrega a una venta interna y se dice por qué", async () => {
    stubBackend([LICENCIA]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    await elegir("licencia", "Licencia");

    expect(screen.getByTestId("aviso-producto-guardado")).toHaveTextContent(/es de exportación/);
    expect((screen.getByLabelText("Descripción") as HTMLInputElement).value).toBe("");
  });

  it("si falla la búsqueda lo dice y la línea se puede teclear a mano", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new TypeError("red"))));
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    fireEvent.change(screen.getByLabelText("Buscar un producto guardado"), { target: { value: "consult" } });

    expect(await screen.findByText(/No se pudieron buscar tus productos/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: "A mano" } });
    expect((screen.getByLabelText("Descripción") as HTMLInputElement).value).toBe("A mano");
  });
});
