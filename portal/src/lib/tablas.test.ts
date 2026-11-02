import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * #523: las tablas siguen el patrón del design system (§12): las celdas del kit pintan cada fila como una banda redondeada, así que la cabecera no lleva franja
 * (`bg-muted`), las filas no llevan bordes ni hover propios, y una fila vacía o de carga es `FilaVacia` (sin la banda gris, que en un mensaje alto era un bloque suelto).
 */
const RAIZ = join(import.meta.dirname, "..");

const REGLAS = [
  { patron: /<TableRow[^>]*\bbg-muted\b/, mensaje: "fila con franja bg-muted" },
  { patron: /<TableRow[^>]*\bborder-b\b/, mensaje: "fila con borde propio" },
  { patron: /<TableRow[^>]*\bhover:bg-muted\b/, mensaje: "fila con hover propio" },
  { patron: /<TableCell[^>]*colSpan[^>]*>/, mensaje: "fila vacía a mano: usa FilaVacia" },
];

function archivos(dir: string): string[] {
  return readdirSync(dir).flatMap((entrada) => {
    const ruta = join(dir, entrada);
    if (statSync(ruta).isDirectory()) return archivos(ruta);
    return /\.tsx$/.test(entrada) && !/\.test\.tsx$/.test(entrada) ? [ruta] : [];
  });
}

describe("las tablas usan el patrón del design system", () => {
  it("ninguna fila trae franja, borde u hover propios, y las vacías son FilaVacia", () => {
    const hallazgos = archivos(RAIZ).flatMap((ruta) => {
      const texto = readFileSync(ruta, "utf8");
      if (!texto.includes("khipu-design-system/ui/table")) return [];
      const rel = ruta.slice(RAIZ.length + 1).replaceAll("\\", "/");
      // Una etiqueta JSX puede ocupar varias líneas: se compara cada `<TableRow …>` / `<TableCell …>` entero.
      const etiquetas = texto.match(/<Table(?:Row|Cell)\b[^>]*>/g) ?? [];
      return etiquetas.flatMap((etiqueta) => REGLAS.filter((r) => r.patron.test(etiqueta.replace(/\s+/g, " "))).map((r) => `${rel}: ${r.mensaje}`));
    });
    expect(hallazgos).toEqual([]);
  });
});
