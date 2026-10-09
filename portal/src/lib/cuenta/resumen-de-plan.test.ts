import { describe, expect, it } from "vitest";
import type { MiCuenta } from "@/lib/api/cuenta";
import { resumenDePlan } from "./resumen-de-plan";

const limites = {
  documentos_al_mes: { maximo: 300, ilimitado: false },
  rucs: 1,
  usuarios: { maximo: 1, ilimitado: false },
  api_keys: { maximo: 2, ilimitado: false },
  retencion_anios: 5,
};

function cuenta(cambios: { documentos?: number; maximo?: number; estado?: MiCuenta["plan"]["estado"]; venceEn?: string; cubre?: string }): MiCuenta {
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
    consumo: { mes: "2026-10", documentos: cambios.documentos ?? 0, maximo: "maximo" in cambios ? cambios.maximo : 300 },
  };
}

/** C1: lo que el menú dice del plan; los números son los del backend (consumo del mes y tope del plan de hoy). */
describe("resumenDePlan", () => {
  it("al día y lejos del tope: documentos contra el tope, sin aviso", () => {
    expect(resumenDePlan(cuenta({ documentos: 12 }))).toEqual({ plan: "Emprende", consumo: "12 / 300", porcentaje: 4, tono: "ok", aviso: null });
  });

  it("desde el 80 % del tope avisa que se acerca", () => {
    const r = resumenDePlan(cuenta({ documentos: 240 }));
    expect(r.tono).toBe("aviso");
    expect(r.aviso).toBe("Cerca del tope de documentos del mes");
  });

  it("en el tope o más allá lo dice, y la barra no pasa del 100 %", () => {
    const r = resumenDePlan(cuenta({ documentos: 320 }));
    expect(r).toMatchObject({ consumo: "320 / 300", porcentaje: 100, tono: "error", aviso: "Superaste el tope de documentos del mes" });
  });

  it("sin tope de documentos no hay barra", () => {
    expect(resumenDePlan(cuenta({ documentos: 5000, maximo: undefined }))).toMatchObject({ consumo: "5000 documentos", porcentaje: null, tono: "ok", aviso: null });
  });

  it("vencido en gracia avisa hasta cuándo se le sirve, aunque el consumo esté bien", () => {
    const r = resumenDePlan(cuenta({ documentos: 1, estado: "EN_GRACIA", venceEn: "2026-10-01T05:00:00Z", cubre: "2026-10-06T05:00:00Z" }));
    expect(r.tono).toBe("aviso");
    // `hasta_cuando_cubre` es exclusivo: la medianoche del 6 en Lima cubre hasta el 5, como lo muestra el backoffice.
    expect(r.aviso).toBe("Pago vencido: se sirve hasta el 5 Oct 2026");
  });

  it("vencido sin gracia es lo más grave", () => {
    const r = resumenDePlan(cuenta({ documentos: 320, estado: "VENCIDA", venceEn: "2026-09-01T05:00:00Z", cubre: "2026-09-06T05:00:00Z" }));
    expect(r.tono).toBe("error");
    expect(r.aviso).toBe("Tu plan venció: escríbenos para renovarlo");
  });
});
