// @vitest-environment node
import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import es from "@/messages/es.json";
import { elegirTextos, type SegmentoDeTextos, TEXTOS_DE_CLIENTE } from "./textos-de-cliente";

/**
 * H2 (#25): cada página manda al navegador solo los namespaces de su segmento (`TEXTOS_DE_CLIENTE`). Si un Client Component lee
 * uno que no está, en el navegador `messages.<ns>` es `undefined` y la pantalla se cae. Esta prueba recorre los imports desde cada
 * página/layout de `src/app` como lo haría el bundler (desde el primer `"use client"` para abajo todo es cliente) y compara lo que
 * leen con lo que su segmento envía. Es un análisis por texto: reconoce `messages.<ns>`, `useMessages().<ns>`,
 * `useTranslations("<ns>")` y `Mensajes["<ns>"]`, que son las formas que usa el portal (src/i18n/mensajes.ts).
 */
const SRC = path.resolve(__dirname, "..");
const APP = path.join(SRC, "app");
const NAMESPACES = new Set(Object.keys(es));
const EXTENSIONES = [".ts", ".tsx", "/index.ts", "/index.tsx"];
const ENTRADAS = /^(page|layout|error|not-found|loading|template|default|global-error)\.tsx?$/;

type Modulo = { imports: string[]; namespaces: Set<string>; cliente: boolean; texto: string };
const modulos = new Map<string, Modulo>();

function resolver(desde: string, spec: string): string | null {
  let base: string;
  if (spec.startsWith("@/")) base = path.join(SRC, spec.slice(2));
  else if (spec.startsWith(".")) base = path.resolve(path.dirname(desde), spec);
  else return null;
  if (fs.existsSync(base) && fs.statSync(base).isFile()) return base;
  for (const e of EXTENSIONES) if (fs.existsSync(base + e)) return base + e;
  return null;
}

function leer(archivo: string): Modulo {
  const cache = modulos.get(archivo);
  if (cache) return cache;
  const texto = fs.readFileSync(archivo, "utf8");
  const imports: string[] = [];
  for (const m of texto.matchAll(/(?:import|export)\s+(type\s+)?[^;'"]*?from\s+["']([^"']+)["']|import\(\s*["']([^"']+)["']\s*\)|^import\s+["']([^"']+)["']/gm)) {
    if (m[1]) continue; // `import type` no llega al bundle
    const r = resolver(archivo, m[2] ?? m[3] ?? m[4]);
    if (r && !r.endsWith(".json")) imports.push(r);
  }
  const namespaces = new Set<string>();
  const formas = [/useTranslations\(\s*["'](\w+)/g, /\b(?:messages|mensajes|useMessages\(\))\??\.(\w+)/g, /Mensajes\[["'](\w+)["']\]/g];
  for (const forma of formas) for (const m of texto.matchAll(forma)) if (NAMESPACES.has(m[1])) namespaces.add(m[1]);
  const modulo = { imports, namespaces, cliente: /^\s*["']use client["']/m.test(texto.slice(0, 300)), texto };
  modulos.set(archivo, modulo);
  return modulo;
}

/** Los namespaces que lee el código de cliente alcanzable desde `entrada`. */
function namespacesDeCliente(entrada: string): Set<string> {
  const usados = new Set<string>();
  const vistos = new Set<string>();
  const pila: Array<[string, boolean]> = [[entrada, false]];
  while (pila.length > 0) {
    const [archivo, enCliente] = pila.pop()!;
    if (vistos.has(`${enCliente}:${archivo}`)) continue;
    vistos.add(`${enCliente}:${archivo}`);
    const m = leer(archivo);
    const cliente = enCliente || m.cliente;
    if (cliente) m.namespaces.forEach((n) => usados.add(n));
    for (const i of m.imports) pila.push([i, cliente]);
  }
  return usados;
}

const SEGMENTO = /<TextosDeCliente\s+segmento="(\w+)"/;

/** El segmento que provee los textos a una entrada: el `<TextosDeCliente>` del propio archivo o del layout más cercano hacia arriba. */
function segmentoDe(entrada: string): SegmentoDeTextos | null {
  const propio = leer(entrada).texto.match(SEGMENTO);
  if (propio) return propio[1] as SegmentoDeTextos;
  for (let dir = path.dirname(entrada); dir.startsWith(APP); dir = path.dirname(dir)) {
    const layout = ["layout.tsx", "layout.ts"].map((n) => path.join(dir, n)).find((p) => fs.existsSync(p));
    const m = layout ? leer(layout).texto.match(SEGMENTO) : null;
    if (m) return m[1] as SegmentoDeTextos;
  }
  return null;
}

function entradas(dir: string): string[] {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) return e.name === "api" ? [] : entradas(p);
    return ENTRADAS.test(e.name) ? [p] : [];
  });
}

describe("textos que viajan al navegador por segmento (H2, #25)", () => {
  const rutas = entradas(APP);

  it("encuentra las páginas (si esto falla, el recorrido dejó de ver src/app)", () => {
    expect(rutas.length).toBeGreaterThan(40);
  });

  it("cada página y layout tiene un segmento, y su segmento envía todo lo que leen sus Client Components", () => {
    const faltan: string[] = [];
    for (const entrada of rutas) {
      const segmento = segmentoDe(entrada);
      const relativa = path.relative(APP, entrada).replaceAll("\\", "/");
      if (!segmento || !(segmento in TEXTOS_DE_CLIENTE)) {
        faltan.push(`${relativa}: sin <TextosDeCliente> arriba`);
        continue;
      }
      const enviados = new Set<string>(TEXTOS_DE_CLIENTE[segmento]);
      const sinEnviar = [...namespacesDeCliente(entrada)].filter((n) => !enviados.has(n));
      if (sinEnviar.length > 0) faltan.push(`${relativa} (${segmento}): ${sinEnviar.join(", ")}`);
    }
    expect(faltan).toEqual([]);
  });

  it("la portada y el login no llevan la documentación ni el backoffice", () => {
    for (const segmento of ["base", "landing"] as const) {
      expect(TEXTOS_DE_CLIENTE[segmento]).not.toContain("developers");
      expect(TEXTOS_DE_CLIENTE[segmento]).not.toContain("admin");
    }
  });

  it("elegirTextos deja solo los namespaces pedidos", () => {
    expect(Object.keys(elegirTextos(es, ["auth", "errores"]))).toEqual(["auth", "errores"]);
  });
});
