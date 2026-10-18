import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Comprobante } from "@/lib/api/facturas";
import { NotaForm } from "./nota-form";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

const SERIES = [
  { tipo: "07", serie: "FC01", ultimo_numero: 0, activa: true, establecimiento: "0000" },
  { tipo: "08", serie: "FD01", ultimo_numero: 0, activa: true, establecimiento: "0000" },
  { tipo: "07", serie: "BC01", ultimo_numero: 0, activa: true, establecimiento: "0000" },
  { tipo: "08", serie: "BD01", ultimo_numero: 0, activa: true, establecimiento: "0000" },
];

const entrada = (codigo: string) => ({ codigo, descripcion: `Motivo ${codigo}`, extra: {} });
const CATALOGO_09 = { id: "09", nombre: "NC", columnas: [], entradas: ["01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "13"].map(entrada) };
const CATALOGO_10 = { id: "10", nombre: "ND", columnas: [], entradas: ["01", "02", "03"].map(entrada) };

function documento(tipo: "01" | "03", extra: Partial<Comprobante> = {}): Comprobante {
  const boleta = tipo === "03";
  return {
    id: boleta ? "b-1" : "f-1",
    tipo,
    serie: boleta ? "B001" : "F001",
    numero: 4,
    fecha_emision: "2026-09-03",
    moneda: "PEN",
    tipo_operacion: "0101",
    receptor: boleta ? { tipo_doc: "-", num_doc: "-", razon_social: "CLIENTES VARIOS", direccion: null } : { tipo_doc: "6", num_doc: "20554198211", razon_social: "CLIENTE SAC", direccion: null },
    items: [{ codigo: null, descripcion: "Pan", unidad: "NIU", cantidad: 10, precio_unitario: 1.18, tipo_afectacion_igv: "10", precio_venta: 11.8 }],
    estado_documento: "ACEPTADO",
    totales: { gravado: 10, exonerado: 0, inafecto: 0, igv: 1.8, total: 11.8 },
    // Al crédito a propósito: sobre una boleta el 13 no se ofrece igual (3259), aunque la forma de pago lo permitiría en una factura.
    forma_pago: { tipo: "credito", monto_pendiente: 11.8, cuotas: [{ id: "Cuota001", monto: 11.8, vencimiento: "2026-10-03" }] },
    ...extra,
  } as unknown as Comprobante;
}

function stubApi() {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>((url) => {
    const json = (datos: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }), { status }));
    if (String(url).includes("/catalogos/09")) return json(CATALOGO_09);
    if (String(url).includes("/catalogos/10")) return json(CATALOGO_10);
    return json({ id: "n-1" }, 201);
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

const notasEnviadas = (fetch: ReturnType<typeof stubApi>) =>
  fetch.mock.calls.filter(([url]) => String(url).includes("/notas")).map(([, init]) => JSON.parse(String(init?.body)));

const codigosDeMotivo = () => within(screen.getByLabelText(/Motivo/)).getAllByRole("option").map((o) => (o as HTMLOptionElement).value).filter(Boolean);
const seriesOfrecidas = () => within(screen.getByLabelText("Serie")).getAllByRole("option").map((o) => (o as HTMLOptionElement).value);

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("NotaForm sobre una boleta (#294)", () => {
  it("solo ofrece series B: la nota sobre una boleta sigue su serie (Anexo 3, 1001)", async () => {
    stubApi();
    render(<NotaForm factura={documento("03")} series={SERIES} />);
    await waitFor(() => expect(screen.getByLabelText(/Motivo/)).toBeEnabled());

    expect(seriesOfrecidas()).toEqual(["BC01"]);
    fireEvent.change(screen.getByLabelText("Tipo de nota"), { target: { value: "08" } });
    expect(seriesOfrecidas()).toEqual(["BD01"]);
  });

  it("no ofrece los motivos que SUNAT rechaza sobre una boleta: 13 (3259) ni 04, 05 y 08 (4367)", async () => {
    stubApi();
    render(<NotaForm factura={documento("03")} series={SERIES} />);
    await waitFor(() => expect(screen.getByLabelText(/Motivo/)).toBeEnabled());

    expect(codigosDeMotivo()).toEqual(["01", "02", "03", "06", "07", "09", "10"]);
  });

  it("emite contra la boleta con la serie B y sin datos del comprador: la nota toma el de la boleta, aun «sin documento»", async () => {
    const fetch = stubApi();
    render(<NotaForm factura={documento("03")} series={SERIES} />);
    await waitFor(() => expect(screen.getByLabelText(/Motivo/)).toBeEnabled());

    fireEvent.change(screen.getByLabelText(/Motivo/), { target: { value: "01" } });
    fireEvent.change(screen.getByLabelText("Sustento"), { target: { value: "Anulación de la venta" } });
    fireEvent.click(screen.getByRole("button", { name: "Emitir nota de crédito" }));

    await waitFor(() => expect(notasEnviadas(fetch)).toHaveLength(1));
    const cuerpo = notasEnviadas(fetch)[0];
    expect(cuerpo).toMatchObject({ tipo: "07", serie: "BC01", motivo: "01", documento_afectado: { serie: "B001", numero: 4 } });
    expect(cuerpo).not.toHaveProperty("cliente");
    expect(cuerpo).not.toHaveProperty("receptor");
  });

  it("sin series B de notas dice cuál crear", async () => {
    stubApi();
    render(<NotaForm factura={documento("03")} series={SERIES.filter((s) => s.serie.startsWith("F"))} />);

    expect(screen.getByText(/No hay series de nota de crédito sobre boletas/)).toHaveTextContent("BC01");
  });
});

describe("NotaForm sobre una factura", () => {
  it("sigue ofreciendo solo series F y todos los motivos que aplican, 04/05/08 y 13 al crédito incluidos", async () => {
    stubApi();
    render(<NotaForm factura={documento("01")} series={SERIES} />);
    await waitFor(() => expect(screen.getByLabelText(/Motivo/)).toBeEnabled());

    expect(seriesOfrecidas()).toEqual(["FC01"]);
    expect(codigosDeMotivo()).toEqual(["01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "13"]);
  });
});

describe("NotaForm — bolsas del ICBPER (#354)", () => {
  /** SUNAT 2892: la cantidad de bolsas es un entero. Una devolución parcial de 2.5 bolsas saldría 422 del backend; el formulario lo frena antes y dice por qué. */
  it("no deja emitir una devolución parcial de bolsas con cantidad fraccionaria, pero sí enteras", async () => {
    const fetch = stubApi();
    const bolsas = { codigo: null, descripcion: "Bolsa", unidad: "NIU", cantidad: 5, precio_unitario: 0.6, tipo_afectacion_igv: "10", precio_venta: 3, icbper: 2.5 };
    render(<NotaForm factura={documento("03", { items: [bolsas] as unknown as Comprobante["items"] })} series={SERIES} />);
    await waitFor(() => expect(screen.getByLabelText(/Motivo/)).toBeEnabled());
    fireEvent.change(screen.getByLabelText(/Motivo/), { target: { value: "07" } });
    fireEvent.change(screen.getByLabelText("Sustento"), { target: { value: "Devolvió bolsas" } });
    const emitir = screen.getByRole("button", { name: "Emitir nota de crédito" });

    fireEvent.change(screen.getByLabelText("Cantidad de Bolsa en la nota"), { target: { value: "2.5" } });
    expect(emitir).toBeDisabled();
    expect(screen.getByText(/bolsas del ICBPER se devuelven por unidades enteras \(SUNAT 2892\)/)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Cantidad de Bolsa en la nota"), { target: { value: "2" } });
    expect(emitir).toBeEnabled();
    fireEvent.click(emitir);
    await waitFor(() => expect(notasEnviadas(fetch)).toHaveLength(1));
    expect(notasEnviadas(fetch)[0].items[0]).toMatchObject({ cantidad: 2, icbper: true });
  });

  it("el resto de ítems siguen admitiendo cantidades fraccionarias", async () => {
    stubApi();
    const kilos = { codigo: null, descripcion: "Arroz", unidad: "KGM", cantidad: 5, precio_unitario: 4.72, tipo_afectacion_igv: "10", precio_venta: 23.6 };
    render(<NotaForm factura={documento("01", { items: [kilos] as unknown as Comprobante["items"] })} series={SERIES} />);
    await waitFor(() => expect(screen.getByLabelText(/Motivo/)).toBeEnabled());
    fireEvent.change(screen.getByLabelText(/Motivo/), { target: { value: "07" } });
    fireEvent.change(screen.getByLabelText("Sustento"), { target: { value: "Devolvió arroz" } });
    fireEvent.change(screen.getByLabelText("Cantidad de Arroz en la nota"), { target: { value: "2.5" } });
    expect(screen.getByRole("button", { name: "Emitir nota de crédito" })).toBeEnabled();
  });
});

describe("NotaForm — factura con anticipos que cubren el total (H3, revisión de #354)", () => {
  const conAnticipos = (total: number) =>
    documento("01", { anticipos: [{ serie: "F001", numero: 2, monto: 100 }] as unknown as Comprobante["anticipos"], totales: { gravado: 0, exonerado: 0, inafecto: 0, igv: 0, total } });

  /** Con neto 0.00 no hay nota posible (SUNAT rechaza las líneas en cero, 2369/2370, y una nota por más que el total, 3286): el aviso manda a la baja en vez de pedir cantidades. */
  it("con neto 0.00 dice que no hay nota posible y que se anula con la baja", async () => {
    stubApi();
    render(<NotaForm factura={conAnticipos(0)} series={SERIES} />);
    await waitFor(() => expect(screen.getByLabelText(/Motivo/)).toBeEnabled());
    fireEvent.change(screen.getByLabelText(/Motivo/), { target: { value: "01" } });
    expect(screen.getByText(/no hay nota posible.*baja/)).toBeInTheDocument();
    expect(screen.queryByText(/ajuste las cantidades para que la nota no supere ese importe/)).not.toBeInTheDocument();
  });

  it("con un neto mayor que cero sigue pidiendo ajustar las cantidades", async () => {
    stubApi();
    render(<NotaForm factura={conAnticipos(5)} series={SERIES} />);
    await waitFor(() => expect(screen.getByLabelText(/Motivo/)).toBeEnabled());
    fireEvent.change(screen.getByLabelText(/Motivo/), { target: { value: "01" } });
    expect(screen.getByText(/ajuste las cantidades para que la nota no supere ese importe/)).toBeInTheDocument();
    expect(screen.queryByText(/no hay nota posible/)).not.toBeInTheDocument();
  });
});

describe("NotaForm — factura con un descuento que no afecta la base (#393)", () => {
  // 10 panes a 1.18 con descuento 01 de 1.00 (no toca el IGV): el cliente pagó 10.80.
  const conDescuento01 = () => documento("01", {
    items: [{ codigo: null, descripcion: "Pan", unidad: "NIU", cantidad: 10, precio_unitario: 1.18, tipo_afectacion_igv: "10", valor_venta: 10, igv: 1.8, precio_venta: 10.8,
      descuento: { tipo: "MONTO", valor: 1, monto: 1, afecta_base_igv: false, codigo: "01" } }] as unknown as Comprobante["items"],
    totales: { gravado: 10, exonerado: 0, inafecto: 0, igv: 1.8, total: 10.8 },
  });

  it("la anulación no sale como «nota total»: se pide por ítems y lo explica", async () => {
    stubApi();
    render(<NotaForm factura={conDescuento01()} series={SERIES} />);
    await waitFor(() => expect(screen.getByLabelText(/Motivo/)).toBeEnabled());
    fireEvent.change(screen.getByLabelText(/Motivo/), { target: { value: "01" } });
    expect(screen.getByText(/descuento que no afecta la base del IGV/)).toBeInTheDocument();
    expect(screen.getByLabelText("Cantidad de Pan en la nota")).toBeInTheDocument();
  });

  it("la línea viaja por su importe neto y sin descuento: la API no admite un 01 en una nota", async () => {
    const fetch = stubApi();
    render(<NotaForm factura={conDescuento01()} series={SERIES} />);
    await waitFor(() => expect(screen.getByLabelText(/Motivo/)).toBeEnabled());
    fireEvent.change(screen.getByLabelText(/Motivo/), { target: { value: "01" } });
    fireEvent.change(screen.getByLabelText("Sustento"), { target: { value: "Anulación de la operación" } });
    fireEvent.change(screen.getByLabelText("Cantidad de Pan en la nota"), { target: { value: "10" } });
    fireEvent.click(screen.getByRole("button", { name: "Emitir nota de crédito" }));
    await waitFor(() => expect(notasEnviadas(fetch)).toHaveLength(1));
    const linea = (notasEnviadas(fetch)[0].items as Array<Record<string, unknown>>)[0];
    expect(linea.descuento).toBeUndefined();
    expect(linea.precio_unitario).toBe(1.08);
  });
});

describe("NotaForm — Idempotency-Key (#355)", () => {
  /** Como `stubApi`, pero cada POST a /notas responde lo que diga `respuestas` en orden (un `Error` es un corte de red: `fetch` rechaza). */
  function stubConCortes(respuestas: Array<Response | Error>) {
    const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>((url) => {
      const json = (datos: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }), { status }));
      if (String(url).includes("/catalogos/09")) return json(CATALOGO_09);
      if (String(url).includes("/catalogos/10")) return json(CATALOGO_10);
      const siguiente = respuestas.shift();
      if (siguiente instanceof Error) return Promise.reject(siguiente);
      return Promise.resolve(siguiente ?? new Response(JSON.stringify({ estado: "exito", datos: { id: "n-1" }, mensaje: null, codigo: null, errores: null }), { status: 201 }));
    });
    vi.stubGlobal("fetch", fetch);
    return fetch;
  }

  const clavesEnviadas = (fetch: ReturnType<typeof stubConCortes>) =>
    fetch.mock.calls.filter(([url]) => String(url).includes("/notas")).map(([, init]) => (init?.headers as Record<string, string> | undefined)?.["Idempotency-Key"]);

  async function completar(tipoDoc: "01" | "03") {
    render(<NotaForm factura={documento(tipoDoc)} series={SERIES} />);
    await waitFor(() => expect(screen.getByLabelText(/Motivo/)).toBeEnabled());
    fireEvent.change(screen.getByLabelText(/Motivo/), { target: { value: "01" } });
    fireEvent.change(screen.getByLabelText("Sustento"), { target: { value: "Anulación de la venta" } });
  }

  const emitir = () => fireEvent.click(screen.getByRole("button", { name: "Emitir nota de crédito" }));

  it.each(["01", "03"] as const)("la emisión de una nota sobre el documento %s lleva una Idempotency-Key (un UUID)", async (tipoDoc) => {
    const fetch = stubConCortes([]);
    await completar(tipoDoc);
    emitir();

    await waitFor(() => expect(clavesEnviadas(fetch)).toHaveLength(1));
    expect(clavesEnviadas(fetch)[0]).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });

  /** El caso del issue: el POST llegó pero la respuesta se cortó. Volver a emitir sin cambiar nada reusa la clave, así el backend devuelve la misma nota. */
  it("tras un corte, volver a emitir el mismo pedido reusa la clave y el aviso dice que es seguro", async () => {
    const fetch = stubConCortes([new TypeError("Failed to fetch")]);
    await completar("01");
    emitir();

    const aviso = await screen.findByRole("alert");
    expect(aviso).toHaveTextContent("Se cortó la conexión");
    expect(aviso).toHaveTextContent("Vuelve a emitir sin cambiar nada");
    expect(screen.getByRole("button", { name: "Emitir nota de crédito" })).toBeEnabled();

    emitir();
    await waitFor(() => expect(clavesEnviadas(fetch)).toHaveLength(2));
    expect(clavesEnviadas(fetch)[1]).toBe(clavesEnviadas(fetch)[0]);
    expect(notasEnviadas(fetch)[1]).toEqual(notasEnviadas(fetch)[0]);
  });

  it("si después del corte se cambia la nota, el reintento es otra nota con otra clave", async () => {
    const fetch = stubConCortes([new TypeError("Failed to fetch")]);
    await completar("03");
    emitir();
    await screen.findByRole("alert");

    fireEvent.change(screen.getByLabelText("Sustento"), { target: { value: "Otro sustento" } });
    emitir();

    await waitFor(() => expect(clavesEnviadas(fetch)).toHaveLength(2));
    expect(clavesEnviadas(fetch)[1]).not.toBe(clavesEnviadas(fetch)[0]);
  });

  it("una respuesta con el mismo 422 de la clave se muestra tal cual, sin avisar de corte", async () => {
    stubConCortes([new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "La clave de idempotencia x ya se usó con otro contenido", codigo: "IDEMPOTENCIA_INVALIDA", errores: null }), { status: 422 })]);
    await completar("01");
    emitir();

    expect(await screen.findByRole("alert")).toHaveTextContent("ya se usó con otro contenido");
  });
});
