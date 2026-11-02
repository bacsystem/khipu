import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * #510: la escala tipográfica del design system tiene nombre (`text-etiqueta` 11.5, `text-control` 13, `text-cuerpo` 13, `text-titulo` 14.5, `text-titulo-pagina` 21, `text-metrica` 26).
 * Es la misma comprobación que hace su `verificar-tipografia`: ni tamaños sueltos (`text-[13px]`), ni los de Tailwind (`text-sm`), ni mayúsculas o tracking ancho fuera de las
 * recetas del kit. Una línea con `tipografia-ok` queda exenta.
 */
const RAIZ = join(import.meta.dirname, "..");

/** Páginas de marketing y documentación, con tamaños de lectura propios que la escala de la aplicación no cubre (títulos de portada, texto largo). */
const EXENTAS = ["app/page.tsx", "components/landing/", "components/legal/documento-legal.tsx", "app/developers/", "components/developers/"];

/** Líneas concretas que el kit no cubre: el espaciado de los dígitos de un código y el de la máscara de una llave. */
const REGLAS = [
  { patron: /(?<![\w-])text-\[\d+(?:\.\d+)?(?:px|rem|em)\]/, mensaje: "tamaño suelto" },
  { patron: /(?<![\w:-])text-(?:xs|sm|base|lg|xl|2xl)(?![\w-])/, mensaje: "tamaño de Tailwind" },
  { patron: /(?<![\w-])uppercase(?![\w-])/, mensaje: "mayúsculas" },
  { patron: /(?<![\w-])tracking-(?:wide|wider|widest|\[0\.\d+em\])(?![\w-])/, mensaje: "tracking ancho" },
];
const EXCEPCIONES = ["components/admin/segundo-factor.tsx:tracking ancho", "components/api-keys/api-keys-table.tsx:tracking ancho"];

function archivos(dir: string): string[] {
  return readdirSync(dir).flatMap((entrada) => {
    const ruta = join(dir, entrada);
    if (statSync(ruta).isDirectory()) return archivos(ruta);
    return /\.tsx?$/.test(entrada) && !/\.test\.tsx?$/.test(entrada) ? [ruta] : [];
  });
}

describe("el portal usa la escala tipográfica con nombre del design system", () => {
  it("ninguna página de la aplicación usa tamaños sueltos, de Tailwind, mayúsculas o tracking ancho", () => {
    const hallazgos = archivos(RAIZ).flatMap((ruta) => {
      const rel = ruta.slice(RAIZ.length + 1).replaceAll("\\", "/");
      if (EXENTAS.some((e) => rel.startsWith(e))) return [];
      return readFileSync(ruta, "utf8")
        .split("\n")
        .flatMap((linea, i) =>
          linea.includes("tipografia-ok")
            ? []
            : REGLAS.filter((r) => r.patron.test(linea) && !EXCEPCIONES.includes(`${rel}:${r.mensaje}`)).map((r) => `${rel}:${i + 1} ${r.mensaje}`),
        );
    });
    expect(hallazgos).toEqual([]);
  });
});
