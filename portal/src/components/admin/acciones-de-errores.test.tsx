import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ErrorDeEmision } from "@/lib/api/admin-errores";
import type { ApiEnvelope } from "@/lib/api/types";
import { DescartarComprobante } from "./descartar-comprobante";
import { ReintentarEnvio } from "./reintentar-envio";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

const ID = "00000000-0000-4000-d000-000000000101";
const NOMBRE = "20100047226-01-F001-101";

const comprobante: ErrorDeEmision = {
  comprobante_id: ID,
  empresa_id: "00000000-0000-4000-9000-000000000001",
  ruc: "20100047226",
  razon_social: "PANADERIA SOL SAC",
  nombre_archivo: NOMBRE,
  tipo: "01",
  serie: "F001",
  numero: 101,
  fecha_emision: "2026-10-01",
  estado: "ERROR_ENVIO",
  clase: "ERROR_DE_ENVIO",
  intentos: 3,
  actualizado_en: "2026-10-04T15:00:00Z",
  accionable: true,
};

const exito = (datos: unknown = {}): ApiEnvelope<unknown> => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });
const error = (codigo: string, mensaje: string): ApiEnvelope<unknown> => ({ estado: "error", datos: null, mensaje, codigo, errores: null });

afterEach(() => {
  cleanup();
  refresh.mockClear();
  apiRequest.mockReset();
});

describe("ReintentarEnvio (#196)", () => {
  it("el diálogo dice que reenvía a SUNAT de verdad, qué pasa si vuelve a fallar y que queda en la bitácora, y no pide nada hasta confirmar", () => {
    render(<ReintentarEnvio comprobante={comprobante} alResultado={vi.fn()} />);
    fireEvent.click(screen.getByTestId("errores-reintentar"));

    const texto = screen.getByTestId("errores-reintentar-dialogo").textContent ?? "";
    expect(texto).toContain(`Envía ${NOMBRE} a SUNAT ahora`);
    expect(texto).toContain("con las credenciales de su empresa");
    expect(texto).toContain("Si SUNAT vuelve a fallar, queda en error de envío");
    expect(texto).toContain("se pasó el plazo, no se envía");
    expect(texto).toContain("Queda en la bitácora");
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("confirmar hace un POST al reintento de ese comprobante, recarga y le cuenta al llamador cómo terminó", async () => {
    apiRequest.mockResolvedValue(exito({ comprobante_id: ID, estado: "ACEPTADO", intentos: 3 }));
    const alResultado = vi.fn();
    render(<ReintentarEnvio comprobante={comprobante} alResultado={alResultado} />);
    fireEvent.click(screen.getByTestId("errores-reintentar"));

    fireEvent.click(screen.getByTestId("errores-reintentar-confirmar"));

    await waitFor(() => expect(alResultado).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledWith(`/api/admin/comprobantes/${ID}/reintento`, { method: "POST", body: undefined });
    expect(alResultado).toHaveBeenCalledWith(`${NOMBRE}: SUNAT lo aceptó.`);
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("si SUNAT vuelve a fallar no es un error: el resultado dice el intento y el fault", async () => {
    apiRequest.mockResolvedValue(exito({ comprobante_id: ID, estado: "ERROR_ENVIO", intentos: 4, fault: { codigo: "0000", mensaje: "SUNAT respondió HTTP 503" } }));
    const alResultado = vi.fn();
    render(<ReintentarEnvio comprobante={comprobante} alResultado={alResultado} />);
    fireEvent.click(screen.getByTestId("errores-reintentar"));

    fireEvent.click(screen.getByTestId("errores-reintentar-confirmar"));

    await waitFor(() => expect(alResultado).toHaveBeenCalledWith(`${NOMBRE}: SUNAT volvió a fallar (intento 4). 0000 - SUNAT respondió HTTP 503`));
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it.each(["ESTADO_NO_ENVIABLE", "FUERA_DE_PLAZO", "NO_ENCONTRADO"])("un %s dice por qué no se pudo, deja el diálogo abierto y recarga para mostrar el estado real", async (codigo) => {
    apiRequest.mockResolvedValue(error(codigo, "No se pudo reintentar"));
    const alResultado = vi.fn();
    render(<ReintentarEnvio comprobante={comprobante} alResultado={alResultado} />);
    fireEvent.click(screen.getByTestId("errores-reintentar"));

    fireEvent.click(screen.getByTestId("errores-reintentar-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toBe("No se pudo reintentar");
    expect(refresh).toHaveBeenCalledTimes(1);
    // La recarga puede sacar la fila de la lista y con ella el diálogo: el mensaje también va donde sobrevive.
    expect(alResultado).toHaveBeenCalledExactlyOnceWith(`${NOMBRE}: No se pudo reintentar`);
  });

  it("cualquier otro error del backend se muestra pero no recarga", async () => {
    apiRequest.mockResolvedValue(error("ERROR_RARO", "Algo falló"));
    const alResultado = vi.fn();
    render(<ReintentarEnvio comprobante={comprobante} alResultado={alResultado} />);
    fireEvent.click(screen.getByTestId("errores-reintentar"));

    fireEvent.click(screen.getByTestId("errores-reintentar-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toBe("Algo falló");
    expect(refresh).not.toHaveBeenCalled();
    expect(alResultado).not.toHaveBeenCalled();
  });
});

describe("DescartarComprobante (#196)", () => {
  const abrir = () => {
    fireEvent.click(screen.getByTestId("errores-descartar"));
  };
  const motivo = () => screen.getByTestId("errores-motivo") as HTMLTextAreaElement;

  it("el diálogo avisa que es irreversible, que el número queda consumido y que queda en la bitácora con el motivo", () => {
    render(<DescartarComprobante comprobante={comprobante} alResultado={vi.fn()} />);
    abrir();

    const texto = screen.getByTestId("errores-descartar-dialogo").textContent ?? "";
    expect(texto).toContain(`Deja de intentar enviar ${NOMBRE}`);
    expect(texto).toContain("No se puede deshacer");
    expect(texto).toContain("el número de este queda consumido");
    expect(texto).toContain("Queda en la bitácora con tu motivo");
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("pide un motivo, de hasta 200 caracteres", () => {
    render(<DescartarComprobante comprobante={comprobante} alResultado={vi.fn()} />);
    abrir();

    expect(screen.getByLabelText("Motivo")).toBe(motivo());
    expect(motivo().maxLength).toBe(200);
    expect(screen.getByText("Obligatorio. Queda en la bitácora. Hasta 200 caracteres.")).toBeTruthy();
  });

  it("confirmar manda el motivo tal cual a la ruta de ese comprobante, recarga y le cuenta al llamador que se descartó", async () => {
    apiRequest.mockResolvedValue(exito({ comprobante_id: ID, estado: "DESCARTADO" }));
    const alResultado = vi.fn();
    render(<DescartarComprobante comprobante={comprobante} alResultado={alResultado} />);
    abrir();
    fireEvent.change(motivo(), { target: { value: "El cliente lo reemitió" } });

    fireEvent.click(screen.getByTestId("errores-descartar-confirmar"));

    await waitFor(() => expect(alResultado).toHaveBeenCalledWith(`${NOMBRE} se descartó.`));
    expect(apiRequest).toHaveBeenCalledWith(`/api/admin/comprobantes/${ID}/descarte`, { method: "POST", body: { motivo: "El cliente lo reemitió" } });
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("sin motivo el backend lo rechaza: el diálogo muestra su mensaje, sigue abierto y no recarga", async () => {
    apiRequest.mockResolvedValue(error("MOTIVO_REQUERIDO", "Indica por qué se descarta el comprobante"));
    const alResultado = vi.fn();
    render(<DescartarComprobante comprobante={comprobante} alResultado={alResultado} />);
    abrir();

    fireEvent.click(screen.getByTestId("errores-descartar-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toBe("Indica por qué se descarta el comprobante");
    expect(apiRequest).toHaveBeenCalledWith(expect.any(String), { method: "POST", body: { motivo: "" } });
    expect(screen.getByTestId("errores-descartar-dialogo")).toBeTruthy();
    expect(refresh).not.toHaveBeenCalled();
    expect(alResultado).not.toHaveBeenCalled();
  });

  it.each(["ESTADO_NO_DESCARTABLE", "ESTADO_CONFLICTO", "NO_ENCONTRADO", "SUNAT_YA_LO_TIENE"])("un %s dice que el estado cambió y recarga para mostrar el real", async (codigo) => {
    apiRequest.mockResolvedValue(error(codigo, "Cambió de estado"));
    const alResultado = vi.fn();
    render(<DescartarComprobante comprobante={comprobante} alResultado={alResultado} />);
    abrir();
    fireEvent.change(motivo(), { target: { value: "x" } });

    fireEvent.click(screen.getByTestId("errores-descartar-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toBe("Cambió de estado");
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(alResultado).toHaveBeenCalledExactlyOnceWith(`${NOMBRE}: Cambió de estado`);
  });

  it("cancelar y volver a abrir deja el motivo vacío: lo escrito para un comprobante no se arrastra", () => {
    render(<DescartarComprobante comprobante={comprobante} alResultado={vi.fn()} />);
    abrir();
    fireEvent.change(motivo(), { target: { value: "algo a medias" } });

    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    abrir();

    expect(motivo().value).toBe("");
  });

  it("es de peligro: el botón de confirmar es el destructivo", () => {
    render(<DescartarComprobante comprobante={comprobante} alResultado={vi.fn()} />);
    abrir();

    expect(screen.getByTestId("errores-descartar-confirmar").className).toContain("destructive");
  });
});
