import { describe, expect, it } from "vitest";
import { ENTRADAS } from "./entradas";
import { buscarEnLaDocumentacion, indexar, normalizar } from "./indice";

const INDICE = indexar(ENTRADAS);
const buscar = (texto: string, maximo?: number) => buscarEnLaDocumentacion(texto, INDICE, maximo);

describe("buscador de la documentación (#26)", () => {
  it("no distingue mayúsculas ni tildes", () => {
    expect(normalizar("VALIDACIÓN de Boleta")).toBe("validacion de boleta");
    expect(buscar("validacion").map((e) => e.titulo)).toContain("VALIDACION");
    expect(buscar("BOLETA").length).toBeGreaterThan(0);
  });

  it("un código de error lleva a la tabla de códigos y un estado, a la de estados", () => {
    const [codigo] = buscar("IDEMPOTENCIA_INVALIDA");
    expect(codigo).toMatchObject({ titulo: "IDEMPOTENCIA_INVALIDA", href: "/developers/errores#codigos" });
    expect(codigo.seccion).toContain("HTTP 422");
    const [estado] = buscar("ERROR_ENVIO");
    expect(estado).toMatchObject({ titulo: "ERROR_ENVIO", href: "/developers/errores#estados" });
  });

  it("lo que lleva la frase en el título va antes que lo que solo la menciona", () => {
    const resultados = buscar("ACEPTADO", 20);
    expect(resultados[0].titulo).toBe("ACEPTADO");
    expect(resultados.length).toBeGreaterThan(1);
  });

  it("todas las palabras tienen que estar, en cualquier orden", () => {
    expect(buscar("limite plan").some((e) => e.href === "/developers/entornos#limites")).toBe(true);
    expect(buscar("plan zzzzqq")).toEqual([]);
  });

  it("un texto en blanco no busca nada y el máximo se respeta", () => {
    expect(buscar("   ")).toEqual([]);
    expect(buscar("a", 3)).toHaveLength(3);
  });

  it("los ids son únicos y todo lleva a una de las páginas de la documentación con su ancla", () => {
    expect(new Set(ENTRADAS.map((e) => e.id)).size).toBe(ENTRADAS.length);
    for (const e of ENTRADAS) expect(e.href, e.id).toMatch(/^\/developers\/(guia|errores|entornos)#[a-z0-9-]+$/);
  });

  it("lo que dice la página de entornos se encuentra: sus filas son resultados, con el código de error que publican", () => {
    expect(buscar("MODDATOS")[0].href).toBe("/developers/entornos#entornos");
    expect(buscar("DEMASIADAS_CONSULTAS")[0]).toMatchObject({ titulo: expect.stringContaining("Consulta de un RUC"), href: "/developers/entornos#limites" });
    expect(buscar("DEMASIADOS_INTENTOS_LOGIN")[0].href).toBe("/developers/entornos#limites");
    expect(buscar("ARCHIVO_DEMASIADO_GRANDE")[0].href).toBe("/developers/entornos#limites");
    expect(buscar("429", 60).some((e) => e.href === "/developers/entornos#limites")).toBe(true);
    expect(buscar("webhook reintenta")[0].href).toBe("/developers/entornos#reintentos");
  });

  it("los temas de la guía se encuentran por su nombre propio", () => {
    expect(buscar("X-Khipu-Signature")[0].href).toBe("/developers/guia#webhooks");
    expect(buscar("firma webhook")[0].href).toBe("/developers/guia#webhooks");
    // Idempotency-Key está en la guía (su caso) y en la tabla de vigencias de Entornos: las dos se ofrecen.
    expect(buscar("Idempotency-Key").map((e) => e.href)).toEqual(expect.arrayContaining(["/developers/guia#idempotency-key", "/developers/entornos#reintentos"]));
  });

  it("el detalle que viaja al navegador está acotado", () => {
    expect(Math.max(...ENTRADAS.map((e) => e.detalle.length))).toBeLessThanOrEqual(320);
  });
});
