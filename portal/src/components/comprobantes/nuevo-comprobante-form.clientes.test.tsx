import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ClienteGuardado } from "@/lib/api/clientes";
import { elegirDocumento } from "@/test/combos-del-comprobante";
import { NuevoComprobanteForm } from "./nuevo-comprobante-form";
import { abrirDatosDelCliente, campoCliente, escribirCliente } from "@/test/cliente-del-comprobante";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));
vi.mock("@/lib/use-preferencias-de-emision", () => ({ usePreferenciasDeEmision: () => ({ opcionesAvanzadas: true, cargando: false }) }));

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
  fireEvent.change(campoCliente(), { target: { value: texto } });
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
    abrirDatosDelCliente();

    await elegir("pacif", "PESQUERA");

    expect((screen.getByLabelText("RUC") as HTMLInputElement).value).toBe("20601234565");
    expect((screen.getByLabelText("Razón social") as HTMLInputElement).value).toBe("PESQUERA PACÍFICO S.A.C.");
    expect((screen.getByLabelText(/Dirección/) as HTMLInputElement).value).toBe("Av. Argentina 2450, Callao");
    expect(screen.queryByTestId("aviso-cliente-guardado")).toBeNull();
  });

  it("un cliente con DNI no se aplica a una factura y se dice por qué", async () => {
    stubBackend([JUAN]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    abrirDatosDelCliente();

    await elegir("juan", "JUAN");

    expect((screen.getByLabelText("RUC") as HTMLInputElement).value).toBe("");
    expect(screen.getByTestId("aviso-cliente-guardado")).toHaveTextContent(/necesita un cliente con RUC/);
  });

  it("al emitir guarda siempre al cliente por su documento, sin casilla y sin los datos que no se informaron (#557)", async () => {
    const fetch = stubBackend([]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    abrirDatosDelCliente();
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    conUnaLinea();

    expect(screen.queryByRole("checkbox", { name: /Guardar en mis clientes/ })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));

    await waitFor(() => expect(guardados(fetch)).toHaveLength(1));
    const [guardado] = guardados(fetch);
    expect(guardado.url).toBe("/api/proxy/clientes/documento/6/20554198211");
    expect(guardado.metodo).toBe("PUT");
    expect(guardado.cuerpo).toEqual({ razon_social: "Cliente SAC" });
  });

  it("una boleta sin documento no guarda al comprador: no hay documento por el que guardarlo", async () => {
    const fetch = stubBackend([]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} tipo="boleta" />);
    abrirDatosDelCliente();
    await elegirDocumento("-");
    conUnaLinea();

    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));

    await waitFor(() => expect(fetch.mock.calls.some(([url, init]) => String(url).includes("/facturas") && !String(url).includes("/calcular") && init?.method === "POST")).toBe(true));
    expect(guardados(fetch)).toHaveLength(0);
  });

  it("si al cliente elegido de los guardados se le cambia el documento, el nuevo sí se guarda", async () => {
    const fetch = stubBackend([PACIFICO]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    abrirDatosDelCliente();
    await elegir("pacif", "PESQUERA");
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    conUnaLinea();

    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));

    await waitFor(() => expect(guardados(fetch)).toHaveLength(1));
    expect(guardados(fetch)[0].url).toBe("/api/proxy/clientes/documento/6/20554198211");
  });

  it("en una boleta no arrastra la dirección del cliente elegido, que ese formulario no muestra ni deja borrar", async () => {
    const fetch = stubBackend([{ ...JUAN, direccion: "Jr. Oculto 1" }]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} tipo="boleta" />);
    abrirDatosDelCliente();
    await elegir("juan", "JUAN");
    conUnaLinea();

    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));

    await waitFor(() => expect(fetch.mock.calls.some(([url, init]) => String(url).includes("/facturas") && !String(url).includes("/calcular") && init?.method === "POST")).toBe(true));
    const emision = fetch.mock.calls.filter(([url, init]) => String(url).includes("/facturas") && !String(url).includes("/calcular") && init?.method === "POST").map(([, init]) => JSON.parse(String(init?.body)))[0];
    expect(emision.cliente.direccion).toBeUndefined();
  });

  it("con un cliente que ya estaba guardado no vuelve a guardar", async () => {
    const fetch = stubBackend([PACIFICO]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    abrirDatosDelCliente();
    await elegir("pacif", "PESQUERA");
    conUnaLinea();

    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));

    await waitFor(() => expect(fetch.mock.calls.some(([url, init]) => String(url).includes("/facturas") && !String(url).includes("/calcular") && init?.method === "POST")).toBe(true));
    expect(guardados(fetch)).toHaveLength(0);
  });

  // --- #325: el correo del cliente, para el envío automático al aceptarse ---------------------------------------------------

  const emisiones = (fetch: ReturnType<typeof stubBackend>) =>
    fetch.mock.calls.filter(([url, init]) => String(url).includes("/facturas") && !String(url).includes("/calcular") && init?.method === "POST").map(([, init]) => JSON.parse(String(init?.body)));

  it("el correo del cliente viaja en la emisión y se guarda con el cliente", async () => {
    const fetch = stubBackend([]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    abrirDatosDelCliente();
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    // #557: el correo va a pedido, detrás de su «+».
    fireEvent.click(screen.getByRole("button", { name: "Correo" }));
    fireEvent.change(screen.getByLabelText(/Correo del cliente/), { target: { value: "  compras@cliente.pe " } });
    conUnaLinea();

    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));

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
    abrirDatosDelCliente();
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    conUnaLinea();

    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));

    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].cliente).not.toHaveProperty("email");
  });

  it("elegir un cliente guardado con correo lo propone (abre su panel), y uno sin correo lo deja vacío", async () => {
    stubBackend([{ ...PACIFICO, email: "pagos@pacifico.pe" }, JUAN]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} tipo="boleta" />);
    abrirDatosDelCliente();

    expect(screen.queryByLabelText(/Correo del cliente/)).not.toBeInTheDocument();
    await elegir("pacif", "PESQUERA");
    expect((screen.getByLabelText(/Correo del cliente/) as HTMLInputElement).value).toBe("pagos@pacifico.pe");

    // #557: elegido, el cliente es una tarjeta; «Cambiar» vuelve al campo (y suelta el correo que vino con él).
    fireEvent.click(screen.getByRole("button", { name: "Cambiar" }));
    await elegir("juan", "JUAN");
    expect((screen.getByLabelText(/Correo del cliente/) as HTMLInputElement).value).toBe("");
  });

  it("si falla la búsqueda lo dice y el cliente se puede teclear a mano", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new TypeError("red"))));
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    abrirDatosDelCliente();

    fireEvent.change(campoCliente(), { target: { value: "pacif" } });

    expect(await screen.findByText(/No se pudieron buscar tus clientes/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    expect((screen.getByLabelText("RUC") as HTMLInputElement).value).toBe("20554198211");
  });
});

// --- #557: el cliente en un solo campo -------------------------------------------------------------------------------------------

const TIENDAS = { ruc: "20100066603", razon_social: "TIENDAS DEL SUR S.A.C.", estado: "ACTIVO", condicion: "HABIDO", activo: true, habido: true, ubigeo: "150101", direccion: "AV. REPUBLICA DE PANAMA NRO. 2457", actualizado_al: "2026-10-16T09:30:00Z" };

/** Como `stubBackend`, y además el padrón: `20100066603` es TIENDAS DEL SUR; cualquier otro RUC no figura. */
function stubConPadron(encontrados: ClienteGuardado[] = []) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>((url) => {
    const u = String(url);
    const ruc = u.match(/\/consultas\/ruc\/(\d+)/)?.[1];
    if (ruc === "20100066603") return Promise.resolve(sobre(TIENDAS));
    if (ruc) return Promise.resolve(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "x", codigo: "NO_ENCONTRADO", errores: null }), { status: 404 }));
    if (u.includes("/api/proxy/clientes?")) return Promise.resolve(sobre(encontrados, 200, { "x-total-count": String(encontrados.length) }));
    if (u.includes("/clientes/documento/")) return Promise.resolve(sobre({ id: "c-9" }, 201));
    if (u.includes("/facturas/calcular")) return Promise.resolve(sobre({ totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 }, total_en_soles: null }));
    return Promise.resolve(sobre({ id: "c-1" }, 201));
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

const emitidas = (fetch: ReturnType<typeof stubConPadron>) =>
  fetch.mock.calls.filter(([url, init]) => String(url).endsWith("/facturas") && init?.method === "POST").map(([, init]) => JSON.parse(String(init?.body)));

describe("NuevoComprobanteForm — el cliente en un solo campo (#557)", () => {
  it("la factura muestra solo el campo «Cliente», con los datos del comprador plegados", () => {
    stubConPadron();
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    expect(campoCliente()).toHaveAttribute("placeholder", "RUC o razón social");
    expect(screen.queryByLabelText("RUC")).toBeNull();
    expect(screen.queryByLabelText("Razón social")).toBeNull();
    expect(screen.queryByTestId("tarjeta-cliente")).toBeNull();
  });

  it("escribir un RUC completo lo toma como documento y la tarjeta muestra lo que trae el padrón", async () => {
    const fetch = stubConPadron();
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    escribirCliente("20100066603");

    const tarjeta = await screen.findByTestId("tarjeta-cliente");
    await waitFor(() => expect(screen.getByTestId("tarjeta-cliente-nombre")).toHaveTextContent("TIENDAS DEL SUR S.A.C."));
    expect(screen.getByTestId("tarjeta-cliente-documento")).toHaveTextContent("RUC 20100066603");
    expect(screen.getByTestId("tarjeta-cliente-direccion")).toHaveTextContent("AV. REPUBLICA DE PANAMA NRO. 2457");
    expect(tarjeta).toContainElement(screen.getByTestId("consulta-ruc"));
    expect(screen.getByTestId("consulta-ruc")).toHaveAttribute("data-estado", "encontrado");
    expect(screen.queryByLabelText("Cliente")).toBeNull();

    conUnaLinea();
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emitidas(fetch)).toHaveLength(1));
    expect(emitidas(fetch)[0].cliente).toEqual({ tipo_doc: "6", num_doc: "20100066603", razon_social: "TIENDAS DEL SUR S.A.C.", direccion: "AV. REPUBLICA DE PANAMA NRO. 2457" });
  });

  it("un RUC que el padrón no tiene despliega los campos para escribir el nombre a mano", async () => {
    stubConPadron();
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    escribirCliente("20601234565");

    expect(await screen.findByText(/no figura en el padrón de SUNAT/)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText("Razón social")).toBeInTheDocument());
    expect(screen.getByTestId("tarjeta-cliente-nombre")).toHaveTextContent("Sin nombre");
  });

  it("en boleta, un DNI se toma al confirmar con Enter; el nombre se escribe en los campos que se despliegan, y viaja", async () => {
    const fetch = stubConPadron();
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} tipo="boleta" />);
    expect(campoCliente()).toHaveAccessibleName("Comprador");

    escribirCliente("12345678");
    // Con 8 dígitos todavía puede ser el comienzo de un RUC: no se toma solo.
    expect(screen.queryByTestId("tarjeta-cliente")).toBeNull();
    fireEvent.keyDown(campoCliente(), { key: "Enter" });

    expect(screen.getByTestId("tarjeta-cliente-documento")).toHaveTextContent("DNI 12345678");
    fireEvent.change(screen.getByLabelText("Nombre del comprador"), { target: { value: "Juan Pérez" } });
    expect(screen.getByTestId("tarjeta-cliente-nombre")).toHaveTextContent("Juan Pérez");
    // Un DNI no se consulta en el padrón.
    expect(fetch.mock.calls.some(([url]) => String(url).includes("/consultas/"))).toBe(false);

    conUnaLinea();
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));
    await waitFor(() => expect(emitidas(fetch)).toHaveLength(1));
    expect(emitidas(fetch)[0].cliente).toEqual({ tipo_doc: "1", num_doc: "12345678", razon_social: "Juan Pérez" });
  });

  it("en boleta, seguir escribiendo después de 8 dígitos llega a un RUC, que se consulta", async () => {
    stubConPadron();
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} tipo="boleta" />);

    for (let i = 1; i <= 11; i++) fireEvent.change(campoCliente(), { target: { value: "20100066603".slice(0, i) } });

    expect(screen.getByTestId("tarjeta-cliente-documento")).toHaveTextContent("RUC 20100066603");
    await waitFor(() => expect(screen.getByTestId("tarjeta-cliente-nombre")).toHaveTextContent("TIENDAS DEL SUR S.A.C."));
  });

  it("elegir un guardado muestra su tarjeta, sin desplegar los campos", async () => {
    stubConPadron([PACIFICO]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    await elegir("pacif", "PESQUERA");

    expect(screen.getByTestId("tarjeta-cliente-nombre")).toHaveTextContent("PESQUERA PACÍFICO S.A.C.");
    expect(screen.getByTestId("tarjeta-cliente-documento")).toHaveTextContent("RUC 20601234565");
    expect(screen.getByTestId("tarjeta-cliente-direccion")).toHaveTextContent("Av. Argentina 2450, Callao");
    expect(screen.queryByLabelText("Razón social")).toBeNull();
  });

  it("«Cambiar» vuelve al campo vacío y suelta al cliente: así la factura no se emite", async () => {
    const fetch = stubConPadron([PACIFICO]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    await elegir("pacif", "PESQUERA");

    fireEvent.click(screen.getByRole("button", { name: "Cambiar" }));

    expect(screen.queryByTestId("tarjeta-cliente")).toBeNull();
    expect(campoCliente().value).toBe("");
    expect(campoCliente()).toHaveFocus();
    conUnaLinea();
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    expect(await screen.findByText(/Falta el cliente/)).toBeInTheDocument();
    expect(emitidas(fetch)).toHaveLength(0);
  });

  it("«Editar datos» despliega los campos de siempre y lo corregido es lo que viaja", async () => {
    const fetch = stubConPadron([PACIFICO]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    await elegir("pacif", "PESQUERA");

    const editar = screen.getByRole("button", { name: "Editar datos" });
    expect(editar).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(editar);
    expect(editar).toHaveAttribute("aria-expanded", "true");
    expect((screen.getByLabelText("RUC") as HTMLInputElement).value).toBe("20601234565");
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "PESQUERA PACIFICO SAC" } });
    fireEvent.change(screen.getByLabelText(/Dirección/), { target: { value: "Jr. Nuevo 1" } });
    expect(screen.getByTestId("tarjeta-cliente-nombre")).toHaveTextContent("PESQUERA PACIFICO SAC");

    conUnaLinea();
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emitidas(fetch)).toHaveLength(1));
    expect(emitidas(fetch)[0].cliente).toEqual({ tipo_doc: "6", num_doc: "20601234565", razon_social: "PESQUERA PACIFICO SAC", direccion: "Jr. Nuevo 1" });
  });

  it("la boleta con el campo vacío sale a «Clientes varios», sin documento", async () => {
    const fetch = stubConPadron();
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} tipo="boleta" />);
    expect(screen.getByText(/Vacío, la boleta va a «Clientes varios»/)).toBeInTheDocument();

    conUnaLinea();
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));

    await waitFor(() => expect(emitidas(fetch)).toHaveLength(1));
    expect(emitidas(fetch)[0].cliente).toEqual({ tipo_doc: "-", num_doc: "-", razon_social: "CLIENTES VARIOS" });
  });

  it("la boleta vacía por encima del tope avisa y no emite", async () => {
    const fetch = stubConPadron();
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} tipo="boleta" />);
    fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: "Televisor" } });
    fireEvent.change(screen.getByLabelText(/Precio unit\./), { target: { value: "1000" } });
    fireEvent.blur(screen.getByLabelText(/Precio unit\./));

    expect(await screen.findByTestId("aviso-sin-documento")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));
    expect(await screen.findByText(/identifica al comprador con su documento/)).toBeInTheDocument();
    expect(emitidas(fetch)).toHaveLength(0);
  });

  it("la factura sin cliente no se emite y dice qué falta", async () => {
    const fetch = stubConPadron();
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conUnaLinea();

    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));

    expect(await screen.findByText(/Falta el cliente/)).toBeInTheDocument();
    expect(emitidas(fetch)).toHaveLength(0);
  });

  it("un nombre escrito sin elegirlo de la lista no se emite como si nada", async () => {
    const fetch = stubConPadron();
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} tipo="boleta" />);
    escribirCliente("María Quispe");
    conUnaLinea();

    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));

    expect(await screen.findByText(/Elige un cliente de la lista/)).toBeInTheDocument();
    expect(emitidas(fetch)).toHaveLength(0);
  });

  it("«Ingresar a mano» en boleta despliega el documento (DNI) y otros, como el carné de extranjería", async () => {
    const fetch = stubConPadron();
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} tipo="boleta" />);

    fireEvent.click(screen.getByRole("button", { name: "Ingresar a mano" }));
    await elegirDocumento("4");
    fireEvent.change(screen.getByLabelText("Número de documento"), { target: { value: "001234567" } });
    fireEvent.change(screen.getByLabelText("Nombre del comprador"), { target: { value: "Ana Silva" } });
    expect(screen.getByTestId("tarjeta-cliente-documento")).toHaveTextContent("Carné de extranjería 001234567");

    conUnaLinea();
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));
    await waitFor(() => expect(emitidas(fetch)).toHaveLength(1));
    expect(emitidas(fetch)[0].cliente).toEqual({ tipo_doc: "4", num_doc: "001234567", razon_social: "Ana Silva" });
  });
});
