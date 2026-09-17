import { describe, expect, it } from "vitest";
import { paginasVisibles, porPaginaValido } from "./paginacion";

describe("paginasVisibles", () => {
  it("muestra todas las páginas cuando son pocas", () => {
    expect(paginasVisibles(1, 5)).toEqual([1, 2, 3, 4, 5]);
  });

  it("resume con puntos suspensivos alrededor de la página actual", () => {
    expect(paginasVisibles(5, 10)).toEqual([1, 2, "…", 4, 5, 6, "…", 9, 10]);
    expect(paginasVisibles(1, 10)).toEqual([1, 2, "…", 9, 10]);
  });
});

describe("porPaginaValido", () => {
  it("acepta solo las opciones permitidas", () => {
    expect(porPaginaValido("20")).toBe(20);
    expect(porPaginaValido("7")).toBe(10);
    expect(porPaginaValido(null)).toBe(10);
  });
});
