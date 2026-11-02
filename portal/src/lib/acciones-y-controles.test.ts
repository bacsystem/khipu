import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Las acciones y los controles son los del design system: un envío o una acción que espera al backend es `BotonAsync` (patrones/boton-async), un
 * indicador de carga suelto es `Spinner` (feedback/spinner), un botón es una receta (`BOTON_PRIMARIO`, `ACCION_SECUNDARIA`…) o el `Button` del DS, y
 * las sombras y los colores salen de los tokens del tema. Un `animate-spin` a mano, una píldora `rounded-lg bg-primary`, una sombra
 * `shadow-[0_…_rgba(…)]` o un `text-[#1b1206]` se desvían del DS (radio, altura, color en modo oscuro, paleta elegida por el usuario).
 *
 * Reglas, sobre el código de `src/**\/*.tsx` sin pruebas ni comentarios:
 * - SPINNER: la clase `animate-spin` (con o sin variantes). El DS ya la pone dentro de `Spinner` y `BotonAsync`.
 * - BOTON: una línea con `rounded-lg` y un fondo `bg-primary` o `bg-foreground` sin variante: un botón primario armado a mano.
 * - SOMBRA: una clase arbitraria `shadow-[…]`; las sombras son los tokens `shadow-*` o `TARJETA_ELEVABLE`.
 * - HEX: un color hexadecimal arbitrario `-[#…]`; los colores son tokens del tema (y cambian con la paleta y el modo oscuro).
 */
const RAIZ = join(import.meta.dirname, "..");

/**
 * Lo que queda a mano a propósito, con su motivo. Clave: `ruta:regla`; el valor dice por qué. Una excepción que ya no hace falta hace fallar la prueba,
 * para que esta lista no crezca sola.
 */
const EXCEPCIONES: Record<string, string> = {};

type Regla = "SPINNER" | "BOTON" | "SOMBRA" | "HEX";

function clases(linea: string): string[] {
  return linea.split(/[\s"'`{}(),?]+/).filter(Boolean);
}

/** Las reglas que rompe una línea de código (ya sin comentarios). */
function reglasRotas(linea: string): Regla[] {
  const tokens = clases(linea);
  const rotas: Regla[] = [];
  if (tokens.some((t) => t.split(":").pop() === "animate-spin")) rotas.push("SPINNER");
  if (tokens.includes("rounded-lg") && tokens.some((t) => /^bg-(primary|foreground)$/.test(t))) rotas.push("BOTON");
  if (tokens.some((t) => /(^|:)shadow-\[/.test(t))) rotas.push("SOMBRA");
  if (/-\[#[0-9a-fA-F]{3,8}\]/.test(linea)) rotas.push("HEX");
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

describe("las acciones, los spinners, las sombras y los colores son los del design system", () => {
  const hallazgos = archivos(RAIZ).flatMap((ruta) => {
    const rel = ruta.slice(RAIZ.length + 1).replaceAll("\\", "/");
    return codigo(readFileSync(ruta, "utf8")).flatMap((linea, i) => reglasRotas(linea).map((regla) => ({ clave: `${rel}:${regla}`, donde: `${rel}:${i + 1} ${regla}` })));
  });

  it("no hay spinners, botones primarios, sombras ni colores hexadecimales a mano", () => {
    expect(hallazgos.filter((h) => !(h.clave in EXCEPCIONES)).map((h) => h.donde)).toEqual([]);
  });

  it("cada excepción sigue haciendo falta", () => {
    const usadas = new Set(hallazgos.map((h) => h.clave));
    expect(Object.keys(EXCEPCIONES).filter((clave) => !usadas.has(clave))).toEqual([]);
  });
});

describe("las reglas de la guarda", () => {
  it("detectan un spinner a mano", () => {
    expect(reglasRotas(`<CircleNotchIcon className="size-4 animate-spin" />`)).toEqual(["SPINNER"]);
    expect(reglasRotas(`className={cn("size-4", pendiente && "motion-safe:animate-spin")}`)).toEqual(["SPINNER"]);
  });

  it("detectan un botón primario a mano", () => {
    expect(reglasRotas(`<button className="rounded-lg bg-primary px-3 py-1.5 text-primary-foreground">`)).toEqual(["BOTON"]);
    expect(reglasRotas(`<button className="h-control rounded-lg bg-foreground px-4 text-background">`)).toEqual(["BOTON"]);
  });

  it("detectan sombras y colores arbitrarios", () => {
    expect(reglasRotas(`<div className="shadow-[0_24px_60px_-20px_rgba(0,0,0,0.35)]">`)).toEqual(["SOMBRA"]);
    expect(reglasRotas(`<div className="hover:shadow-[0_0_0_1px_red]">`)).toEqual(["SOMBRA"]);
    expect(reglasRotas(`<span className="text-[#1b1206]">`)).toEqual(["HEX"]);
    expect(reglasRotas(`<span className="bg-[#FFF]">`)).toEqual(["HEX"]);
  });

  it("no confunden lo que no es una desviación", () => {
    expect(reglasRotas(`<button className={BOTON_PRIMARIO}>`)).toEqual([]);
    expect(reglasRotas(`<div className="rounded-lg bg-primary/10 text-primary">`)).toEqual([]);
    expect(reglasRotas(`<div className="rounded-lg hover:bg-primary">`)).toEqual([]);
    expect(reglasRotas(`<div className="shadow-sm hover:shadow-md">`)).toEqual([]);
    expect(reglasRotas(`<a href="#seccion">`)).toEqual([]);
  });

  it("ignoran los comentarios", () => {
    expect(codigo(`// antes: animate-spin\n{/* shadow-[0_1px_2px_rgba(0,0,0,.1)] */}\n/**\n * text-[#1b1206]\n */\nconst x = 1;`).flatMap(reglasRotas)).toEqual([]);
  });
});
