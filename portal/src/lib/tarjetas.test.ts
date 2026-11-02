import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * #511: una tarjeta es la receta `TARJETA` del design system (sin borde en claro; borde suave y brillo en oscuro), no una hecha a mano con
 * `rounded-xl border border-border bg-card shadow-xs`, que se ve distinta junto al shell del DS. Un `bg-card` suelto (una banda de la landing, un
 * encabezado, el fondo de un control chico) no es tarjeta: lo que cuenta es `bg-card` con las esquinas de tarjeta en la misma clase.
 */
const RAIZ = join(import.meta.dirname, "..");
const TARJETA_A_MANO = /["'`][^"'`]*(\brounded-(?:xl|2xl)\b[^"'`]*\bbg-card\b|\bbg-card\b[^"'`]*\brounded-(?:xl|2xl)\b)[^"'`]*["'`]/;

function archivos(dir: string): string[] {
  return readdirSync(dir).flatMap((entrada) => {
    const ruta = join(dir, entrada);
    if (statSync(ruta).isDirectory()) return archivos(ruta);
    return /\.tsx?$/.test(entrada) && !/\.test\.tsx?$/.test(entrada) ? [ruta] : [];
  });
}

describe("las tarjetas son las del design system", () => {
  it("no hay tarjetas hechas a mano con rounded-xl y bg-card", () => {
    const hallazgos = archivos(RAIZ).flatMap((ruta) => {
      const rel = ruta.slice(RAIZ.length + 1).replaceAll("\\", "/");
      return readFileSync(ruta, "utf8")
        .split("\n")
        .flatMap((linea, i) => (TARJETA_A_MANO.test(linea) ? [`${rel}:${i + 1}`] : []));
    });
    expect(hallazgos).toEqual([]);
  });

  it("reconoce la tarjeta a mano en cualquier orden de clases, y no un bg-card suelto", () => {
    expect(TARJETA_A_MANO.test(`className="space-y-3 rounded-xl border border-border bg-card p-5 shadow-xs"`)).toBe(true);
    expect(TARJETA_A_MANO.test(`className={cn("bg-card shadow-xs rounded-2xl", x)}`)).toBe(true);
    expect(TARJETA_A_MANO.test(`className="border-y border-border bg-card"`)).toBe(false);
    expect(TARJETA_A_MANO.test(`className="rounded-full border border-border bg-card p-1"`)).toBe(false);
  });
});
