import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * #531. `CAMPO` del design system lleva `text-campo md:text-control`: 16 px en el teléfono para que iOS no amplíe la página al enfocar un campo. Un
 * `cn(CAMPO, "text-control")` se lo quita (lo hacían 9 formularios con su alias `CAMPO_DENSO`). Y `CAMPO` mide h-control: un `<textarea>` con él necesita `h-auto`,
 * o queda de 32 px aunque pida varias filas.
 */
const RAIZ = join(import.meta.dirname, "..");

function archivos(dir: string): string[] {
  return readdirSync(dir).flatMap((entrada) => {
    const ruta = join(dir, entrada);
    if (statSync(ruta).isDirectory()) return entrada === "mocks" ? [] : archivos(ruta);
    return /\.tsx?$/.test(entrada) && !/\.test\.tsx?$/.test(entrada) ? [ruta] : [];
  });
}

describe("los campos conservan lo que trae CAMPO", () => {
  it("ningún campo pisa su letra de 16 px en el teléfono con text-control", () => {
    const hallazgos = archivos(RAIZ).flatMap((ruta) => {
      const rel = ruta.slice(RAIZ.length + 1).replaceAll("\\", "/");
      return [...readFileSync(ruta, "utf8").matchAll(/cn\(\s*CAMPO\s*,[^)]*\btext-control\b/g)].map(() => rel);
    });
    expect(hallazgos).toEqual([]);
  });

  it("un textarea con CAMPO no queda con la altura de un control", () => {
    const hallazgos = archivos(RAIZ).flatMap((ruta) => {
      const rel = ruta.slice(RAIZ.length + 1).replaceAll("\\", "/");
      return (readFileSync(ruta, "utf8").match(/<textarea\b[^>]*>/g) ?? [])
        .map((e) => e.replace(/\s+/g, " "))
        .filter((e) => /\bCAMPO\b/.test(e) && !/\bh-auto\b/.test(e))
        .map((e) => `${rel}: ${e.slice(0, 90)}`);
    });
    expect(hallazgos).toEqual([]);
  });
});
