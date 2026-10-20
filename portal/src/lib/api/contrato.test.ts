import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import ts from "typescript";
import { afterAll, describe, expect, it } from "vitest";

/**
 * #5: `contrato.ts` solo tiene tipos, así que su prueba es compilar. Cada caso arma un archivo con los tipos de `contrato.ts` y unos tipos de ejemplo (uno «a mano», uno
 * «del esquema»), lo compila con el compilador de TypeScript y mira si hay errores: sin esta prueba, un `Contrato` que dejara pasar todo sería indistinguible de uno que funciona.
 */
const FUENTE = readFileSync(path.resolve(__dirname, "contrato.ts"), "utf-8");
const carpeta = mkdtempSync(path.join(tmpdir(), "contrato-"));
afterAll(() => rmSync(carpeta, { recursive: true, force: true }));

let n = 0;
function errores(ejemplo: string): string {
  const archivo = path.join(carpeta, `caso${n++}.ts`);
  writeFileSync(archivo, `${FUENTE}\n${ejemplo}\n`);
  const programa = ts.createProgram([archivo], { strict: true, noEmit: true, target: ts.ScriptTarget.ES2022, lib: ["lib.es2022.d.ts"], types: [], skipLibCheck: true });
  return ts.getPreEmitDiagnostics(programa).map((d) => ts.flattenDiagnosticMessageText(d.messageText, "\n")).join("\n");
}

describe("Contrato entre los tipos a mano y el OpenAPI (#5)", () => {
  it("pasa cuando el tipo a mano es más estrecho, opcional o nullable que el esquema, y cuando el esquema tiene claves de más", () => {
    expect(
      errores(`
        type Esquema = { id?: string; estado?: string; total?: number; cdr?: { codigo?: string; descripcion?: string }; extra?: string };
        type Mano = { id: string; estado: "A" | "B"; total: number | null; cdr: { codigo: string; descripcion: string } | null };
        type _Cabe = Verifica<Contrato<Mano, Esquema>>;
      `),
    ).toBe("");
  });

  it("falla con el nombre de la clave cuando la API ya no la tiene (renombrada o quitada)", () => {
    const e = errores(`
      type Esquema = { id?: string; razon_social?: string };
      type Mano = { id: string; nombre: string };
      type _NoCabe = Verifica<Contrato<Mano, Esquema>>;
    `);
    expect(e).toContain("la_api_cambio:nombre(ya no existe)");
    expect(e).not.toContain("la_api_cambio:id");
  });

  it("falla con el nombre de la clave cuando la API cambió su forma", () => {
    const e = errores(`
      type Esquema = { total?: number; items?: string[] };
      type Mano = { total: string; items: string[] };
      type _NoCabe = Verifica<Contrato<Mano, Esquema>>;
    `);
    expect(e).toContain("la_api_cambio:total");
    expect(e).not.toContain("la_api_cambio:items");
  });

  it("una forma anidada que ya no encaja también falla, y un objeto donde la API tiene un arreglo", () => {
    const e = errores(`
      type Esquema = { cdr?: { codigo?: string }; lineas?: string[] };
      type Mano = { cdr: { codigo: number }; lineas: { a: string } };
      type _NoCabe = Verifica<Contrato<Mano, Esquema>>;
    `);
    expect(e).toContain("la_api_cambio:cdr.codigo");
    expect(e).toContain("la_api_cambio:lineas");
  });

  it("mira dentro de los objetos y de los arreglos: una clave anidada renombrada se nombra con su camino", () => {
    const e = errores(`
      type Esquema = { totales?: { total?: number; igv?: number }; items?: Array<{ codigo?: string; precio?: number }> };
      type Mano = { totales: { total: number; total_en_soles: number }; items: Array<{ codigo: string; precio: string }> };
      type _NoCabe = Verifica<Contrato<Mano, Esquema>>;
    `);
    expect(e).toContain("la_api_cambio:totales.total_en_soles(ya no existe)");
    expect(e).toContain("la_api_cambio:items.precio");
    expect(e).not.toContain("la_api_cambio:totales.total\"");
  });

  it("un nullable, un opcional y un literal más estrecho dentro de un objeto anidado no son un cambio de contrato", () => {
    expect(
      errores(`
        type Esquema = { receptor?: { tipo_doc?: string; direccion?: string }; items?: Array<{ unidad?: string }> };
        type Mano = { receptor: { tipo_doc: "1" | "6"; direccion: string | null } | null; items: Array<{ unidad: string }> };
        type _Cabe = Verifica<Contrato<Mano, Esquema>>;
      `),
    ).toBe("");
  });

  it("una clave opcional a mano frente a una del esquema también se compara", () => {
    expect(errores(`
      type Esquema = { id?: string; nota?: string };
      type Mano = { id: string; nota?: string | null };
      type _Cabe = Verifica<Contrato<Mano, Esquema>>;
    `)).toBe("");
    const e = errores(`
      type Esquema = { id?: string; nota?: number };
      type Mano = { id: string; nota?: string };
      type _NoCabe = Verifica<Contrato<Mano, Esquema>>;
    `);
    expect(e).toContain("la_api_cambio:nota");
  });

  it("un valor nuevo en un enum de la API falla: el tipo a mano tiene que cubrirlo", () => {
    const e = errores(`
      type Esquema = { estado?: "VIGENTE" | "EN_GRACIA" | "VENCIDA" | "CANCELADA" };
      type Mano = { estado: "VIGENTE" | "EN_GRACIA" | "VENCIDA" };
      type _NoCabe = Verifica<Contrato<Mano, Esquema>>;
    `);
    expect(e).toContain("la_api_cambio:estado(valor nuevo en la API: CANCELADA)");
  });

  it("un enum de la API que el tipo a mano cubre, o que cubre de sobra, o un string a mano frente a un enum, pasan", () => {
    expect(errores(`
      type Esquema = { estado?: "A" | "B" };
      type Igual = { estado: "A" | "B" };
      type DeSobra = { estado: "A" | "B" | "C" };
      type Ancho = { estado: string };
      type _1 = Verifica<Contrato<Igual, Esquema>>;
      type _2 = Verifica<Contrato<DeSobra, Esquema>>;
      type _3 = Verifica<Contrato<Ancho, Esquema>>;
    `)).toBe("");
  });

  it("literales sin ningún valor en común fallan", () => {
    const e = errores(`
      type Esquema = { tipo?: "C" | "D" };
      type Mano = { tipo: "A" | "B" };
      type _NoCabe = Verifica<Contrato<Mano, Esquema>>;
    `);
    expect(e).toContain("la_api_cambio:tipo");
  });

  it("en una petición, un campo que el esquema marca requerido y el tipo a mano no trae, falla", () => {
    const e = errores(`
      type Esquema = { nombre: string; correo: string; telefono?: string };
      type Mano = { nombre: string; telefono?: string };
      type _NoCabe = Verifica<ContratoDePeticion<Mano, Esquema>>;
    `);
    expect(e).toContain("la_api_exige:correo");
    expect(e).not.toContain("la_api_exige:nombre");
    expect(e).not.toContain("la_api_exige:telefono");
  });

  it("una petición que trae todos los requeridos pasa, y sigue comprobando claves y formas", () => {
    expect(errores(`
      type Esquema = { nombre: string; correo?: string };
      type Mano = { nombre: string; correo?: string };
      type _Cabe = Verifica<ContratoDePeticion<Mano, Esquema>>;
    `)).toBe("");
    const e = errores(`
      type Esquema = { nombre: string; correo?: string };
      type Mano = { nombre: number; correo?: string; extra: string };
      type _NoCabe = Verifica<ContratoDePeticion<Mano, Esquema>>;
    `);
    expect(e).toContain("la_api_cambio:nombre");
    expect(e).toContain("la_api_cambio:extra(ya no existe)");
  });
});
