import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * La marca del portal (teal/papel/ámbar) se monta como una capa sobre el tema del paquete `khipu-design-system`. Un `:root` o un `.dark` a medias dejaría
 * heredar el índigo del paquete en un token que el portal no redefinió, sin que ninguna otra prueba falle. Si el paquete agrega un token en una versión nueva,
 * esta prueba obliga a decidir su valor en `globals.css`.
 */
function variables(css: string, selector: ":root" | ".dark"): Set<string> {
  // Anclado al inicio de línea: el comentario que explica la regla en globals.css también escribe «:root { … }».
  const inicio = css.search(new RegExp("^" + selector.replace(".", "\\.") + "\\s*\\{", "m"));
  if (inicio < 0) throw new Error(`no encontré ${selector}`);
  let nivel = 0;
  let fin = css.indexOf("{", inicio);
  const cuerpoDesde = fin + 1;
  for (let i = fin; i < css.length; i++) {
    if (css[i] === "{") nivel++;
    if (css[i] === "}" && --nivel === 0) {
      fin = i;
      break;
    }
  }
  return new Set([...css.slice(cuerpoDesde, fin).matchAll(/(--[\w-]+)\s*:/g)].map((m) => m[1]));
}

describe("la marca del portal redefine todos los tokens del paquete", () => {
  const portal = readFileSync(resolve(__dirname, "../app/globals.css"), "utf8");
  const paquete = readFileSync(resolve(__dirname, "../../node_modules/khipu-design-system/dist/styles/tokens.css"), "utf8");

  for (const selector of [":root", ".dark"] as const) {
    it(`${selector} del portal define cada variable de ${selector} del paquete`, () => {
      const propias = variables(portal, selector);
      const delPaquete = variables(paquete, selector);
      expect(delPaquete.size).toBeGreaterThan(20); // si el formato del paquete cambia y no se lee nada, la prueba no puede pasar en vacío
      const faltan = [...delPaquete].filter((v) => !propias.has(v));
      expect(faltan).toEqual([]);
    });
  }
});
