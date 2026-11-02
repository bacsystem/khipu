import { describe, expect, it } from "vitest";
import { MAX_LINEAS as DEL_MOCK } from "@/mocks/reglas-linea";
import { MAX_LINEAS } from "./limites";

describe("tope de líneas (#354)", () => {
  /** `Comprobante.MAX_LINEAS`: el número de orden del ítem es de hasta 3 dígitos. El mock del backend usa el mismo. */
  it("es 999, el máximo de 3 dígitos, y el mock usa el mismo", () => {
    expect(MAX_LINEAS).toBe(999);
    expect(DEL_MOCK).toBe(MAX_LINEAS);
  });
});
