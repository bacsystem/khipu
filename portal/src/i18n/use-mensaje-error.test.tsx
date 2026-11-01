import { renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useMensajeError } from "./use-mensaje-error";
import { fijarLocaleDePrueba } from "./prueba";
import en from "@/messages/en.json";
import es from "@/messages/es.json";

const SERVIDOR = "Tu plan Gratis incluye 1 API key y ya tienes 1 API key activas. Revoca una que ya no uses, o pide un cambio de plan";

/** #498: con `CUPO_DEL_PLAN` el texto que sirve es el del backend (nombra el plan, cuánto incluye y cómo liberar un lugar); el resto de los códigos sigue siendo el catálogo. */
describe("useMensajeError", () => {
  it("con un código del catálogo da su texto, y el mensaje del servidor no lo pisa", () => {
    const { result } = renderHook(() => useMensajeError());
    expect(result.current("LIMITE_PLAN", "otro texto")).toBe(es.errores.LIMITE_PLAN);
    expect(result.current("CODIGO_QUE_NO_EXISTE")).toBe(es.errores.generico);
    expect(result.current(null)).toBe(es.errores.generico);
  });

  it("con CUPO_DEL_PLAN en español da el mensaje del servidor", () => {
    const { result } = renderHook(() => useMensajeError());
    expect(result.current("CUPO_DEL_PLAN", SERVIDOR)).toBe(SERVIDOR);
  });

  it("con CUPO_DEL_PLAN y sin mensaje del servidor da el genérico del catálogo", () => {
    const { result } = renderHook(() => useMensajeError());
    expect(result.current("CUPO_DEL_PLAN")).toBe(es.errores.CUPO_DEL_PLAN);
    expect(result.current("CUPO_DEL_PLAN", "")).toBe(es.errores.CUPO_DEL_PLAN);
  });

  /** El mensaje del servidor está en español: a quien usa el portal en inglés no se le mezcla otro idioma. */
  it("en inglés se queda con el genérico del catálogo", () => {
    fijarLocaleDePrueba("en");
    const { result } = renderHook(() => useMensajeError());
    expect(result.current("CUPO_DEL_PLAN", SERVIDOR)).toBe(en.errores.CUPO_DEL_PLAN);
  });
});
