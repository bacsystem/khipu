import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * #509: desde el design system v0.5.0 todos los íconos son Phosphor (duotono, rellenos al activarse, en negrita los funcionales). Un `lucide-react` que vuelve a colarse
 * se vería con otro trazo junto a los del kit, y el paquete ya no está entre las dependencias.
 */
const RAIZ = join(import.meta.dirname, "..");

function archivos(dir: string): string[] {
  return readdirSync(dir).flatMap((entrada) => {
    const ruta = join(dir, entrada);
    if (statSync(ruta).isDirectory()) return archivos(ruta);
    return /\.tsx?$/.test(entrada) && entrada !== "iconos.test.ts" ? [ruta] : [];
  });
}

describe("los íconos del portal son los del design system", () => {
  it("ningún archivo importa lucide-react", () => {
    const con = archivos(RAIZ).filter((ruta) => readFileSync(ruta, "utf8").includes("lucide-react"));
    expect(con.map((ruta) => ruta.slice(RAIZ.length + 1))).toEqual([]);
  });

  it("los íconos funcionales (caret, check, cerrar, más, lupa…) van en negrita, como en el design system", () => {
    const sinPeso = archivos(RAIZ).filter((ruta) => !/\.test\.tsx?$/.test(ruta)).flatMap((ruta) =>
      [...readFileSync(ruta, "utf8").matchAll(/<(CaretDownIcon|CaretUpIcon|CaretLeftIcon|CaretRightIcon|CaretUpDownIcon|CheckIcon|XIcon|PlusIcon|MinusIcon|MagnifyingGlassIcon)(?![\w])([^>]*?)\/?>/g)]
        .filter((m) => !/\bweight=/.test(m[2]))
        .map(() => ruta.slice(RAIZ.length + 1)),
    );
    expect(sinPeso).toEqual([]);
  });
});
