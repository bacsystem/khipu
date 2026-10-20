import { describe, expect, it } from "vitest";
import { buscarEnLaDocumentacion, ENTRADAS, normalizar } from "./indice";

describe("buscador de la documentación (#26)", () => {
  it("no distingue mayúsculas ni tildes", () => {
    expect(normalizar("VALIDACIÓN de Boleta")).toBe("validacion de boleta");
    expect(buscarEnLaDocumentacion("validacion").map((e) => e.titulo)).toContain("VALIDACION");
    expect(buscarEnLaDocumentacion("BOLETA").length).toBeGreaterThan(0);
  });

  it("un código de error lleva a la tabla de códigos y un estado, a la de estados", () => {
    const [codigo] = buscarEnLaDocumentacion("IDEMPOTENCIA_INVALIDA");
    expect(codigo).toMatchObject({ titulo: "IDEMPOTENCIA_INVALIDA", href: "/developers/errores#codigos" });
    expect(codigo.seccion).toContain("HTTP 422");
    const [estado] = buscarEnLaDocumentacion("ERROR_ENVIO");
    expect(estado).toMatchObject({ titulo: "ERROR_ENVIO", href: "/developers/errores#estados" });
  });

  it("lo que lleva la frase en el título va antes que lo que solo la menciona", () => {
    const resultados = buscarEnLaDocumentacion("ACEPTADO", 20);
    expect(resultados[0].titulo).toBe("ACEPTADO");
    expect(resultados.length).toBeGreaterThan(1);
  });

  it("todas las palabras tienen que estar, en cualquier orden", () => {
    expect(buscarEnLaDocumentacion("limite plan").some((e) => e.href === "/developers/entornos#limites")).toBe(true);
    expect(buscarEnLaDocumentacion("plan zzzzqq")).toEqual([]);
  });

  it("un texto en blanco no busca nada y el máximo se respeta", () => {
    expect(buscarEnLaDocumentacion("   ")).toEqual([]);
    expect(buscarEnLaDocumentacion("a", 3)).toHaveLength(3);
  });

  it("los ids son únicos y todo lleva a una de las páginas de la documentación con su ancla", () => {
    expect(new Set(ENTRADAS.map((e) => e.id)).size).toBe(ENTRADAS.length);
    for (const e of ENTRADAS) expect(e.href, e.id).toMatch(/^\/developers\/(guia|errores|entornos)#[a-z0-9-]+$/);
  });

  it("los entornos y el tope del plan se encuentran por sus palabras de siempre", () => {
    expect(buscarEnLaDocumentacion("MODDATOS")[0].href).toBe("/developers/entornos#entornos");
    expect(buscarEnLaDocumentacion("429").length).toBeGreaterThan(0);
  });
});
