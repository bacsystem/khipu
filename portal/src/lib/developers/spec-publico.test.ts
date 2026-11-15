import { describe, expect, it } from "vitest";
import { conServidorPublico } from "./spec-publico";

const INTERNO = { url: "http://khipu-backend.railway.internal:8001", description: "Generated server url" };

describe("conServidorPublico", () => {
  // #678: springdoc pone en `servers` la URL interna con la que el portal leyó el spec. «Send» de la referencia tiene que
  // ir a la pública, y la interna no debe llegar al HTML que recibe el navegador.
  it("reemplaza los servers por la URL pública y deja el resto del spec igual", () => {
    const spec = { openapi: "3.0.1", servers: [INTERNO], paths: {} };

    const publico = conServidorPublico(spec, "https://api.ejemplo.pe");

    expect(publico.servers).toEqual([{ url: "https://api.ejemplo.pe" }]);
    expect(publico.paths).toBe(spec.paths);
    expect(JSON.stringify(publico)).not.toContain("railway.internal");
  });

  it("no modifica el spec recibido", () => {
    const spec = { servers: [INTERNO] };

    conServidorPublico(spec, "https://api.ejemplo.pe");

    expect(spec.servers).toEqual([INTERNO]);
  });

  it("agrega la URL pública si el spec no trae servers", () => {
    expect(conServidorPublico({ openapi: "3.0.1" }, "https://api.ejemplo.pe").servers).toEqual([{ url: "https://api.ejemplo.pe" }]);
  });
});
