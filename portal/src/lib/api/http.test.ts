// @vitest-environment node
import { describe, expect, it } from "vitest";
import { errorResponse } from "./http";
import { ApiError, CODIGO_COMUNICACION, CODIGO_INESPERADO } from "./types";

/**
 * #25: un error de transporte entre el portal y la API viaja al navegador solo con su código; el texto sale del catálogo en el idioma de la sesión.
 * El `message` del ApiError que lo lleva es técnico, para los logs, y no debe llegar a la pantalla.
 */
describe("errorResponse", () => {
  it("un fallo de comunicación con la API viaja sin mensaje, con su código y el status", async () => {
    const res = errorResponse(new ApiError(502, CODIGO_COMUNICACION, "API communication error"));

    expect(res.status).toBe(502);
    expect(await res.json()).toEqual({ estado: "error", datos: null, mensaje: null, codigo: "ERROR_DE_COMUNICACION", errores: null });
  });

  it("un error que no es de la API sale como inesperado, sin mensaje", async () => {
    const res = errorResponse(new TypeError("fetch failed"));

    expect(res.status).toBe(502);
    expect(await res.json()).toEqual({ estado: "error", datos: null, mensaje: null, codigo: CODIGO_INESPERADO, errores: null });
  });

  it("el error que sí respondió la API conserva su mensaje, su código y sus errores", async () => {
    const res = errorResponse(new ApiError(422, "RUC_INVALIDO", "El RUC no es válido", { ruc: ["11 dígitos"] }));

    expect(res.status).toBe(422);
    expect(await res.json()).toEqual({ estado: "error", datos: null, mensaje: "El RUC no es válido", codigo: "RUC_INVALIDO", errores: { ruc: ["11 dígitos"] } });
  });
});
