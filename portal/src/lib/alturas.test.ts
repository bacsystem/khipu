import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Todos los controles del portal miden lo mismo: h-9 (36 px), la altura única del design system v0.2.0. Botones, inputs, selects, filtros, acciones de fila y
 * pie de diálogo quedan alineados donde sea que se junten. Se vigila de tres formas: las recetas de estilos.ts miden h-9, ningún componente les cambia la
 * altura (`cn(CAMPO, "h-8")`, `` `${BOTON_PRIMARIO} h-10` ``) y ningún `<button>`/`<input>`/`<select>`/enlace declara otra altura a mano. `h-auto` sí vale:
 * es un textarea, que crece con su contenido y no forma fila con nadie.
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

// Etiquetas que son un control: el botón, el enlace con forma de botón, el input y el select. Un `<a>` de texto no declara altura.
const CONTROL = /<(button|input|select|a|Button|Link|BotonCopiar|BotonAsync|SelectTrigger)\b/g;
// Toda altura de control que no sea la única (h-9 / size-9). `h-auto` y las del textarea quedan fuera por no estar en la lista.
const ALTURA_FUERA_DE_ESCALA = /(?<![\w-])(?:[a-z-]+:)*(?:h|size)-(?:5|6|7|8|10|11|12)(?![\w.])/;

/** La etiqueta de apertura de un elemento JSX desde `inicio` (respeta llaves, para no cortar en el `>` de un `=>`). */
function etiquetaDeApertura(texto: string, inicio: number): string {
  let llaves = 0;
  for (let i = inicio; i < texto.length; i++) {
    const c = texto[i];
    if (c === "{") llaves++;
    else if (c === "}") llaves--;
    else if (c === ">" && llaves === 0 && texto[i - 1] !== "=") return texto.slice(inicio, i + 1);
  }
  return texto.slice(inicio);
}

/** Los controles de un archivo que declaran una altura distinta de h-9, con su línea. */
export function controlesFueraDeEscala(texto: string): Array<{ linea: number; codigo: string }> {
  const hallazgos: Array<{ linea: number; codigo: string }> = [];
  // Las clases que un archivo guarda en una constante (`const ACCION = "inline-flex h-7 …"`) y luego pasa a `className={ACCION}`.
  const constantes = new Map([...texto.matchAll(/\bconst ([A-Z][A-Z0-9_]*)\s*=\s*\n?\s*"([^"]*)"/g)].map((c) => [c[1], c[2]]));
  for (const m of texto.matchAll(CONTROL)) {
    const etiqueta = etiquetaDeApertura(texto, m.index);
    // Un input oculto, casilla, radio o archivo no forma fila con los demás controles.
    if (/type="(?:hidden|checkbox|radio|file)"/.test(etiqueta)) continue;
    const usadas = [...etiqueta.matchAll(/\b[A-Z][A-Z0-9_]*\b/g)].map((u) => constantes.get(u[0]) ?? "");
    if (![etiqueta, ...usadas].some((clases) => ALTURA_FUERA_DE_ESCALA.test(clases))) continue;
    hallazgos.push({ linea: texto.slice(0, m.index).split("\n").length, codigo: etiqueta.replace(/\s+/g, " ").slice(0, 120) });
  }
  // Una constante de clases con estado interactivo (hover/disabled) es un control aunque llegue a su `<button>` por una prop
  // (`<BotonPagina className={BOTON_PAGINA}>`), donde la etiqueta de arriba no la ve.
  for (const c of texto.matchAll(/\bconst ([A-Z][A-Z0-9_]*)\s*=\s*\n?\s*"([^"]*)"/g)) {
    if (/\b(?:hover|disabled):/.test(c[2]) && ALTURA_FUERA_DE_ESCALA.test(c[2])) {
      hallazgos.push({ linea: texto.slice(0, c.index).split("\n").length, codigo: `${c[1]} = "${c[2].slice(0, 80)}"` });
    }
  }
  return hallazgos;
}

/**
 * Los controles que miden otra cosa a propósito, por archivo. Cada uno vive DENTRO de otro control de h-9 (no forma fila con nadie)
 * o es la portada pública, que no es el portal.
 */
const EXCEPCIONES: Record<string, string> = {
  "app/page.tsx": "portada pública: sus CTA de marketing miden h-11",
  "components/landing/planes.tsx": "portada pública: el CTA de cada plan",
  "components/empresa/certificado-form.tsx": "mostrar/ocultar la clave va dentro del input",
  "components/empresa/credenciales-sol-form.tsx": "mostrar/ocultar la clave va dentro del input",
  "components/formularios/entrada-monto.tsx": "el selector de moneda va dentro del input",
  "components/nav/theme-toggle.tsx": "segmentos de h-7 dentro de su caja de h-9",
  "components/ui/selector-por-pagina.tsx": "segmentos de h-7 dentro de su caja de h-9",
  "lib/estilos.ts": "SEGMENTO (h-7) va dentro de SEGMENTADO (h-9); las recetas de control tienen su propio test",
};

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

  it("toda receta de control mide h-9, la altura única", () => {
    const estilos = readFileSync(resolve(__dirname, "estilos.ts"), "utf8");
    const recetas = [...estilos.matchAll(/export const ((?:CAMPO|BOTON_\w+|ACCION_\w+|CONTROL_FILTRO|SELECT_NATIVO))\s*=\s*\n?\s*"([^"]*)"/g)];
    expect(recetas.map((r) => r[1])).toEqual(
      expect.arrayContaining(["ACCION_PRINCIPAL", "ACCION_SECUNDARIA", "CONTROL_FILTRO", "CAMPO", "SELECT_NATIVO", "BOTON_PRIMARIO", "BOTON_SECUNDARIO", "BOTON_DESTRUCTIVO"]),
    );
    expect(recetas.filter((r) => !/(?<![\w-])h-9(?![\w.])/.test(r[2])).map((r) => r[1])).toEqual([]);
  });

  it("detecta un control que declara otra altura", () => {
    expect(controlesFueraDeEscala(`<button type="button" className="inline-flex h-7 items-center">x</button>`)).toHaveLength(1);
    expect(controlesFueraDeEscala(`<button\n  onClick={() => setAbierto(true)}\n  className="flex size-8 items-center"\n>`)).toHaveLength(1);
    expect(controlesFueraDeEscala(`<Button className="h-10 px-4">Entrar</Button>`)).toHaveLength(1);
    expect(controlesFueraDeEscala(`const BOTON =\n  "inline-flex h-7 items-center";\n<button type="button" className={BOTON}>Anterior</button>`)).toHaveLength(1);
    expect(controlesFueraDeEscala(`const BOTON_PAGINA = "inline-flex h-7 hover:bg-muted";\n<Pagina className={BOTON_PAGINA} />`)).toHaveLength(1);
    expect(controlesFueraDeEscala(`const INSIGNIA = "flex size-7 rounded bg-accent";`)).toEqual([]);
    expect(controlesFueraDeEscala(`<button type="button" className="inline-flex h-9 items-center"><XIcon className="size-4" /></button>`)).toEqual([]);
    expect(controlesFueraDeEscala(`<input type="checkbox" className="size-4" />`)).toEqual([]);
  });

  it("ningún control del portal mide otra cosa que h-9", () => {
    const raiz = resolve(__dirname, "..");
    const encontradas = archivosTsx(raiz).flatMap((ruta) => {
      const relativa = relative(raiz, ruta).replaceAll("\\", "/");
      if (relativa in EXCEPCIONES) return [];
      return controlesFueraDeEscala(readFileSync(ruta, "utf8")).map((s) => `${relativa}:${s.linea}  ${s.codigo}`);
    });
    expect(encontradas).toEqual([]);
  });

  it("ningún componente del portal sobrescribe la altura de una receta", () => {
    const raiz = resolve(__dirname, "..");
    const encontradas = archivosTsx(raiz).flatMap((ruta) =>
      sobrescrituras(readFileSync(ruta, "utf8")).map((s) => `${relative(raiz, ruta).replaceAll("\\", "/")}:${s.linea}  ${s.codigo}`),
    );
    expect(encontradas).toEqual([]);
  });
});
