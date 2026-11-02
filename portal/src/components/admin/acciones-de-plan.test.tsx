import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PlanAdmin } from "@/lib/api/admin-planes";
import type { ApiEnvelope } from "@/lib/api/types";
import { AccionesDePlan } from "./acciones-de-plan";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

afterEach(() => {
  cleanup();
  refresh.mockClear();
  apiRequest.mockReset();
});

const exito = (): ApiEnvelope<unknown> => ({ estado: "exito", datos: {}, mensaje: null, codigo: null, errores: null });
const error = (codigo: string, mensaje: string): ApiEnvelope<unknown> => ({ estado: "error", datos: null, mensaje, codigo, errores: null });

const PLAN: PlanAdmin = {
  id: "p-1",
  nombre: "Negocio",
  precio_mensual: 69,
  limites: { documentos_al_mes: { maximo: 1500, ilimitado: false }, rucs: 3, usuarios: { maximo: 3, ilimitado: false }, api_keys: { maximo: 5, ilimitado: false }, retencion_anios: 5 },
  estado: "ACTIVO",
  por_defecto: false, visible_en_publicidad: true,
  cuentas: 0,
};

const montar = (plan: Partial<PlanAdmin> = {}) => render(<AccionesDePlan plan={{ ...PLAN, ...plan }} />);

const hay = (testId: string) => screen.queryByTestId(testId) !== null;

/** Las acciones de cada fila de la tabla de planes (#190): solo se ofrece lo que el plan permite. */
describe("AccionesDePlan — qué se ofrece", () => {
  it("un plan activo y sin cuentas se puede editar, desactivar y borrar", () => {
    montar();

    expect(hay("plan-editar-p-1")).toBe(true);
    expect(hay("plan-desactivar-p-1")).toBe(true);
    expect(hay("plan-borrar-p-1")).toBe(true);
    expect(hay("plan-activar-p-1")).toBe(false);
  });

  /** «Un plan con cuentas activas no se puede borrar, solo desactivar». */
  it("con cuentas no se ofrece borrar, solo desactivar", () => {
    montar({ cuentas: 3 });

    expect(hay("plan-desactivar-p-1")).toBe(true);
    expect(hay("plan-borrar-p-1")).toBe(false);
  });

  it("un plan inactivo se puede volver a ofrecer en vez de desactivar", () => {
    montar({ estado: "INACTIVO" });

    expect(hay("plan-activar-p-1")).toBe(true);
    expect(hay("plan-desactivar-p-1")).toBe(false);
    expect(hay("plan-editar-p-1")).toBe(true);
  });

  it("el plan por defecto solo se edita: ni desactivar ni borrar", () => {
    montar({ por_defecto: true });

    expect(hay("plan-editar-p-1")).toBe(true);
    expect(hay("plan-desactivar-p-1")).toBe(false);
    expect(hay("plan-borrar-p-1")).toBe(false);
  });
});

describe("AccionesDePlan — desactivar", () => {
  it("el diálogo dice que no toca a las cuentas que ya lo tienen, y cuántas son", () => {
    montar({ cuentas: 12 });
    fireEvent.click(screen.getByTestId("plan-desactivar-p-1"));

    const dialogo = screen.getByTestId("plan-desactivar-p-1-dialogo").textContent ?? "";
    expect(dialogo).toContain("Desactivar Negocio");
    expect(dialogo).toContain("no se podrá asignar a cuentas nuevas");
    expect(dialogo).toContain("Las 12 cuentas que lo tienen siguen con él: su suscripción no cambia.");
    expect(dialogo).toContain("Se puede volver a ofrecer cuando quieras.");
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("con una sola cuenta lo dice en singular y sin ninguna, que nadie lo tiene", () => {
    montar({ cuentas: 1 });
    fireEvent.click(screen.getByTestId("plan-desactivar-p-1"));
    expect(screen.getByTestId("plan-desactivar-p-1-dialogo").textContent).toContain("La cuenta que lo tiene sigue con él");
    cleanup();

    montar({ cuentas: 0 });
    fireEvent.click(screen.getByTestId("plan-desactivar-p-1"));
    expect(screen.getByTestId("plan-desactivar-p-1-dialogo").textContent).toContain("Ninguna cuenta lo tiene hoy.");
  });

  it("confirmar hace POST a /api/admin/planes/{id}/desactivar y recarga", async () => {
    apiRequest.mockResolvedValue(exito());
    montar();
    fireEvent.click(screen.getByTestId("plan-desactivar-p-1"));

    fireEvent.click(screen.getByTestId("plan-desactivar-p-1-confirmar"));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledWith("/api/admin/planes/p-1/desactivar", { method: "POST", body: undefined });
  });

  it("si otro administrador ya lo desactivó, lo dice y recarga para mostrar el estado real", async () => {
    apiRequest.mockResolvedValue(error("PLAN_YA_INACTIVO", "El plan ya está inactivo"));
    montar();
    fireEvent.click(screen.getByTestId("plan-desactivar-p-1"));

    fireEvent.click(screen.getByTestId("plan-desactivar-p-1-confirmar"));

    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("ya está inactivo"));
    expect(refresh).toHaveBeenCalledTimes(1);
  });
});

describe("AccionesDePlan — activar", () => {
  it("confirmar hace POST a /api/admin/planes/{id}/activar", async () => {
    apiRequest.mockResolvedValue(exito());
    montar({ estado: "INACTIVO" });
    fireEvent.click(screen.getByTestId("plan-activar-p-1"));
    expect(screen.getByTestId("plan-activar-p-1-dialogo").textContent).toContain("Volver a ofrecer Negocio");

    fireEvent.click(screen.getByTestId("plan-activar-p-1-confirmar"));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledWith("/api/admin/planes/p-1/activar", { method: "POST", body: undefined });
  });
});

describe("AccionesDePlan — borrar", () => {
  it("el diálogo avisa que es para siempre y que con historial no se puede", () => {
    montar();
    fireEvent.click(screen.getByTestId("plan-borrar-p-1"));

    const dialogo = screen.getByTestId("plan-borrar-p-1-dialogo").textContent ?? "";
    expect(dialogo).toContain("Borrar Negocio");
    expect(dialogo).toContain("se borra para siempre");
    expect(dialogo).toContain("hay que desactivarlo");
  });

  it("confirmar hace DELETE a /api/admin/planes/{id} y recarga", async () => {
    apiRequest.mockResolvedValue(exito());
    montar();
    fireEvent.click(screen.getByTestId("plan-borrar-p-1"));

    fireEvent.click(screen.getByTestId("plan-borrar-p-1-confirmar"));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledWith("/api/admin/planes/p-1", { method: "DELETE", body: undefined });
  });

  /** El plan tuvo cuentas (historial) o alguien se lo asignó justo ahora: el backend lo rechaza y el mensaje explica por qué. */
  it("si el backend dice que está en uso, muestra su mensaje y recarga (la página estaba vieja)", async () => {
    apiRequest.mockResolvedValue(error("PLAN_EN_USO", "El plan tiene historial de suscripciones: desactívalo en vez de borrarlo"));
    montar();
    fireEvent.click(screen.getByTestId("plan-borrar-p-1"));

    fireEvent.click(screen.getByTestId("plan-borrar-p-1-confirmar"));

    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("historial de suscripciones"));
    expect(refresh).toHaveBeenCalledTimes(1);
  });
});
