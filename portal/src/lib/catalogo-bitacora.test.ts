import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { messages } from "@/lib/messages";

/**
 * Cada acción que el backend escribe en la bitácora (`AccionAdmin`) tiene su nombre en el catálogo del portal. Sin esto, la acción nueva de una PR se ve con
 * su código crudo en el detalle de la cuenta (pasó con la suspensión en #182 y con la impersonación en #184). Se lee el enum del dominio del propio repo, así
 * la guarda no depende de que alguien se acuerde de agregarla a mano.
 */
describe("catálogo de acciones de la bitácora", () => {
  const fuente = readFileSync(resolve(__dirname, "../../../domain/src/main/java/pe/factura/domain/plataforma/AccionAdmin.java"), "utf8");
  const cuerpo = fuente.slice(fuente.indexOf("{") + 1, fuente.lastIndexOf("}"));
  const acciones = [...cuerpo.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/.*$/gm, "").matchAll(/\b([A-Z][A-Z0-9_]+)\b/g)].map((m) => m[1]);

  it("lee las acciones del enum del backend", () => {
    expect(acciones.length).toBeGreaterThan(5);
    expect(acciones).toContain("CREAR_CUENTA");
  });

  it("todas tienen nombre en el catálogo del portal", () => {
    const catalogo = messages.admin.detalle.eventos.acciones as Record<string, string>;
    expect(acciones.filter((a) => !catalogo[a])).toEqual([]);
  });
});
