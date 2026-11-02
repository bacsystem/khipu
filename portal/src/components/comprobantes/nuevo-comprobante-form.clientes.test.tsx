import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ClienteGuardado } from "@/lib/api/clientes";
import { NuevoComprobanteForm } from "./nuevo-comprobante-form";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

const SERIES = [
  { tipo: "01", serie: "F001", ultimo_numero: 2, activa: true, establecimiento: "0000" },
  { tipo: "03", serie: "B001", ultimo_numero: 9, activa: true, establecimiento: "0000" },
];

const PACIFICO: ClienteGuardado = { id: "c-1", tipo_doc: "6", num_doc: "20601234565", razon_social: "PESQUERA PACÍFICO S.A.C.", direccion: "Av. Argentina 2450, Callao", creado_en: "2026-09-02T15:00:00Z", actualizado_en: "2026-09-02T15:00:00Z" };
const JUAN: ClienteGuardado = { id: "c-2", tipo_doc: "1", num_doc: "12345678", razon_social: "JUAN PÉREZ GÓMEZ", creado_en: "2026-09-03T15:00:00Z", actualizado_en: "2026-09-03T15:00:00Z" };

const sobre = (datos: unknown, status = 200, cabeceras: Record<string, string> = {}) =>
  new Response(JSON.stringify({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }), { status, headers: cabeceras });

/** Contesta la búsqueda de clientes con `encontrados`, el cálculo y la emisión como un backend sano, y deja pasar el guardado del cliente. */
function stubBackend(encontrados: ClienteGuardado[]) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>((url) => {
    const u = String(url);
    if (u.includes("/api/proxy/clientes?")) return Promise.resolve(sobre(encontrados, 200, { "x-total-count": String(encontrados.length) }));
    if (u.includes("/clientes/documento/")) return Promise.resolve(sobre({ id: "c-9" }, 201));
    if (u.includes("/facturas/calcular")) return Promise.resolve(sobre({ totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 }, total_en_soles: null }));
    return Promise.resolve(sobre({ id: "c-1" }, 201));
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

const guardados = (fetch: ReturnType<typeof stubBackend>) =>
  fetch.mock.calls.filter(([url]) => String(url).includes("/clientes/documento/")).map(([url, init]) => ({ url: String(url), metodo: init?.method, cuerpo: JSON.parse(String(init?.body)) }));

async function elegir(texto: string, nombre: string) {
  fireEvent.change(screen.getByLabelText("Buscar un cliente guardado"), { target: { value: texto } });
  fireEvent.mouseDown(await screen.findByRole("option", { name: new RegExp(nombre) }));
}

function conUnaLinea() {
  fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: "Servicio" } });
  fireEvent.change(screen.getByLabelText(/Precio unit\./), { target: { value: "118" } });
  fireEvent.blur(screen.getByLabelText(/Precio unit\./));
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("NuevoComprobanteForm — clientes guardados (#319)", () => {
  it("elegir un cliente guardado rellena el documento, el nombre y la dirección", async () => {
    stubBackend([PACIFICO]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    await elegir("pacif", "PESQUERA");

    expect((screen.getByLabelText("RUC") as HTMLInputElement).value).toBe("20601234565");
    expect((screen.getByLabelText("Razón social") as HTMLInputElement).value).toBe("PESQUERA PACÍFICO S.A.C.");
    expect((screen.getByLabelText(/Dirección/) as HTMLInputElement).value).toBe("Av. Argentina 2450, Callao");
    expect(screen.queryByTestId("aviso-cliente-guardado")).toBeNull();
  });

  it("un cliente con DNI no se aplica a una factura y se dice por qué", async () => {
    stubBackend([JUAN]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    await elegir("juan", "JUAN");

    expect((screen.getByLabelText("RUC") as HTMLInputElement).value).toBe("");
    expect(screen.getByTestId("aviso-cliente-guardado")).toHaveTextContent(/necesita un cliente con RUC/);
  });

  it("al emitir con la casilla marcada guarda al cliente por su documento, sin los datos que no se informaron", async () => {
    const fetch = stubBackend([]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    conUnaLinea();

    expect(screen.getByRole("checkbox", { name: "Guardar en mis clientes al emitir" })).toBeChecked();
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));

    await waitFor(() => expect(guardados(fetch)).toHaveLength(1));
    const [guardado] = guardados(fetch);
    expect(guardado.url).toBe("/api/proxy/clientes/documento/6/20554198211");
    expect(guardado.metodo).toBe("PUT");
    expect(guardado.cuerpo).toEqual({ razon_social: "Cliente SAC" });
  });

  it("con la casilla desmarcada a mano no guarda", async () => {
    const fetch = stubBackend([]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    conUnaLinea();

    fireEvent.click(screen.getByRole("checkbox", { name: "Guardar en mis clientes al emitir" }));
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));

    await waitFor(() => expect(fetch.mock.calls.some(([url, init]) => String(url).includes("/facturas") && !String(url).includes("/calcular") && init?.method === "POST")).toBe(true));
    expect(guardados(fetch)).toHaveLength(0);
  });

  it("en una boleta no arrastra la dirección del cliente elegido, que ese formulario no muestra ni deja borrar", async () => {
    const fetch = stubBackend([{ ...JUAN, direccion: "Jr. Oculto 1" }]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} tipo="boleta" />);
    await elegir("juan", "JUAN");
    conUnaLinea();

    fireEvent.click(screen.getByRole("button", { name: "Emitir boleta" }));

    await waitFor(() => expect(fetch.mock.calls.some(([url, init]) => String(url).includes("/facturas") && !String(url).includes("/calcular") && init?.method === "POST")).toBe(true));
    const emision = fetch.mock.calls.filter(([url, init]) => String(url).includes("/facturas") && !String(url).includes("/calcular") && init?.method === "POST").map(([, init]) => JSON.parse(String(init?.body)))[0];
    expect(emision.cliente.direccion).toBeUndefined();
  });

  it("con la casilla desmarcada, o con un cliente que ya estaba guardado, no vuelve a guardar", async () => {
    const fetch = stubBackend([PACIFICO]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    await elegir("pacif", "PESQUERA");
    conUnaLinea();

    expect(screen.getByRole("checkbox", { name: "Guardar en mis clientes al emitir" })).not.toBeChecked();
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));

    await waitFor(() => expect(fetch.mock.calls.some(([url, init]) => String(url).includes("/facturas") && !String(url).includes("/calcular") && init?.method === "POST")).toBe(true));
    expect(guardados(fetch)).toHaveLength(0);
  });

  // --- #325: el correo del cliente, para el envío automático al aceptarse ---------------------------------------------------

  const emisiones = (fetch: ReturnType<typeof stubBackend>) =>
    fetch.mock.calls.filter(([url, init]) => String(url).includes("/facturas") && !String(url).includes("/calcular") && init?.method === "POST").map(([, init]) => JSON.parse(String(init?.body)));

  it("el correo del cliente viaja en la emisión y se guarda con el cliente", async () => {
    const fetch = stubBackend([]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    fireEvent.change(screen.getByLabelText(/Correo del cliente/), { target: { value: "  compras@cliente.pe " } });
    conUnaLinea();

    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));

    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].cliente.email).toBe("compras@cliente.pe");
    // El cálculo de importes no lleva al comprador de verdad: tampoco su correo.
    const calculos = fetch.mock.calls.filter(([url]) => String(url).includes("/facturas/calcular")).map(([, init]) => JSON.parse(String(init?.body)));
    expect(calculos.every((c) => c.cliente.email === undefined)).toBe(true);
    await waitFor(() => expect(guardados(fetch)).toHaveLength(1));
    expect(guardados(fetch)[0].cuerpo).toEqual({ razon_social: "Cliente SAC", email: "compras@cliente.pe" });
  });

  it("sin correo la emisión no lleva la clave", async () => {
    const fetch = stubBackend([]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    conUnaLinea();

    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));

    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].cliente).not.toHaveProperty("email");
  });

  it("elegir un cliente guardado con correo lo propone, y uno sin correo lo deja vacío", async () => {
    stubBackend([{ ...PACIFICO, email: "pagos@pacifico.pe" }, JUAN]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} tipo="boleta" />);

    await elegir("pacif", "PESQUERA");
    expect((screen.getByLabelText(/Correo del cliente/) as HTMLInputElement).value).toBe("pagos@pacifico.pe");

    await elegir("juan", "JUAN");
    expect((screen.getByLabelText(/Correo del cliente/) as HTMLInputElement).value).toBe("");
  });

  it("si falla la búsqueda lo dice y el cliente se puede teclear a mano", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new TypeError("red"))));
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    fireEvent.change(screen.getByLabelText("Buscar un cliente guardado"), { target: { value: "pacif" } });

    expect(await screen.findByText(/No se pudieron buscar tus clientes/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    expect((screen.getByLabelText("RUC") as HTMLInputElement).value).toBe("20554198211");
  });
});
