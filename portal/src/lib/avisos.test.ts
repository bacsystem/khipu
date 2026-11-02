import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Los avisos y las pills de estado son los del design system: `Alerta` (feedback/alerta) en una página, `Aviso` (patrones/cabecera-dialogo) dentro de un
 * diálogo, `Banner` (feedback/banner) para una banda de ancho completo, y `PillEstado` / `Chip` / `ChipCodigo` (patrones/pill-estado) para un estado. Un `div`
 * teñido a mano con el rojo o el ámbar se desvía del tinte, del radio y del icono del DS (y dos pantallas acababan con dos rojos distintos: `bg-destructive/5`
 * y `bg-destructive-suave`); una pill a mano, de su borde y su punto. Las filas de tabla tampoco se tiñen: la pill de la fila dice el estado.
 *
 * Reglas, sobre el código de `src/**\/*.tsx` sin pruebas ni comentarios:
 * - TINTE: una clase `bg-destructive-suave`, `bg-destructive/N`, `bg-warning` o `bg-warning/N`, salvo con variantes solo de interacción (`hover:`, `focus:`…):
 *   el fondo de un botón destructivo al pasar el mouse no es un aviso. `bg-warning-solid` (el punto de una pill o una barra de progreso) no cuenta.
 * - PILL: una línea con `rounded-full`, `border` y un fondo de estado (`bg-success`, `bg-warning`, `bg-destructive`…, no `-solid`).
 */
const RAIZ = join(import.meta.dirname, "..");

/**
 * Lo que queda a mano a propósito, con su motivo. Clave: `ruta:regla`; el valor dice por qué. Una excepción que ya no hace falta hace fallar la prueba,
 * para que esta lista no crezca sola.
 */
const EXCEPCIONES: Record<string, string> = {};

/** Partes de `src/` que esta guarda no mira porque otra tarea las está migrando al DS en paralelo; se quitan cuando esa tarea termine. */
const FUERA_DE_ALCANCE = [/^app\/\(privado\)\/empresa\//, /^components\/empresa\//, /^components\/comprobantes\/nuevo-comprobante-/];

const INTERACCION = new Set(["hover", "focus", "focus-visible", "focus-within", "active", "group-hover", "peer-hover", "disabled", "aria-selected", "data-highlighted"]);
const TINTE = /^bg-(destructive-suave|destructive\/\d+|warning(\/\d+)?)$/;
const FONDO_DE_ESTADO = /^bg-(success|warning|destructive)(-suave)?(\/\d+)?$/;

function clases(linea: string): string[] {
  return linea.split(/[\s"'`{}(),?]+/).filter(Boolean);
}

/** Las reglas que rompe una línea de código (ya sin comentarios). */
function reglasRotas(linea: string): ("TINTE" | "PILL")[] {
  const tokens = clases(linea);
  const rotas: ("TINTE" | "PILL")[] = [];
  const tinte = tokens.some((t) => {
    const partes = t.split(":");
    const base = partes.pop() ?? "";
    return TINTE.test(base) && !(partes.length > 0 && partes.every((v) => INTERACCION.has(v)));
  });
  if (tinte) rotas.push("TINTE");
  const pill = tokens.includes("rounded-full") && tokens.includes("border") && tokens.some((t) => !t.includes(":") && FONDO_DE_ESTADO.test(t));
  if (pill) rotas.push("PILL");
  return rotas;
}

/** El código de un archivo, línea por línea, sin comentarios (pueden nombrar las clases para explicar por qué ya no se usan). */
function codigo(texto: string): string[] {
  let enBloque = false;
  return texto.split("\n").map((linea) => {
    let l = linea;
    if (enBloque) {
      const fin = l.indexOf("*/");
      if (fin === -1) return "";
      l = l.slice(fin + 2);
      enBloque = false;
    }
    l = l.replace(/\/\*.*?\*\//g, "");
    const inicio = l.indexOf("/*");
    if (inicio !== -1) {
      enBloque = true;
      l = l.slice(0, inicio);
    }
    return l.replace(/(^|\s)\/\/.*$/, "");
  });
}

function archivos(dir: string): string[] {
  return readdirSync(dir).flatMap((entrada) => {
    const ruta = join(dir, entrada);
    if (statSync(ruta).isDirectory()) return archivos(ruta);
    return /\.tsx$/.test(entrada) && !/\.test\.tsx$/.test(entrada) ? [ruta] : [];
  });
}

describe("los avisos y las pills de estado son los del design system", () => {
  const hallazgos = archivos(RAIZ).flatMap((ruta) => {
    const rel = ruta.slice(RAIZ.length + 1).replaceAll("\\", "/");
    if (FUERA_DE_ALCANCE.some((r) => r.test(rel))) return [];
    return codigo(readFileSync(ruta, "utf8")).flatMap((linea, i) => reglasRotas(linea).map((regla) => ({ clave: `${rel}:${regla}`, donde: `${rel}:${i + 1} ${regla}` })));
  });

  it("no hay avisos teñidos ni pills hechos a mano", () => {
    expect(hallazgos.filter((h) => !(h.clave in EXCEPCIONES)).map((h) => h.donde)).toEqual([]);
  });

  it("cada excepción sigue haciendo falta", () => {
    const usadas = new Set(hallazgos.map((h) => h.clave));
    expect(Object.keys(EXCEPCIONES).filter((clave) => !usadas.has(clave))).toEqual([]);
  });
});

describe("las reglas de la guarda", () => {
  it("detectan un aviso teñido a mano", () => {
    expect(reglasRotas(`<div className="rounded-lg border border-destructive/40 bg-destructive/5 px-3 py-2">`)).toEqual(["TINTE"]);
    expect(reglasRotas(`<p role="alert" className="rounded-xl bg-destructive-suave text-destructive-texto">`)).toEqual(["TINTE"]);
    expect(reglasRotas(`<div className="border border-warning-border bg-warning px-4 py-2">`)).toEqual(["TINTE"]);
    expect(reglasRotas(`<TableRow className={cn(c.en_alerta && "*:bg-warning/40")}>`)).toEqual(["TINTE"]);
  });

  it("detectan una pill a mano", () => {
    expect(reglasRotas(`<span className="inline-flex rounded-full border border-success-border bg-success px-2.5">`)).toEqual(["PILL"]);
    expect(reglasRotas(`<span className="rounded-full border bg-warning px-2">`)).toEqual(["TINTE", "PILL"]);
  });

  it("no confunden lo que no es un aviso", () => {
    // El fondo de un botón destructivo al pasar el mouse.
    expect(reglasRotas(`className={cn(ACCION, "text-destructive-texto hover:bg-destructive-suave")}`)).toEqual([]);
    // El punto de una pill y la barra de un medidor.
    expect(reglasRotas(`<span className="size-1.5 shrink-0 rounded-full bg-warning-solid" />`)).toEqual([]);
    expect(reglasRotas(`<div className={cn("h-full rounded-full", tono === "error" && "bg-destructive")} />`)).toEqual([]);
    // Un botón con estado de «copiado» (no es una pill: no es `rounded-full`).
    expect(reglasRotas(`className={cn(BOTON_SECUNDARIO, copiada && "border-success-border bg-success text-success-foreground")}`)).toEqual([]);
  });

  it("ignoran los comentarios", () => {
    expect(codigo(`// antes: bg-destructive/5\n{/* bg-warning/40 */}\n/**\n * bg-destructive-suave\n */\nconst x = 1;`).flatMap(reglasRotas)).toEqual([]);
  });
});
