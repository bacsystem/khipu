import { readFileSync, readdirSync, statSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

/**
 * khipu es un producto peruano: el portal habla en **tuteo** («revisa», «intenta», «crea»). El voseo rioplatense
 * («revisá», «intentá», «creá») se colaba de a poco en los textos nuevos y dejó el portal con dos registros mezclados,
 * que es exactamente lo que hace que un producto no parezca local. Este test lo ataja en el commit, no en producción.
 *
 * El `/developers` usa **usted** a propósito: es la documentación para integradores, otra audiencia y otro registro.
 * Eso no es voseo, así que este test no lo toca.
 */
const VOSEO = [
  // Imperativos: la marca más común, vocal acentuada al final.
  "revisá", "intentá", "creá", "elegí", "ingresá", "cargá", "configurá", "verificá", "avisá", "recargá", "actualizá",
  "volvé", "tené", "poné", "hacé", "mandá", "guardá", "subí", "dejá", "esperá", "probá", "mirá", "bajá", "entrá",
  "sumá", "restá", "agregá", "quitá", "borrá", "cerrá", "abrí", "escribí", "seguí", "usá", "aplicá", "corregí",
  "completá", "marcá", "activá", "desactivá", "emití", "anulá", "enviá", "descargá", "copiá", "buscá", "filtrá",
  "seleccioná", "confirmá", "cancelá", "continuá", "empezá", "administrá", "considerá", "tomá", "andá",
  // Imperativos con pronombre pegado, que no llevan tilde y por eso se escapan del patrón de arriba.
  "escribinos", "avisanos", "contanos", "decinos", "decime", "fijate", "quedate", "acordate", "cargalo", "cargala",
  "ponela", "ponelo", "reasignalas", "mandanos",
  // Presente de indicativo y el pronombre.
  "tenés", "podés", "querés", "hacés", "sabés", "necesitás", "vos",
];

/**
 * `\b` de JavaScript no sirve acá: `á` no es carácter de palabra para el motor, así que `revisá\b` nunca casa. Los
 * límites se hacen con letras Unicode.
 */
function patronVoseo(): RegExp {
  return new RegExp(`(?<!\\p{L})(${VOSEO.join("|")})(?!\\p{L})`, "iu");
}

function archivosDeTexto(dir: string): string[] {
  const salida: string[] = [];
  for (const entrada of readdirSync(dir)) {
    const completo = path.join(dir, entrada);
    if (statSync(completo).isDirectory()) {
      salida.push(...archivosDeTexto(completo));
    } else if (/\.(tsx?|json)$/.test(entrada)) {
      salida.push(completo);
    }
  }
  return salida;
}

describe("registro lingüístico del portal", () => {
  it("no usa voseo en ningún archivo de src", () => {
    const raiz = path.join(__dirname, "..");
    const patron = patronVoseo();
    const hallazgos: string[] = [];

    for (const archivo of archivosDeTexto(raiz)) {
      // Este archivo enumera las formas a propósito.
      if (archivo.endsWith("registro-linguistico.test.ts")) continue;
      const lineas = readFileSync(archivo, "utf8").split("\n");
      lineas.forEach((linea, i) => {
        const m = linea.match(patron);
        if (m) hallazgos.push(`${path.relative(raiz, archivo)}:${i + 1}  «${m[1]}»  ${linea.trim().slice(0, 90)}`);
      });
    }

    expect(hallazgos, `Voseo encontrado. El portal habla en tuteo peruano:\n${hallazgos.join("\n")}`).toEqual([]);
  });

  it("el patrón detecta de verdad: si no, el test de arriba pasaría por vacío", () => {
    const patron = patronVoseo();
    expect("Revisá tu conexión e intentá de nuevo").toMatch(patron);
    expect("Escribinos para pedir acceso").toMatch(patron);
    expect("Ya tenés una serie configurada").toMatch(patron);
    expect("Portal para vos").toMatch(patron);
    // Y no marca tuteo ni usted, que son los dos registros legítimos del producto.
    expect("Revisa tu conexión e intenta de nuevo").not.toMatch(patron);
    expect("Revise su conexión e intente de nuevo").not.toMatch(patron);
    // Ni palabras comunes con tilde final que no son voseo.
    expect("está así aquí, según el número de la línea").not.toMatch(patron);
  });
});
