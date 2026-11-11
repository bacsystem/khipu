import { describe, expect, it } from "vitest";
import type { MiCuenta } from "@/lib/api/cuenta";
import { resumenDePruebas } from "./pruebas-del-plan";
import { resumenDePlan } from "./resumen-de-plan";
import en from "@/messages/en.json";
import es from "@/messages/es.json";

const limites = {
  documentos_al_mes: { maximo: 300, ilimitado: false },
  rucs: 1,
  usuarios: { maximo: 1, ilimitado: false },
  api_keys: { maximo: 2, ilimitado: false },
  retencion_anios: 5,
  periodo_documentos: "MENSUAL" as const,
  pruebas_beta_por_ruc: { maximo: 1000, ilimitado: false },
  pruebas_beta_al_mes: { maximo: 1000, ilimitado: false },
};

function cuenta(cambios: {
  documentos?: number;
  maximo?: number;
  estado?: MiCuenta["plan"]["estado"];
  venceEn?: string;
  cubre?: string;
  periodo?: MiCuenta["consumo"]["periodo"];
  pruebas?: MiCuenta["pruebas"];
}): MiCuenta {
  return {
    nombre: "Ferretería Torres",
    plan: {
      cuenta_id: "c1",
      plan: { id: "p1", nombre: "Emprende", precio_mensual: 29, limites },
      estado: cambios.estado ?? "VIGENTE",
      inicia_en: "2026-10-08T17:00:00Z",
      vence_en: cambios.venceEn,
      dias_de_gracia: 5,
      hasta_cuando_cubre: cambios.cubre,
    },
    consumo: { mes: "2026-10", documentos: cambios.documentos ?? 0, maximo: "maximo" in cambios ? cambios.maximo : 300, periodo: cambios.periodo ?? "MENSUAL" },
    pruebas: cambios.pruebas ?? { mes: "2026-10", usadas: 0, maximo: 1000 },
    uso: { api_keys: 0, usuarios: 1, rucs: 1 },
  };
}

/** C1: lo que el menú dice del plan; los números son los del backend (consumo del mes y tope del plan de hoy). */
describe("resumenDePlan", () => {
  it("al día y lejos del tope: documentos contra el tope, sin aviso", () => {
    expect(resumenDePlan(cuenta({ documentos: 12 }), es, "es")).toEqual({
      plan: "Emprende",
      consumo: "12 / 300",
      accesible: "Plan Emprende: 12 / 300 documentos este mes",
      porcentaje: 4,
      tono: "ok",
      aviso: null,
    });
  });

  it("desde el 80 % del tope avisa que se acerca", () => {
    const r = resumenDePlan(cuenta({ documentos: 240 }), es, "es");
    expect(r.tono).toBe("aviso");
    expect(r.aviso).toBe("Cerca del tope de documentos del mes");
  });

  it("en el tope o más allá lo dice, y la barra no pasa del 100 %", () => {
    const r = resumenDePlan(cuenta({ documentos: 320 }), es, "es");
    expect(r).toMatchObject({ consumo: "320 / 300", porcentaje: 100, tono: "error", aviso: "Superaste el tope de documentos del mes" });
  });

  /** 265-H1: 300 de 300 todavía no es «superaste»: el backend rechaza recién el documento 301, y el backoffice usa `>`. */
  it("en el tope justo dice que llegó, no que lo superó", () => {
    expect(resumenDePlan(cuenta({ documentos: 300 }), es, "es")).toMatchObject({ tono: "aviso", aviso: "Llegaste al tope de documentos del mes" });
    expect(resumenDePlan(cuenta({ documentos: 301 }), es, "es")).toMatchObject({ tono: "error", aviso: "Superaste el tope de documentos del mes" });
  });

  it("sin tope de documentos no hay barra", () => {
    expect(resumenDePlan(cuenta({ documentos: 5000, maximo: undefined }), es, "es")).toMatchObject({ consumo: "5000 documentos", porcentaje: null, tono: "ok", aviso: null });
  });

  it("vencido en gracia avisa hasta cuándo se le sirve, aunque el consumo esté bien", () => {
    const r = resumenDePlan(cuenta({ documentos: 1, estado: "EN_GRACIA", venceEn: "2026-10-01T05:00:00Z", cubre: "2026-10-06T05:00:00Z" }), es, "es");
    expect(r.tono).toBe("aviso");
    // `hasta_cuando_cubre` es exclusivo: la medianoche del 6 en Lima cubre hasta el 5, como lo muestra el backoffice.
    expect(r.aviso).toBe("Pago vencido: se sirve hasta el 5 Oct 2026");
  });

  it("vencido sin gracia es lo más grave", () => {
    const r = resumenDePlan(cuenta({ documentos: 320, estado: "VENCIDA", venceEn: "2026-09-01T05:00:00Z", cubre: "2026-09-06T05:00:00Z" }), es, "es");
    expect(r.tono).toBe("error");
    expect(r.aviso).toBe("Tu plan venció: escríbenos para renovarlo");
  });
});

/** #613: el plan Gratis da 10 documentos de Producción en total, de por vida: no se habla del mes ni de renovar. */
describe("resumenDePlan con tope total", () => {
  const total = (documentos: number) => cuenta({ documentos, maximo: 10, periodo: "TOTAL" });

  it("dice «X de 10 en total» y lo mismo al lector de pantalla", () => {
    expect(resumenDePlan(total(3), es, "es")).toMatchObject({
      consumo: "3 de 10 en total",
      accesible: "Plan Emprende: 3 de 10 documentos en total",
      porcentaje: 30,
      tono: "ok",
      aviso: null,
    });
  });

  it("los avisos son del tope del plan, no del mes", () => {
    expect(resumenDePlan(total(8), es, "es").aviso).toBe("Cerca del tope de documentos de tu plan");
    expect(resumenDePlan(total(10), es, "es")).toMatchObject({ tono: "aviso", aviso: "Usaste todos los documentos de tu plan" });
    expect(resumenDePlan(total(11), es, "es")).toMatchObject({ tono: "error", aviso: "Superaste el tope de documentos de tu plan" });
  });

  it("en inglés también dice «in total»", () => {
    expect(resumenDePlan(total(3), en, "en").consumo).toBe("3 of 10 in total");
  });
});

/** #613: las pruebas en Beta del mes contra la bolsa de la cuenta (las de cada RUC por sus RUC). */
describe("resumenDePruebas", () => {
  const conPruebas = (usadas: number, maximo?: number) => cuenta({ pruebas: { mes: "2026-10", usadas, ...(maximo === undefined ? {} : { maximo }) } });

  it("lejos del tope: usadas contra el tope, con de dónde sale el tope", () => {
    expect(resumenDePruebas(conPruebas(40, 1000), es)).toEqual({
      texto: "40 / 1,000",
      porcentaje: 4,
      tono: "ok",
      aviso: null,
      nota: "Las pruebas que SUNAT Beta aceptó este mes en todas tus empresas. Tu plan da 1,000 al mes por cada RUC, compartidas entre tus empresas: no gastan tus documentos de Producción y se renuevan el día 1.",
    });
  });

  it("desde el 80 % avisa, y en el tope dice que se renuevan el día 1", () => {
    expect(resumenDePruebas(conPruebas(800, 1000), es)).toMatchObject({ tono: "aviso", aviso: "Estás cerca del tope de pruebas en Beta del mes." });
    expect(resumenDePruebas(conPruebas(1000, 1000), es)).toMatchObject({
      porcentaje: 100,
      tono: "aviso",
      aviso: "Llegaste a las 1,000 pruebas en Beta de este mes: se renuevan el día 1. Para seguir probando antes, pide un cambio de plan.",
    });
  });

  it("sin tope no hay barra ni aviso", () => {
    expect(resumenDePruebas(conPruebas(5000), es)).toMatchObject({ texto: "5,000 · sin tope", porcentaje: null, tono: "ok", aviso: null });
  });
});
