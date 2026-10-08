import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PlanAdmin } from "@/lib/api/admin-planes";
import { PlanesTabla } from "./planes-tabla";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn(), push: vi.fn() }) }));
vi.mock("@/lib/api/browser", () => ({ apiRequest: vi.fn() }));

afterEach(cleanup);

const LIMITES = { documentos_al_mes: { maximo: 300, ilimitado: false }, rucs: 1, usuarios: { maximo: 1, ilimitado: false }, api_keys: { maximo: 2, ilimitado: false }, retencion_anios: 5 };

const GRATIS: PlanAdmin = {
  id: "g",
  nombre: "Gratis",
  precio_mensual: 0,
  limites: { ...LIMITES, documentos_al_mes: { maximo: 30, ilimitado: false }, api_keys: { maximo: 1, ilimitado: false }, retencion_anios: 1 },
  estado: "ACTIVO",
  por_defecto: true,
  cuentas: 12,
};
const NEGOCIO: PlanAdmin = {
  id: "n",
  nombre: "Negocio",
  precio_mensual: 69,
  limites: { documentos_al_mes: { maximo: 1500, ilimitado: false }, rucs: 3, usuarios: { maximo: 3, ilimitado: false }, api_keys: { maximo: 5, ilimitado: false }, retencion_anios: 5 },
  estado: "ACTIVO",
  por_defecto: false,
  cuentas: 4,
};
const PRO: PlanAdmin = {
  id: "p",
  nombre: "Pro",
  precio_mensual: 129,
  limites: { documentos_al_mes: { ilimitado: true }, rucs: 10, usuarios: { ilimitado: true }, api_keys: { ilimitado: true }, retencion_anios: 5 },
  estado: "ACTIVO",
  por_defecto: false,
  cuentas: 0,
};

const fila = (id: string) => screen.getByTestId(`plan-fila-${id}`);
const celdas = (id: string) => within(fila(id)).getAllByRole("cell").map((c) => c.textContent ?? "");

/** El listado de planes del backoffice (#190): precio, límites y cuántas cuentas los usan. */
describe("PlanesTabla", () => {
  it("muestra cada plan con su precio, sus límites y cuántas cuentas lo tienen", () => {
    render(<PlanesTabla planes={[GRATIS, NEGOCIO, PRO]} />);

    const n = celdas("n");
    expect(n[0]).toContain("Negocio");
    expect(n[1]).toBe("S/ 69.00");
    expect(n[2]).toBe("1,500");
    expect(n[3]).toBe("3");
    expect(n[4]).toBe("3");
    expect(n[5]).toBe("5");
    expect(n[6]).toBe("5 años");
    expect(n[7]).toBe("4");
  });

  it("gratis se lee «Gratis», 1 año de retención se lee en singular y lo ilimitado dice «Ilimitados»", () => {
    render(<PlanesTabla planes={[GRATIS, NEGOCIO, PRO]} />);

    expect(celdas("g")[1]).toBe("Gratis");
    expect(celdas("g")[6]).toBe("1 año");
    expect(celdas("p")[2]).toBe("Ilimitados");
    expect(celdas("p")[4]).toBe("Ilimitados");
    expect(celdas("p")[5]).toBe("Ilimitados");
    expect(celdas("p")[3]).toBe("10");
  });

  it("marca el plan de las cuentas nuevas", () => {
    render(<PlanesTabla planes={[GRATIS, NEGOCIO]} />);

    expect(within(fila("g")).getByText("Para cuentas nuevas")).toBeTruthy();
    expect(within(fila("n")).queryByText("Para cuentas nuevas")).toBeNull();
  });

  it("un plan desactivado se ve fuera de la oferta", () => {
    render(<PlanesTabla planes={[{ ...NEGOCIO, estado: "INACTIVO" }]} />);

    expect(within(fila("n")).getByText("Fuera de la oferta")).toBeTruthy();
    expect(fila("n").getAttribute("data-estado")).toBe("INACTIVO");
  });

  it("un plan activo no lleva la marca de fuera de la oferta", () => {
    render(<PlanesTabla planes={[NEGOCIO]} />);

    expect(within(fila("n")).queryByText("Fuera de la oferta")).toBeNull();
    expect(fila("n").getAttribute("data-estado")).toBe("ACTIVO");
  });

  /** Lo que ya se decidió pero todavía no manda: la tabla muestra lo vigente y, aparte, lo que viene y desde cuándo. */
  it("un cambio de límites programado se muestra aparte, con qué cambia y desde cuándo, sin tocar lo vigente", () => {
    const conCambio: PlanAdmin = {
      ...NEGOCIO,
      limites_programados: {
        aplica_desde: "2026-11-01T05:00:00Z",
        limites: { ...NEGOCIO.limites, documentos_al_mes: { maximo: 3000, ilimitado: false }, retencion_anios: 7 },
      },
    };
    render(<PlanesTabla planes={[conCambio]} />);

    expect(celdas("n")[2]).toBe("1,500");
    const aviso = screen.getByTestId("plan-programado-n");
    expect(aviso.textContent).toContain("Desde el 1 Nov 2026");
    expect(aviso.textContent).toContain("Documentos / mes: 1,500 → 3,000");
    expect(aviso.textContent).toContain("Retención (años): 5 años → 7 años");
    expect(aviso.textContent).not.toContain("RUC");
  });

  /** Un programado idéntico a lo vigente no cambia nada: no se anuncia un cambio vacío. */
  it("un cambio programado sin diferencias con lo vigente no muestra aviso", () => {
    render(<PlanesTabla planes={[{ ...NEGOCIO, limites_programados: { aplica_desde: "2026-11-01T05:00:00Z", limites: NEGOCIO.limites } }]} />);

    expect(screen.queryByTestId("plan-programado-n")).toBeNull();
  });

  it("sin cambio programado no hay aviso", () => {
    render(<PlanesTabla planes={[NEGOCIO]} />);

    expect(screen.queryByTestId("plan-programado-n")).toBeNull();
  });

  it("cada fila ofrece sus acciones", () => {
    render(<PlanesTabla planes={[GRATIS, PRO]} />);

    expect(within(fila("g")).getByTestId("plan-editar-g")).toBeTruthy();
    expect(within(fila("p")).getByTestId("plan-editar-p")).toBeTruthy();
    expect(within(fila("p")).getByTestId("plan-borrar-p")).toBeTruthy();
    expect(within(fila("g")).queryByTestId("plan-borrar-g")).toBeNull();
  });

  /** Crear un plan es la acción principal de la página y va en la cabecera, como «Nueva cuenta» en Cuentas: la tabla no repite el botón. */
  it("la tabla no trae su propio botón para crear un plan", () => {
    render(<PlanesTabla planes={[GRATIS]} />);

    expect(screen.queryByTestId("plan-nuevo")).toBeNull();
  });

  it("sin planes dice que todavía no hay", () => {
    render(<PlanesTabla planes={[]} />);

    expect(screen.getByText("Todavía no hay planes.")).toBeTruthy();
  });

  it("las filas siguen el orden en que llegan (el backend ya las ordena por precio)", () => {
    render(<PlanesTabla planes={[GRATIS, NEGOCIO, PRO]} />);

    const orden = screen.getAllByTestId(/^plan-fila-/).map((f) => f.getAttribute("data-testid"));
    expect(orden).toEqual(["plan-fila-g", "plan-fila-n", "plan-fila-p"]);
  });
});
