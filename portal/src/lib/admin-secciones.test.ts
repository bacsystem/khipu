import { describe, expect, it } from "vitest";
import { migaAdmin } from "./admin-migas";
import { seccionesAdmin } from "./admin-secciones";
import es from "@/messages/es.json";

describe("secciones del backoffice", () => {
  it("agrupa las páginas en Clientes, Comercial, Operación y Plataforma, en ese orden", () => {
    expect(seccionesAdmin(es).map((s) => [s.titulo, s.items.map((i) => i.href)])).toEqual([
      ["Clientes", ["/admin/cuentas", "/admin/empresas"]],
      ["Comercial", ["/admin/planes", "/admin/consumo"]],
      ["Operación", ["/admin/monitor", "/admin/errores", "/admin/avisos", "/admin/integridad"]],
      ["Plataforma", ["/admin/configuracion"]],
    ]);
  });

  it("todo ítem lleva a una página y tiene un resumen para el inicio: no hay ítems «Pronto»", () => {
    for (const item of seccionesAdmin(es).flatMap((s) => s.items)) {
      expect(item.href).toMatch(/^\/admin\/[a-z]+$/);
      expect(item.resumen.length).toBeGreaterThan(10);
    }
  });

  it("la miga de cada página dice la misma sección que el menú", () => {
    for (const seccion of seccionesAdmin(es)) {
      for (const item of seccion.items) expect(migaAdmin(item.href, es)?.seccion).toBe(seccion.titulo);
    }
  });
});
