import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PlanAdmin } from "@/lib/api/admin-planes";
import type { PlanDeCuentaAdmin } from "@/lib/api/admin-plan-de-cuenta";
import { PlanDeCuenta } from "./plan-de-cuenta";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn(), push: vi.fn() }) }));
vi.mock("@/lib/api/browser", () => ({ apiRequest: vi.fn() }));

afterEach(cleanup);

const LIMITES = { documentos_al_mes: { maximo: 300, ilimitado: false }, rucs: 1, usuarios: { maximo: 1, ilimitado: false }, api_keys: { maximo: 2, ilimitado: false }, retencion_anios: 5 };
const resumen = (id: string, nombre: string, precio: number) => ({ id, nombre, precio_mensual: precio, limites: LIMITES });
const planAdmin = (id: string, nombre: string, precio: number, estado: "ACTIVO" | "INACTIVO" = "ACTIVO"): PlanAdmin => ({ id, nombre, precio_mensual: precio, limites: LIMITES, estado, por_defecto: false, cuentas: 0 });

const PLANES = [planAdmin("p-gratis", "Gratis", 0), planAdmin("p-emprende", "Emprende", 29), planAdmin("p-viejo", "Viejo", 10, "INACTIVO")];

const PLAN: PlanDeCuentaAdmin = {
  cuenta_id: "c1",
  plan: resumen("p-emprende", "Emprende", 29),
  estado: "VIGENTE",
  inicia_en: "2026-09-01T10:00:00Z",
  vence_en: "2026-11-01T05:00:00Z",
  dias_de_gracia: 5,
  hasta_cuando_cubre: "2026-11-06T05:00:00Z",
};

const montar = (plan: PlanDeCuentaAdmin = PLAN, planes: PlanAdmin[] = PLANES) => render(<PlanDeCuenta cuentaId="c1" cuentaNombre="Mi negocio" plan={plan} planes={planes} hoy="2026-10-04" />);

const seccion = () => screen.getByTestId("plan-de-cuenta");

/** El plan de una cuenta en su ficha del backoffice (#191): cuál es, en qué estado de pago está y hasta cuándo se la sirve. */
describe("PlanDeCuenta", () => {
  it("muestra el plan, su precio, hasta qué día está pagado y los días de gracia", () => {
    montar();

    const texto = seccion().textContent ?? "";
    expect(texto).toContain("Emprende");
    expect(texto).toContain("S/ 29.00 al mes");
    expect(texto).toContain("Pagado hasta el 31 Oct 2026");
    expect(texto).toContain("5 días de gracia");
    expect(texto).toContain("Se la sirve hasta el 5 Nov 2026, gracia incluida.");
    expect(texto).toContain("Con este plan desde el 1 Set 2026");
  });

  it("resume los límites que mandan hoy", () => {
    montar();

    expect(seccion().textContent).toContain("Límites vigentes: 300 documentos al mes · 1 RUC · 1 usuario · 2 API keys · 5 años de retención");
  });

  it("el estado de pago se dice con una etiqueta: al día, en gracia o vencido", () => {
    for (const [estado, texto] of [["VIGENTE", "Al día"], ["EN_GRACIA", "En gracia"], ["VENCIDA", "Vencido"]] as const) {
      montar({ ...PLAN, estado });
      expect(screen.getByTestId("plan-estado").textContent).toBe(texto);
      expect(screen.getByTestId("plan-estado").getAttribute("data-estado")).toBe(estado);
      cleanup();
    }
  });

  it("un plan sin vencimiento (el gratis) lo dice y no habla de gracia ni de hasta cuándo se sirve", () => {
    montar({ ...PLAN, plan: resumen("p-gratis", "Gratis", 0), vence_en: undefined, dias_de_gracia: 0, hasta_cuando_cubre: undefined });

    const texto = seccion().textContent ?? "";
    expect(texto).toContain("Sin vencimiento");
    expect(texto).toContain("Gratis");
    expect(texto).not.toContain("Pagado hasta");
    expect(texto).not.toContain("Se la sirve hasta");
    expect(texto).not.toContain("gracia");
  });

  it("sin gracia lo dice, y con un solo día lo dice en singular", () => {
    montar({ ...PLAN, dias_de_gracia: 0, hasta_cuando_cubre: PLAN.vence_en });
    expect(seccion().textContent).toContain("Sin gracia");
    cleanup();

    montar({ ...PLAN, dias_de_gracia: 1 });
    expect(seccion().textContent).toContain("1 día de gracia");
    expect(seccion().textContent).not.toContain("1 días");
  });

  it("una bajada esperando se dice aparte: a qué plan pasa, cuándo, y que hasta entonces sigue con el de hoy", () => {
    montar({ ...PLAN, programado: { plan: resumen("p-gratis", "Gratis", 0), aplica_desde: "2026-11-01T05:00:00Z", dias_de_gracia: 0 } });

    const aviso = screen.getByTestId("plan-programado").textContent ?? "";
    expect(aviso).toContain("Pasa a Gratis el 1 Nov 2026, al inicio del ciclo siguiente. Hasta entonces sigue con Emprende.");
  });

  it("sin bajada esperando no hay aviso", () => {
    montar();

    expect(screen.queryByTestId("plan-programado")).toBeNull();
  });

  it("ofrece cambiar el plan", () => {
    montar();

    expect(screen.getByTestId("cambiar-plan")).toBeTruthy();
  });

  /** Los planes fuera de la oferta no se pueden asignar: el modal solo ofrece los activos (la cuenta que ya tiene uno inactivo lo conserva, pero no se le puede dar a otra). */
  it("el modal solo ofrece los planes de la oferta", () => {
    montar();
    fireEvent.click(screen.getByTestId("cambiar-plan"));

    const opciones = Array.from(screen.getByLabelText("Plan nuevo").querySelectorAll("option")).map((o) => o.textContent);
    expect(opciones).toEqual(["Elige un plan", "Gratis — Gratis", "Emprende — S/ 29.00 (plan actual)"]);
  });
});
