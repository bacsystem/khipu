import { describe, expect, it } from "vitest";
import { migaAdmin } from "./admin-migas";
import { SECCIONES_ADMIN } from "./admin-secciones";

describe("secciones del backoffice", () => {
  it("agrupa las páginas en Clientes, Comercial, Operación y Plataforma, en ese orden", () => {
    expect(SECCIONES_ADMIN.map((s) => [s.titulo, s.items.map((i) => i.href)])).toEqual([
      ["Clientes", ["/admin/cuentas", "/admin/empresas"]],
      ["Comercial", ["/admin/planes", "/admin/consumo"]],
      ["Operación", ["/admin/monitor", "/admin/errores", "/admin/avisos", "/admin/integridad"]],
      ["Plataforma", ["/admin/configuracion"]],
    ]);
  });

  it("todo ítem lleva a una página y tiene un resumen para el inicio: no hay ítems «Pronto»", () => {
    for (const item of SECCIONES_ADMIN.flatMap((s) => s.items)) {
      expect(item.href).toMatch(/^\/admin\/[a-z]+$/);
      expect(item.resumen.length).toBeGreaterThan(10);
    }
  });

  it("la miga de cada página dice la misma sección que el menú", () => {
    for (const seccion of SECCIONES_ADMIN) {
      for (const item of seccion.items) expect(migaAdmin(item.href)?.seccion).toBe(seccion.titulo);
    }
  });
});
