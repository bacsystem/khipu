import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ProductoGuardado } from "@/lib/api/productos";
import { NuevoComprobanteForm } from "./nuevo-comprobante-form";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));
// Con 999 ítems de tope probar el límite costaría mil clics: aquí es 2.
vi.mock("@/lib/comprobantes/limites", async (original) => ({ ...(await original<typeof import("@/lib/comprobantes/limites")>()), MAX_LINEAS: 2 }));

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

  it("reescribir la descripción de la línea suelta los códigos del producto: ya es otro ítem y no viajan", async () => {
    const fetch = stubBackend([CONSULTORIA]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    await elegir("consult", "Consultoría");

    // El precio o la unidad no cambian qué producto es: los códigos siguen.
    fireEvent.change(screen.getByLabelText(/Precio unit\./), { target: { value: "99" } });
    fireEvent.blur(screen.getByLabelText(/Precio unit\./));
    fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: "Otra cosa distinta" } });
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));

    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    const item = emisiones(fetch)[0].items[0];
    expect(item).toMatchObject({ descripcion: "Otra cosa distinta", precio_unitario: 99 });
    expect(item.codigo).toBeUndefined();
    expect(item.codigo_sunat).toBeUndefined();
  });

  it("cambiar solo el precio conserva los códigos del producto", async () => {
    const fetch = stubBackend([CONSULTORIA]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    await elegir("consult", "Consultoría");

    fireEvent.change(screen.getByLabelText(/Precio unit\./), { target: { value: "99" } });
    fireEvent.blur(screen.getByLabelText(/Precio unit\./));
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));

    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].items[0]).toMatchObject({ codigo: "SRV-001", codigo_sunat: "80101500", precio_unitario: 99 });
  });

  describe("en una exportación", () => {
    it("un producto gravado entra como línea 40 y avisa que su precio trae el IGV adentro", async () => {
      stubBackend([CONSULTORIA]);
      render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
      fireEvent.change(screen.getByLabelText("Tipo de operación"), { target: { value: "0200" } });

      await elegir("consult", "Consultoría");

      expect((screen.getByLabelText("Descripción") as HTMLInputElement).value).toBe("Consultoría tributaria");
      expect(screen.getByTestId("aviso-producto-guardado")).toHaveTextContent(/IGV dentro de su precio/);
      fireEvent.click(screen.getByRole("button", { name: "Más opciones del ítem 1" }));
      expect(screen.getByLabelText("Afectación del IGV")).toHaveValue("40");
    });

    it("un producto gratuito no se agrega: la exportación cobra todas sus líneas", async () => {
      const GRATIS: ProductoGuardado = { id: "p-4", descripcion: "Muestra sin costo", unidad: "NIU", tipo_afectacion_igv: "21", precio_unitario: 5, creado_en: "2026-09-08T15:00:00Z", actualizado_en: "2026-09-08T15:00:00Z" };
      stubBackend([GRATIS]);
      render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
      fireEvent.change(screen.getByLabelText("Tipo de operación"), { target: { value: "0200" } });

      await elegir("muestra", "Muestra");

      expect(screen.getByTestId("aviso-producto-guardado")).toHaveTextContent(/es gratuito/);
      expect((screen.getByLabelText("Descripción") as HTMLInputElement).value).toBe("");
    });

    it("el aviso de un producto que no se pudo agregar se va al cambiar la operación", async () => {
      stubBackend([LICENCIA]);
      render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
      await elegir("licencia", "Licencia");
      expect(screen.getByTestId("aviso-producto-guardado")).toBeInTheDocument();

      fireEvent.change(screen.getByLabelText("Tipo de operación"), { target: { value: "0200" } });

      expect(screen.queryByTestId("aviso-producto-guardado")).not.toBeInTheDocument();
    });
  });

  it("un producto gravado en una venta interna conserva su afectación y no avisa nada", async () => {
    stubBackend([CONSULTORIA]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    await elegir("consult", "Consultoría");

    expect(screen.queryByTestId("aviso-producto-guardado")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Más opciones del ítem 1" }));
    expect(screen.getByLabelText("Afectación del IGV")).toHaveValue("10");
  });

  it("al llegar al tope de ítems no agrega otra línea y lo dice", async () => {
    stubBackend([LIBRO]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    await elegir("libro", "Libro técnico");
    await elegir("libro", "Libro técnico");
    expect(screen.getAllByLabelText("Descripción")).toHaveLength(2);

    await elegir("libro", "Libro técnico");

    expect(screen.getByTestId("aviso-producto-guardado")).toHaveTextContent(/hasta 2 ítems/);
    expect(screen.getAllByLabelText("Descripción")).toHaveLength(2);
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
