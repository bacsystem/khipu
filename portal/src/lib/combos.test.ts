import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * #555: los combos son los del design system (`SelectCampo`, `Select` o `Combobox`). El `<select>` nativo lo dibuja el sistema operativo (lista gris en
 * Windows, rueda en iPhone) y no sigue la plantilla; `SELECT_NATIVO` es su receta, deprecada desde DS 0.5.7.
 */
const RAIZ = join(import.meta.dirname, "..");

function archivos(dir: string): string[] {
  return readdirSync(dir).flatMap((entrada) => {
    const ruta = join(dir, entrada);
    if (statSync(ruta).isDirectory()) return archivos(ruta);
    return /\.tsx?$/.test(entrada) && !/\.test\.tsx?$/.test(entrada) ? [ruta] : [];
  });
}

describe("los combos son los del design system", () => {
  it("no hay <select> nativo ni SELECT_NATIVO en el portal", () => {
    const hallazgos = archivos(RAIZ).flatMap((ruta) => {
      const rel = ruta.slice(RAIZ.length + 1).replaceAll("\\", "/");
      return readFileSync(ruta, "utf8")
        .split("\n")
        // Los comentarios pueden nombrar el `<select>` (por qué ya no se usa): solo cuenta el código.
        .map((linea) => (/^\s*(\/\/|\/?\*)/.test(linea) ? "" : linea.replace(/\/\/.*$/, "").replace(/`[^`]*`/g, "")))
        .flatMap((linea, i) => (/<select\b|\bSELECT_NATIVO\b/.test(linea) ? [`${rel}:${i + 1}`] : []));
    });
    expect(hallazgos).toEqual([]);
  });
});
