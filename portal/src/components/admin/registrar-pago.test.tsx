import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PlanDeCuentaAdmin } from "@/lib/api/admin-plan-de-cuenta";
import type { ApiEnvelope } from "@/lib/api/types";
import { elegirFecha, fechaElegida, fechaEsElegible } from "@/test/fecha-de-prueba";
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
/** La casilla del design system (base-ui): un `role="checkbox"` con `aria-checked` y `aria-disabled`, no un `<input>`. */
const casillaExtender = () => screen.getByRole("checkbox", { name: "Extender el vencimiento del plan hasta el final del periodo" });
const extender = () => {
  const c = casillaExtender();
  return { checked: c.getAttribute("aria-checked") === "true", disabled: c.getAttribute("aria-disabled") === "true" };
};
const nota = () => screen.getByTestId("registrar-pago-extender-nota").textContent;

/** Lo mínimo para un pago válido de octubre; `hasta` es el último día que cubre. */
async function llenar(hasta = "2026-11-20") {
  await elegirFecha("Periodo hasta (inclusive)", hasta);
  escribir("Monto (S/)", "29.00");
  escribir("Medio de pago", "YAPE");
}

describe("RegistrarPago", () => {
  it("el botón abre el modal con el nombre de la cuenta y la fecha de pago de hoy", async () => {
    abrir();

    expect(screen.getByTestId("registrar-pago-dialogo")).toBeTruthy();
    expect(screen.getByText("Registrar un pago de Mi negocio")).toBeTruthy();
    expect(await fechaElegida("Fecha de pago")).toBe(HOY);
  });

  it("el periodo sugerido empieza el día siguiente a donde está pagada la cuenta", async () => {
    abrir(EMPRENDE);

    expect(await fechaElegida("Periodo desde")).toBe("2026-10-21");
  });

  it("si la cuenta ya está vencida o no vence, el periodo sugerido empieza hoy", async () => {
    abrir({ ...EMPRENDE, vence_en: "2026-10-01T05:00:00Z" });
    expect(await fechaElegida("Periodo desde")).toBe(HOY);
    cleanup();

    abrir(GRATIS);
    expect(await fechaElegida("Periodo desde")).toBe(HOY);
  });

  it("ofrece los siete medios de pago", async () => {
    abrir();

    const opciones = [...(campo("Medio de pago") as HTMLSelectElement).options].map((o) => o.textContent);
    expect(opciones).toEqual(["Elige el medio", "Transferencia", "Depósito", "Yape", "Plin", "Tarjeta", "Efectivo", "Otro"]);
  });

  // --- extender el vencimiento ------------------------------------------------------------------------------------------------------------

  it("si el periodo adelanta el vencimiento, la casilla viene marcada y dice de qué día a qué día se mueve", async () => {
    abrir();

    await elegirFecha("Periodo hasta (inclusive)", "2026-11-20");

    expect(extender().checked).toBe(true);
    expect(extender().disabled).toBe(false);
    expect(nota()).toBe("Hoy está pagada hasta el 20 Oct 2026; pasaría al 20 Nov 2026.");
  });

  /**
   * H6: antes aparecía vacía y se marcaba sola al elegir el fin del periodo; quien la veía vacía y hacía clic para marcarla la apagaba, y el pago quedaba sin
   * extender. Ahora se ve marcada desde el principio, deshabilitada mientras no haya periodo, y elegirlo no la cambia.
   */
  it("sin el fin del periodo la casilla ya se ve marcada, esperando, y elegirlo no la cambia (H6)", async () => {
    abrir();

    expect(extender().checked).toBe(true);
    expect(extender().disabled).toBe(true);
    expect(nota()).toBe("Elige el fin del periodo para ver el nuevo vencimiento.");

    await elegirFecha("Periodo hasta (inclusive)", "2026-11-20");
    expect(extender().checked).toBe(true);
    expect(extender().disabled).toBe(false);
  });

  it("un periodo que no adelanta el vencimiento deshabilita la casilla y lo explica, el mismo día incluido", async () => {
    abrir();

    await elegirFecha("Periodo hasta (inclusive)", "2026-10-20");

    expect(extender().checked).toBe(false);
    expect(extender().disabled).toBe(true);
    expect(nota()).toBe("La cuenta ya está pagada hasta el 20 Oct 2026: este periodo no adelanta el vencimiento.");
  });

  it("un día más que el actual ya adelanta el vencimiento", async () => {
    abrir();

    await elegirFecha("Periodo hasta (inclusive)", "2026-10-21");

    expect(extender().disabled).toBe(false);
    expect(extender().checked).toBe(true);
  });

  it("un plan que no vence no se puede extender", async () => {
    abrir(GRATIS);

    await elegirFecha("Periodo hasta (inclusive)", "2026-11-20");

    expect(extender().checked).toBe(false);
    expect(extender().disabled).toBe(true);
    expect(nota()).toBe("El plan de esta cuenta no vence: no hay vencimiento que extender.");
  });

  it("si no se pudo cargar el plan, no se ofrece extender y se dice por qué", async () => {
    abrir(null);

    await elegirFecha("Periodo hasta (inclusive)", "2026-11-20");

    expect(extender().disabled).toBe(true);
    expect(nota()).toBe("No se pudo cargar el plan de la cuenta, así que desde acá no se puede extender el vencimiento.");
  });

  it("el administrador puede desmarcar la extensión y la elección se respeta al cambiar el periodo", async () => {
    abrir();
    await elegirFecha("Periodo hasta (inclusive)", "2026-11-20");

    fireEvent.click(casillaExtender());
    expect(extender().checked).toBe(false);
    await elegirFecha("Periodo hasta (inclusive)", "2026-12-20");

    expect(extender().checked).toBe(false);
    expect(extender().disabled).toBe(false);
  });

  // --- enviar -----------------------------------------------------------------------------------------------------------------------------

  it("confirmar manda el pago completo, con la extensión marcada, y luego cierra y recarga la página", async () => {
    apiRequest.mockResolvedValue(exito());
    abrir();
    await llenar("2026-11-20");
    await elegirFecha("Fecha de pago", "2026-10-14");
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
    await llenar("2026-11-20");
    fireEvent.click(casillaExtender());

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
    await llenar("2026-11-20");

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    await waitFor(() => expect(apiRequest).toHaveBeenCalledTimes(1));
    expect(apiRequest.mock.calls[0][1].body.extender_vencimiento).toBe(false);
  });

  // El inicio del periodo viene sugerido y el calendario no lo deja en blanco: «Indica desde cuándo» lo prueba `pago-formulario.test.ts`.
  it("confirmar con el formulario vacío muestra los errores, no llama al backend y los quita al corregir cada campo", async () => {
    abrir();

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    expect(apiRequest).not.toHaveBeenCalled();
    expect(screen.getByText("Indica hasta cuándo cubre el pago.")).toBeTruthy();
    // La fecha del design system se marca como el input nativo: inválida y descrita por su error.
    expect(campo("Periodo hasta (inclusive)").getAttribute("aria-invalid")).toBe("true");
    expect(campo("Periodo hasta (inclusive)").getAttribute("aria-describedby")).toBe("registrar-pago-hasta-error");
    expect(screen.getByText("Escribe el monto.")).toBeTruthy();
    expect(screen.getByText("Elige el medio de pago.")).toBeTruthy();

    await elegirFecha("Periodo hasta (inclusive)", "2026-11-20");
    escribir("Monto (S/)", "29");
    escribir("Medio de pago", "YAPE");
    expect(screen.queryByText("Indica desde cuándo cubre el pago.")).toBeNull();
    expect(screen.queryByText("Indica hasta cuándo cubre el pago.")).toBeNull();
    expect(screen.queryByText("Escribe el monto.")).toBeNull();
    expect(screen.queryByText("Elige el medio de pago.")).toBeNull();
  });

  // El calendario no ofrece un día futuro; la regla («no puede ser futura») la prueba `pago-formulario.test.ts`.
  it("una fecha de pago futura no se puede elegir: hoy sí", async () => {
    abrir();

    expect(await fechaEsElegible("Fecha de pago", "2026-10-16")).toBe(false);
    expect(await fechaEsElegible("Fecha de pago", HOY)).toBe(true);
  });

  it("corregir el fin del periodo quita el error del periodo y el de la extensión", async () => {
    abrir();
    await elegirFecha("Periodo desde", "2026-11-01");
    await elegirFecha("Periodo hasta (inclusive)", "2026-10-01");
    escribir("Monto (S/)", "29");
    escribir("Medio de pago", "YAPE");
    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));
    expect(screen.getByText("El periodo no puede terminar antes de empezar.")).toBeTruthy();

    await elegirFecha("Periodo hasta (inclusive)", "2026-11-30");

    expect(screen.queryByText("El periodo no puede terminar antes de empezar.")).toBeNull();
  });

  /** Un doble clic en el mismo tick no puede anotar el pago dos veces: la guarda es un ref, no el estado. */
  it("dos clics a la vez mandan un solo pago", async () => {
    let terminar!: (r: ApiEnvelope<unknown>) => void;
    apiRequest.mockReturnValue(new Promise((resolve) => (terminar = resolve)));
    abrir();
    await llenar();
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
    await llenar();

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
    await llenar();

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
    apiRequest.mockResolvedValue(error("PAGO_DUPLICADO", "Esa cuenta ya tiene un pago por Yape con la referencia «OP-1»"));
    abrir();
    await llenar();

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toContain("ya tiene un pago por Yape");
    expect(screen.getByTestId("registrar-pago-dialogo")).toBeTruthy();
    expect(refresh).not.toHaveBeenCalled();
  });

  it("si otro administrador movió el vencimiento, lo dice y recarga la página para ver lo real", async () => {
    apiRequest.mockResolvedValue(error("CAMBIO_CONCURRENTE", "Otro administrador movió el vencimiento de la cuenta mientras tanto: vuelve a mirarla"));
    abrir();
    await llenar();

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toContain("Otro administrador");
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("registrar-pago-dialogo")).toBeTruthy();
  });

  it("si la cuenta ya no existe, recarga la página", async () => {
    apiRequest.mockResolvedValue(error("NO_ENCONTRADO", "La cuenta no existe"));
    abrir();
    await llenar();

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    await screen.findByRole("alert");
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("un rechazo de validación del backend no recarga la página", async () => {
    apiRequest.mockResolvedValue(error("MONTO_INVALIDO", "El monto debe ser mayor que cero"));
    abrir();
    await llenar();

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toContain("El monto debe ser mayor que cero");
    expect(refresh).not.toHaveBeenCalled();
  });

  /** Si se cortó la conexión no se sabe si el pago llegó: no se reintenta a ciegas (podría quedar anotado dos veces), se manda a mirar. */
  it("un corte de red no reintenta y manda a recargar para ver si quedó registrado", async () => {
    apiRequest.mockResolvedValue({ estado: "error", datos: null, mensaje: "Se cortó la conexión.", codigo: "RED", errores: null });
    abrir();
    await llenar();

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toContain("Recarga la página para ver si quedó registrado.");
    expect(apiRequest).toHaveBeenCalledTimes(1);
    expect(refresh).not.toHaveBeenCalled();
  });

  it("una respuesta que no es JSON se trata igual que un corte", async () => {
    apiRequest.mockResolvedValue({ estado: "error", datos: null, mensaje: "Respuesta inválida.", codigo: "RESPUESTA_INVALIDA", errores: null });
    abrir();
    await llenar();

    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));

    expect((await screen.findByRole("alert")).textContent).toContain("Recarga la página");
  });

  // --- cerrar -----------------------------------------------------------------------------------------------------------------------------

  it("cancelar cierra el modal y lo escrito no sobrevive a abrirlo otra vez", async () => {
    abrir();
    await llenar();
    escribir("Referencia", "OP-9");

    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(screen.queryByTestId("registrar-pago-dialogo")).toBeNull();
    fireEvent.click(screen.getByTestId("registrar-pago"));

    expect(await fechaElegida("Periodo hasta (inclusive)")).toBeNull();
    expect(campo("Monto (S/)").value).toBe("");
    expect(campo("Medio de pago").value).toBe("");
    expect(campo("Referencia").value).toBe("");
    expect(await fechaElegida("Periodo desde")).toBe("2026-10-21");
    expect(await fechaElegida("Fecha de pago")).toBe(HOY);
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("tras registrar un pago el modal se abre limpio para el siguiente", async () => {
    apiRequest.mockResolvedValue(exito());
    abrir();
    await llenar();
    fireEvent.click(screen.getByTestId("registrar-pago-confirmar"));
    await waitFor(() => expect(refresh).toHaveBeenCalled());

    fireEvent.click(screen.getByTestId("registrar-pago"));

    expect(campo("Monto (S/)").value).toBe("");
    // Como recién abierto (H6): marcada y en espera del periodo.
    expect(extender().checked).toBe(true);
    expect(extender().disabled).toBe(true);
  });
});
