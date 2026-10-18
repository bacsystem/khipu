import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { hoyLima, sumarDias } from "@/lib/formato";
import { NuevoComprobanteForm } from "./nuevo-comprobante-form";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

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
function stubConCalculo(totales: Record<string, number>, rechazo?: { codigo: string; mensaje: string }) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>((url) => {
    const esCalculo = String(url).includes("/facturas/calcular");
    if (esCalculo && rechazo) return Promise.resolve(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: rechazo.mensaje, codigo: rechazo.codigo, errores: null }), { status: 422 }));
    const datos = esCalculo ? { totales, total_en_soles: null } : { id: "c-1" };
    return Promise.resolve(new Response(JSON.stringify({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }), { status: esCalculo ? 200 : 201 }));
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

function linea(descripcion: string, precio: string) {
  fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: descripcion } });
  fireEvent.change(screen.getByLabelText("Precio unit. (con IGV)"), { target: { value: precio } });
  fireEvent.blur(screen.getByLabelText("Precio unit. (con IGV)"));
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
    const unidad = screen.getByLabelText("Unidad de medida");
    await waitFor(() => expect(unidad.querySelectorAll("option")).toHaveLength(2));
    expect([...unidad.querySelectorAll("option")].map((o) => o.getAttribute("value"))).toEqual(["NIU", "ZZ"]);
  });
});

describe("NuevoComprobanteForm — totales del backend y redondeo (#405, #358)", () => {
  it("el pie muestra los importes que calculó el backend, con el redondeo", async () => {
    const fetch = stubConCalculo({ gravado: 100, exonerado: 0, inafecto: 0, igv: 18, redondeo: -0.05, total: 117.95 });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    linea("Servicio", "118");
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
    fireEvent.change(screen.getByLabelText("Redondeo"), { target: { value: "-0.05" } });

    await waitFor(() => expect(screen.getByTestId("calculo-rechazado")).toHaveTextContent("3303 - El redondeo deja el importe total en negativo"));
  });

  it("el redondeo viaja en la emisión y un valor fuera de rango no se envía", async () => {
    const fetch = stubConCalculo({ gravado: 100, exonerado: 0, inafecto: 0, igv: 18, total: 117.95 });
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20554198211" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "Cliente SAC" } });
    linea("Servicio", "118");

    fireEvent.change(screen.getByLabelText("Redondeo"), { target: { value: "1.5" } });
    expect(screen.getByText(/no puede superar 1\.00/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/no puede superar 1\.00/));
    expect(emisiones(fetch)).toHaveLength(0);

    fireEvent.change(screen.getByLabelText("Redondeo"), { target: { value: "-0.05" } });
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
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
    fireEvent.change(screen.getByLabelText("Cargo del ítem: tipo"), { target: { value: "monto" } });
    fireEvent.change(screen.getByLabelText("Cargo del ítem: valor"), { target: { value: "25" } });
    // El descuento reduce la base del IGV por defecto (00); el cargo paga IGV (47). Cada uno se puede cambiar al otro código.
    const reduceBase = screen.getByLabelText("Reduce la base del IGV");
    const pagaIgv = screen.getByLabelText("Paga IGV");
    expect(reduceBase).toBeChecked();
    expect(pagaIgv).toBeChecked();
    fireEvent.click(reduceBase);   // descuento 01: no toca el IGV
    fireEvent.click(pagaIgv);      // cargo 48: sin IGV

    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].items[0]).toMatchObject({ descuento: { porcentaje: 10, afecta_base_igv: false }, cargos: [{ monto: 25, afecta_base_igv: false }] });
  });

  it("el descuento global y el recargo al consumo van en el cuerpo (02 y 46), y el cálculo los recibe", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();
    fireEvent.click(screen.getByText("Más opciones del comprobante"));

    fireEvent.change(screen.getByLabelText("Descuento global: tipo"), { target: { value: "monto" } });
    fireEvent.change(screen.getByLabelText("Descuento global: valor"), { target: { value: "5.50" } });
    fireEvent.change(screen.getByLabelText("Cargo global: valor"), { target: { value: "10" } });
    fireEvent.click(screen.getByLabelText("Recargo al consumo (restaurantes, sin IGV)"));

    await waitFor(() => expect(calculos(fetch).at(-1)).toMatchObject({ descuento_global: { monto: 5.5, afecta_base_igv: true }, cargos: [{ porcentaje: 10, afecta_base_igv: false, motivo: "recargo_consumo" }] }));
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ descuento_global: { monto: 5.5, afecta_base_igv: true }, cargos: [{ porcentaje: 10, afecta_base_igv: false, motivo: "recargo_consumo" }] });
  });

  it("un valor que el dominio rechazaría se marca en su campo y no deja emitir ni calcular", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();
    fireEvent.click(screen.getByText("Más opciones del comprobante"));
    fireEvent.change(screen.getByLabelText("Descuento global: valor"), { target: { value: "150" } });

    expect(screen.getByText("El porcentaje debe ser menor que 100.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
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

  it("ofrece las afectaciones agrupadas del catálogo 07: onerosas, IVAP y las gratuitas", () => {
    stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    fireEvent.click(screen.getByRole("button", { name: "Más opciones del ítem 1" }));
    const select = screen.getByLabelText("Afectación del IGV");
    expect([...select.querySelectorAll("optgroup")].map((g) => g.getAttribute("label"))).toEqual(["Onerosas", "Gratuitas gravadas", "Gratuitas exoneradas", "Gratuitas inafectas"]);
    expect([...select.querySelectorAll("option")].map((o) => o.getAttribute("value"))).toEqual(["10", "20", "30", "17", "11", "12", "13", "14", "15", "16", "21", "31", "32", "33", "34", "35", "36", "37"]);
  });

  it("el ISC viaja con los campos de su sistema (01 tasa, 02 monto por unidad, 03 tasa y PVP)", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();

    fireEvent.change(screen.getByLabelText("Sistema del ISC"), { target: { value: "03" } });
    fireEvent.change(screen.getByLabelText("Tasa del ISC (%)"), { target: { value: "30" } });
    fireEvent.change(screen.getByLabelText("Precio de venta al público sugerido"), { target: { value: "3.50" } });
    await waitFor(() => expect(calculos(fetch).at(-1)?.items[0]).toMatchObject({ isc: { sistema: "03", tasa: 30, base_pvp: 3.5 } }));

    fireEvent.change(screen.getByLabelText("Sistema del ISC"), { target: { value: "02" } });
    expect(screen.queryByLabelText("Tasa del ISC (%)")).not.toBeInTheDocument();   // cambiar de sistema limpia lo del anterior
    fireEvent.change(screen.getByLabelText("Monto por unidad del ISC"), { target: { value: "2.25" } });
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].items[0].isc).toEqual({ sistema: "02", monto_unitario: 2.25 });
  });

  it("un ISC a medio escribir se marca y no deja emitir", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();
    fireEvent.change(screen.getByLabelText("Sistema del ISC"), { target: { value: "01" } });
    expect(screen.getByText("La tasa es un porcentaje mayor que cero, con hasta 5 decimales.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
    await waitFor(() => expect(screen.getAllByRole("alert").map((a) => a.textContent).join(" ")).toMatch(/Corrija los descuentos, cargos o tributos/));
    expect(emisiones(fetch)).toHaveLength(0);
  });

  it("las bolsas del ICBPER llevan NIU y viajan como bandera; con una cantidad fraccionaria avisa y no emite", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();
    fireEvent.change(screen.getByLabelText("Unidad de medida"), { target: { value: "ZZ" } });   // una unidad que no es NIU
    fireEvent.click(screen.getByLabelText("Bolsas de plástico (ICBPER)"));
    expect(screen.getByLabelText("Unidad de medida")).toHaveValue("NIU");                        // marcarlo la corrige

    fireEvent.change(screen.getByLabelText("Cantidad"), { target: { value: "2.5" } });
    expect(screen.getByTestId("tributos-incompatibles")).toHaveTextContent(/entero de 1 a 99999/);
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
    await waitFor(() => expect(screen.getAllByRole("alert").map((a) => a.textContent).join(" ")).toMatch(/entero de 1 a 99999/));
    expect(emisiones(fetch)).toHaveLength(0);

    fireEvent.change(screen.getByLabelText("Cantidad"), { target: { value: "3" } });
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].items[0]).toMatchObject({ icbper: true, unidad: "NIU", cantidad: 3 });
  });

  it("el IVAP es de todo el comprobante: mezclado con otra afectación avisa y no emite", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();
    fireEvent.change(screen.getByLabelText("Afectación del IGV"), { target: { value: "17" } });
    fireEvent.click(screen.getByRole("button", { name: "Agregar ítem" }));
    fireEvent.change(screen.getAllByLabelText("Descripción")[1], { target: { value: "Otro" } });
    fireEvent.change(screen.getAllByLabelText("Precio unit. (con IGV)")[1], { target: { value: "10" } });
    fireEvent.blur(screen.getAllByLabelText("Precio unit. (con IGV)")[1]);

    expect(screen.getByTestId("tributos-incompatibles")).toHaveTextContent(/todos sus ítems con IVAP/);
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
    await waitFor(() => expect(screen.getAllByRole("alert").map((a) => a.textContent).join(" ")).toMatch(/todos sus ítems con IVAP/));
    expect(emisiones(fetch)).toHaveLength(0);
  });

  it("una gratuita de las del catálogo 07 (15 bonificación) viaja con su código", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    conCliente();
    fireEvent.change(screen.getByLabelText("Afectación del IGV"), { target: { value: "15" } });
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
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
    fireEvent.change(screen.getByLabelText("Forma de pago"), { target: { value: "credito" } });
    // El neto pendiente por defecto es el total que calculó el backend: se espera a que llegue.
    await waitFor(() => expect(screen.getByLabelText("Neto pendiente")).toHaveAttribute("placeholder", "1180.00"));
    expect(fetch).toBeDefined();
  }

  it("el selector de forma de pago es solo de la factura: la boleta se emite al contado", () => {
    stubConCalculo(TOTALES);
    const { unmount } = render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    expect(screen.getByLabelText("Forma de pago")).toHaveValue("contado");
    unmount();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    expect(screen.queryByLabelText("Forma de pago")).not.toBeInTheDocument();
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
    fireEvent.click(screen.getByText("Más opciones del comprobante"));
    fireEvent.change(screen.getByLabelText(/^Fecha de vencimiento/), { target: { value: calculos(fetch).at(-1).forma_pago.cuotas[1].vencimiento } });
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
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
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
    await waitFor(() => expect(screen.getAllByRole("alert").map((a) => a.textContent).join(" ")).toMatch(/suman 1090\.00/));
    expect(emisiones(fetch)).toHaveLength(0);
  });

  it("sin cuotas no se puede emitir al crédito, y volver a contado manda la factura como siempre", async () => {
    const fetch = stubConCalculo(TOTALES);
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    await alCredito(fetch);
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
    await waitFor(() => expect(screen.getAllByRole("alert").map((a) => a.textContent).join(" ")).toMatch(/al menos una cuota/));
    expect(emisiones(fetch)).toHaveLength(0);

    fireEvent.change(screen.getByLabelText("Forma de pago"), { target: { value: "contado" } });
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0].forma_pago).toBeUndefined();
  });
});

describe("NuevoComprobanteForm — boleta (#20)", () => {
  it("sin documento manda «-» como tipo y número, con el nombre por defecto", async () => {
    const fetch = stubEmision();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);

    fireEvent.change(screen.getByLabelText("Documento del comprador"), { target: { value: "-" } });
    expect(screen.queryByLabelText("Número de documento")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Nombre del comprador")).toHaveValue("CLIENTES VARIOS");
    linea("Pan", "5");
    fireEvent.click(screen.getByRole("button", { name: "Emitir boleta" }));

    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ serie: "B001", cliente: { tipo_doc: "-", num_doc: "-", razon_social: "CLIENTES VARIOS" } });
  });

  /** #299: el catálogo 06 completo que acepta `Receptor.DOCUMENTOS_BOLETA`: antes el formulario solo ofrecía DNI, carné, pasaporte y RUC. */
  it("ofrece todos los documentos del catálogo 06 y manda el elegido con su número", async () => {
    const fetch = stubEmision();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);

    const select = screen.getByLabelText("Documento del comprador");
    expect([...select.querySelectorAll("option")].map((o) => o.getAttribute("value"))).toEqual(["1", "4", "7", "6", "0", "A", "B", "C", "D", "E", "F", "G", "H", "-"]);

    fireEvent.change(select, { target: { value: "A" } });
    fireEvent.change(screen.getByLabelText("Número de documento"), { target: { value: "CD-1234 " } });
    expect(screen.getByLabelText("Número de documento")).toHaveValue("CD-1234");   // sin espacios (4208)
    fireEvent.change(screen.getByLabelText("Nombre del comprador"), { target: { value: "Embajada de Chile" } });
    linea("Pan", "5");
    fireEvent.click(screen.getByRole("button", { name: "Emitir boleta" }));

    await waitFor(() => expect(emisiones(fetch)).toHaveLength(1));
    expect(emisiones(fetch)[0]).toMatchObject({ cliente: { tipo_doc: "A", num_doc: "CD-1234", razon_social: "Embajada de Chile" } });
  });

  it("sin documento por más de S/ 700 avisa y no emite: SUNAT exige identificar al comprador", async () => {
    const fetch = stubEmision();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);

    fireEvent.change(screen.getByLabelText("Documento del comprador"), { target: { value: "-" } });
    linea("Televisor", "700.01");

    expect(screen.getByTestId("aviso-sin-documento")).toHaveTextContent("S/ 700.00");
    fireEvent.click(screen.getByRole("button", { name: "Emitir boleta" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/identifica al comprador/));
    expect(emisiones(fetch)).toHaveLength(0);
  });

  /** #303: `Receptor` cuenta el nombre sin los espacios de los extremos (2022); `minLength` sola dejaba pasar «  JP  » hasta el 422. */
  it("el nombre del comprador tiene al menos 3 caracteres sin contar los espacios de los extremos", () => {
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
    fireEvent.change(screen.getByLabelText("Documento del comprador"), { target: { value: "-" } });
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
    fireEvent.click(screen.getByRole("button", { name: "Emitir boleta" }));

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

    fireEvent.change(screen.getByLabelText("Documento del comprador"), { target: { value: "-" } });
    linea("Pan", "5");
    fireEvent.click(screen.getByRole("button", { name: "Emitir boleta" }));

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

    fireEvent.change(screen.getByLabelText("Documento del comprador"), { target: { value: "-" } });
    linea("Pan", "5");
    fireEvent.click(screen.getByRole("button", { name: "Emitir boleta" }));

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/Tu plan Emprende venció/));
    expect(screen.getByRole("link", { name: "Ver plan y consumo" })).toHaveAttribute("href", "/cuenta/plan");
  });

  /** #292: la boleta admite hasta 7 días atrás (del 6.º al 7.º va en el resumen diario); la factura, 3. */
  it("la fecha de la boleta llega a 7 días atrás y la de la factura a 3", () => {
    stubEmision();
    const hoy = hoyLima();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    expect(screen.getByLabelText("Fecha de emisión")).toHaveAttribute("min", sumarDias(hoy, -7));
    expect(screen.getByText("Máx. 7 días")).toBeInTheDocument();
    cleanup();

    render(<NuevoComprobanteForm tipo="factura" series={SERIES} tasaIgv={18} />);
    expect(screen.getByLabelText("Fecha de emisión")).toHaveAttribute("min", sumarDias(hoy, -3));
    expect(screen.getByText("Máx. 3 días")).toBeInTheDocument();
  });

  it("una boleta de 6 días atrás avisa que va en el resumen diario; una de 5, no", () => {
    stubEmision();
    const hoy = hoyLima();
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);

    fireEvent.change(screen.getByLabelText("Fecha de emisión"), { target: { value: sumarDias(hoy, -5) } });
    expect(screen.queryByTestId("aviso-resumen-diario")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Fecha de emisión"), { target: { value: sumarDias(hoy, -6) } });
    // Región viva (336-H2): un lector de pantalla anuncia el aviso al cambiar la fecha.
    expect(screen.getByTestId("aviso-resumen-diario")).toHaveAttribute("role", "status");
    expect(screen.getByTestId("aviso-resumen-diario")).toHaveTextContent(/resumen diario/);
  });

  it("la factura sigue pidiendo RUC y usa solo series F", () => {
    stubEmision();
    render(<NuevoComprobanteForm tipo="factura" series={SERIES} tasaIgv={18} />);
    expect(screen.getByLabelText("RUC")).toHaveAttribute("pattern", "[0-9]{11}");
    expect(screen.queryByRole("option", { name: /B001/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Emitir factura" })).toBeInTheDocument();
  });

  it("la razón social de la factura sigue la misma regla 2022, sin contar los espacios de los extremos", () => {
    render(<NuevoComprobanteForm tipo="factura" series={SERIES} tasaIgv={18} />);
    const razon = screen.getByLabelText("Razón social");
    fireEvent.change(razon, { target: { value: "  AB  " } });
    expect(razon).toBeInvalid();
    fireEvent.change(razon, { target: { value: "ABC" } });
    expect(razon).toBeValid();
  });
});
