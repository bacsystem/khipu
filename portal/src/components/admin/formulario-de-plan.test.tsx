import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { PlanAdmin } from "@/lib/api/admin-planes";
import type { ApiEnvelope } from "@/lib/api/types";
import { FormularioDePlan } from "./formulario-de-plan";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/browser", () => ({ apiRequest }));

beforeEach(() => {
  // 15 de octubre de 2026, 10:00 en Lima: el ciclo siguiente empieza el 1 de noviembre.
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-10-15T15:00:00Z"));
});

afterEach(() => {
  vi.useRealTimers();
  cleanup();
  refresh.mockClear();
  apiRequest.mockReset();
});

const exito = (datos: unknown = {}): ApiEnvelope<unknown> => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });
const error = (codigo: string, mensaje: string): ApiEnvelope<unknown> => ({ estado: "error", datos: null, mensaje, codigo, errores: null });

const PLAN: PlanAdmin = {
  id: "p-1",
  nombre: "Negocio",
  precio_mensual: 69,
  limites: { documentos_al_mes: { maximo: 1500, ilimitado: false }, rucs: 3, usuarios: { maximo: 3, ilimitado: false }, api_keys: { maximo: 5, ilimitado: false }, retencion_anios: 5 },
  estado: "ACTIVO",
  por_defecto: false,
  cuentas: 4,
};

function abrirNuevo() {
  render(<FormularioDePlan />);
  fireEvent.click(screen.getByTestId("plan-nuevo"));
}

function abrirEdicion(plan: PlanAdmin = PLAN) {
  render(<FormularioDePlan plan={plan} />);
  fireEvent.click(screen.getByTestId(`plan-editar-${plan.id}`));
}

const campo = (id: string) => screen.getByLabelText(id) as HTMLInputElement;

function llenar(valores: Record<string, string>) {
  for (const [etiqueta, valor] of Object.entries(valores)) fireEvent.change(campo(etiqueta), { target: { value: valor } });
}

const VALIDOS = {
  Nombre: "Estudio",
  "Precio mensual (S/)": "49.90",
  "Documentos por mes": "800",
  RUC: "2",
  Usuarios: "3",
  "API keys": "5",
  "Retención de XML y CDR (años)": "6",
};

/** Crear y editar planes (#190): el modal dice el efecto, valida lo evidente, y el backend decide lo demás. */
describe("FormularioDePlan — nuevo", () => {
  it("abrirlo solo lo muestra, con los campos vacíos y sin enviar nada", () => {
    abrirNuevo();

    const dialogo = screen.getByTestId("plan-formulario");
    expect(within(dialogo).getByText("Nuevo plan", { selector: "h2" })).toBeTruthy();
    expect(dialogo.textContent).toContain("Nace activo");
    expect(campo("Nombre").value).toBe("");
    expect(campo("Precio mensual (S/)").value).toBe("");
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("enviarlo vacío muestra todos los errores a la vez y no llama al backend", () => {
    abrirNuevo();

    fireEvent.click(screen.getByTestId("plan-guardar"));

    const dialogo = screen.getByTestId("plan-formulario");
    expect(dialogo.textContent).toContain("Escribe el nombre del plan.");
    expect(dialogo.textContent).toContain("Escribe un precio de cero o más");
    expect(dialogo.querySelectorAll("[aria-invalid='true']").length).toBe(7);
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("al corregir un campo se le quita su error", () => {
    abrirNuevo();
    fireEvent.click(screen.getByTestId("plan-guardar"));

    fireEvent.change(campo("Nombre"), { target: { value: "Estudio" } });

    expect(screen.getByTestId("plan-formulario").textContent).not.toContain("Escribe el nombre del plan.");
    expect(campo("Nombre").getAttribute("aria-invalid")).toBeNull();
    expect(campo("Precio mensual (S/)").getAttribute("aria-invalid")).toBe("true");
  });

  it("con datos válidos hace POST a /api/admin/planes con el cuerpo, cierra y recarga la página", async () => {
    apiRequest.mockResolvedValue(exito());
    abrirNuevo();
    llenar(VALIDOS);

    fireEvent.click(screen.getByTestId("plan-guardar"));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledWith("/api/admin/planes", {
      method: "POST",
      body: {
        nombre: "Estudio",
        precio_mensual: 49.9,
        limites: { documentos_al_mes: { maximo: 800, ilimitado: false }, rucs: 2, usuarios: { maximo: 3, ilimitado: false }, api_keys: { maximo: 5, ilimitado: false }, retencion_anios: 6 },
      },
    });
    expect(screen.queryByTestId("plan-formulario")).toBeNull();
  });

  it("«Ilimitado» deshabilita el campo y manda el límite sin máximo", async () => {
    apiRequest.mockResolvedValue(exito());
    abrirNuevo();
    llenar(VALIDOS);
    const ilimitados = screen.getAllByLabelText("Ilimitado") as HTMLInputElement[];
    expect(ilimitados).toHaveLength(3);

    fireEvent.click(ilimitados[0]);
    fireEvent.click(ilimitados[2]);

    expect(campo("Documentos por mes").disabled).toBe(true);
    expect(campo("Usuarios").disabled).toBe(false);
    expect(campo("API keys").disabled).toBe(true);
    fireEvent.click(screen.getByTestId("plan-guardar"));
    await waitFor(() => expect(apiRequest).toHaveBeenCalled());
    const cuerpo = apiRequest.mock.calls[0][1].body;
    expect(cuerpo.limites.documentos_al_mes).toEqual({ ilimitado: true });
    expect(cuerpo.limites.api_keys).toEqual({ ilimitado: true });
    expect(cuerpo.limites.usuarios).toEqual({ maximo: 3, ilimitado: false });
  });

  it("un nombre repetido lo dice el backend: se muestra bajo el campo, sin cerrar", async () => {
    apiRequest.mockResolvedValue(error("NOMBRE_DUPLICADO", "Ya existe un plan llamado «Estudio»"));
    abrirNuevo();
    llenar(VALIDOS);

    fireEvent.click(screen.getByTestId("plan-guardar"));

    await waitFor(() => expect(screen.getAllByText("Ya existe un plan llamado «Estudio»").length).toBeGreaterThan(0));
    expect(campo("Nombre").getAttribute("aria-invalid")).toBe("true");
    expect(screen.getByRole("alert").textContent).toContain("Ya existe un plan");
    expect(screen.getByTestId("plan-formulario")).toBeTruthy();
    expect(refresh).not.toHaveBeenCalled();
  });

  it("un corte de red no se reintenta a ciegas: dice que no se sabe y manda a recargar", async () => {
    apiRequest.mockResolvedValue(error("RED", "No se pudo conectar con el servidor."));
    abrirNuevo();
    llenar(VALIDOS);

    fireEvent.click(screen.getByTestId("plan-guardar"));

    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("Recarga la página para ver el estado real."));
    expect(refresh).not.toHaveBeenCalled();
  });

  /** Dos clics en el mismo tick leen el `enviando` viejo del closure: la guardia es un ref. */
  it("un doble clic envía una sola vez", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockReturnValue(new Promise((r) => (resolver = r)));
    abrirNuevo();
    llenar(VALIDOS);

    await act(async () => {
      fireEvent.click(screen.getByTestId("plan-guardar"));
      fireEvent.click(screen.getByTestId("plan-guardar"));
    });
    await act(async () => resolver(exito()));

    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  it("mientras envía no se puede cerrar (Escape incluido) ni cancelar", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockReturnValue(new Promise((r) => (resolver = r)));
    abrirNuevo();
    llenar(VALIDOS);

    await act(async () => {
      fireEvent.click(screen.getByTestId("plan-guardar"));
    });
    fireEvent.keyDown(screen.getByTestId("plan-formulario"), { key: "Escape" });

    expect(screen.getByTestId("plan-formulario")).toBeTruthy();
    expect((screen.getByText("Cancelar") as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByTestId("plan-guardar").textContent).toContain("Creando…");
    await act(async () => resolver(exito()));
  });

  it("cerrar sin guardar descarta lo escrito", () => {
    abrirNuevo();
    llenar({ Nombre: "Estudio" });

    fireEvent.click(screen.getByText("Cancelar"));
    fireEvent.click(screen.getByTestId("plan-nuevo"));

    expect(campo("Nombre").value).toBe("");
  });
});

describe("FormularioDePlan — editar", () => {
  it("se abre con los datos del plan y dice que el nombre y el precio cambian al instante pero los límites, el 1 de noviembre", () => {
    abrirEdicion();

    const dialogo = screen.getByTestId("plan-formulario");
    expect(within(dialogo).getByText("Editar Negocio", { selector: "h2" })).toBeTruthy();
    expect(campo("Nombre").value).toBe("Negocio");
    expect(campo("Precio mensual (S/)").value).toBe("69.00");
    expect(campo("Documentos por mes").value).toBe("1500");
    expect(campo("RUC").value).toBe("3");
    expect(campo("Usuarios").value).toBe("3");
    expect(campo("API keys").value).toBe("5");
    expect(campo("Retención de XML y CDR (años)").value).toBe("5");
    expect(dialogo.textContent).toContain("El nombre y el precio cambian al instante.");
    expect(dialogo.textContent).toContain("Los límites nuevos entran el 1 Nov 2026");
    expect(dialogo.textContent).toContain("Las cuentas que ya tienen el plan no se tocan.");
    expect(screen.queryByTestId("plan-hay-programado")).toBeNull();
  });

  it("guardar hace PUT a /api/admin/planes/{id} con el cuerpo", async () => {
    apiRequest.mockResolvedValue(exito());
    abrirEdicion();
    fireEvent.change(campo("Documentos por mes"), { target: { value: "3000" } });

    fireEvent.click(screen.getByTestId("plan-guardar"));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(apiRequest).toHaveBeenCalledWith("/api/admin/planes/p-1", {
      method: "PUT",
      body: {
        nombre: "Negocio",
        precio_mensual: 69,
        limites: { documentos_al_mes: { maximo: 3000, ilimitado: false }, rucs: 3, usuarios: { maximo: 3, ilimitado: false }, api_keys: { maximo: 5, ilimitado: false }, retencion_anios: 5 },
      },
    });
  });

  it("un plan con límites ilimitados abre con «Ilimitado» marcado y el campo vacío", () => {
    abrirEdicion({ ...PLAN, limites: { ...PLAN.limites, documentos_al_mes: { ilimitado: true } } });

    expect(campo("Documentos por mes").value).toBe("");
    expect(campo("Documentos por mes").disabled).toBe(true);
    expect((screen.getAllByLabelText("Ilimitado")[0] as HTMLInputElement).checked).toBe(true);
  });

  /** Lo último que se decidió es lo que va a regir: se muestra eso, y se avisa que volver a poner lo de hoy cancela el cambio. */
  it("con un cambio de límites programado muestra esos límites y lo avisa", () => {
    abrirEdicion({
      ...PLAN,
      limites_programados: {
        aplica_desde: "2026-11-01T05:00:00Z",
        limites: { ...PLAN.limites, documentos_al_mes: { maximo: 3000, ilimitado: false } },
      },
    });

    expect(campo("Documentos por mes").value).toBe("3000");
    const aviso = screen.getByTestId("plan-hay-programado").textContent;
    expect(aviso).toContain("desde el 1 Nov 2026");
    expect(aviso).toContain("se cancela");
  });

  it("un plan que ya no existe (404) lo dice y recarga la página", async () => {
    apiRequest.mockResolvedValue(error("NO_ENCONTRADO", "El plan no existe"));
    abrirEdicion();

    fireEvent.click(screen.getByTestId("plan-guardar"));

    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("El plan no existe"));
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("el botón dice «Guardando…» mientras envía", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockReturnValue(new Promise((r) => (resolver = r)));
    abrirEdicion();

    await act(async () => {
      fireEvent.click(screen.getByTestId("plan-guardar"));
    });

    expect(screen.getByTestId("plan-guardar").textContent).toContain("Guardando…");
    await act(async () => resolver(exito()));
  });
});
