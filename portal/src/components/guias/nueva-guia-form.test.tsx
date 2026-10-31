import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Establecimiento } from "@/lib/api/establecimientos";
import type { Serie } from "@/lib/api/series";
import { SoloLectura } from "@/lib/solo-lectura";
import { apiRequest } from "@/lib/api/browser";
import { NuevaGuiaForm, type CatalogosDeGuia } from "./nueva-guia-form";

const push = vi.fn();
const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh }) }));
vi.mock("@/lib/api/browser", () => ({ apiRequest: vi.fn() }));

const SERIES: Serie[] = [
  { tipo: "09", serie: "T001", ultimo_numero: 8, activa: true, establecimiento: "0000" },
  { tipo: "31", serie: "V001", ultimo_numero: 1, activa: true, establecimiento: "0000" },
];
const ANEXO = { codigo: "0002", nombre: "Tienda Miraflores", principal: false, activo: true };
const DOMICILIO_FISCAL = { codigo: "0000", nombre: "Domicilio fiscal", principal: true, activo: true };
/** Una empresa que todavía no cargó su domicilio fiscal: solo tiene un anexo. */
const ESTABLECIMIENTOS = [ANEXO] as unknown as Establecimiento[];
const CON_DOMICILIO_FISCAL = [DOMICILIO_FISCAL, ANEXO] as unknown as Establecimiento[];
const CATALOGOS: CatalogosDeGuia = {
  motivos: [
    { codigo: "01", descripcion: "Venta" },
    { codigo: "13", descripcion: "Otros" },
  ],
  tiposDocumento: [
    { codigo: "1", descripcion: "DNI" },
    { codigo: "6", descripcion: "RUC" },
  ],
  documentos: [
    { codigo: "01", descripcion: "Factura", extra: { "GRE aplicable": "remitente, transportista" } },
    { codigo: "49", descripcion: "Constancia de Depósito - IVAP", extra: { "GRE aplicable": "solo remitente" } },
    { codigo: "69", descripcion: "Permiso de operación de transporte", extra: { "GRE aplicable": "solo transportista" } },
  ],
};
const UBIGEOS = {
  id: "13",
  nombre: "Ubigeo",
  columnas: [],
  entradas: [{ codigo: "150131", descripcion: "LIMA / LIMA / SAN ISIDRO", extra: { Departamento: "LIMA", Provincia: "LIMA", Distrito: "SAN ISIDRO" } }],
};

const exito = <T,>(datos: T) => ({ estado: "exito" as const, datos, mensaje: null, codigo: null, errores: null });
const fallo = (codigo: string, mensaje: string | null = null) => ({ estado: "error" as const, datos: null, mensaje, codigo, errores: null });

/** Responde el catálogo de ubigeos como la API y deja que cada test decida qué contesta `/guias-remision`. */
function responder(guias: (ruta: string, init: { body?: unknown; headers?: Record<string, string> }) => unknown) {
  vi.mocked(apiRequest).mockImplementation(((ruta: string, init: { method: string; body?: unknown; headers?: Record<string, string> }) =>
    Promise.resolve(ruta.includes("/catalogos/13") ? exito(UBIGEOS) : guias(ruta, init))) as typeof apiRequest);
}

const llamadasA = (parte: string) => vi.mocked(apiRequest).mock.calls.filter(([ruta]) => String(ruta).includes(parte));

function escribir(etiqueta: string | RegExp, valor: string) {
  fireEvent.change(screen.getByLabelText(etiqueta), { target: { value: valor } });
}

/** Llena una guía remitente en transporte privado con lo mínimo para que pase la validación del formulario. */
async function llenarRemitentePrivada() {
  const form = screen.getByTestId("nueva-guia-form");
  fireEvent.change(form.querySelector("#destinatario-num")!, { target: { value: "20601234565" } });
  fireEvent.change(form.querySelector("#destinatario-nombre")!, { target: { value: "PESQUERA PACÍFICO S.A.C." } });
  fireEvent.change(form.querySelector("#guia-motivo")!, { target: { value: "01" } });
  fireEvent.change(form.querySelector("#guia-peso")!, { target: { value: "25,5" } });
  await waitFor(() => expect(form.querySelector("#llegada-departamento")).not.toBeDisabled());
  fireEvent.change(form.querySelector("#llegada-departamento")!, { target: { value: "LIMA" } });
  fireEvent.change(form.querySelector("#llegada-provincia")!, { target: { value: "LIMA" } });
  fireEvent.change(form.querySelector("#llegada-distrito")!, { target: { value: "150131" } });
  fireEvent.change(form.querySelector("#llegada-direccion")!, { target: { value: "Calle Los Pinos 450" } });
  // Sin domicilio fiscal cargado la partida nace como dirección (F3): también se llena.
  fireEvent.change(form.querySelector("#partida-departamento")!, { target: { value: "LIMA" } });
  fireEvent.change(form.querySelector("#partida-provincia")!, { target: { value: "LIMA" } });
  fireEvent.change(form.querySelector("#partida-distrito")!, { target: { value: "150131" } });
  fireEvent.change(form.querySelector("#partida-direccion")!, { target: { value: "Av. Argentina 1234" } });
  fireEvent.change(form.querySelector("#guia-placa-0")!, { target: { value: "abc123" } });
  fireEvent.change(form.querySelector("#guia-conductor-0-num")!, { target: { value: "45678912" } });
  fireEvent.change(form.querySelector("#guia-conductor-0-nombres")!, { target: { value: "JUAN" } });
  fireEvent.change(form.querySelector("#guia-conductor-0-apellidos")!, { target: { value: "QUISPE MAMANI" } });
  fireEvent.change(form.querySelector("#guia-conductor-0-licencia")!, { target: { value: "Q45678912" } });
  fireEvent.change(form.querySelector("#guia-item-0-descripcion")!, { target: { value: "Laptop" } });
}

function montar(series = SERIES) {
  return render(<NuevaGuiaForm series={series} establecimientos={ESTABLECIMIENTOS} catalogos={CATALOGOS} hoy="2026-10-20" />);
}

beforeEach(() => {
  vi.mocked(apiRequest).mockReset();
  push.mockReset();
  refresh.mockReset();
});
afterEach(cleanup);

describe("NuevaGuiaForm: qué se pide según la guía", () => {
  it("la remitente pide motivo y modalidad, con vehículos y conductores en transporte privado", () => {
    responder(() => exito(null));
    montar();

    expect(screen.getByLabelText("Motivo del traslado")).toBeInTheDocument();
    expect(screen.getByLabelText("Placa 1")).toBeInTheDocument();
    expect(screen.queryByLabelText("Registro MTC")).toBeNull();
  });

  it("los documentos que sustentan el traslado se ofrecen según la guía: lo «solo transportista» no está en la remitente, y al revés", () => {
    responder(() => exito(null));
    montar();
    const agregarDocumento = () => within(screen.getByText("Documentos que sustentan el traslado").closest("section")!).getByRole("button", { name: "Agregar" });

    fireEvent.click(agregarDocumento());
    const tipos = () => Array.from(screen.getByLabelText("Tipo", { selector: "#guia-documento-0-tipo" }).querySelectorAll("option")).map((o) => o.value);
    expect(tipos()).toEqual(["01", "49"]);

    cleanup();
    montar();
    fireEvent.change(screen.getByLabelText("Serie"), { target: { value: "V001" } });
    fireEvent.click(agregarDocumento());
    expect(tipos()).toEqual(["01", "69"]);
  });

  it("pasar a transporte público cambia vehículos y conductores por la empresa de transporte", () => {
    responder(() => exito(null));
    montar();

    fireEvent.click(screen.getByLabelText(/Transporte público/));

    expect(screen.queryByLabelText("Placa 1")).toBeNull();
    expect(screen.getByLabelText("RUC del transportista")).toBeInTheDocument();
  });

  it("elegir una serie V pide al remitente y el registro MTC, y quita el motivo y los establecimientos", () => {
    responder(() => exito(null));
    montar();

    fireEvent.change(screen.getByLabelText("Serie"), { target: { value: "V001" } });

    expect(screen.queryByLabelText("Motivo del traslado")).toBeNull();
    expect(screen.getByLabelText("Registro MTC")).toBeInTheDocument();
    expect(screen.queryByLabelText("Un establecimiento de mi empresa")).toBeNull();
    expect(screen.getAllByLabelText("Número de documento")).toHaveLength(3);
  });

  it("los indicadores ofrecidos dependen de la modalidad y del tipo de guía", () => {
    responder(() => exito(null));
    montar();

    expect(screen.getByLabelText("Vehículo M1 o L (autos o motos)")).toBeInTheDocument();
    expect(screen.queryByLabelText("Vehículos y conductores del transportista")).toBeNull();

    fireEvent.click(screen.getByLabelText(/Transporte público/));
    expect(screen.queryByLabelText("Vehículo M1 o L (autos o motos)")).toBeNull();
    expect(screen.getByLabelText("Vehículos y conductores del transportista")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Serie"), { target: { value: "V001" } });
    expect(screen.queryByLabelText("Vehículo M1 o L (autos o motos)")).toBeNull();
    expect(screen.queryByLabelText("Vehículos y conductores del transportista")).toBeNull();
    expect(screen.getByLabelText("Transbordo programado")).toBeInTheDocument();
  });
});

describe("NuevaGuiaForm: de dónde sale la mercadería (F3)", () => {
  it("una empresa sin domicilio fiscal empieza con la partida como dirección y no ofrece el 0000, que la API rechazaría (3366)", () => {
    responder(() => exito(null));
    montar();

    const form = screen.getByTestId("nueva-guia-form");
    expect(form.querySelector("#partida-departamento")).not.toBeNull();
    expect(form.querySelector("#partida-establecimiento")).toBeNull();
    // Con un anexo sí puede elegir un establecimiento, y la lista no trae el 0000.
    fireEvent.click(within(form.querySelector("fieldset:has(#partida-departamento)")!).getByLabelText("Un establecimiento de mi empresa"));
    const opciones = Array.from(form.querySelectorAll<HTMLOptionElement>("#partida-establecimiento option")).map((o) => o.value);
    expect(opciones).toEqual(["0002"]);
  });

  it("al elegir «un establecimiento» el estado toma el primero que existe, no un 0000 que la empresa no tiene", async () => {
    responder((ruta) => (ruta.endsWith("/guias-remision") ? exito({ id: "g-1" }) : exito(null)));
    montar();
    await llenarRemitentePrivada();
    const form = screen.getByTestId("nueva-guia-form");

    fireEvent.click(within(form.querySelector("fieldset:has(#partida-departamento)")!).getByLabelText("Un establecimiento de mi empresa"));
    fireEvent.click(screen.getByRole("button", { name: "Emitir la guía" }));

    await waitFor(() => expect(push).toHaveBeenCalled());
    const cuerpo = llamadasA("/guias-remision")[0][1]?.body as { traslado: { partida: unknown } };
    expect(cuerpo.traslado.partida).toEqual({ establecimiento: "0002" });
  });

  it("una empresa con domicilio fiscal empieza con la partida en él y manda el código 0000", async () => {
    responder((ruta) => (ruta.endsWith("/guias-remision") ? exito({ id: "g-2" }) : exito(null)));
    render(<NuevaGuiaForm series={SERIES} establecimientos={CON_DOMICILIO_FISCAL} catalogos={CATALOGOS} hoy="2026-10-20" />);
    const form = screen.getByTestId("nueva-guia-form");

    expect((form.querySelector("#partida-establecimiento") as HTMLSelectElement).value).toBe("0000");
    expect(within(form.querySelector("#partida-establecimiento") as HTMLElement).getByText("0000 · Domicilio fiscal")).toBeInTheDocument();
  });
});

describe("NuevaGuiaForm: validar y emitir", () => {
  it("un formulario incompleto no llama a la API y dice qué falta", async () => {
    responder(() => exito(null));
    montar();

    fireEvent.click(screen.getByRole("button", { name: "Emitir la guía" }));

    expect(await screen.findByTestId("guia-error")).toHaveTextContent("Completa el tipo, el número y el nombre del destinatario.");
    expect(llamadasA("/guias-remision")).toHaveLength(0);
  });

  it("validar manda el cuerpo a /validar sin clave de idempotencia y no navega", async () => {
    responder((ruta) => (ruta.endsWith("/validar") ? exito({ id: "provisional" }) : exito(null)));
    montar();
    await llenarRemitentePrivada();

    fireEvent.click(screen.getByRole("button", { name: "Validar sin emitir" }));

    expect(await screen.findByTestId("guia-valida")).toHaveTextContent("La guía es válida");
    const [ruta, init] = llamadasA("/guias-remision")[0];
    expect(ruta).toBe("/api/proxy/guias-remision/validar");
    expect(init?.headers).toBeUndefined();
    expect(push).not.toHaveBeenCalled();
  });

  it("emitir manda el cuerpo con una clave de idempotencia y abre la ficha de la guía", async () => {
    responder((ruta) => (ruta.endsWith("/guias-remision") ? exito({ id: "g-nueva-1-9" }) : exito(null)));
    montar();
    await llenarRemitentePrivada();

    fireEvent.click(screen.getByRole("button", { name: "Emitir la guía" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/guias/g-nueva-1-9"));
    const [ruta, init] = llamadasA("/guias-remision")[0];
    expect(ruta).toBe("/api/proxy/guias-remision");
    expect(init?.headers?.["Idempotency-Key"]).toMatch(/^[0-9a-f-]{36}$/);
    const cuerpo = init?.body as { serie: string; motivo: string; enviar_automatico: boolean; traslado: { peso_bruto: number; vehiculos: Array<{ placa: string }> } };
    expect(cuerpo.serie).toBe("T001");
    expect(cuerpo.motivo).toBe("01");
    expect(cuerpo.enviar_automatico).toBe(false);
    expect(cuerpo.traslado.peso_bruto).toBe(25.5);
    expect(cuerpo.traslado.vehiculos).toEqual([{ placa: "ABC123" }]);
  });

  it("tras un corte de red reintentar con el mismo contenido reusa la clave, y si el contenido cambia estrena otra", async () => {
    let n = 0;
    responder((ruta) => (ruta.endsWith("/guias-remision") ? (++n === 1 ? fallo("RED_CORTADA") : exito({ id: "g-nueva-2-9" })) : exito(null)));
    montar();
    await llenarRemitentePrivada();

    fireEvent.click(screen.getByRole("button", { name: "Emitir la guía" }));
    expect(await screen.findByTestId("guia-error")).toHaveTextContent("no sabemos si la guía se emitió");
    expect(push).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Emitir la guía" }));
    await waitFor(() => expect(push).toHaveBeenCalled());

    const claves = llamadasA("/guias-remision").map(([, init]) => init?.headers?.["Idempotency-Key"]);
    expect(claves).toHaveLength(2);
    expect(claves[0]).toBe(claves[1]);
  });

  it("un cambio de contenido después de un corte estrena clave: es otra guía", async () => {
    let n = 0;
    responder((ruta) => (ruta.endsWith("/guias-remision") ? (++n === 1 ? fallo("RED") : exito({ id: "g-nueva-3-9" })) : exito(null)));
    montar();
    await llenarRemitentePrivada();

    fireEvent.click(screen.getByRole("button", { name: "Emitir la guía" }));
    await screen.findByTestId("guia-error");
    escribir("Peso bruto total", "30");
    fireEvent.click(screen.getByRole("button", { name: "Emitir la guía" }));
    await waitFor(() => expect(push).toHaveBeenCalled());

    const claves = llamadasA("/guias-remision").map(([, init]) => init?.headers?.["Idempotency-Key"]);
    expect(claves[0]).not.toBe(claves[1]);
  });

  it("un rechazo del backend se muestra con su mensaje y no navega", async () => {
    responder((ruta) => (ruta.endsWith("/guias-remision") ? fallo("TRASLADO_INVALIDO", "3343 - La fecha de inicio del traslado no puede ser anterior a la de emisión") : exito(null)));
    montar();
    await llenarRemitentePrivada();

    fireEvent.click(screen.getByRole("button", { name: "Emitir la guía" }));

    expect(await screen.findByTestId("guia-error")).toHaveTextContent("3343");
    expect(push).not.toHaveBeenCalled();
  });

  it("sin mensaje del backend muestra el texto del código en el idioma de la sesión", async () => {
    responder((ruta) => (ruta.endsWith("/guias-remision") ? fallo("GRE_NO_DISPONIBLE") : exito(null)));
    montar();
    await llenarRemitentePrivada();

    fireEvent.click(screen.getByRole("button", { name: "Emitir la guía" }));

    expect(await screen.findByTestId("guia-error")).toHaveTextContent("no está disponible en este momento");
  });

  it("dos clics seguidos en emitir mandan un solo pedido", async () => {
    let resolver: (v: unknown) => void = () => {};
    vi.mocked(apiRequest).mockImplementation(((ruta: string) => (ruta.includes("/catalogos/13") ? Promise.resolve(exito(UBIGEOS)) : new Promise((r) => (resolver = r as (v: unknown) => void)))) as unknown as typeof apiRequest);
    montar();
    await llenarRemitentePrivada();
    const boton = screen.getByRole("button", { name: "Emitir la guía" });

    fireEvent.click(boton);
    fireEvent.click(boton);

    expect(llamadasA("/guias-remision")).toHaveLength(1);
    resolver(exito({ id: "g-nueva-4-9" }));
    await waitFor(() => expect(push).toHaveBeenCalled());
  });

  it("con el rol de solo lectura no se puede validar ni emitir, y el motivo está en el botón", () => {
    responder(() => exito(null));
    render(
      <SoloLectura activa={false} rol="LECTURA">
        <NuevaGuiaForm series={SERIES} establecimientos={ESTABLECIMIENTOS} catalogos={CATALOGOS} hoy="2026-10-20" />
      </SoloLectura>,
    );

    const emitir = screen.getByRole("button", { name: "Emitir la guía" });
    expect(emitir).toBeDisabled();
    expect(emitir).toHaveAttribute("title");
    expect(screen.getByRole("button", { name: "Validar sin emitir" })).toBeDisabled();
  });

  it("sin los catálogos el motivo y los tipos de documento se escriben a mano en vez de impedir emitir", () => {
    responder(() => exito(null));
    render(<NuevaGuiaForm series={SERIES} establecimientos={[]} catalogos={{ motivos: [], tiposDocumento: [], documentos: [] }} hoy="2026-10-20" />);

    const motivo = screen.getByLabelText("Motivo del traslado");
    expect(motivo.tagName).toBe("INPUT");
    const form = screen.getByTestId("nueva-guia-form");
    expect(within(form).getAllByLabelText("Tipo de documento")[0].tagName).toBe("INPUT");
  });
});
