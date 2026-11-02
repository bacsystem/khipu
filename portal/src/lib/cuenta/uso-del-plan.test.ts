import { describe, expect, it } from "vitest";
import type { MiCuenta } from "@/lib/api/cuenta";
import { usoDelPlan } from "./uso-del-plan";
import es from "@/messages/es.json";

type Limites = MiCuenta["plan"]["plan"]["limites"];

const gratis: Limites = {
  documentos_al_mes: { maximo: 30, ilimitado: false },
  rucs: 1,
  usuarios: { maximo: 1, ilimitado: false },
  api_keys: { maximo: 1, ilimitado: false },
  retencion_anios: 1,
};

function cuenta(uso: MiCuenta["uso"], limites: Limites = gratis): MiCuenta {
  return {
    nombre: "Ferretería Torres",
    plan: {
      cuenta_id: "c1",
      plan: { id: "p1", nombre: "Gratis", precio_mensual: 0, limites },
      estado: "VIGENTE",
      inicia_en: "2026-10-08T17:00:00Z",
      dias_de_gracia: 0,
    },
    consumo: { mes: "2026-10", documentos: 0, maximo: 30 },
    uso,
  };
}

const por = (c: MiCuenta) => Object.fromEntries(usoDelPlan(c, es).map((u) => [u.clave, u]));

/** #498: lo que la cuenta ocupa de las API keys, los usuarios y los RUC de su plan, con la regla del backend (el N-ésimo cabe, el N+1 no). */
describe("usoDelPlan", () => {
  it("lejos del tope: cifra contra tope, barra y ningún aviso", () => {
    const u = por(cuenta({ api_keys: 0, usuarios: 2, rucs: 0 }, { ...gratis, usuarios: { maximo: 10, ilimitado: false }, rucs: 5 }));
    expect(u.apiKeys).toMatchObject({ texto: "0 / 1", porcentaje: 0, tono: "ok", aviso: null });
    expect(u.usuarios).toMatchObject({ texto: "2 / 10", porcentaje: 20, tono: "ok", aviso: null });
    expect(u.rucs).toMatchObject({ texto: "0 / 5", porcentaje: 0, tono: "ok", aviso: null });
  });

  it("en el tope justo dice que llegó: 1 de 1 no es «te pasaste», pero no se puede sumar más", () => {
    const u = por(cuenta({ api_keys: 1, usuarios: 1, rucs: 1 }));
    for (const r of [u.apiKeys, u.usuarios, u.rucs]) expect(r).toMatchObject({ porcentaje: 100, tono: "aviso" });
    expect(u.apiKeys.aviso).toBe("Llegaste al tope de API keys de tu plan: para sumar más, libera un lugar o pide un cambio de plan.");
    expect(u.usuarios.aviso).toContain("tope de usuarios");
    expect(u.rucs.aviso).toContain("tope de empresas");
  });

  /** Una cuenta a la que le bajaron el plan: no se le quitó nada, y la barra no pasa del 100 %. */
  it("por encima del tope lo dice como error y la barra se queda en 100 %", () => {
    const u = por(cuenta({ api_keys: 1, usuarios: 2, rucs: 1 }));
    expect(u.usuarios).toMatchObject({ texto: "2 / 1", porcentaje: 100, tono: "error" });
    expect(u.usuarios.aviso).toBe("Tienes más usuarios de los que incluye tu plan. No se quitó nada, pero no puedes sumar más.");
  });

  it("desde el 80 % del tope avisa que se acerca, y justo debajo no", () => {
    const l: Limites = { ...gratis, usuarios: { maximo: 10, ilimitado: false } };
    expect(por(cuenta({ api_keys: 0, usuarios: 8, rucs: 0 }, l)).usuarios).toMatchObject({ tono: "aviso", aviso: "Estás cerca del tope de usuarios de tu plan." });
    expect(por(cuenta({ api_keys: 0, usuarios: 7, rucs: 0 }, l)).usuarios).toMatchObject({ tono: "ok", aviso: null });
  });

  it("sin tope no hay barra ni aviso, por mucho que se use", () => {
    const l: Limites = { ...gratis, usuarios: { ilimitado: true }, api_keys: { ilimitado: true } };
    const u = por(cuenta({ api_keys: 500, usuarios: 300, rucs: 0 }, l));
    expect(u.apiKeys).toMatchObject({ maximo: null, texto: "500 · sin tope", porcentaje: null, tono: "ok", aviso: null });
    expect(u.usuarios).toMatchObject({ maximo: null, texto: "300 · sin tope", porcentaje: null, tono: "ok", aviso: null });
  });

  /** Un límite que el backend no manda (ni máximo ni la marca de ilimitado) no se lee como «0 de 0 = lleno»: se trata como sin tope y no inventa un aviso. */
  it("un límite sin máximo ni marca no inventa un tope", () => {
    const l: Limites = { ...gratis, usuarios: { ilimitado: false } };
    expect(por(cuenta({ api_keys: 0, usuarios: 3, rucs: 0 }, l)).usuarios).toMatchObject({ maximo: null, tono: "ok", aviso: null });
  });

  it("devuelve los tres recursos en el orden de la pantalla", () => {
    expect(usoDelPlan(cuenta({ api_keys: 0, usuarios: 1, rucs: 1 }), es).map((u) => u.clave)).toEqual(["apiKeys", "usuarios", "rucs"]);
  });
});
