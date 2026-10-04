import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PlanDeCuentaAdmin } from "@/lib/api/admin-plan-de-cuenta";
import type { ApiEnvelope } from "@/lib/api/types";
import { RegistrarPago } from "./registrar-pago";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

afterEach(() => {
  cleanup();
  refresh.mockClear();
  apiRequest.mockReset();
});

const CUENTA = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const HOY = "2026-10-15";
const exito = (datos: unknown = {}): ApiEnvelope<unknown> => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });
const error = (codigo: string, mensaje: string): ApiEnvelope<unknown> => ({ estado: "error", datos: null, mensaje, codigo, errores: null });

const LIMITES = { documentos_al_mes: { maximo: 300, ilimitado: false }, rucs: 1, usuarios: { maximo: 1, ilimitado: false }, api_keys: { maximo: 2, ilimitado: false }, retencion_anios: 5 };
/** Pagada hasta el 20 de octubre (el vencimiento es la medianoche de Lima del 21). */
const EMPRENDE: PlanDeCuentaAdmin = {
  cuenta_id: CUENTA,
  plan: { id: "p1", nombre: "Emprende", precio_mensual: 29, limites: LIMITES },
  estado: "VIGENTE",
  inicia_en: "2026-09-21T05:00:00Z",
  vence_en: "2026-10-21T05:00:00Z",
  dias_de_gracia: 3,
  hasta_cuando_cubre: "2026-10-24T05:00:00Z",
};
const GRATIS: PlanDeCuentaAdmin = { cuenta_id: CUENTA, plan: { id: "p0", nombre: "Gratis", precio_mensual: 0, limites: LIMITES }, estado: "VIGENTE", inicia_en: "2026-09-01T05:00:00Z", dias_de_gracia: 0 };

function abrir(plan: PlanDeCuentaAdmin | null = EMPRENDE) {
  render(<RegistrarPago cuentaId={CUENTA} cuentaNombre="Mi negocio" plan={plan} hoy={HOY} />);
  fireEvent.click(screen.getByTestId("registrar-pago"));
}

const campo = (etiqueta: string | RegExp) => screen.getByLabelText(etiqueta) as HTMLInputElement | HTMLSelectElement;
const escribir = (etiqueta: string | RegExp, valor: string) => fireEvent.change(campo(etiqueta), { target: { value: valor } });
const extender = () => screen.getByTestId("registrar-pago-extender") as HTMLInputElement;
const nota = () => screen.getByTestId("registrar-pago-extender-nota").textContent;

/** Lo mínimo para un pago válido de octubre; `hasta` es el último día que cubre. */
function llenar(hasta = "2026-11-20") {
  escribir("Periodo hasta (inclusive)", hasta);
  escribir("Monto (S/)", "29.00");
  escribir("Medio de pago", "YAPE");
}

describe("RegistrarPago", () => {
  it("el botón abre el modal con el nombre de la cuenta y la fecha de pago de hoy", () => {
    abrir();

    expect(screen.getByTestId("registrar-pago-dialogo")).toBeTruthy();
    expect(screen.getByText("Registrar un pago de Mi negocio")).toBeTruthy();
    expect(campo("Fecha de pago").value).toBe(HOY);
  });

  it("el periodo sugerido empieza el día siguiente a donde está pagada la cuenta", () => {
    abrir(EMPRENDE);

    expect(campo("Periodo desde").value).toBe("2026-10-21");
  });

  it("si la cuenta ya está vencida o no vence, el periodo sugerido empieza hoy", () => {
    abrir({ ...EMPRENDE, vence_en: "2026-10-01T05:00:00Z" });
    expect(campo("Periodo desde").value).toBe(HOY);
    cleanup();

    abrir(GRATIS);
    expect(campo("Periodo desde").value).toBe(HOY);
  });

  it("ofrece los siete medios de pago", () => {
    abrir();

    const opciones = [...(campo("Medio de pago") as HTMLSelectElement).options].map((o) => o.textContent);
    expect(opciones).toEqual(["Elige el medio", "Transferencia", "Depósito", "Yape", "Plin", "Tarjeta", "Efectivo", "Otro"]);
  });

  // --- extender el vencimiento ------------------------------------------------------------------------------------------------------------

  it("si el periodo adelanta el vencimiento, la casilla viene marcada y dice de qué día a qué día se mueve", () => {
    abrir();

    escribir("Periodo hasta (inclusive)", "2026-11-20");

    expect(extender().checked).toBe(true);
    expect(extender().disabled).toBe(false);
    expect(nota()).toBe("Hoy está pagada hasta el 20 Oct 2026; pasaría al 20 Nov 2026.");
  });

  it("sin el fin del periodo, la casilla espera y lo dice", () => {
    abrir();

    expect(extender().checked).toBe(false);
    expect(extender().disabled).toBe(true);
    expect(nota()).toBe("Elige el fin del periodo para ver el nuevo vencimiento.");
  });

  it("un periodo que no adelanta el vencimiento deshabilita la casilla y lo explica, el mismo día incluido", () => {
    abrir();

    escribir("Periodo hasta (inclusive)", "2026-10-20");

    expect(extender().checked).toBe(false);
    expect(extender().disabled).toBe(true);
    expect(nota()).toBe("La cuenta ya está pagada hasta el 20 Oct 2026: este periodo no adelanta el vencimiento.");
  });

  it("un día más que el actual ya adelanta el vencimiento", () => {
    abrir();

    escribir("Periodo hasta (inclusive)", "2026-10-21");

    expect(extender().disabled).toBe(false);
    expect(extender().checked).toBe(true);
  });

  it("un plan que no vence no se puede extender", () => {
    abrir(GRATIS);

    escribir("Periodo hasta (inclusive)", "2026-11-20");

    expect(extender().checked).toBe(false);
    expect(extender().disabled).toBe(true);
    expect(nota()).toBe("El plan de esta cuenta no vence: no hay vencimiento que extender.");
  });

  it("si no se pudo cargar el plan, no se ofrece extender y se dice por qué", () => {
    abrir(null);

    escribir("Periodo hasta (inclusive)", "2026-11-20");

    expect(extender().disabled).toBe(true);
    expect(nota()).toBe("No se pudo cargar el plan de la cuenta, así que desde acá no se puede extender el vencimiento.");
  });

  it("el administrador puede desmarcar la extensión y la elección se respeta al cambiar el periodo", () => {
    abrir();
    escribir("Periodo hasta (inclusive)", "2026-11-20");

    fireEvent.click(extender());
    expect(extender().checked).toBe(false);
    escribir("Periodo hasta (inclusive)", "2026-12-20");

    expect(extender().checked).toBe(false);
    expect(extender().disabled).toBe(false);
  });

  // --- enviar -----------------------------------------------------------------------------------------------------------------------------

  it("confirmar manda el pago completo, con la extensión marcada, y luego cierra y recarga la página", async () => {
    apiRequest.mockResolvedValue(exito());
    abrir();
    llenar("2026-11-20");
    escribir("Fecha de pago", "2026-10-14");
    escribir("Referencia", "  OP-123  ");
    escribir("Nota", "Pagó por Yape");

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledWith(`/api/admin/cuentas/${CUENTA}/pagos`, {
      method: "POST",
      body: { periodo_desde: "2026-10-21", periodo_hasta: "2026-11-20", monto: 29, medio: "YAPE", fecha_de_pago: "2026-10-14", referencia: "OP-123", nota: "Pagó por Yape", extender_vencimiento: true },
    });
    expect(screen.queryByTestId("registrar-pago-dialogo")).toBeNull();
  });

  it("sin referencia ni nota no las manda, y con la extensión desmarcada manda false", async () => {
    apiRequest.mockResolvedValue(exito());
    abrir();
    llenar("2026-11-20");
    fireEvent.click(extender());

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    await waitFor(() => expect(apiRequest).toHaveBeenCalledTimes(1));
    const cuerpo = apiRequest.mock.calls[0][1].body;
    expect(cuerpo).not.toHaveProperty("referencia");
    expect(cuerpo).not.toHaveProperty("nota");
    expect(cuerpo.extender_vencimiento).toBe(false);
  });

  it("un pago de una cuenta sin vencimiento se manda sin extender", async () => {
    apiRequest.mockResolvedValue(exito());
    abrir(GRATIS);
    llenar("2026-11-20");

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    await waitFor(() => expect(apiRequest).toHaveBeenCalledTimes(1));
    expect(apiRequest.mock.calls[0][1].body.extender_vencimiento).toBe(false);
  });

  it("confirmar con el formulario vacío muestra los errores, no llama al backend y los quita al corregir cada campo", () => {
    abrir();
    escribir("Periodo desde", "");

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    expect(apiRequest).not.toHaveBeenCalled();
    expect(screen.getByText("Indica desde cuándo cubre el pago.")).toBeTruthy();
    expect(screen.getByText("Indica hasta cuándo cubre el pago.")).toBeTruthy();
    expect(screen.getByText("Escribe el monto.")).toBeTruthy();
    expect(screen.getByText("Elige el medio de pago.")).toBeTruthy();

    escribir("Periodo desde", "2026-10-21");
    escribir("Periodo hasta (inclusive)", "2026-11-20");
    escribir("Monto (S/)", "29");
    escribir("Medio de pago", "YAPE");
    expect(screen.queryByText("Indica desde cuándo cubre el pago.")).toBeNull();
    expect(screen.queryByText("Indica hasta cuándo cubre el pago.")).toBeNull();
    expect(screen.queryByText("Escribe el monto.")).toBeNull();
    expect(screen.queryByText("Elige el medio de pago.")).toBeNull();
  });

  it("una fecha de pago futura se rechaza antes de enviar", () => {
    abrir();
    llenar();
    escribir("Fecha de pago", "2026-10-16");

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    expect(apiRequest).not.toHaveBeenCalled();
    expect(screen.getByText("La fecha de pago no puede ser futura.")).toBeTruthy();
  });

  it("corregir el fin del periodo quita el error del periodo y el de la extensión", () => {
    abrir();
    escribir("Periodo desde", "2026-11-01");
    escribir("Periodo hasta (inclusive)", "2026-10-01");
    escribir("Monto (S/)", "29");
    escribir("Medio de pago", "YAPE");
    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));
    expect(screen.getByText("El periodo no puede terminar antes de empezar.")).toBeTruthy();

    escribir("Periodo hasta (inclusive)", "2026-11-30");

    expect(screen.queryByText("El periodo no puede terminar antes de empezar.")).toBeNull();
  });

  /** Un doble clic en el mismo tick no puede anotar el pago dos veces: la guarda es un ref, no el estado. */
  it("dos clics a la vez mandan un solo pago", async () => {
    let terminar!: (r: ApiEnvelope<unknown>) => void;
    apiRequest.mockReturnValue(new Promise((resolve) => (terminar = resolve)));
    abrir();
    llenar();
    const boton = screen.getByTestId("registrar-pago-confirmar");

    await act(async () => {
      fireEvent.click(boton);
      fireEvent.click(boton);
    });

    expect(apiRequest).toHaveBeenCalledTimes(1);
    expect(boton.textContent).toContain("Registrando…");
    await act(async () => terminar(exito()));
  });

  it("mientras envía no se puede cancelar ni cerrar", async () => {
    let terminar!: (r: ApiEnvelope<unknown>) => void;
    apiRequest.mockReturnValue(new Promise((resolve) => (terminar = resolve)));
    abrir();
    llenar();

    await act(async () => fireEvent.click(screen.getByTestId("registrar-pago-confirmar")));

    expect((screen.getByRole("button", { name: "Cancelar" }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.queryByRole("button", { name: /Close|Cerrar/ })).toBeNull();
    await act(async () => terminar(exito()));
  });

  /** El botón y la cruz ya no están, pero Escape y el clic afuera también pasan por `onOpenChange`: tampoco cierran mientras se envía. */
  it("mientras envía, Escape tampoco cierra el modal", async () => {
    let terminar!: (r: ApiEnvelope<unknown>) => void;
    apiRequest.mockReturnValue(new Promise((resolve) => (terminar = resolve)));
    abrir();
    llenar();

    await act(async () => fireEvent.click(screen.getByTestId("registrar-pago-confirmar")));
    await act(async () => {
      fireEvent.keyDown(screen.getByTestId("registrar-pago-dialogo"), { key: "Escape" });
    });

    expect(screen.getByTestId("registrar-pago-dialogo")).toBeTruthy();
    await act(async () => terminar(exito()));
  });

  it("sin enviar, Escape sí cierra el modal", async () => {
    abrir();

    await act(async () => {
      fireEvent.keyDown(screen.getByTestId("registrar-pago-dialogo"), { key: "Escape" });
    });

    await waitFor(() => expect(screen.queryByTestId("registrar-pago-dialogo")).toBeNull());
  });

  // --- errores del backend ----------------------------------------------------------------------------------------------------------------

  it("un pago repetido muestra el motivo, no cierra y no recarga", async () => {
    apiRequest.mockResolvedValue(error("PAGO_DUPLICADO", "Esa cuenta ya tiene un pago por YAPE con la referencia «OP-1»"));
    abrir();
    llenar();

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toContain("ya tiene un pago por YAPE");
    expect(screen.getByTestId("registrar-pago-dialogo")).toBeTruthy();
    expect(refresh).not.toHaveBeenCalled();
  });

  it("si otro administrador movió el vencimiento, lo dice y recarga la página para ver lo real", async () => {
    apiRequest.mockResolvedValue(error("CAMBIO_CONCURRENTE", "Otro administrador movió el vencimiento de la cuenta mientras tanto: vuelve a mirarla"));
    abrir();
    llenar();

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toContain("Otro administrador");
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("registrar-pago-dialogo")).toBeTruthy();
  });

  it("si la cuenta ya no existe, recarga la página", async () => {
    apiRequest.mockResolvedValue(error("NO_ENCONTRADO", "La cuenta no existe"));
    abrir();
    llenar();

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    await screen.findByRole("alert");
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("un rechazo de validación del backend no recarga la página", async () => {
    apiRequest.mockResolvedValue(error("MONTO_INVALIDO", "El monto debe ser mayor que cero"));
    abrir();
    llenar();

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toContain("El monto debe ser mayor que cero");
    expect(refresh).not.toHaveBeenCalled();
  });

  /** Si se cortó la conexión no se sabe si el pago llegó: no se reintenta a ciegas (podría quedar anotado dos veces), se manda a mirar. */
  it("un corte de red no reintenta y manda a recargar para ver si quedó registrado", async () => {
    apiRequest.mockResolvedValue({ estado: "error", datos: null, mensaje: "Se cortó la conexión.", codigo: "RED", errores: null });
    abrir();
    llenar();

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toContain("Recarga la página para ver si quedó registrado.");
    expect(apiRequest).toHaveBeenCalledTimes(1);
    expect(refresh).not.toHaveBeenCalled();
  });

  it("una respuesta que no es JSON se trata igual que un corte", async () => {
    apiRequest.mockResolvedValue({ estado: "error", datos: null, mensaje: "Respuesta inválida.", codigo: "RESPUESTA_INVALIDA", errores: null });
    abrir();
    llenar();

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toContain("Recarga la página");
  });

  // --- cerrar -----------------------------------------------------------------------------------------------------------------------------

  it("cancelar cierra el modal y lo escrito no sobrevive a abrirlo otra vez", () => {
    abrir();
    llenar();
    escribir("Referencia", "OP-9");

    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(screen.queryByTestId("registrar-pago-dialogo")).toBeNull();
    fireEvent.click(screen.getByTestId("registrar-pago"));

    expect(campo("Periodo hasta (inclusive)").value).toBe("");
    expect(campo("Monto (S/)").value).toBe("");
    expect(campo("Medio de pago").value).toBe("");
    expect(campo("Referencia").value).toBe("");
    expect(campo("Periodo desde").value).toBe("2026-10-21");
    expect(campo("Fecha de pago").value).toBe(HOY);
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("tras registrar un pago el modal se abre limpio para el siguiente", async () => {
    apiRequest.mockResolvedValue(exito());
    abrir();
    llenar();
    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));
    await waitFor(() => expect(refresh).toHaveBeenCalled());

    fireEvent.click(screen.getByTestId("registrar-pago"));

    expect(campo("Monto (S/)").value).toBe("");
    expect(extender().checked).toBe(false);
  });
});
