import { describe, expect, it } from "vitest";
import { createTranslator } from "use-intl/core";
import { type Locale, LOCALES } from "@/i18n/config";
import en from "./en.json";
import es from "./es.json";

/**
 * #25: cada locale tiene exactamente las claves de es.json (la fuente), y cada texto los mismos `{datos}` y etiquetas `<x>` que su
 * original: una clave que falta se vería como la ruta de la clave, y un `{nombre}` mal escrito quedaría sin reemplazar.
 */
type Nodo = string | Nodo[] | { [clave: string]: Nodo };

const OTROS: Record<string, Nodo> = { en };

function hojas(nodo: Nodo, ruta = ""): Map<string, string> {
  const salida = new Map<string, string>();
  if (typeof nodo === "string") salida.set(ruta, nodo);
  else if (Array.isArray(nodo)) nodo.forEach((v, i) => hojas(v, `${ruta}[${i}]`).forEach((t, r) => salida.set(r, t)));
  else for (const [k, v] of Object.entries(nodo)) hojas(v, ruta ? `${ruta}.${k}` : k).forEach((t, r) => salida.set(r, t));
  return salida;
}

const datos = (texto: string) => [...texto.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();
const etiquetas = (texto: string) => [...texto.matchAll(/<(\w+)>/g)].map((m) => m[1]).sort();

const fuente = hojas(es as Nodo);

describe("paridad de los textos con es.json (#25)", () => {
  it("los locales soportados son los que tienen archivo", () => {
    expect([...LOCALES].sort()).toEqual(["en", "es"]);
  });

  for (const [locale, mensajes] of Object.entries(OTROS)) {
    const traduccion = hojas(mensajes);

    it(`${locale}: no le falta ninguna clave de es.json ni tiene claves de más`, () => {
      expect([...fuente.keys()].filter((r) => !traduccion.has(r))).toEqual([]);
      expect([...traduccion.keys()].filter((r) => !fuente.has(r))).toEqual([]);
    });

    it(`${locale}: ningún texto vacío y los mismos {datos} y <etiquetas> que el original`, () => {
      const distintos: string[] = [];
      for (const [ruta, original] of fuente) {
        const texto = traduccion.get(ruta);
        if (texto === undefined) continue;
        if (texto.trim() === "" && original.trim() !== "") distintos.push(`${ruta}: vacío`);
        if (datos(texto).join() !== datos(original).join()) distintos.push(`${ruta}: datos ${datos(texto)} ≠ ${datos(original)}`);
        if (etiquetas(texto).join() !== etiquetas(original).join()) distintos.push(`${ruta}: etiquetas ${etiquetas(texto)} ≠ ${etiquetas(original)}`);
      }
      expect(distintos).toEqual([]);
    });

    it(`${locale}: el mismo espacio al principio y al final que el original`, () => {
      // H8: hay textos que se pegan a otro y por eso empiezan o terminan en espacio a propósito (`" Tiene {n} nota…"`); si la
      // traducción lo pierde, en pantalla quedan dos palabras juntas.
      const bordes = (texto: string) => [texto.match(/^\s*/)![0], texto.match(/\s*$/)![0]];
      const distintos: string[] = [];
      for (const [ruta, original] of fuente) {
        const texto = traduccion.get(ruta);
        if (texto === undefined || texto.trim() === "") continue;
        const [inicio, fin] = bordes(original);
        const [inicioT, finT] = bordes(texto);
        if (inicio !== inicioT || fin !== finT) distintos.push(`${ruta}: ${JSON.stringify(texto)} (original ${JSON.stringify(original)})`);
      }
      expect(distintos).toEqual([]);
    });

    it(`${locale}: ninguna frase quedó copiada en castellano (una clave agregada sin traducir)`, () => {
      // Dos palabras seguidas en minúscula: «RUC», «UBL 2.1» o «Yape» pueden repetirse tal cual; una frase no.
      const frase = /[a-záéíóúñ]{3,}\s+[a-záéíóúñ]{3,}/;
      expect([...fuente].filter(([ruta, original]) => frase.test(original) && traduccion.get(ruta) === original).map(([ruta]) => ruta)).toEqual([]);
    });
  }

  for (const [locale, mensajes] of Object.entries({ es, ...OTROS })) {
    it(`${locale}: todo texto es un mensaje ICU válido (lo que se lee con useTranslations no falla al formatear)`, () => {
      const t = createTranslator({
        locale: locale as Locale,
        messages: mensajes as typeof es,
        onError: (e) => {
          throw e;
        },
      });
      const invalidos: string[] = [];
      for (const [ruta, original] of fuente) {
        if (ruta.includes("[")) continue; // las listas se leen con t.raw
        const valores = Object.fromEntries([...datos(original).map((d) => [d, "x"]), ...etiquetas(original).map((e) => [e, (c: string) => c])]);
        try {
          (t as unknown as { markup: (k: string, v: object) => string }).markup(ruta, valores);
        } catch (e) {
          invalidos.push(`${ruta}: ${(e as Error).message}`);
        }
      }
      expect(invalidos).toEqual([]);
    });
  }
});
