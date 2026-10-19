import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Comprobante } from "@/lib/api/facturas";
import { guardarAvisosDeEmision, leerAvisosDeEmisionCrudos } from "@/lib/comprobantes/avisos-de-emision";
import { AvisosDeEmision } from "./avisos-de-emision";
import { NotaForm } from "./nota-form";
import { NuevoComprobanteForm } from "./nuevo-comprobante-form";

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh: vi.fn() }) }));

const AVISO_4013 = { codigo: "4013", mensaje: "El RUC 20160109441 del comprador no está activo en SUNAT (BAJA DE OFICIO): SUNAT aceptará el comprobante con una observación" };
const AVISO_4014 = { codigo: "4014", mensaje: "El RUC 20160109441 del comprador figura como no habido en SUNAT: SUNAT aceptará el comprobante con una observación" };

const SERIES = [
  { tipo: "01", serie: "F001", ultimo_numero: 2, activa: true, establecimiento: "0000" },
  { tipo: "03", serie: "B001", ultimo_numero: 9, activa: true, establecimiento: "0000" },
  { tipo: "07", serie: "FC01", ultimo_numero: 0, activa: true, establecimiento: "0000" },
];

const entrada = (codigo: string) => ({ codigo, descripcion: `Motivo ${codigo}`, extra: {} });
const CATALOGO_09 = { id: "09", nombre: "NC", columnas: [], entradas: ["01"].map(entrada) };
const CATALOGO_10 = { id: "10", nombre: "ND", columnas: [], entradas: ["01"].map(entrada) };

const FACTURA = {
  id: "f-1", tipo: "01", serie: "F001", numero: 4, fecha_emision: "2026-09-03", moneda: "PEN", tipo_operacion: "0101",
  receptor: { tipo_doc: "6", num_doc: "20160109441", razon_social: "CLIENTE SAC", direccion: null },
  items: [{ codigo: null, descripcion: "Pan", unidad: "NIU", cantidad: 10, precio_unitario: 1.18, tipo_afectacion_igv: "10", precio_venta: 11.8 }],
  estado_documento: "ACEPTADO", totales: { gravado: 10, exonerado: 0, inafecto: 0, igv: 1.8, total: 11.8 },
  forma_pago: { tipo: "contado", monto_pendiente: null, cuotas: [] },
} as unknown as Comprobante;

/** Contesta la emisión con `avisos` al nivel de `datos` (o sin la clave si es `undefined`), y lo demás con lo mínimo para que el formulario funcione. */
function stubEmision(id: string, avisos?: Array<{ codigo: string; mensaje: string }>) {
  const sobre = (datos: unknown, extra: object = {}) => Promise.resolve(new Response(JSON.stringify({ estado: "exito", datos, mensaje: null, codigo: null, errores: null, ...extra }), { status: 201 }));
  vi.stubGlobal("fetch", vi.fn((url: string) => {
    if (String(url).includes("/catalogos/09")) return sobre(CATALOGO_09);
    if (String(url).includes("/catalogos/10")) return sobre(CATALOGO_10);
    if (String(url).includes("/consultas/ruc/")) return Promise.resolve(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "x", codigo: "NO_ENCONTRADO", errores: null }), { status: 404 }));
    return sobre({ id }, avisos ? { avisos } : {});
  }));
}

beforeEach(() => {
  window.sessionStorage.clear();
  push.mockClear();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("AvisosDeEmision — la ficha del comprobante recién emitido (#382)", () => {
  it("muestra el mensaje de cada aviso, 4013 y 4014, sin presentarlos como un error", () => {
    guardarAvisosDeEmision("f-1", [AVISO_4013, AVISO_4014]);
    render(<AvisosDeEmision id="f-1" />);

    expect(screen.getByTestId("aviso-emision-4013")).toHaveTextContent(AVISO_4013.mensaje);
    expect(screen.getByTestId("aviso-emision-4014")).toHaveTextContent(AVISO_4014.mensaje);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("sin avisos guardados no pinta nada: la pantalla es la de siempre", () => {
    const { container } = render(<AvisosDeEmision id="f-1" />);
    expect(container).toBeEmptyDOMElement();
  });

  it("los avisos son del comprobante que se emitió: otro id no los muestra", () => {
    guardarAvisosDeEmision("f-1", [AVISO_4013]);
    const { container } = render(<AvisosDeEmision id="f-2" />);
    expect(container).toBeEmptyDOMElement();
  });

  it("sigue mostrándolos al recargar la ficha (se montan de nuevo)", () => {
    guardarAvisosDeEmision("f-1", [AVISO_4013]);
    render(<AvisosDeEmision id="f-1" />).unmount();
    render(<AvisosDeEmision id="f-1" />);
    expect(screen.getByTestId("aviso-emision-4013")).toBeInTheDocument();
  });

  it("un valor guardado que no es una lista de avisos no rompe la ficha", () => {
    window.sessionStorage.setItem("khipu:avisos-de-emision:f-1", "{no es json");
    const { container } = render(<AvisosDeEmision id="f-1" />);
    expect(container).toBeEmptyDOMElement();
  });
});

describe("AvisosDeEmision — valores guardados que no son avisos", () => {
  it.each([
    ["un objeto en vez de una lista", '{"codigo":"4013","mensaje":"x"}'],
    ["una lista de cosas que no son avisos", '[{"x":1},"4013",null]'],
  ])("%s no pinta nada", (_caso, guardado) => {
    window.sessionStorage.setItem("khipu:avisos-de-emision:f-1", guardado);
    const { container } = render(<AvisosDeEmision id="f-1" />);
    expect(container).toBeEmptyDOMElement();
  });

  it("de una lista mezclada se muestran solo los avisos válidos", () => {
    window.sessionStorage.setItem("khipu:avisos-de-emision:f-1", JSON.stringify([{ x: 1 }, AVISO_4014]));
    render(<AvisosDeEmision id="f-1" />);
    expect(screen.getAllByTestId(/^aviso-emision-/)).toHaveLength(1);
    expect(screen.getByTestId("aviso-emision-4014")).toBeInTheDocument();
  });
});

describe("guardarAvisosDeEmision", () => {
  it("no guarda nada si no hay avisos: ni ausentes, ni vacíos", () => {
    guardarAvisosDeEmision("f-1", undefined);
    guardarAvisosDeEmision("f-1", null);
    guardarAvisosDeEmision("f-1", []);
    expect(leerAvisosDeEmisionCrudos("f-1")).toBe("");
  });

  it("un almacenamiento que lanza (ventana privada, cuota) no rompe la emisión", () => {
    const set = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("cuota", "QuotaExceededError");
    });
    const get = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new DOMException("bloqueado", "SecurityError");
    });
    try {
      expect(() => guardarAvisosDeEmision("f-1", [AVISO_4013])).not.toThrow();
      expect(leerAvisosDeEmisionCrudos("f-1")).toBe("");
    } finally {
      set.mockRestore();
      get.mockRestore();
    }
  });
});

describe("los formularios de emisión dejan los avisos para la ficha (#382)", () => {
  async function emitirFactura() {
    render(<NuevoComprobanteForm series={SERIES} tasaIgv={18} />);
    fireEvent.change(screen.getByLabelText("RUC"), { target: { value: "20100066603" } });
    fireEvent.change(screen.getByLabelText("Razón social"), { target: { value: "CLIENTE SAC" } });
    fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: "Pan" } });
    fireEvent.change(screen.getByLabelText(/Precio unit\./), { target: { value: "5" } });
    fireEvent.blur(screen.getByLabelText(/Precio unit\./));
    fireEvent.click(screen.getByRole("button", { name: "Emitir factura" }));
    await waitFor(() => expect(push).toHaveBeenCalled());
  }

  it("la factura que devuelve avisos 4013 y 4014 navega a su ficha con los dos guardados", async () => {
    stubEmision("f-9", [AVISO_4013, AVISO_4014]);
    await emitirFactura();

    expect(push).toHaveBeenCalledWith("/comprobantes/f-9");
    render(<AvisosDeEmision id="f-9" />);
    expect(screen.getByTestId("aviso-emision-4013")).toHaveTextContent("BAJA DE OFICIO");
    expect(screen.getByTestId("aviso-emision-4014")).toBeInTheDocument();
  });

  it("la factura sin avisos navega igual y no deja nada guardado", async () => {
    stubEmision("f-9");
    await emitirFactura();

    expect(push).toHaveBeenCalledWith("/comprobantes/f-9");
    expect(leerAvisosDeEmisionCrudos("f-9")).toBe("");
  });

  it("la boleta con RUC que devuelve avisos también los deja para su ficha", async () => {
    stubEmision("b-9", [AVISO_4014]);
    render(<NuevoComprobanteForm tipo="boleta" series={SERIES} tasaIgv={18} />);
    fireEvent.change(screen.getByLabelText("Documento del comprador"), { target: { value: "6" } });
    fireEvent.change(screen.getByLabelText("Número de documento"), { target: { value: "20100066603" } });
    fireEvent.change(screen.getByLabelText("Nombre del comprador"), { target: { value: "CLIENTE SAC" } });
    fireEvent.change(screen.getByLabelText("Descripción"), { target: { value: "Pan" } });
    fireEvent.change(screen.getByLabelText(/Precio unit\./), { target: { value: "5" } });
    fireEvent.blur(screen.getByLabelText(/Precio unit\./));
    fireEvent.click(screen.getByRole("button", { name: "Emitir boleta" }));
    await waitFor(() => expect(push).toHaveBeenCalled());

    expect(push).toHaveBeenCalledWith("/comprobantes/b-9");
    render(<AvisosDeEmision id="b-9" />);
    expect(screen.getByTestId("aviso-emision-4014")).toBeInTheDocument();
  });

  async function emitirNota() {
    render(<NotaForm factura={FACTURA} series={SERIES} />);
    await waitFor(() => expect(screen.getByLabelText(/Motivo/)).toBeEnabled());
    fireEvent.change(screen.getByLabelText(/Motivo/), { target: { value: "01" } });
    fireEvent.change(screen.getByLabelText("Sustento"), { target: { value: "Anulación de la venta" } });
    fireEvent.click(screen.getByRole("button", { name: "Emitir nota de crédito" }));
    await waitFor(() => expect(push).toHaveBeenCalled());
  }

  it("la nota que devuelve avisos navega a su ficha con el aviso guardado", async () => {
    stubEmision("n-9", [AVISO_4013]);
    await emitirNota();

    expect(push).toHaveBeenCalledWith("/comprobantes/n-9");
    render(<AvisosDeEmision id="n-9" />);
    expect(screen.getByTestId("aviso-emision-4013")).toBeInTheDocument();
  });

  it("la nota sin avisos no deja nada guardado", async () => {
    stubEmision("n-9");
    await emitirNota();

    expect(leerAvisosDeEmisionCrudos("n-9")).toBe("");
  });
});
