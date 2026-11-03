import { describe, expect, it } from "vitest";
import type { LimitesAdmin } from "./admin-planes";
import { planDePagoElegido, type PlanPublicado } from "./planes-publicados";

const LIMITES = {} as LimitesAdmin;
const PUBLICADOS: PlanPublicado[] = [
  { nombre: "Gratis", precio_mensual: 0, limites: LIMITES },
  { nombre: "Negocio", precio_mensual: 69, limites: LIMITES },
];

/** #581: la clave de `/registro?plan=<clave>` se valida contra lo publicado; lo que no es un plan de pago publicado no se muestra ni se envía. */
describe("planDePagoElegido", () => {
  it("encuentra el plan de pago por su nombre, sin importar mayúsculas ni espacios", () => {
    expect(planDePagoElegido(PUBLICADOS, "negocio")?.nombre).toBe("Negocio");
    expect(planDePagoElegido(PUBLICADOS, "  NEGOCIO ")?.precio_mensual).toBe(69);
  });

  it("el gratis, uno que no existe, una clave vacía o sin planes publicados no cuentan", () => {
    for (const clave of ["gratis", "corporativo", "", "   ", undefined]) expect(planDePagoElegido(PUBLICADOS, clave)).toBeNull();
    expect(planDePagoElegido(null, "negocio")).toBeNull();
  });
});
