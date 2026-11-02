import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { codigoDeOpcion, codigosDe, documentosOfrecidos, elegirCodigo, elegirDocumento, elegirEnCombobox, opcionesDeCombobox } from "@/test/combos-del-comprobante";
import { elegirFecha, fechaEsElegible } from "@/test/fecha-de-prueba";
import { elegirOpcion, opcionesDe } from "@/test/select-de-prueba";
import { afterEach, describe, expect, it, vi } from "vitest";
import { hoyLima, sumarDias } from "@/lib/formato";
import { NuevoComprobanteForm } from "./nuevo-comprobante-form";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));
// Varias pruebas eligen muchos combos del design system (#555), y cada uno abre y cierra su lista: con la suite entera en paralelo, los 5 s por defecto no alcanzan.
vi.setConfig({ testTimeout: 20_000 });

const SERIES = [
  { tipo: "01", serie: "F001", ultimo_numero: 2, activa: true, establecimiento: "0000" },
  { tipo: "03", serie: "B001", ultimo_numero: 9, activa: true, establecimiento: "0000" },
];

function stubEmision() {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(() =>
    Promise.resolve(new Response(JSON.stringify({ estado: "exito", datos: { id: "c-1" }, mensaje: null, codigo: null, errores: null }), { status: 201 })),
  );
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

function emisiones(fetch: ReturnType<typeof stubEmision>) {
  return fetch.mock.calls.filter(([url]) => String(url).includes("/facturas") && !String(url).includes("/calcular")).map(([, init]) => JSON.parse(String(init?.body)));
}

/** Los pedidos de cálculo (#405): el formulario le pregunta al backend los importes que va a emitir. */
function calculos(fetch: ReturnType<typeof stubEmision>) {
  return fetch.mock.calls.filter(([url]) => String(url).includes("/facturas/calcular")).map(([, init]) => JSON.parse(String(init?.body)));
}

/** Un backend que calcula (200 con totales) y emite (201). `totales` es lo que responde `/calcular`. */
const CATALOGO_54 = { id: "54", nombre: "Detracciones", columnas: ["Código", "Descripción"], entradas: [
  { codigo: "004", descripcion: "Recursos hidrobiológicos" }, { codigo: "019", descripcion: "Arrendamiento de bienes muebles" }, { codigo: "022", descripcion: "Otros servicios empresariales" },
  { codigo: "027", descripcion: "Servicio de transporte de carga" }, { codigo: "028", descripcion: "Transporte de pasajeros" }, { codigo: "037", descripcion: "Demás servicios gravados con el IGV" },
] };
const CATALOGO_03 = { id: "03", nombre: "Unidades de medida", columnas: ["Código", "Descripción"], entradas: [
  { codigo: "NIU", descripcion: "Unidad (bienes)", extra: { "Más usada": "Sí" } }, { codigo: "ZZ", descripcion: "Unidad (servicios)", extra: { "Más usada": "Sí" } },
] };
const CATALOGO_59 ={ id: "59", nombre: "Medios de pago", columnas: ["Código", "Descripción"], entradas: [{ codigo: "001", descripcion: "Depósito en cuenta" }, { codigo: "002", descripcion: "Giro" }] };

function stubConCalculo(totales: Record<string, number>, rechazo?: { codigo: string; mensaje: string }, datosExtra: Record<string, unknown> = {}) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>((url) => {
    if (String(url).includes("/catalogos/54")) return Promise.resolve(new Response(JSON.stringify({ estado: "exito", datos: CATALOGO_54, mensaje: null, codigo: null, errores: null })));
    if (String(url).includes("/catalogos/59")) return Promise.resolve(new Response(JSON.stringify({ estado: "exito", datos: CATALOGO_59, mensaje: null, codigo: null, errores: null })));
    if (String(url).includes("/catalogos/03")) return Promise.resolve(new Response(JSON.stringify({ estado: "exito", datos: CATALOGO_03, mensaje: null, codigo: null, errores: null })));
    const esCalculo = String(url).includes("/facturas/calcular");
    if (esCalculo && rechazo) return Promise.resolve(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: rechazo.mensaje, codigo: rechazo.codigo, errores: null }), { status: 422 }));
    const datos = esCalculo ? { totales, total_en_soles: null, ...datosExtra } : { id: "c-1" };
    return Promise.resolve(new Response(JSON.stringify({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }), { status: esCalculo ? 200 : 201 }));
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

function linea(descripcion: string, precio: string) {
  fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: descripcion } });
  fireEvent.change(screen.getByLabelText(/Precio unit\./), { target: { value: precio } });
  fireEvent.blur(screen.getByLabelText(/Precio unit\./));
}

/** #557: abre (o, si ya estaba activo, quita) un panel de la fila «Agregar:» con su botón «+». */
function agregar(nombre: string) {
  fireEvent.click(screen.getByRole("button", { name: nombre }));
}

/** Abre el panel del tipo de operación si todavía no se ve (una exportación ya lo tiene abierto: pulsar su «+» lo quitaría). */
function abrirOperacion() {
  if (!screen.queryByLabelText("Tipo de operación")) agregar("Tipo de operación");
}

/** Elige el tipo de operación por su código, abriendo antes su panel. */
async function elegirOperacion(codigo: string) {
  abrirOperacion();
  await elegirCodigo("Tipo de operación", codigo);
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("NuevoComprobanteForm — unidad de medida (#308)", () => {
  /** El catálogo 03 es la rec 20 completa (más de 2000 códigos): el selector ofrece las marcadas «Más usada», no la lista entera. */
  it("ofrece las unidades más usadas del catálogo 03, no las 2000 de la rec 20", async () => {
    const catalogo = {
      id: "03", nombre: "Unidades", columnas: ["Código", "Descripción", "Más usada"],
      entradas: [
        { codigo: "NIU", descripcion: "Unidad (bienes)", extra: { "Más usada": "Sí" } },
        { codigo: "ZZ", descripcion: "Unidad (servicios)", extra: { "Más usada": "Sí" } },
        { codigo: "E48", descripcion: "service unit", extra: {} },
      ],
    };
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response(JSON.stringify({ estado: "exito", datos: catalogo, mensaje: null, codigo: null, errores: null })))));
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);

    fireEvent.click(screen.getByRole("button", { name: "Más opciones del ítem 1" }));
    // Cada intento abre y cierra la lista del combo: con la suite entera en paralelo, el segundo por defecto de `waitFor` no alcanza para que llegue el catálogo.
    await waitFor(async () => expect((await opcionesDeCombobox("Unidad de medida")).map(codigoDeOpcion)).toEqual(["NIU", "ZZ"]), { timeout: 10_000 });
  });
});

describe("NuevoComprobanteForm — totales del backend y redondeo (#405, #358)", () => {
  it("el pie muestra los importes que calculó el backend, con el redondeo", async () => {
    const fetch = stubConCalculo({ gravado: 100, exonerado: 0, inafecto: 0, igv: 18, redondeo: -0.05, total: 117.95 });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    linea("Servicio", "118");
    agregar("Más opciones");
    fireEvent.change(screen.getByLabelText("Redondeo"), { target: { value: "-0.05" } });

    // La fila «Redondeo» solo la conoce el backend: aparece cuando respondió (el total local ya coincidía, así que se espera por ella).
    await waitFor(() => expect(screen.getByTestId("totales-previsualizados")).toHaveTextContent(/Redondeo\s*S\/ -0\.05/));
    expect(screen.getByTestId("total-a-pagar")).toHaveTextContent("S/ 117.95");
    // Para calcular va un comprador de relleno válido y el redondeo; lo demás es lo mismo que se emitirá.
    expect(calculos(fetch).at(-1)).toMatchObject({ serie: "F001", moneda: "PEN", redondeo: -0.05, cliente: { tipo_doc: "6", num_doc: "20131312955" }, items: [{ descripcion: "Servicio", precio_unitario: 118 }] });
  });

  it("si el backend rechaza el cuerpo, el motivo se ve y el pie sigue con el cálculo local", async () => {
    stubConCalculo({}, { codigo: "REDONDEO_INVALIDO", mensaje: "3303 - El redondeo deja el importe total en negativo (-0.03)" });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    linea("Servicio", "0.01");
    agregar("Más opciones");
    fireEvent.change(screen.getByLabelText("Redondeo"), { target: { value: "-0.05" } });

    await waitFor(() => expect(screen.getByTestId("calculo-rechazado")).toHaveTextContent("3303 - El redondeo deja el importe total en negativo"));
  });

  it("el redondeo viaja en la emisión y un valor fuera de rango no se envía", async () => {
    const fetch = stubConCalculo({ gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 117.95 });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    linea("Servicio", "118");

    agregar("Más opciones");
    fireEvent.change(screen.getByLabelText("Redondeo"), { target: { value: "1.5" } });
    expect(screen.getByText(/no puede superar 1\.00/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/no puede superar 1\.00/));
    expect(emisiones(fetch)).toHaveLength(0);

    fireEvent.change(screen.getByLabelText("Redondeo"), { target: { value: "-0.05" } });
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ redondeo: -0.05, cliente: { tipo_doc: "6", num_doc: "20554198211" } });
  });
});

describe("NuevoComprobanteForm — descuentos y cargos (#310)", () => {
  const TOTALES = { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 };

  function conCliente() {
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    linea("Servicio", "118");
  }

  it("el descuento y el cargo del ítem viajan con el código de afectación que se elige (00/01 y 47/48)", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();
    fireEvent.click(screen.getByRole("button", { name: "Más opciones del ítem 1" }));

    fireEvent.change(screen.getByLabelText("Descuento del ítem: valor"), { target: { value: "10" } });
    await elegirOpcion(screen.getByLabelText("Cargo del ítem: tipo"), "Monto");
    fireEvent.change(screen.getByLabelText("Cargo del ítem: valor"), { target: { value: "25" } });
    // El descuento reduce la base del IGV por defecto (00); el cargo paga IGV (47). Cada uno se puede cambiar al otro código.
    const reduceBase = screen.getByRole("checkbox", { name: "Reduce la base del IGV" });
    const pagaIgv = screen.getByRole("checkbox", { name: "Paga IGV" });
    expect(reduceBase).toBeChecked();
    expect(pagaIgv).toBeChecked();
    fireEvent.click(reduceBase);   // descuento 01: no toca el IGV
    fireEvent.click(pagaIgv);      // cargo 48: sin IGV

    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].items[0]).toMatchObject({ descuento: { porcentaje: 10, afecta_base_igv: false }, cargos: [{ monto: 25, afecta_base_igv: false }] });
  });

  it("el descuento global y el recargo al consumo van en el cuerpo (02 y 46), y el cálculo los recibe", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();
    agregar("Más opciones");

    await elegirOpcion(screen.getByLabelText("Descuento global: tipo"), "Monto");
    fireEvent.change(screen.getByLabelText("Descuento global: valor"), { target: { value: "5.50" } });
    fireEvent.change(screen.getByLabelText("Cargo global: valor"), { target: { value: "10" } });
    fireEvent.click(screen.getByRole("checkbox", { name: "Recargo al consumo (restaurantes, sin IGV)" }));

    await waitFor(() => expect(calculos(fetch).at(-1)).toMatchObject({ descuento_global: { monto: 5.5, afecta_base_igv: true }, cargos: [{ porcentaje: 10, afecta_base_igv: false, motivo: "recargo_consumo" }] }));
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ descuento_global: { monto: 5.5, afecta_base_igv: true }, cargos: [{ porcentaje: 10, afecta_base_igv: false, motivo: "recargo_consumo" }] });
  });

  it("un valor que el dominio rechazaría se marca en su campo y no deja emitir ni calcular", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();
    agregar("Más opciones");
    fireEvent.change(screen.getByLabelText("Descuento global: valor"), { target: { value: "150" } });

    expect(screen.getByText("El porcentaje debe ser menor que 100.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(screen.getAllByRole("alert").map((a) => a.textContent).join(" ")).toMatch(/Corrija los descuentos, cargos o tributos/));
    expect(emisiones(fetch)).toHaveLength(0);
    expect(calculos(fetch).filter((c) => c.descuento_global)).toHaveLength(0);
  });
});

describe("NuevoComprobanteForm — afectaciones, ISC, ICBPER e IVAP (#298)", () => {
  const TOTALES = { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 };

  function conCliente() {
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    linea("Cerveza", "118");
    fireEvent.click(screen.getByRole("button", { name: "Más opciones del ítem 1" }));
  }

  it("ofrece las afectaciones agrupadas del catálogo 07: onerosas, IVAP y las gratuitas", async () => {
    stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    fireEvent.click(screen.getByRole("button", { name: "Más opciones del ítem 1" }));
    const combo = screen.getByLabelText("Afectación del IGV");
    await act(async () => {
      fireEvent.click(combo);
    });
    const grupos = within(await screen.findByRole("listbox")).getAllByRole("group");
    expect(grupos.map((g) => g.getAttribute("aria-label") ?? document.getElementById(g.getAttribute("aria-labelledby") ?? "")?.textContent)).toEqual([
      "Onerosas",
      "Gratuitas gravadas",
      "Gratuitas exoneradas",
      "Gratuitas inafectas",
    ]);
    await act(async () => {
      fireEvent.keyDown(screen.getByRole("listbox"), { key: "Escape" });
    });
    await waitFor(() => expect(screen.queryByRole("listbox")).not.toBeInTheDocument());
    expect(await codigosDe("Afectación del IGV")).toEqual(["10", "20", "30", "17", "11", "12", "13", "14", "15", "16", "21", "31", "32", "33", "34", "35", "36", "37"]);
  });

  it("el ISC viaja con los campos de su sistema (01 tasa, 02 monto por unidad, 03 tasa y PVP)", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();

    await elegirCodigo("Sistema del ISC", "03");
    fireEvent.change(screen.getByLabelText("Tasa del ISC (%)"), { target: { value: "30" } });
    fireEvent.change(screen.getByLabelText("Precio de venta al público sugerido"), { target: { value: "3.50" } });
    await waitFor(() => expect(calculos(fetch).at(-1)?.items[0]).toMatchObject({ isc: { sistema: "03", tasa: 30, base_pvp: 3.5 } }));

    await elegirCodigo("Sistema del ISC", "02");
    expect(screen.queryByLabelText("Tasa del ISC (%)")).not.toBeInTheDocument();   // cambiar de sistema limpia lo del anterior
    fireEvent.change(screen.getByLabelText("Monto por unidad del ISC"), { target: { value: "2.25" } });
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].items[0].isc).toEqual({ sistema: "02", monto_unitario: 2.25 });
  });

  it("un ISC a medio escribir se marca y no deja emitir", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();
    await elegirCodigo("Sistema del ISC", "01");
    expect(screen.getByText("La tasa es un porcentaje mayor que cero, con hasta 5 decimales.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(screen.getAllByRole("alert").map((a) => a.textContent).join(" ")).toMatch(/Corrija los descuentos, cargos o tributos/));
    expect(emisiones(fetch)).toHaveLength(0);
  });

  it("las bolsas del ICBPER llevan NIU y viajan como bandera; con una cantidad fraccionaria avisa y no emite", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();
    await elegirEnCombobox("Unidad de medida", "ZZ", /^ZZ · /);                                 // una unidad que no es NIU
    expect(screen.getByLabelText("Unidad de medida")).toHaveValue("ZZ · Unidad (servicios)");
    fireEvent.click(screen.getByRole("checkbox", { name: "Bolsas de plástico (ICBPER)" }));
    expect(screen.getByLabelText("Unidad de medida")).toHaveValue("NIU · Unidad (bienes)");     // marcarlo la corrige

    fireEvent.change(screen.getByLabelText("Cantidad"), { target: { value: "2.5" } });
    expect(screen.getByTestId("tributos-incompatibles")).toHaveTextContent(/entero de 1 a 99999/);
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(screen.getAllByRole("alert").map((a) => a.textContent).join(" ")).toMatch(/entero de 1 a 99999/));
    expect(emisiones(fetch)).toHaveLength(0);

    fireEvent.change(screen.getByLabelText("Cantidad"), { target: { value: "3" } });
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].items[0]).toMatchObject({ icbper: true, unidad: "NIU", cantidad: 3 });
  });

  it("el IVAP es de todo el comprobante: mezclado con otra afectación avisa y no emite", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();
    await elegirCodigo("Afectación del IGV", "17");
    fireEvent.click(screen.getByRole("button", { name: "Agregar ítem" }));
    fireEvent.change(screen.getAllByLabelText("Descripción")[1], { target: { value: "Otro" } });
    fireEvent.change(screen.getAllByLabelText(/Precio unit\./)[1], { target: { value: "10" } });
    fireEvent.blur(screen.getAllByLabelText(/Precio unit\./)[1]);

    expect(screen.getByTestId("tributos-incompatibles")).toHaveTextContent(/todos sus ítems con IVAP/);
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(screen.getAllByRole("alert").map((a) => a.textContent).join(" ")).toMatch(/todos sus ítems con IVAP/));
    expect(emisiones(fetch)).toHaveLength(0);
  });

  it("una gratuita de las del catálogo 07 (15 bonificación) viaja con su código", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();
    await elegirCodigo("Afectación del IGV", "15");
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].items[0].tipo_afectacion_igv).toBe("15");
  });
});

describe("NuevoComprobanteForm — factura al crédito (#309)", () => {
  const TOTALES = { gravado: 1000, exonerado: 0, inafecto: 0, igv: 180, total: 1180 };

  async function alCredito(fetch: ReturnType<typeof stubConCalculo>) {
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    linea("Proyecto", "1180");
    agregar("Crédito");
    // El neto pendiente por defecto es el total que calculó el backend: se espera a que llegue.
    await waitFor(() => expect(screen.getByLabelText("Neto pendiente")).toHaveAttribute("placeholder", "1180.00"));
    expect(fetch).toBeDefined();
  }

  it("el crédito es solo de la factura y empieza al contado: la boleta se emite siempre al contado", async () => {
    stubConCalculo(TOTALES);
    const { unmount } = render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    expect(screen.getByRole("button", { name: "Crédito" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.queryByLabelText("Neto pendiente")).not.toBeInTheDocument();
    unmount();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    expect(screen.queryByRole("button", { name: "Crédito" })).not.toBeInTheDocument();
  });

  it("reparte el neto pendiente en cuotas iguales y manda la forma de pago al calcular y al emitir", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    await alCredito(fetch);

    fireEvent.click(screen.getByRole("button", { name: "Repartir" }));   // 2 cuotas iguales por defecto
    expect(screen.getByLabelText("Cuota 1: monto")).toHaveValue("590.00");
    expect(screen.getByLabelText("Cuota 2: monto")).toHaveValue("590.00");
    expect(screen.getByTestId("credito-resumen")).toHaveTextContent("Las 2 cuotas suman S/ 1,180.00 de S/ 1,180.00 pendientes.");

    await waitFor(() => expect(calculos(fetch).at(-1)?.forma_pago).toMatchObject({ tipo: "credito", monto_pendiente: 1180, cuotas: [{ monto: 590 }, { monto: 590 }] }));
    agregar("Más opciones");
    await elegirFecha(/^Fecha de vencimiento/, calculos(fetch).at(-1).forma_pago.cuotas[1].vencimiento);
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ forma_pago: { tipo: "credito", monto_pendiente: 1180, cuotas: [{ monto: 590 }, { monto: 590 }] } });
    expect(emisiones(fetch)[0].fecha_vencimiento).toBeTruthy();
  });

  it("unas cuotas que no suman el neto pendiente se marcan y no dejan emitir", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    await alCredito(fetch);
    fireEvent.click(screen.getByRole("button", { name: "Repartir" }));
    fireEvent.change(screen.getByLabelText("Cuota 2: monto"), { target: { value: "500" } });

    expect(screen.getByTestId("credito-resumen")).toHaveTextContent(/suman 1090\.00 y el neto pendiente es 1180\.00/);
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(screen.getAllByRole("alert").map((a) => a.textContent).join(" ")).toMatch(/suman 1090\.00/));
    expect(emisiones(fetch)).toHaveLength(0);
  });

  it("sin cuotas no se puede emitir al crédito, y volver a contado manda la factura como siempre", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    await alCredito(fetch);
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(screen.getAllByRole("alert").map((a) => a.textContent).join(" ")).toMatch(/al menos una cuota/));
    expect(emisiones(fetch)).toHaveLength(0);

    fireEvent.click(screen.getByRole("button", { name: "Quitar «Crédito»" }));
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].forma_pago).toBeUndefined();
  });
});

describe("NuevoComprobanteForm — exportación (#311)", () => {
  const TOTALES = { gravado: 0, exonerado: 0, inafecto: 0, igv: 0, total: 4500 };

  async function exportar(operacion = "0200") {
    await elegirOperacion(operacion);
    linea("Café verde en grano", "4500");
  }

  it("al elegir una exportación: afectación 40, moneda en dólares y un comprador del exterior sin RUC", async () => {
    stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    await elegirOperacion("0200");

    expect(screen.getByLabelText("Moneda")).toHaveTextContent("(USD)");
    fireEvent.click(screen.getByRole("button", { name: "Más opciones del ítem 1" }));
    expect(await codigosDe("Afectación del IGV")).toEqual(["40"]);
    expect(screen.getByLabelText("Afectación del IGV")).toHaveTextContent(/^40 · /);
    // El cliente se elige por su documento (catálogo 06) y su país; el RUC no vale en 0200 (2800).
    const documentos = await documentosOfrecidos();
    expect(documentos).toEqual(["1", "4", "7", "0", "A", "B", "C", "D", "E", "G"]);
    expect(screen.getByLabelText("País del cliente")).toBeInTheDocument();
    expect(screen.getByLabelText(/^Incoterm/)).toBeInTheDocument();
    expect(screen.queryByLabelText("País de uso del servicio")).not.toBeInTheDocument();   // solo 0201 y 0208
  });

  it("emite con el tipo de operación, el país del cliente, el incoterm y las líneas en 40", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    await exportar();
    fireEvent.change(screen.getByLabelText("Número de documento"), { target: { value: "US123456789" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "ACME IMPORTS LLC" } });
    await elegirEnCombobox("País del cliente", "Estados", "Estados Unidos");
    await elegirOpcion(screen.getByLabelText(/^Incoterm/), "FOB");

    await waitFor(() => expect(calculos(fetch).at(-1)).toMatchObject({ tipo_operacion: "0200", moneda: "USD", exportacion: { incoterm: "FOB" }, items: [{ tipo_afectacion_igv: "40" }] }));
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({
      tipo_operacion: "0200",
      moneda: "USD",
      cliente: { tipo_doc: "0", num_doc: "US123456789", razon_social: "ACME IMPORTS LLC", pais: "US" },
      exportacion: { incoterm: "FOB" },
      items: [{ tipo_afectacion_igv: "40" }],
    });
  });

  it("una factura de exportación sin país del cliente no sale", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    await exportar();
    fireEvent.change(screen.getByLabelText("Número de documento"), { target: { value: "US123456789" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "ACME IMPORTS LLC" } });
    expect(screen.getByTestId("error-exportacion")).toHaveTextContent(/país/);
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(screen.getAllByRole("alert").map((a) => a.textContent).join(" ")).toMatch(/lleva su país/));
    expect(emisiones(fetch)).toHaveLength(0);
  });

  it("los servicios 0201 y 0208 piden el país de uso, y lo mandan", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    await exportar("0201");
    fireEvent.change(screen.getByLabelText("Número de documento"), { target: { value: "US123456789" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "ACME IMPORTS LLC" } });
    await elegirEnCombobox("País del cliente", "Estados", "Estados Unidos");
    expect(screen.getByTestId("error-exportacion")).toHaveTextContent(/3098/);

    expect(await opcionesDeCombobox("País de uso del servicio")).not.toContain("Perú");
    await elegirEnCombobox("País de uso del servicio", "Chile");
    expect(screen.queryByTestId("error-exportacion")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ tipo_operacion: "0201", exportacion: { pais_uso: "CL" } });
  });

  it("volver a la venta interna restablece la afectación, la moneda y el cliente con RUC", async () => {
    stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    await exportar();
    await elegirOperacion("0101");

    expect(screen.getByLabelText("Moneda")).toHaveTextContent("(PEN)");
    expect(screen.getByLabelText("RUC")).toBeInTheDocument();
    expect(screen.queryByTestId("datos-exportacion")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Más opciones del ítem 1" }));
    expect(screen.getByLabelText("Afectación del IGV")).toHaveTextContent(/^10 · /);
  });

  it("la boleta también exporta (#296): comprador sin RUC, país opcional", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    await exportar();
    const documentos = await documentosOfrecidos();
    expect(documentos).not.toContain("6");
    expect(documentos).toContain("-");
    await elegirDocumento("7");
    fireEvent.change(screen.getByLabelText("Número de documento"), { target: { value: "US123456" } });
    fireEvent.change(screen.getByLabelText("Nombre del comprador"), { target: { value: "John Smith" } });
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ tipo_operacion: "0200", cliente: { tipo_doc: "7", num_doc: "US123456" } });
  });
});

describe("NuevoComprobanteForm — detracción (#288)", () => {
  const TOTALES = { gravado: 1000, exonerado: 0, inafecto: 0, igv: 180, total: 1180 };
  const CON_DETRACCION = { detraccion: { codigo_bien_servicio: "022", porcentaje: 12, monto: 142, cuenta_banco_nacion: "00-000-123456", medio_pago: "001" } };

  function conCliente(precio = "1180") {
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    linea("Servicio", precio);
  }

  it("el control es de la venta interna (factura o boleta, #297): la exportación no lo ofrece", async () => {
    stubConCalculo(TOTALES);
    const { unmount } = render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    expect(screen.getByRole("button", { name: "Detracción" })).toBeInTheDocument();
    await elegirOperacion("0200");
    expect(screen.queryByRole("button", { name: "Detracción" })).not.toBeInTheDocument();
    unmount();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    expect(screen.getByRole("button", { name: "Detracción" })).toBeInTheDocument();
  });

  it("al activarla pide el bien o servicio, sugiere el porcentaje, precarga la cuenta y fija el tipo de operación 1001", async () => {
    const fetch = stubConCalculo(TOTALES, undefined, CON_DETRACCION);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} cuentaDetracciones="00-000-123456" />);
    conCliente();
    fireEvent.click(screen.getByRole("button", { name: "Detracción" }));
    expect(screen.getByLabelText("Cuenta del Banco de la Nación")).toHaveValue("00-000-123456");
    await waitFor(async () => expect((await codigosDe("Bien o servicio sujeto")).length).toBeGreaterThan(0));
    // 004 y 027 llevan datos por ítem que el formulario no captura: no se ofrecen.
    expect(await opcionesDe(screen.getByLabelText("Bien o servicio sujeto"))).toEqual(["Seleccione", "019 · Arrendamiento de bienes muebles", "022 · Otros servicios empresariales", "028 · Transporte de pasajeros", "037 · Demás servicios gravados con el IGV"]);

    expect(screen.getByTestId("detraccion-resumen")).toHaveTextContent(/Elija el bien o servicio/);
    await elegirCodigo("Bien o servicio sujeto", "022");
    expect(screen.getByLabelText("Porcentaje")).toHaveValue("12");
    await waitFor(() => expect(calculos(fetch).at(-1)).toMatchObject({ tipo_operacion: "1001", detraccion: { codigo_bien_servicio: "022", porcentaje: 12, medio_pago: "001", cuenta_banco_nacion: "00-000-123456" } }));
    // El monto y el neto a cobrar son los del backend.
    await waitFor(() => expect(screen.getByTestId("detraccion-pie")).toHaveTextContent("Detracción S/ 142.00 · Neto a cobrar S/ 1,038.00"));
    expect(screen.getByTestId("detraccion-resumen")).toHaveTextContent("Se depositan S/ 142.00");

    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ tipo_operacion: "1001", detraccion: { codigo_bien_servicio: "022", porcentaje: 12 } });
  });

  it("el buscador del catálogo 54 filtra por código o nombre y no suelta el bien ya elegido", async () => {
    stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} cuentaDetracciones="00-000-123456" />);
    conCliente();
    fireEvent.click(screen.getByRole("button", { name: "Detracción" }));
    await waitFor(async () => expect((await codigosDe("Bien o servicio sujeto")).length).toBeGreaterThan(0));
    const valores = () => codigosDe("Bien o servicio sujeto");
    fireEvent.change(screen.getByLabelText("Buscar en el catálogo 54"), { target: { value: "arrend" } });
    expect(await valores()).toEqual(["019"]);
    expect(await opcionesDe(screen.getByLabelText("Bien o servicio sujeto"))).toContain("Seleccione");
    await elegirCodigo("Bien o servicio sujeto", "019");
    fireEvent.change(screen.getByLabelText("Buscar en el catálogo 54"), { target: { value: "037" } });
    expect(await valores()).toEqual(["019", "037"]);
  });

  it("al crédito, el neto pendiente por defecto es el total menos la detracción (en soles)", async () => {
    const fetch = stubConCalculo(TOTALES, undefined, CON_DETRACCION);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} cuentaDetracciones="00-000-123456" />);
    conCliente();
    fireEvent.click(screen.getByRole("button", { name: "Detracción" }));
    await waitFor(async () => expect((await codigosDe("Bien o servicio sujeto")).length).toBeGreaterThan(0));
    await elegirCodigo("Bien o servicio sujeto", "022");
    await waitFor(() => expect(screen.getByTestId("detraccion-pie")).toHaveTextContent("Detracción S/ 142.00"));
    agregar("Crédito");
    fireEvent.click(screen.getByRole("button", { name: "Repartir" }));
    // 1180.00 de total menos 142.00 de detracción: lo que queda por cobrar a plazos.
    await waitFor(() => expect(screen.getByTestId("credito-resumen")).toHaveTextContent("de S/ 1,038.00 pendientes"));
    await waitFor(() => expect(calculos(fetch).at(-1)?.forma_pago).toMatchObject({ tipo: "credito", monto_pendiente: 1038 }));
  });

  it("muestra junto al control lo que el backend rechaza de la detracción (DETRACCION_INVALIDA)", async () => {
    stubConCalculo(TOTALES, { codigo: "DETRACCION_INVALIDA", mensaje: "3129 - El tipo de operación 1003 exige el código de bien/servicio 028" });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} cuentaDetracciones="00-000-123456" />);
    conCliente();
    fireEvent.click(screen.getByRole("button", { name: "Detracción" }));
    await waitFor(async () => expect((await codigosDe("Bien o servicio sujeto")).length).toBeGreaterThan(0));
    await elegirCodigo("Bien o servicio sujeto", "022");
    await waitFor(() => expect(screen.getByTestId("calculo-rechazado")).toHaveTextContent(/3129/));
  });

  it("el transporte de pasajeros (028) sale como operación 1003", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} cuentaDetracciones="00-000-123456" />);
    conCliente();
    fireEvent.click(screen.getByRole("button", { name: "Detracción" }));
    await waitFor(async () => expect((await codigosDe("Bien o servicio sujeto")).length).toBeGreaterThan(0));
    await elegirCodigo("Bien o servicio sujeto", "028");
    fireEvent.change(screen.getByLabelText("Porcentaje"), { target: { value: "10" } });
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ tipo_operacion: "1003", detraccion: { codigo_bien_servicio: "028", porcentaje: 10 } });
  });

  it("sin cuenta de detracciones lo dice con el enlace a los datos fiscales y no deja emitir", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();
    fireEvent.click(screen.getByRole("button", { name: "Detracción" }));
    await waitFor(async () => expect((await codigosDe("Bien o servicio sujeto")).length).toBeGreaterThan(0));
    await elegirCodigo("Bien o servicio sujeto", "037");
    expect(screen.getByRole("link", { name: "configurarla" })).toHaveAttribute("href", "/empresa?seccion=datos");
    expect(screen.getByTestId("detraccion-resumen")).toHaveTextContent(/Falta la cuenta de detracciones/);
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(screen.getAllByRole("alert").map((a) => a.textContent).join(" ")).toMatch(/cuenta de detracciones/));
    expect(emisiones(fetch)).toHaveLength(0);
  });

  it("avisa, sin bloquear, que una factura de más de S/ 700 puede estar sujeta; con la detracción activa el aviso se va", async () => {
    stubConCalculo({ ...TOTALES, total: 1180 });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente("1180");
    await waitFor(() => expect(screen.getByTestId("aviso-detraccion")).toHaveTextContent(/pasa de S\/ 700\.00.*SPOT/));
    expect(screen.getByRole("button", { name: /^Emitir factura/ })).toBeEnabled();
    fireEvent.click(screen.getByRole("button", { name: "Detracción" }));
    expect(screen.queryByTestId("aviso-detraccion")).not.toBeInTheDocument();
  });

  it("en dólares pide el monto a depositar en soles (3208)", async () => {
    stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} cuentaDetracciones="00-000-123456" />);
    conCliente();
    await elegirCodigo("Moneda", "USD");
    fireEvent.click(screen.getByRole("button", { name: "Detracción" }));
    expect(screen.getByLabelText("Monto a depositar, en soles")).toBeInTheDocument();
  });
});

describe("NuevoComprobanteForm — NRUS, detracción y percepción en boleta y factura (#297)", () => {
  const TOTALES = { gravado: 1000, exonerado: 0, inafecto: 0, igv: 180, total: 1180 };
  const CON_PERCEPCION = { percepcion: { regimen: "51", descripcion: "Percepción venta interna - Tasa 2%", porcentaje: 2, base: 1180, monto: 23.6, total_con_percepcion: 1203.6 } };

  function conComprador() {
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    linea("Mercadería", "1180");
  }
  const opciones = async () => {
    abrirOperacion();
    return codigosDe("Tipo de operación");
  };

  it("el NRUS (0113) solo se ofrece en la boleta", async () => {
    stubConCalculo(TOTALES);
    const { unmount } = render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    expect(await opciones()).not.toContain("0113");
    unmount();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    expect(await opciones()).toContain("0113");
  });

  it("una boleta NRUS sale con la operación 0113, y con bolsas de plástico no se emite (3240)", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    await elegirDocumento("-");
    linea("Pan", "5");
    await elegirOperacion("0113");
    fireEvent.click(screen.getByRole("button", { name: "Más opciones del ítem 1" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "Bolsas de plástico (ICBPER)" }));
    await waitFor(() => expect(screen.getByTestId("tributos-incompatibles")).toHaveTextContent(/3240.*NRUS/));
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));
    expect(emisiones(fetch)).toHaveLength(0);

    fireEvent.click(screen.getByRole("checkbox", { name: "Bolsas de plástico (ICBPER)" }));
    await waitFor(() => expect(screen.queryByTestId("tributos-incompatibles")).not.toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ serie: "B001", tipo_operacion: "0113" });
  });

  it("la boleta lleva la detracción como la factura: la operación pasa a 1001", async () => {
    const fetch = stubConCalculo(TOTALES, undefined, { detraccion: { codigo_bien_servicio: "022", porcentaje: 12, monto: 142, cuenta_banco_nacion: "00-000-123456", medio_pago: "001" } });
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} cuentaDetracciones="00-000-123456" />);
    await elegirDocumento("-");
    linea("Servicio", "650");
    fireEvent.click(screen.getByRole("button", { name: "Detracción" }));
    await waitFor(async () => expect((await codigosDe("Bien o servicio sujeto")).length).toBeGreaterThan(0));
    await elegirCodigo("Bien o servicio sujeto", "022");
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ serie: "B001", tipo_operacion: "1001", detraccion: { codigo_bien_servicio: "022" } });
  });

  it("la boleta también lleva percepción: 2001 con el régimen, sin forma de pago, y el pie suma el monto", async () => {
    const fetch = stubConCalculo({ gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 }, undefined, { percepcion: { regimen: "51", descripcion: "Percepción venta interna - Tasa 2%", porcentaje: 2, base: 118, monto: 2.36, total_con_percepcion: 120.36 } });
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    await elegirDocumento("-");
    linea("Mercadería", "118");
    expect(screen.queryByRole("button", { name: "Crédito" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Percepción" }));
    await waitFor(() => expect(screen.getByTestId("percepcion-pie")).toHaveTextContent("Percepción S/ 2.36 · Total a cobrar S/ 120.36"));
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ serie: "B001", tipo_operacion: "2001", percepcion: { regimen: "51" } });
    expect(emisiones(fetch)[0]).not.toHaveProperty("forma_pago");
  });

  it("la percepción pide el régimen, fija la operación 2001 y el pie muestra su monto y el total a cobrar", async () => {
    const fetch = stubConCalculo(TOTALES, undefined, CON_PERCEPCION);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conComprador();
    fireEvent.click(screen.getByRole("button", { name: "Percepción" }));
    expect(await codigosDe("Régimen de percepción")).toEqual(["51", "52", "53"]);
    await waitFor(() => expect(calculos(fetch).at(-1)).toMatchObject({ tipo_operacion: "2001", percepcion: { regimen: "51" } }));
    await waitFor(() => expect(screen.getByTestId("percepcion-pie")).toHaveTextContent("Percepción S/ 23.60 · Total a cobrar S/ 1,203.60"));

    await elegirCodigo("Régimen de percepción", "52");
    fireEvent.change(screen.getByLabelText(/Base de la percepción/), { target: { value: "500" } });
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ tipo_operacion: "2001", percepcion: { regimen: "52", base: 500 } });
  });

  it("la percepción y la detracción se excluyen: activar una apaga la otra", async () => {
    stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} cuentaDetracciones="00-000-123456" />);
    conComprador();
    fireEvent.click(screen.getByRole("button", { name: "Percepción" }));
    expect(screen.getByTestId("percepcion-form")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Detracción" }));
    expect(screen.queryByTestId("percepcion-form")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Percepción" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Detracción" }));
    expect(screen.getByRole("button", { name: "Percepción" })).toHaveAttribute("aria-pressed", "false");
  });

  it("la percepción en dólares o al crédito lo dice y no deja emitir (2788, 3330)", async () => {
    const fetch = stubConCalculo(TOTALES, undefined, CON_PERCEPCION);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conComprador();
    fireEvent.click(screen.getByRole("button", { name: "Percepción" }));
    await elegirCodigo("Moneda", "USD");
    expect(screen.getByTestId("percepcion-resumen")).toHaveTextContent(/2788/);
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    expect(emisiones(fetch)).toHaveLength(0);
    await elegirCodigo("Moneda", "PEN");
    agregar("Crédito");
    expect(screen.getByTestId("percepcion-resumen")).toHaveTextContent(/3330/);
  });

  it("el control de percepción es de la venta interna: la exportación no lo ofrece", async () => {
    stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    expect(screen.getByRole("button", { name: "Percepción" })).toBeInTheDocument();
    await elegirOperacion("0200");
    expect(screen.queryByRole("button", { name: "Percepción" })).not.toBeInTheDocument();
  });
});

describe("NuevoComprobanteForm — monedas y tipo de cambio (#316)", () => {
  const TOTALES = { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 };

  function conComprador(precio = "118") {
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    linea("Servicio", precio);
  }

  it("ofrece las 17 monedas del catálogo 02 que admite el backend", async () => {
    stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    const monedas = await codigosDe("Moneda");
    expect(monedas).toHaveLength(17);
    expect(monedas).toContain("GBP");
    expect(monedas).toContain("PYG");
  });

  it("el tipo de cambio solo aparece fuera de soles y viaja como número; en soles no se manda", async () => {
    const fetch = stubConCalculo(TOTALES, undefined, { total_en_soles: 442.5 });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conComprador();
    expect(screen.queryByLabelText(/Tipo de cambio/)).not.toBeInTheDocument();
    await elegirCodigo("Moneda", "GBP");
    fireEvent.change(screen.getByLabelText(/Tipo de cambio/), { target: { value: "3.75" } });
    await waitFor(() => expect(calculos(fetch).at(-1)).toMatchObject({ moneda: "GBP", tipo_cambio: 3.75 }));
    await waitFor(() => expect(screen.getByTestId("total-en-soles")).toHaveTextContent("Equivale a S/ 442.50 al tipo de cambio 3.75"));
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ moneda: "GBP", tipo_cambio: 3.75 });

    // Al volver a soles el campo desaparece y el valor no se manda.
    await elegirCodigo("Moneda", "PEN");
    expect(screen.queryByLabelText(/Tipo de cambio/)).not.toBeInTheDocument();
    await waitFor(() => expect(calculos(fetch).at(-1)).not.toHaveProperty("tipo_cambio"));
  });

  it("cambiar de moneda vacía el tipo de cambio: el de 0.025 yenes no se queda para los dólares", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conComprador();
    await elegirCodigo("Moneda", "JPY");
    fireEvent.change(screen.getByLabelText(/Tipo de cambio/), { target: { value: "0.025" } });
    await elegirCodigo("Moneda", "USD");
    expect(screen.getByLabelText(/Tipo de cambio/)).toHaveValue("");
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ moneda: "USD" });
    expect(emisiones(fetch)[0]).not.toHaveProperty("tipo_cambio");
  });

  it("un tipo de cambio mal escrito lo dice y no deja emitir; vacío vale", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conComprador();
    await elegirCodigo("Moneda", "USD");
    fireEvent.change(screen.getByLabelText(/Tipo de cambio/), { target: { value: "3.7567" } });
    expect(screen.getByText(/soles por 1 USD: un número mayor que cero/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    expect(emisiones(fetch)).toHaveLength(0);
    fireEvent.change(screen.getByLabelText(/Tipo de cambio/), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).not.toHaveProperty("tipo_cambio");
  });

  it("una boleta en dólares sin documento: con tipo de cambio el tope de S/ 700 se mide en soles; sin él se pide el documento", async () => {
    const fetch = stubConCalculo({ gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 });
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    await elegirDocumento("-");
    linea("Servicio", "118");
    await elegirCodigo("Moneda", "USD");
    await waitFor(() => expect(screen.getByTestId("aviso-sin-documento")).toHaveTextContent(/no indica el tipo de cambio/));
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));
    expect(emisiones(fetch)).toHaveLength(0);

    // 118 USD × 3.5 = S/ 413.00 ≤ 700: pasa.
    fireEvent.change(screen.getByLabelText(/Tipo de cambio/), { target: { value: "3.5" } });
    await waitFor(() => expect(screen.queryByTestId("aviso-sin-documento")).not.toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ moneda: "USD", tipo_cambio: 3.5, cliente: { tipo_doc: "-" } });

    // 118 USD × 6 = S/ 708.00 > 700: vuelve a pedir el documento.
    fireEvent.change(screen.getByLabelText(/Tipo de cambio/), { target: { value: "6" } });
    await waitFor(() => expect(screen.getByTestId("aviso-sin-documento")).toBeInTheDocument());
  });
});

describe("NuevoComprobanteForm — retención del IGV (#313)", () => {
  const TOTALES = { gravado: 1000, exonerado: 0, inafecto: 0, igv: 180, total: 1180 };
  const CON_RETENCION = { retencion_igv: { porcentaje: 3, monto: 35.4, neto_cobrar: 1144.6 } };

  function conComprador() {
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    linea("Mercadería", "1180");
  }
  const marcar = () => fireEvent.click(screen.getByRole("button", { name: "Retención" }));

  it("es de la factura de venta interna: ni la boleta ni la exportación la ofrecen", async () => {
    stubConCalculo(TOTALES);
    const { unmount } = render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    expect(screen.getByRole("button", { name: "Retención" })).toBeInTheDocument();
    await elegirOperacion("0200");
    expect(screen.queryByRole("button", { name: "Retención" })).not.toBeInTheDocument();
    unmount();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    expect(screen.queryByRole("button", { name: "Retención" })).not.toBeInTheDocument();
  });

  it("arranca en 3 %, manda el porcentaje y el pie muestra la retención y el neto a cobrar del backend", async () => {
    const fetch = stubConCalculo(TOTALES, undefined, CON_RETENCION);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conComprador();
    marcar();
    expect(screen.getByLabelText("Porcentaje de retención")).toHaveValue("3");
    await waitFor(() => expect(calculos(fetch).at(-1)).toMatchObject({ retencion_igv: { porcentaje: 3 } }));
    await waitFor(() => expect(screen.getByTestId("retencion-pie")).toHaveTextContent("Retención S/ 35.40 · Neto a cobrar S/ 1,144.60"));
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ retencion_igv: { porcentaje: 3 } });
    expect(emisiones(fetch)[0]).not.toHaveProperty("tipo_operacion");
  });

  it("un porcentaje fuera de rango lo dice y no deja emitir", async () => {
    const fetch = stubConCalculo(TOTALES, undefined, CON_RETENCION);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conComprador();
    marcar();
    fireEvent.change(screen.getByLabelText("Porcentaje de retención"), { target: { value: "100" } });
    expect(screen.getByTestId("retencion-resumen")).toHaveTextContent(/mayor que 0 y menor que 100/);
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    expect(emisiones(fetch)).toHaveLength(0);
  });

  it("al crédito, el neto pendiente por defecto es el total menos la retención", async () => {
    const fetch = stubConCalculo(TOTALES, undefined, CON_RETENCION);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conComprador();
    marcar();
    await waitFor(() => expect(screen.getByTestId("retencion-pie")).toHaveTextContent("Retención S/ 35.40"));
    agregar("Crédito");
    fireEvent.click(screen.getByRole("button", { name: "Repartir" }));
    await waitFor(() => expect(screen.getByTestId("credito-resumen")).toHaveTextContent("de S/ 1,144.60 pendientes"));
    await waitFor(() => expect(calculos(fetch).at(-1)?.forma_pago).toMatchObject({ tipo: "credito", monto_pendiente: 1144.6 }));
  });

  it("se excluye con la percepción y con la detracción: activar una apaga la otra", async () => {
    stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} cuentaDetracciones="00-000-123456" />);
    conComprador();
    marcar();
    expect(screen.getByTestId("retencion-form")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Percepción" }));
    expect(screen.queryByTestId("retencion-form")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Retención" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Percepción" }));
    // Apagada la percepción, la retención no reaparece marcada: quedó apagada, no escondida.
    expect(screen.getByRole("button", { name: "Retención" })).toHaveAttribute("aria-pressed", "false");
    marcar();
    fireEvent.click(screen.getByRole("button", { name: "Detracción" }));
    expect(screen.queryByTestId("retencion-form")).not.toBeInTheDocument();
    // Apagada la detracción, la retención no reaparece marcada: quedó apagada, no escondida.
    fireEvent.click(screen.getByRole("button", { name: "Detracción" }));
    expect(screen.getByRole("button", { name: "Retención" })).toHaveAttribute("aria-pressed", "false");
  });
});

describe("NuevoComprobanteForm — menores de la certificación (#117)", () => {
  const TOTALES = { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 };
  const abrirLinea = () => fireEvent.click(screen.getByRole("button", { name: "Más opciones del ítem 1" }));
  const conRuc = () => {
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
  };

  it("la etiqueta del precio dice si lleva IGV según la afectación de la línea", async () => {
    stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    expect(screen.getByLabelText("Precio unit. (con IGV)")).toBeInTheDocument();
    abrirLinea();
    for (const [afectacion, etiqueta] of [["20", "Precio unit. (sin IGV)"], ["30", "Precio unit. (sin IGV)"], ["15", "Precio unit. (sin IGV)"], ["17", "Precio unit. (con IVAP)"], ["10", "Precio unit. (con IGV)"]] as const) {
      await elegirCodigo("Afectación del IGV", afectacion);
      expect(screen.getByLabelText(etiqueta), afectacion).toBeInTheDocument();
    }
  });

  it("una factura toda exonerada no muestra «IGV (18 %) S/ 0.00»; con una línea gravada, sí", async () => {
    stubConCalculo({ gravado: 0, exonerado: 100, inafecto: 0, igv: 0, total: 100 });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    const pie = () => screen.getByTestId("totales-previsualizados");
    expect(pie()).toHaveTextContent("IGV (18 %)"); // vacío: se muestra la tasa de la empresa
    conRuc();
    abrirLinea();
    await elegirCodigo("Afectación del IGV", "20");
    linea("Libro", "100");
    expect(pie()).not.toHaveTextContent("IGV");
    expect(pie()).toHaveTextContent("Exonerado");
    await elegirCodigo("Afectación del IGV", "10");
    expect(pie()).toHaveTextContent("IGV (18 %)");
  });

  it("la serie anuncia el último emitido (no un «siguiente» que otra pestaña pudo gastar) o que aún no emitió", async () => {
    stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={[{ tipo: "01", serie: "F001", ultimo_numero: 12, activa: true, establecimiento: "0000" }, { tipo: "01", serie: "F002", ultimo_numero: 0, activa: true, establecimiento: "0000" }]} tasaIgv={18} />);
    expect(await opcionesDe(screen.getByLabelText("Serie"))).toEqual(["F001 · último emitido N.º 12", "F002 · aún sin emitir"]);
    expect(screen.queryByText(/siguiente/i)).not.toBeInTheDocument();
  });

  it("IVAP o exportación mientras el backend no responde no se muestran como S/ 0.00: el importe dice que lo calcula khipu", async () => {
    // El cálculo queda pendiente a propósito.
    vi.stubGlobal("fetch", vi.fn<(url: string) => Promise<Response>>(() => new Promise<Response>(() => {})));
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conRuc();
    abrirLinea();
    await elegirCodigo("Afectación del IGV", "17");
    linea("Arroz", "104");
    expect(screen.getByTestId("total-a-pagar")).toHaveTextContent("—");
    expect(screen.getByTestId("importe-por-calcular")).toHaveTextContent("Lo calcula khipu");
  });

  it("con el importe del backend ya no dice «lo calcula khipu»", async () => {
    stubConCalculo({ gravado: 100, exonerado: 0, inafecto: 0, igv: 4, total: 104 });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conRuc();
    abrirLinea();
    await elegirCodigo("Afectación del IGV", "17");
    linea("Arroz", "104");
    await waitFor(() => expect(screen.getByTestId("total-a-pagar")).toHaveTextContent("S/ 104.00"));
    expect(screen.queryByTestId("importe-por-calcular")).not.toBeInTheDocument();
  });

  it("el encabezado de la columna de precio sigue a las líneas: igual afectación lo dice, mezcladas no afirma ninguna", async () => {
    stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    const encabezado = () => screen.getByTestId("encabezado-precio");
    expect(encabezado()).toHaveTextContent("Precio unit. (con IGV)");
    abrirLinea();
    await elegirCodigo("Afectación del IGV", "20");
    expect(encabezado()).toHaveTextContent("Precio unit. (sin IGV)");
    fireEvent.click(screen.getByRole("button", { name: "Agregar ítem" }));
    expect(encabezado().textContent).toBe("Precio unit.");
  });

  it("una exportación mientras el backend no responde no muestra filas de 0.00 ni «IGV (18 %)»: solo que lo calcula khipu", async () => {
    vi.stubGlobal("fetch", vi.fn<(url: string) => Promise<Response>>(() => new Promise<Response>(() => {})));
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conRuc();
    await elegirOperacion("0200");
    linea("Café", "100");
    const pie = screen.getByTestId("totales-previsualizados");
    expect(pie).not.toHaveTextContent("IGV (18 %)");
    expect(pie).not.toHaveTextContent("Gravado");
    expect(screen.getByTestId("total-a-pagar")).toHaveTextContent("—");
    expect(screen.getByTestId("importe-por-calcular")).toBeInTheDocument();
  });

  it("si la emisión vence la espera dice que el servidor tardó, no que se cortó la conexión, y sigue recomendando reemitir igual", async () => {
    const base = stubConCalculo(TOTALES);
    vi.stubGlobal("fetch", vi.fn<(url: string, init?: RequestInit) => Promise<Response>>((url, init) =>
      !String(url).includes("/calcular") && String(url).includes("/facturas") ? Promise.reject(new DOMException("timed out", "TimeoutError")) : base(url, init)));
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conRuc();
    linea("Servicio", "118");
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/El servidor tardó demasiado en responder mientras se emitía\. Vuelve a emitir sin cambiar nada/);
  });

  it("Enter en un campo con el formulario incompleto no emite ni queda mudo: muestra qué falta", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    const form = screen.getByTestId("form-nuevo-comprobante") as HTMLFormElement;
    const reportar = vi.spyOn(form, "reportValidity");
    fireEvent.keyDown(screen.getByLabelText("RUC"), { key: "Enter" });
    expect(reportar).toHaveBeenCalledTimes(1);
    expect(emisiones(fetch)).toHaveLength(0);
  });
});

describe("NuevoComprobanteForm — datos adicionales (#300)", () => {
  const TOTALES = { gravado: 1000, exonerado: 0, inafecto: 0, igv: 180, total: 1180 };

  function conComprador() {
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    linea("Mercadería", "1180");
  }
  const abrir = () => agregar("Referencias");

  it("está a pedido y, en la factura, ofrece orden de compra, guías, documentos y observaciones", async () => {
    stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    expect(screen.queryByTestId("extra-referencias")).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Orden de compra/)).not.toBeInTheDocument();
    abrir();
    expect(screen.getByLabelText(/Orden de compra/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Agregar guía" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Agregar documento" })).toBeInTheDocument();
    expect(screen.getByLabelText(/Observaciones/)).toBeInTheDocument();
  });

  it("lo escrito viaja en la emisión (no en el cálculo) y las guías se pueden agregar y quitar", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conComprador();
    await waitFor(() => expect(calculos(fetch).length).toBeGreaterThan(0));
    await new Promise((r) => setTimeout(r, 700));
    const antes = calculos(fetch).length;
    abrir();
    fireEvent.change(screen.getByLabelText(/Orden de compra/), { target: { value: "OC-2026-0457" } });
    fireEvent.click(screen.getByRole("button", { name: "Agregar guía" }));
    fireEvent.click(screen.getByRole("button", { name: "Agregar guía" }));
    fireEvent.change(screen.getByLabelText("Número de la guía 1"), { target: { value: "T001-123" } });
    await elegirCodigo("Tipo de la guía 2", "31");
    fireEvent.change(screen.getByLabelText("Número de la guía 2"), { target: { value: "V001-45" } });
    fireEvent.click(screen.getByRole("button", { name: "Quitar la guía 1" }));
    fireEvent.click(screen.getByRole("button", { name: "Agregar documento" }));
    await elegirCodigo("Tipo del documento 1", "05");
    fireEvent.change(screen.getByLabelText("Número del documento 1"), { target: { value: "SCOP-8841203" } });
    fireEvent.change(screen.getByLabelText(/Observaciones/), { target: { value: "Entrega en almacén central." } });
    // Escribir esto no cambia importes: ni pausa el cálculo ni pide otro. Pasado el rebote, el pie sigue siendo el del servidor y los pedidos no llevan los datos adicionales.
    await new Promise((r) => setTimeout(r, 700));
    expect(calculos(fetch)).toHaveLength(antes);

    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({
      orden_compra: "OC-2026-0457",
      guias: [{ tipo: "31", numero: "V001-45" }],
      documentos_relacionados: [{ tipo: "05", numero: "SCOP-8841203" }],
      observaciones: "Entrega en almacén central.",
    });
    // Los datos adicionales no cambian importes: el cálculo no los lleva.
    for (const c of calculos(fetch)) expect(c).not.toHaveProperty("orden_compra");
    for (const c of calculos(fetch)) expect(c).not.toHaveProperty("observaciones");
  });

  it("una guía a medias no apaga el cálculo: con un descuento global el total del servidor sigue en pantalla", async () => {
    stubConCalculo({ gravado: 1000, exonerado: 0, inafecto: 0, igv: 180, total: 1180 });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conComprador();
    await waitFor(() => expect(screen.getByTestId("total-a-pagar")).toHaveTextContent("S/ 1,180.00"));
    abrir();
    fireEvent.click(screen.getByRole("button", { name: "Agregar guía" }));
    await new Promise((r) => setTimeout(r, 500));
    expect(screen.getByTestId("total-a-pagar")).toHaveTextContent("S/ 1,180.00");
    expect(screen.getByTestId("referencias-error")).toBeInTheDocument();
  });

  it("la sección no se cierra sola al borrar lo escrito mientras se usa", async () => {
    stubConCalculo({ gravado: 1000, exonerado: 0, inafecto: 0, igv: 180, total: 1180 });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    abrir();
    expect(screen.getByTestId("extra-referencias")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Agregar guía" }));
    fireEvent.click(screen.getByRole("button", { name: "Quitar la guía 1" }));
    fireEvent.change(screen.getByLabelText(/Observaciones/), { target: { value: "x" } });
    fireEvent.change(screen.getByLabelText(/Observaciones/), { target: { value: "" } });
    expect(screen.getByTestId("extra-referencias")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Referencias" })).toHaveAttribute("aria-pressed", "true");
  });

  it("una guía con un número mal escrito o repetida lo dice y no deja emitir (4006, 2364)", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conComprador();
    abrir();
    fireEvent.click(screen.getByRole("button", { name: "Agregar guía" }));
    fireEvent.change(screen.getByLabelText("Número de la guía 1"), { target: { value: "123" } });
    expect(screen.getByTestId("referencias-error")).toHaveTextContent(/4006/);
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    expect(emisiones(fetch)).toHaveLength(0);

    fireEvent.change(screen.getByLabelText("Número de la guía 1"), { target: { value: "T001-1" } });
    fireEvent.click(screen.getByRole("button", { name: "Agregar guía" }));
    fireEvent.change(screen.getByLabelText("Número de la guía 2"), { target: { value: "T001-1" } });
    expect(screen.getByTestId("referencias-error")).toHaveTextContent(/2364/);
  });

  it("en la boleta no hay orden de compra y los documentos 06 y 07 no se ofrecen (4009)", async () => {
    stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    abrir();
    expect(screen.queryByLabelText(/Orden de compra/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Agregar documento" }));
    expect(await codigosDe("Tipo del documento 1")).toEqual(["04", "05", "08", "09", "99"]);
  });

  it("la boleta lleva sus guías y observaciones", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    await elegirDocumento("-");
    linea("Pan", "5");
    abrir();
    fireEvent.click(screen.getByRole("button", { name: "Agregar guía" }));
    fireEvent.change(screen.getByLabelText("Número de la guía 1"), { target: { value: "EG01-45" } });
    fireEvent.change(screen.getByLabelText(/Observaciones/), { target: { value: "Recoger en tienda" } });
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ serie: "B001", guias: [{ tipo: "09", numero: "EG01-45" }], observaciones: "Recoger en tienda" });
    expect(emisiones(fetch)[0]).not.toHaveProperty("orden_compra");
  });
});

describe("NuevoComprobanteForm — anticipos (#312)", () => {
  const TOTALES = { gravado: 1000, exonerado: 0, inafecto: 0, igv: 180, total: 1180 };
  const receptor = (num: string) => ({ tipo_doc: "6", num_doc: num, razon_social: "Cliente SAC", direccion: null });
  const anticipo = (n: number, extra: Record<string, unknown> = {}) => ({
    id: `f-${n}`, tipo: "01", serie: "F001", numero: n, fecha_emision: "2026-09-01", moneda: "PEN", estado_documento: "ACEPTADO", receptor: receptor("20554198211"),
    totales: { gravado: 500, exonerado: 0, inafecto: 0, igv: 90, total: 590 }, anticipos: null, ...extra,
  });

  /** El formulario más la lista de comprobantes que ve el editor de anticipos. */
  function stubConLista(lista: unknown[], extra: Record<string, unknown> = {}) {
    const base = stubConCalculo(TOTALES, undefined, extra);
    const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>((url, init) =>
      String(url).includes("/facturas?por_pagina=100")
        ? Promise.resolve(new Response(JSON.stringify({ estado: "exito", datos: lista, mensaje: null, codigo: null, errores: null })))
        : base(url, init));
    vi.stubGlobal("fetch", fetch);
    return base;
  }
  function conComprador(ruc = "20554198211") {
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: ruc } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    linea("Obra", "1180");
  }
  const abrir = () => agregar("Anticipos");

  it("lista los anticipos aceptados del cliente, con lo que queda por descontar, y propone descontarlo todo", async () => {
    stubConLista([anticipo(1), anticipo(2, { receptor: receptor("20100066603") }), anticipo(3, { estado_documento: "RECHAZADO" })]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conComprador();
    abrir();
    await waitFor(() => expect(screen.getByRole("checkbox", { name: /F001-1/ })).toBeInTheDocument());
    expect(screen.queryByLabelText(/F001-2/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/F001-3/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("checkbox", { name: /F001-1/ }));
    expect(screen.getByLabelText("Valor a descontar de F001-1")).toHaveValue("500.00");
  });

  it("lo elegido viaja en el cálculo y en la emisión con serie, número, monto y afectación", async () => {
    const fetch = stubConLista([anticipo(1)], { anticipos: [{ comprobante: "F001-1", serie: "F001", numero: 1, monto: 300, importe_pagado: 354, afectacion: "gravado", codigo_sunat: "04", fecha_pago: null }] });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conComprador();
    abrir();
    await waitFor(() => expect(screen.getByRole("checkbox", { name: /F001-1/ })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("checkbox", { name: /F001-1/ }));
    fireEvent.change(screen.getByLabelText("Valor a descontar de F001-1"), { target: { value: "300" } });
    await waitFor(() => expect(calculos(fetch).at(-1)).toMatchObject({ anticipos: [{ serie: "F001", numero: 1, monto: 300, afectacion: "gravado" }] }));
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ anticipos: [{ serie: "F001", numero: 1, monto: 300, afectacion: "gravado" }] });
  });

  it("no deja descontar más de lo disponible y descuenta lo ya regularizado por otras facturas", async () => {
    const final = { id: "f-9", tipo: "01", serie: "F002", numero: 9, estado_documento: "ACEPTADO", moneda: "PEN", receptor: receptor("20554198211"),
      totales: { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 }, anticipos: [{ comprobante: "F001-1", serie: "F001", numero: 1, monto: 200, importe_pagado: 236, afectacion: "gravado", codigo_sunat: "04", fecha_pago: null }] };
    const fetch = stubConLista([anticipo(1), final]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conComprador();
    abrir();
    await waitFor(() => expect(screen.getByRole("checkbox", { name: /F001-1/ })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("checkbox", { name: /F001-1/ }));
    expect(screen.getByLabelText("Valor a descontar de F001-1")).toHaveValue("300.00");
    fireEvent.change(screen.getByLabelText("Valor a descontar de F001-1"), { target: { value: "301" } });
    expect(screen.getByTestId("anticipos-error")).toHaveTextContent(/solo tiene 300\.00 por descontar en gravado \(ya se regularizaron 200\.00\)/);
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    expect(emisiones(fetch)).toHaveLength(0);
  });

  it("un cliente sin anticipos lo dice, y sin documento pide el documento", async () => {
    stubConLista([anticipo(1, { receptor: receptor("20100066603") })]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    abrir();
    expect(screen.getByText(/Indique el documento del cliente/)).toBeInTheDocument();
    conComprador();
    await waitFor(() => expect(screen.getByTestId("anticipos-vacio")).toBeInTheDocument());
  });

  it("marcar la factura como anticipo no manda nada especial: sugiere la descripción y explica cómo se regulariza", async () => {
    const fetch = stubConLista([]);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conComprador();
    fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: "" } });
    abrir();
    fireEvent.click(screen.getByRole("checkbox", { name: "Esta factura es un anticipo" }));
    expect(screen.getByLabelText("Descripción")).toHaveValue("Anticipo por ");
    expect(screen.getByTestId("anticipo-ayuda")).toHaveTextContent(/Descontar anticipos/);
    expect(screen.queryByTestId("anticipos-form")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: "Anticipo por obra" } });
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).not.toHaveProperty("anticipos");
  });

  it("la boleta también descuenta anticipos de su comprador", async () => {
    stubConLista([anticipo(1, { tipo: "03", serie: "B001", receptor: { tipo_doc: "1", num_doc: "12345678", razon_social: "Juan", direccion: null } })]);
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    fireEvent.change(screen.getByLabelText("Número de documento"), { target: { value: "12345678" } });
    fireEvent.change(screen.getByLabelText("Nombre del comprador"), { target: { value: "Juan Pérez" } });
    linea("Pan", "5");
    abrir();
    await waitFor(() => expect(screen.getByRole("checkbox", { name: /B001-1/ })).toBeInTheDocument());
  });
});

describe("NuevoComprobanteForm — boleta (#20)", () => {
  it("sin documento manda «-» como tipo y número, con el nombre por defecto", async () => {
    const fetch = stubEmision();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);

    await elegirDocumento("-");
    expect(screen.queryByLabelText("Número de documento")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Nombre del comprador")).toHaveValue("CLIENTES VARIOS");
    linea("Pan", "5");
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));

    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ serie: "B001", cliente: { tipo_doc: "-", num_doc: "-", razon_social: "CLIENTES VARIOS" } });
  });

  /** #299: el catálogo 06 completo que acepta `Receptor.DOCUMENTOS_BOLETA`: antes el formulario solo ofrecía DNI, carné, pasaporte y RUC. */
  it("ofrece todos los documentos del catálogo 06 y manda el elegido con su número", async () => {
    const fetch = stubEmision();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);

    expect(await documentosOfrecidos()).toEqual(["1", "4", "7", "6", "0", "A", "B", "C", "D", "E", "F", "G", "H", "-"]);

    await elegirDocumento("A");
    fireEvent.change(screen.getByLabelText("Número de documento"), { target: { value: "CD-1234 " } });
    expect(screen.getByLabelText("Número de documento")).toHaveValue("CD-1234");   // sin espacios (4208)
    fireEvent.change(screen.getByLabelText("Nombre del comprador"), { target: { value: "Embajada de Chile" } });
    linea("Pan", "5");
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));

    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ cliente: { tipo_doc: "A", num_doc: "CD-1234", razon_social: "Embajada de Chile" } });
  });

  it("sin documento por más de S/ 700 avisa y no emite: SUNAT exige identificar al comprador", async () => {
    const fetch = stubEmision();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);

    await elegirDocumento("-");
    linea("Televisor", "700.01");

    expect(screen.getByTestId("aviso-sin-documento")).toHaveTextContent("S/ 700.00");
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/identifica al comprador/));
    expect(emisiones(fetch)).toHaveLength(0);
  });

  /** #303: `Receptor` cuenta el nombre sin los espacios de los extremos (2022); `minLength` sola dejaba pasar «  JP  » hasta el 422. */
  it("el nombre del comprador tiene al menos 3 caracteres sin contar los espacios de los extremos", async () => {
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    const nombre = screen.getByLabelText("Nombre del comprador");

    for (const corto of ["JP", "  JP  ", "   "]) {
      fireEvent.change(nombre, { target: { value: corto } });
      expect(nombre, corto).toBeInvalid();
    }
    // 347-H3: el patrón no rechaza nombres que el dominio admite: 3 exactos, puntos, acentos y ñ, espacios internos, hasta 1500.
    for (const valido of [" Ana ", "ANA", "A.B", "Ñoño Peña", "José  Pérez", "Á".repeat(1500)]) {
      fireEvent.change(nombre, { target: { value: valido } });
      expect(nombre, valido.slice(0, 20)).toBeValid();
    }

    // Sin documento el nombre propuesto cumple la regla.
    fireEvent.change(nombre, { target: { value: "" } });
    await elegirDocumento("-");
    expect(screen.getByLabelText("Nombre del comprador")).toHaveValue("CLIENTES VARIOS");
    expect(screen.getByLabelText("Nombre del comprador")).toBeValid();
  });

  it("con DNI manda el tipo 1 y exige 8 dígitos", async () => {
    const fetch = stubEmision();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);

    const numero = screen.getByLabelText("Número de documento");
    expect(numero).toHaveAttribute("pattern", "[0-9]{8}");
    fireEvent.change(numero, { target: { value: "12345678" } });
    fireEvent.change(screen.getByLabelText("Nombre del comprador"), { target: { value: "Juan Pérez" } });
    linea("Pan", "5");
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));

    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].cliente).toMatchObject({ tipo_doc: "1", num_doc: "12345678", razon_social: "Juan Pérez" });
  });

  /**
   * 328-H2: el diálogo avisa del tope con lo aceptado, pero el backend también cuenta lo que está en camino. Si la emisión llega igual al tope (429
   * LIMITE_PLAN), el error lleva a Plan y consumo en vez de dejar solo el texto.
   */
  it("si el backend responde LIMITE_PLAN muestra su mensaje y el enlace a Plan y consumo", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        Promise.resolve(
          new Response(
            JSON.stringify({ estado: "error", datos: null, codigo: "LIMITE_PLAN", mensaje: "Llegaste al tope de 30 documentos de octubre de 2026 de tu plan Gratis", errores: null }),
            { status: 429 },
          ),
        ),
      ),
    );
    const cancelar = vi.fn();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} onCancelar={cancelar} />);

    await elegirDocumento("-");
    linea("Pan", "5");
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/Llegaste al tope de 30 documentos/));
    const enlace = screen.getByRole("link", { name: "Ver plan y consumo" });
    expect(enlace).toHaveAttribute("href", "/cuenta/plan");
    fireEvent.click(enlace);
    expect(cancelar).toHaveBeenCalled();
  });

  /** #285: si el plan vence con el diálogo abierto (402 PLAN_VENCIDO), el error también lleva a Plan y consumo. */
  it("si el backend responde PLAN_VENCIDO muestra su mensaje y el enlace a Plan y consumo", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        Promise.resolve(
          new Response(
            JSON.stringify({ estado: "error", datos: null, codigo: "PLAN_VENCIDO", mensaje: "Tu plan Emprende venció y su gracia terminó el 8 de octubre", errores: null }),
            { status: 402 },
          ),
        ),
      ),
    );
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} onCancelar={vi.fn()} />);

    await elegirDocumento("-");
    linea("Pan", "5");
    fireEvent.click(screen.getByRole("button", { name: /^Emitir boleta/ }));

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/Tu plan Emprende venció/));
    expect(screen.getByRole("link", { name: "Ver plan y consumo" })).toHaveAttribute("href", "/cuenta/plan");
  });

  /** #292: la boleta admite hasta 7 días atrás (del 6.º al 7.º va en el resumen diario); la factura, 3. */
  it("la fecha de la boleta llega a 7 días atrás y la de la factura a 3", async () => {
    stubEmision();
    const hoy = hoyLima();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    expect(await fechaEsElegible("Fecha de emisión", sumarDias(hoy, -7))).toBe(true);
    expect(await fechaEsElegible("Fecha de emisión", sumarDias(hoy, -8))).toBe(false);
    expect(screen.getByText("Máx. 7 días")).toBeInTheDocument();
    cleanup();

    render(<NuevoComprobanteForm tipo="factura" series={SERIES} tasaIgv={18} />);
    expect(await fechaEsElegible("Fecha de emisión", sumarDias(hoy, -3))).toBe(true);
    expect(await fechaEsElegible("Fecha de emisión", sumarDias(hoy, -4))).toBe(false);
    expect(screen.getByText("Máx. 3 días")).toBeInTheDocument();
  });

  it("una boleta de 6 días atrás avisa que va en el resumen diario; una de 5, no", async () => {
    stubEmision();
    const hoy = hoyLima();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);

    await elegirFecha("Fecha de emisión", sumarDias(hoy, -5));
    expect(screen.queryByTestId("aviso-resumen-diario")).not.toBeInTheDocument();
    await elegirFecha("Fecha de emisión", sumarDias(hoy, -6));
    // Región viva (336-H2): un lector de pantalla anuncia el aviso al cambiar la fecha.
    expect(screen.getByTestId("aviso-resumen-diario")).toHaveAttribute("role", "status");
    expect(screen.getByTestId("aviso-resumen-diario")).toHaveTextContent(/resumen diario/);
  });

  it("la factura sigue pidiendo RUC y usa solo series F", async () => {
    stubEmision();
    render(<NuevoComprobanteForm tipo="factura" series={SERIES} tasaIgv={18} />);
    expect(screen.getByLabelText("RUC")).toHaveAttribute("pattern", "[0-9]{11}");
    expect(await opcionesDe(screen.getByLabelText("Serie"))).not.toContainEqual(expect.stringMatching(/B001/));
    expect(screen.getByRole("button", { name: /^Emitir factura/ })).toBeInTheDocument();
  });

  it("la razón social de la factura sigue la misma regla 2022, sin contar los espacios de los extremos", async () => {
    render(<NuevoComprobanteForm tipo="factura" series={SERIES} tasaIgv={18} />);
    const razon = screen.getByLabelText("Razón social");
    fireEvent.change(razon, { target: { value: "  AB  " } });
    expect(razon).toBeInvalid();
    fireEvent.change(razon, { target: { value: "ABC" } });
    expect(razon).toBeValid();
  });
});

describe("NuevoComprobanteForm — lo poco frecuente, a pedido (#557)", () => {
  const TOTALES = { gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 118 };
  const extrasOfrecidos = () => within(screen.getByTestId("extras")).getAllByRole("button").map((b) => b.textContent);

  function conCliente() {
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    linea("Servicio", "118");
  }

  it("la factura ofrece todos los «+»; la boleta, sin crédito ni retención; y todos empiezan cerrados", () => {
    stubConCalculo(TOTALES);
    const { unmount } = render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    expect(extrasOfrecidos()).toEqual(["Crédito", "Detracción", "Percepción", "Retención", "Tipo de operación", "Más opciones", "Anticipos", "Referencias", "Correo"]);
    for (const b of within(screen.getByTestId("extras")).getAllByRole("button")) expect(b, b.textContent ?? "").toHaveAttribute("aria-pressed", "false");
    expect(screen.queryByTestId(/^extra-/)).not.toBeInTheDocument();
    // Lo que antes estaba siempre a la vista ya no está.
    expect(screen.queryByLabelText("Tipo de operación")).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Correo del cliente/)).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: /detracción|percepción|retiene/ })).not.toBeInTheDocument();
    unmount();

    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    expect(extrasOfrecidos()).toEqual(["Detracción", "Percepción", "Tipo de operación", "Más opciones", "Anticipos", "Referencias", "Correo"]);
  });

  it("fuera de la venta interna no se ofrecen detracción, percepción ni retención; quitar el tipo de operación vuelve a 0101", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    await elegirOperacion("0200");
    expect(extrasOfrecidos()).toEqual(["Crédito", "Tipo de operación", "Más opciones", "Anticipos", "Referencias", "Correo"]);
    expect(screen.getByRole("button", { name: "Tipo de operación" })).toHaveAttribute("aria-pressed", "true");

    fireEvent.click(screen.getByRole("button", { name: "Quitar «Tipo de operación»" }));
    expect(screen.queryByLabelText("Tipo de operación")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Moneda")).toHaveTextContent("(PEN)");
    expect(screen.getByLabelText("RUC")).toBeInTheDocument();
    expect(extrasOfrecidos()).toContain("Detracción");
    // El foco vuelve a su «+», que sigue en la fila.
    expect(screen.getByRole("button", { name: "Tipo de operación" })).toHaveFocus();

    conCliente();
    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).not.toHaveProperty("tipo_operacion");
  });

  it("quitar un panel lo limpia: lo que tenía no se calcula ni se envía", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();

    agregar("Más opciones");
    fireEvent.change(screen.getByLabelText("Redondeo"), { target: { value: "-0.05" } });
    fireEvent.change(screen.getByLabelText("Descuento global: valor"), { target: { value: "5" } });
    await waitFor(() => expect(calculos(fetch).at(-1)).toMatchObject({ redondeo: -0.05, descuento_global: { porcentaje: 5 } }));
    agregar("Correo");
    fireEvent.change(screen.getByLabelText(/Correo del cliente/), { target: { value: "compras@cliente.pe" } });
    agregar("Referencias");
    fireEvent.change(screen.getByLabelText(/Observaciones/), { target: { value: "Entrega en almacén" } });
    agregar("Crédito");
    expect(screen.getByLabelText("Neto pendiente")).toBeInTheDocument();
    agregar("Retención");
    expect(screen.getByTestId("retencion-form")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Quitar «Más opciones»" }));
    fireEvent.click(screen.getByRole("button", { name: "Quitar «Correo»" }));
    fireEvent.click(screen.getByRole("button", { name: "Quitar «Referencias»" }));
    // Pulsar un «+» activo también lo quita.
    agregar("Crédito");
    agregar("Retención");
    expect(screen.queryByTestId(/^extra-/)).not.toBeInTheDocument();
    await waitFor(() => expect(calculos(fetch).at(-1)).not.toHaveProperty("redondeo"));

    // Al volver a abrirlos están vacíos.
    agregar("Más opciones");
    expect(screen.getByLabelText("Redondeo")).toHaveValue("");
    agregar("Correo");
    expect(screen.getByLabelText(/Correo del cliente/)).toHaveValue("");

    fireEvent.click(screen.getByRole("button", { name: /^Emitir factura/ }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    const emitida = emisiones(fetch)[0];
    for (const clave of ["redondeo", "descuento_global", "observaciones", "forma_pago", "retencion_igv"]) expect(emitida, clave).not.toHaveProperty(clave);
    expect(emitida.cliente).not.toHaveProperty("email");
  });

  it("quitar la detracción limpia el bien elegido: al volver a agregarla hay que elegirlo de nuevo", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} cuentaDetracciones="00-000-123456" />);
    conCliente();
    agregar("Detracción");
    await waitFor(async () => expect((await codigosDe("Bien o servicio sujeto")).length).toBeGreaterThan(0));
    await elegirCodigo("Bien o servicio sujeto", "022");
    await waitFor(() => expect(calculos(fetch).at(-1)).toMatchObject({ tipo_operacion: "1001" }));

    fireEvent.click(screen.getByRole("button", { name: "Quitar «Detracción»" }));
    expect(screen.getByRole("button", { name: "Detracción" })).toHaveFocus();
    await waitFor(() => expect(calculos(fetch).at(-1)).not.toHaveProperty("detraccion"));
    agregar("Detracción");
    expect(screen.getByTestId("detraccion-resumen")).toHaveTextContent(/Elija el bien o servicio/);
  });

  it("el aviso de detracción ofrece agregarla, y al hacerlo se va", async () => {
    stubConCalculo({ gravado: 1000, exonerado: 0, inafecto: 0, igv: 180, total: 1180 });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} cuentaDetracciones="00-000-123456" />);
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    linea("Servicio", "1180");
    await waitFor(() => expect(screen.getByTestId("aviso-detraccion")).toHaveTextContent(/pasa de S\/ 700\.00/));

    fireEvent.click(screen.getByRole("button", { name: "Agregar detracción" }));
    expect(screen.queryByTestId("aviso-detraccion")).not.toBeInTheDocument();
    expect(screen.getByTestId("extra-detraccion")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Detracción" })).toHaveAttribute("aria-pressed", "true");
  });

  it("el botón dice qué emite y cuánto, con el total del pie; sin líneas completas, el texto de siempre", async () => {
    stubConCalculo(TOTALES);
    const { unmount } = render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    expect(screen.getByRole("button", { name: "Emitir factura" })).toBeInTheDocument();
    linea("Servicio", "118");
    await waitFor(() => expect(screen.getByRole("button", { name: "Emitir factura · S/ 118.00" })).toBeInTheDocument());
    unmount();

    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    linea("Pan", "12.5");
    await waitFor(() => expect(screen.getByRole("button", { name: /^Emitir boleta · S\/ 12\.50$/ })).toBeInTheDocument());
  });

  it("con el importe por calcular (IVAP sin respuesta del backend) el botón no inventa un total", async () => {
    vi.stubGlobal("fetch", vi.fn<(url: string) => Promise<Response>>(() => new Promise<Response>(() => {})));
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    fireEvent.click(screen.getByRole("button", { name: "Más opciones del ítem 1" }));
    await elegirCodigo("Afectación del IGV", "17");
    linea("Arroz", "104");
    expect(screen.getByRole("button", { name: "Emitir factura" })).toBeInTheDocument();
  });

  it("una copia con datos abre sus paneles: operación de exportación, más opciones y correo", () => {
    stubConCalculo(TOTALES);
    const vacio = { modo: "porcentaje" as const, valor: "", afectaBase: true };
    const lineaCopiada = { descripcion: "Café", cantidad: 1, precioUnitario: 100, unidad: "NIU", tipoAfectacionIgv: "40", descuento: vacio, cargo: vacio, isc: { sistema: "" as const, tasa: "", montoUnitario: "", basePvp: "" }, icbper: false };
    render(
      <NuevoComprobanteForm
        series={SERIES}
        tasaIgv={18}
        plantilla={{
          tipo: "factura",
          moneda: "USD",
          operacion: "0200",
          cliente: { tipoDoc: "0", numDoc: "US123", razonSocial: "ACME LLC", direccion: "", pais: "US", email: "ap@acme.com" },
          lineas: [lineaCopiada],
          descuentoGlobal: vacio,
          cargoGlobal: vacio,
          redondeo: "-0.05",
          omitidos: [],
        }}
      />,
    );
    expect(screen.getByTestId("extra-operacion")).toBeInTheDocument();
    expect(screen.getByLabelText("Tipo de operación")).toHaveTextContent(/^0200 · /);
    expect(screen.getByTestId("extra-masOpciones")).toBeInTheDocument();
    expect(screen.getByLabelText("Redondeo")).toHaveValue("-0.05");
    expect(screen.getByTestId("extra-correo")).toBeInTheDocument();
    expect(screen.getByLabelText(/Correo del cliente/)).toHaveValue("ap@acme.com");
    // Lo que la copia no trae sigue cerrado.
    for (const id of ["credito", "anticipos", "referencias"]) expect(screen.queryByTestId(`extra-${id}`), id).not.toBeInTheDocument();
  });

  it("la serie, la fecha y la moneda van en la línea de arriba, con sus nombres y sus ayudas, junto al selector de tipo", () => {
    stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} selectorDeTipo={<span data-testid="selector-tipo" />} />);
    expect(screen.getByRole("combobox", { name: "Serie" })).toHaveAccessibleDescription("El correlativo lo asigna khipu al emitir");
    expect(screen.getByLabelText("Fecha de emisión")).toHaveAccessibleDescription("Máx. 3 días");
    expect(screen.getByRole("combobox", { name: "Moneda" })).toBeInTheDocument();
    expect(screen.getByTestId("selector-tipo").parentElement).toBe(screen.getByRole("combobox", { name: "Serie" }).parentElement);
  });
});
