import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Ningún componente le cambia la altura a una receta de control (`cn(CAMPO, "h-9")`, `` `${BOTON_PRIMARIO} h-8` ``). La escala de alturas del design system
 * tiene una receta por altura y contexto (estilos.ts): si un control necesita otra, se usa esa. Sobrescribirla a mano es lo que dejó, por ejemplo, el buscador
 * de la cola de errores en h-10 junto a su botón en h-9. `h-auto` sí vale: es un textarea, que crece con su contenido y no forma fila con nadie.
 */
const RECETA = String.raw`(?:CAMPO\w*|BOTON_\w+|ACCION_\w+|CONTROL_FILTRO|SELECT_NATIVO|SEGMENT\w*)`;
const ALTURA = /(?<![\w-])(?:[a-z-]+:)*h-(?!auto\b)[\d.[]/;
const EN_CN = new RegExp(String.raw`\bcn\(\s*(?:[^,()?]+\?\s*)?${RECETA}(?:\s*:\s*${RECETA})?\s*,([^()]*(?:\([^()]*\)[^()]*)*)\)`, "g");
const EN_PLANTILLA = new RegExp(String.raw`\$\{${RECETA}\}([^\x60]*)`, "g");

/** Las sobrescrituras de altura de un archivo, con su línea. */
export function sobrescrituras(texto: string): Array<{ linea: number; codigo: string }> {
  const hallazgos: Array<{ linea: number; codigo: string }> = [];
  for (const patron of [EN_CN, EN_PLANTILLA]) {
    for (const m of texto.matchAll(patron)) {
      if (!ALTURA.test(m[1])) continue;
      hallazgos.push({ linea: texto.slice(0, m.index).split("\n").length, codigo: m[0].replace(/\s+/g, " ").slice(0, 100) });
    }
  }
  return hallazgos;
}

function archivosTsx(dir: string): string[] {
  return readdirSync(dir).flatMap((nombre) => {
    const ruta = join(dir, nombre);
    if (statSync(ruta).isDirectory()) return nombre === "mocks" ? [] : archivosTsx(ruta);
    return /\.tsx?$/.test(nombre) && !/\.test\.tsx?$/.test(nombre) ? [ruta] : [];
  });
}

describe("alturas de los controles", () => {
  it("detecta una altura puesta encima de una receta", () => {
    expect(sobrescrituras(`cn(BOTON_SECUNDARIO, "h-9 px-3.5 text-[13px]")`)).toHaveLength(1);
    expect(sobrescrituras(`cn(abierto ? BOTON_PRIMARIO : BOTON_SECUNDARIO, "h-8 text-xs")`)).toHaveLength(1);
    expect(sobrescrituras("className={`${CAMPO} h-9 w-64`}")).toHaveLength(1);
    expect(sobrescrituras(`cn(CAMPO, variante === "filtro" && "h-8", "pl-9")`)).toHaveLength(1);
  });

  it("no confunde lo que no cambia la altura", () => {
    expect(sobrescrituras(`cn(CAMPO, "h-auto min-h-20 resize-y py-2")`)).toEqual([]);
    expect(sobrescrituras(`cn(CONTROL_FILTRO, "inline-flex size-8 items-center")`)).toEqual([]);
    expect(sobrescrituras(`cn(BOTON_PRIMARIO, "mt-6")`)).toEqual([]);
    expect(sobrescrituras("className={`${CAMPO} w-64`}")).toEqual([]);
  });

  it("ningún componente del portal sobrescribe la altura de una receta", () => {
    const raiz = resolve(__dirname, "..");
    const encontradas = archivosTsx(raiz).flatMap((ruta) =>
      sobrescrituras(readFileSync(ruta, "utf8")).map((s) => `${relative(raiz, ruta).replaceAll("\\", "/")}:${s.linea}  ${s.codigo}`),
    );
    expect(encontradas).toEqual([]);
  });
});
