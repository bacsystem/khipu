import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PlanAdmin } from "@/lib/api/admin-planes";
import type { PrevisualizacionDePlanAdmin } from "@/lib/api/admin-plan-de-cuenta";
import type { ApiEnvelope } from "@/lib/api/types";
import { CambiarPlan } from "./cambiar-plan";

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
const exito = (datos: unknown = {}): ApiEnvelope<unknown> => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });
const error = (codigo: string, mensaje: string): ApiEnvelope<unknown> => ({ estado: "error", datos: null, mensaje, codigo, errores: null });

const LIMITES = { documentos_al_mes: { maximo: 300, ilimitado: false }, rucs: 1, usuarios: { maximo: 1, ilimitado: false }, api_keys: { maximo: 2, ilimitado: false }, retencion_anios: 5 };
const plan = (id: string, nombre: string, precio: number): PlanAdmin => ({ id, nombre, precio_mensual: precio, limites: LIMITES, estado: "ACTIVO", por_defecto: false, cuentas: 0 });
const GRATIS = plan("p-gratis", "Gratis", 0);
const EMPRENDE = plan("p-emprende", "Emprende", 29);
const NEGOCIO = plan("p-negocio", "Negocio", 69);
const PLANES = [GRATIS, EMPRENDE, NEGOCIO];

const resumen = (p: PlanAdmin) => ({ id: p.id, nombre: p.nombre, precio_mensual: p.precio_mensual, limites: p.limites });

function previa(extra: Partial<PrevisualizacionDePlanAdmin> = {}): PrevisualizacionDePlanAdmin {
  return {
    cuenta_id: CUENTA,
    plan_actual: resumen(EMPRENDE),
    plan_nuevo: resumen(NEGOCIO),
    direccion: "SUBIDA",
    efecto: "INMEDIATO",
    aplica_desde: "2026-10-04T15:00:00Z",
    mes: "2026-10",
    consumo_del_mes: 312,
    limite_de_documentos: { maximo: 1500, ilimitado: false },
    supera_el_limite: false,
    ...extra,
  };
}

/** Las llamadas se distinguen por la ruta: la previsualización es un GET con el plan en la URL; el cambio, un POST. */
function responder(vistaPrevia: ApiEnvelope<unknown> | ((planId: string) => ApiEnvelope<unknown>), cambio: ApiEnvelope<unknown> = exito()) {
  apiRequest.mockImplementation(async (ruta: string, init: { method: string }) => {
    if (init.method === "POST") return cambio;
    const planId = new URL(ruta, "http://x").searchParams.get("plan_id") ?? "";
    return typeof vistaPrevia === "function" ? vistaPrevia(planId) : vistaPrevia;
  });
}

function abrir(actual: PlanAdmin = EMPRENDE) {
  render(<CambiarPlan cuentaId={CUENTA} cuentaNombre="Mi negocio" planActualId={actual.id} planes={PLANES} hoy="2026-10-04" />);
  fireEvent.click(screen.getByTestId("cambiar-plan"));
}

const elegir = (id: string) => fireEvent.change(screen.getByLabelText("Plan nuevo"), { target: { value: id } });
const dialogo = () => screen.getByTestId("cambiar-plan-dialogo");

const llamadasPost = () => apiRequest.mock.calls.filter((c) => c[1].method === "POST");

/** Cambiar el plan de una cuenta (#191): el modal dice qué pasa con el consumo del mes y cuándo entra el cambio **antes** de confirmar. */
describe("CambiarPlan — antes de confirmar", () => {
  it("abrirlo no envía nada y ofrece los planes, con el actual marcado", () => {
    abrir();

    expect(dialogo().textContent).toContain("Cambiar el plan de Mi negocio");
    const opciones = Array.from(screen.getByLabelText("Plan nuevo").querySelectorAll("option")).map((o) => o.textContent);
    expect(opciones).toEqual(["Elige un plan", "Gratis — Gratis", "Emprende — S/ 29.00 (plan actual)", "Negocio — S/ 69.00"]);
    expect(apiRequest).not.toHaveBeenCalled();
    expect((screen.getByTestId("cambiar-plan-confirmar") as HTMLButtonElement).disabled).toBe(true);
  });

  it("al elegir un plan pide la previsualización de ese plan y dice que se está calculando", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockReturnValue(new Promise((r) => (resolver = r)));
    abrir();

    elegir(NEGOCIO.id);

    await waitFor(() => expect(apiRequest).toHaveBeenCalledWith(`/api/admin/cuentas/${CUENTA}/plan/previsualizacion?plan_id=${NEGOCIO.id}`, { method: "GET" }));
    expect(screen.getByTestId("cambiar-plan-previa").textContent).toContain("Calculando lo que pasaría…");
    expect((screen.getByTestId("cambiar-plan-confirmar") as HTMLButtonElement).disabled).toBe(true);
    await act(async () => resolver(exito(previa())));
  });

  it("una subida dice que entra ahora y cuánto consumió la cuenta este mes frente al tope nuevo", async () => {
    responder(exito(previa()));
    abrir();

    elegir(NEGOCIO.id);

    await waitFor(() => expect(screen.getByTestId("cambiar-plan-previa").getAttribute("data-direccion")).toBe("SUBIDA"));
    const texto = screen.getByTestId("cambiar-plan-previa").textContent ?? "";
    expect(texto).toContain("Subida de plan");
    expect(texto).toContain("Entra ahora.");
    expect(texto).toContain("En Oct 2026 la cuenta consumió 312 documentos (solo cuentan los comprobantes aceptados por SUNAT).");
    expect(texto).toContain("El plan Negocio permite 1,500 al mes.");
    expect(screen.queryByTestId("cambiar-plan-supera")).toBeNull();
  });

  it("una bajada dice cuándo entra y que hasta entonces la cuenta sigue con el plan de hoy", async () => {
    responder(exito(previa({ plan_actual: resumen(NEGOCIO), plan_nuevo: resumen(EMPRENDE), direccion: "BAJADA", efecto: "CICLO_SIGUIENTE", aplica_desde: "2026-11-01T05:00:00Z" })));
    abrir(NEGOCIO);

    elegir(EMPRENDE.id);

    await waitFor(() => expect(screen.getByTestId("cambiar-plan-previa").getAttribute("data-direccion")).toBe("BAJADA"));
    const texto = screen.getByTestId("cambiar-plan-previa").textContent ?? "";
    expect(texto).toContain("Bajada de plan");
    expect(texto).toContain("Entra el 1 Nov 2026, al inicio del ciclo siguiente. Hasta entonces la cuenta sigue con Negocio.");
    expect(texto).not.toContain("Entra ahora.");
  });

  it("renovar el mismo plan lo llama renovación y entra ahora", async () => {
    responder(exito(previa({ plan_nuevo: resumen(EMPRENDE), direccion: "RENOVACION" })));
    abrir();

    elegir(EMPRENDE.id);

    await waitFor(() => expect(screen.getByTestId("cambiar-plan-previa").textContent).toContain("Renovación del mismo plan"));
  });

  it("si el consumo ya supera el tope del plan nuevo y el cambio es inmediato lo advierte", async () => {
    responder(exito(previa({ consumo_del_mes: 400, limite_de_documentos: { maximo: 300, ilimitado: false }, supera_el_limite: true })));
    abrir();

    elegir(NEGOCIO.id);

    const aviso = await screen.findByTestId("cambiar-plan-supera");
    expect(aviso.textContent).toContain("Ya supera ese tope: con el cambio inmediato, la cuenta quedaría por encima de su límite de este mes.");
  });

  it("si lo supera pero es una bajada dice que este mes no cambia nada", async () => {
    responder(exito(previa({ direccion: "BAJADA", efecto: "CICLO_SIGUIENTE", aplica_desde: "2026-11-01T05:00:00Z", consumo_del_mes: 400, supera_el_limite: true })));
    abrir();

    elegir(NEGOCIO.id);

    const aviso = await screen.findByTestId("cambiar-plan-supera");
    expect(aviso.textContent).toContain("Este mes no cambia nada: hasta el ciclo siguiente la cuenta sigue con su plan de hoy.");
    expect(aviso.textContent).not.toContain("Ya supera ese tope");
  });

  it("un plan sin tope de documentos lo dice en vez de poner una cifra", async () => {
    responder(exito(previa({ limite_de_documentos: { ilimitado: true } })));
    abrir();

    elegir(NEGOCIO.id);

    await waitFor(() => expect(screen.getByTestId("cambiar-plan-previa").textContent).toContain("no tiene tope de documentos."));
  });

  it("si no se puede calcular lo dice y no deja confirmar", async () => {
    responder(error("PLAN_INACTIVO", "El plan «Negocio» está fuera de la oferta: no se puede asignar"));
    abrir();

    elegir(NEGOCIO.id);

    await waitFor(() => expect(screen.getByTestId("cambiar-plan-previa").textContent).toContain("El plan «Negocio» está fuera de la oferta"));
    expect((screen.getByTestId("cambiar-plan-confirmar") as HTMLButtonElement).disabled).toBe(true);
  });

  /** Si el administrador cambia de plan antes de que llegue la respuesta, la respuesta vieja no pisa a la nueva. */
  it("una respuesta atrasada de otro plan no pisa la del plan elegido al final", async () => {
    const resolvers: Record<string, (v: ApiEnvelope<unknown>) => void> = {};
    apiRequest.mockImplementation((ruta: string) => new Promise((r) => { resolvers[new URL(ruta, "http://x").searchParams.get("plan_id")!] = r; }));
    abrir();

    elegir(NEGOCIO.id);
    elegir(GRATIS.id);
    await waitFor(() => expect(Object.keys(resolvers)).toHaveLength(2));
    await act(async () => resolvers[GRATIS.id](exito(previa({ plan_nuevo: resumen(GRATIS), direccion: "BAJADA", efecto: "CICLO_SIGUIENTE", aplica_desde: "2026-11-01T05:00:00Z" }))));
    await act(async () => resolvers[NEGOCIO.id](exito(previa({ plan_nuevo: resumen(NEGOCIO), direccion: "SUBIDA" }))));

    expect(screen.getByTestId("cambiar-plan-previa").getAttribute("data-direccion")).toBe("BAJADA");
    expect(screen.getByTestId("cambiar-plan-previa").textContent).toContain("El plan Gratis");
  });

  it("volver a «Elige un plan» limpia la previsualización", async () => {
    responder(exito(previa()));
    abrir();
    elegir(NEGOCIO.id);
    await screen.findByTestId("cambiar-plan-previa");

    elegir("");

    expect(screen.queryByTestId("cambiar-plan-previa")).toBeNull();
  });
});

describe("CambiarPlan — confirmar", () => {
  async function listoParaConfirmar(extra: Partial<PrevisualizacionDePlanAdmin> = {}, cambio: ApiEnvelope<unknown> = exito()) {
    responder(exito(previa(extra)), cambio);
    abrir();
    elegir(NEGOCIO.id);
    await waitFor(() => expect((screen.getByTestId("cambiar-plan-confirmar") as HTMLButtonElement).disabled).toBe(false));
  }

  it("un plan de pago exige la fecha hasta la que está pagado y no envía nada sin ella", async () => {
    await listoParaConfirmar();

    fireEvent.click(screen.getByTestId("cambiar-plan-confirmar"));

    expect(dialogo().textContent).toContain("Un plan de pago necesita la fecha hasta la que está pagado.");
    expect(llamadasPost()).toHaveLength(0);
  });

  it("confirma con el vencimiento (medianoche de Lima del día siguiente) y la gracia, cierra y recarga", async () => {
    await listoParaConfirmar();
    fireEvent.change(screen.getByLabelText("Pagado hasta (inclusive)"), { target: { value: "2026-11-30" } });
    fireEvent.change(screen.getByLabelText("Días de gracia"), { target: { value: "5" } });

    fireEvent.click(screen.getByTestId("cambiar-plan-confirmar"));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(llamadasPost()).toHaveLength(1);
    expect(llamadasPost()[0]).toEqual([`/api/admin/cuentas/${CUENTA}/plan`, { method: "POST", body: { plan_id: NEGOCIO.id, vence_en: "2026-12-01T05:00:00.000Z", dias_de_gracia: 5 } }]);
    expect(screen.queryByTestId("cambiar-plan-dialogo")).toBeNull();
  });

  it("un plan gratis se confirma sin fecha", async () => {
    responder(exito(previa({ plan_nuevo: resumen(GRATIS), direccion: "BAJADA", efecto: "CICLO_SIGUIENTE", aplica_desde: "2026-11-01T05:00:00Z" })));
    abrir();
    elegir(GRATIS.id);
    await waitFor(() => expect((screen.getByTestId("cambiar-plan-confirmar") as HTMLButtonElement).disabled).toBe(false));

    fireEvent.click(screen.getByTestId("cambiar-plan-confirmar"));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(llamadasPost()[0][1].body).toEqual({ plan_id: GRATIS.id, dias_de_gracia: 0 });
  });

  it("una fecha pasada o unos días de gracia fuera de rango se rechazan antes de enviar", async () => {
    await listoParaConfirmar();
    fireEvent.change(screen.getByLabelText("Pagado hasta (inclusive)"), { target: { value: "2026-10-03" } });
    fireEvent.change(screen.getByLabelText("Días de gracia"), { target: { value: "91" } });

    fireEvent.click(screen.getByTestId("cambiar-plan-confirmar"));

    expect(dialogo().textContent).toContain("La fecha tiene que ser de hoy en adelante.");
    expect(dialogo().textContent).toContain("Los días de gracia van de 0 a 90.");
    expect(llamadasPost()).toHaveLength(0);
  });

  it("si otro administrador cambió el plan en el medio lo dice y recarga para mostrar el estado real", async () => {
    await listoParaConfirmar({}, error("CAMBIO_CONCURRENTE", "Otro administrador cambió el plan de la cuenta mientras tanto: vuelve a mirarlo"));
    fireEvent.change(screen.getByLabelText("Pagado hasta (inclusive)"), { target: { value: "2026-11-30" } });

    fireEvent.click(screen.getByTestId("cambiar-plan-confirmar"));

    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("Otro administrador cambió el plan"));
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("cambiar-plan-dialogo")).toBeTruthy();
  });

  it("un corte de red no se reintenta a ciegas: dice que no se sabe y manda a recargar", async () => {
    await listoParaConfirmar({}, error("RED", "No se pudo conectar con el servidor."));
    fireEvent.change(screen.getByLabelText("Pagado hasta (inclusive)"), { target: { value: "2026-11-30" } });

    fireEvent.click(screen.getByTestId("cambiar-plan-confirmar"));

    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("Recarga la página para ver el estado real."));
    expect(refresh).not.toHaveBeenCalled();
  });

  /** Dos clics en el mismo tick leen el `enviando` viejo del closure: la guardia es un ref. */
  it("un doble clic envía una sola vez", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockImplementation((_: string, init: { method: string }) => (init.method === "POST" ? new Promise((r) => (resolver = r)) : Promise.resolve(exito(previa()))));
    abrir();
    elegir(NEGOCIO.id);
    await waitFor(() => expect((screen.getByTestId("cambiar-plan-confirmar") as HTMLButtonElement).disabled).toBe(false));
    fireEvent.change(screen.getByLabelText("Pagado hasta (inclusive)"), { target: { value: "2026-11-30" } });

    await act(async () => {
      fireEvent.click(screen.getByTestId("cambiar-plan-confirmar"));
      fireEvent.click(screen.getByTestId("cambiar-plan-confirmar"));
    });
    await act(async () => resolver(exito()));

    expect(llamadasPost()).toHaveLength(1);
  });

  it("mientras envía no se puede cerrar (Escape incluido) y el botón lo dice", async () => {
    let resolver: (v: ApiEnvelope<unknown>) => void = () => {};
    apiRequest.mockImplementation((_: string, init: { method: string }) => (init.method === "POST" ? new Promise((r) => (resolver = r)) : Promise.resolve(exito(previa()))));
    abrir();
    elegir(NEGOCIO.id);
    await waitFor(() => expect((screen.getByTestId("cambiar-plan-confirmar") as HTMLButtonElement).disabled).toBe(false));
    fireEvent.change(screen.getByLabelText("Pagado hasta (inclusive)"), { target: { value: "2026-11-30" } });

    await act(async () => {
      fireEvent.click(screen.getByTestId("cambiar-plan-confirmar"));
    });
    fireEvent.keyDown(dialogo(), { key: "Escape" });

    expect(screen.getByTestId("cambiar-plan-dialogo")).toBeTruthy();
    expect(screen.getByTestId("cambiar-plan-confirmar").textContent).toContain("Cambiando…");
    await act(async () => resolver(exito()));
  });

  it("cerrar sin confirmar descarta lo escrito", async () => {
    await listoParaConfirmar();
    fireEvent.change(screen.getByLabelText("Pagado hasta (inclusive)"), { target: { value: "2026-11-30" } });

    fireEvent.click(screen.getByText("Cancelar"));
    fireEvent.click(screen.getByTestId("cambiar-plan"));

    expect((screen.getByLabelText("Plan nuevo") as HTMLSelectElement).value).toBe("");
    expect((screen.getByLabelText("Pagado hasta (inclusive)") as HTMLInputElement).value).toBe("");
    expect(screen.queryByTestId("cambiar-plan-previa")).toBeNull();
  });
});
