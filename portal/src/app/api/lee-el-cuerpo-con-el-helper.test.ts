import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * #608: el cuerpo de una ruta del BFF se lee en un solo lugar, `leerCuerpoJson` (src/lib/api/cuerpo-json.ts). Un `req.json()` suelto vuelve a lanzar con un cuerpo vacío o
 * que no es JSON, y como suele estar fuera de un `try` termina en un 500; uno a mano repite la validación de «es un objeto» que el helper ya hace.
 * El proxy no entra: reenvía los bytes tal cual (`arrayBuffer`) y no parsea.
 */
const RAIZ = import.meta.dirname;

function fuentes(dir: string): string[] {
  return readdirSync(dir).flatMap((entrada) => {
    const ruta = join(dir, entrada);
    if (statSync(ruta).isDirectory()) return fuentes(ruta);
    return /\.ts$/.test(entrada) && !/\.test\.ts$/.test(entrada) ? [ruta] : [];
  });
}

const LECTURA_DIRECTA = /\b(req|request)\.(json|text)\(\)/;

describe("las rutas del BFF leen el cuerpo con leerCuerpoJson", () => {
  it("ningún archivo de src/app/api llama req.json() ni req.text()", () => {
    const con = fuentes(RAIZ).filter((ruta) => LECTURA_DIRECTA.test(readFileSync(ruta, "utf8")));
    expect(con.map((ruta) => ruta.slice(RAIZ.length + 1))).toEqual([]);
  });

  it("la guarda detecta una lectura directa", () => {
    expect(LECTURA_DIRECTA.test("const { email } = await req.json();")).toBe(true);
    expect(LECTURA_DIRECTA.test("JSON.parse(await request.text())")).toBe(true);
    expect(LECTURA_DIRECTA.test("await leerCuerpoJson(req)")).toBe(false);
  });
});
