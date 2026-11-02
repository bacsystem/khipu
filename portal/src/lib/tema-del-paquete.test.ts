import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Desde el design system v0.5.0 el portal usa el tema del paquete tal cual (paleta Lima, tono oscuro Medianoche y las demás que el usuario elige en «Apariencia»):
 * antes montaba encima su marca teal/papel/ámbar y esta prueba obligaba a redefinir cada token. Ahora vigila lo contrario: `globals.css` no declara un `:root` ni un
 * `.dark` propios. Un `:root { --primary: … }` suelto después del import pisaría la paleta y el tono oscuro que el usuario eligió (`html[data-paleta]`, `.dark[data-oscuro]`)
 * y, por tener la misma especificidad que el `.dark` del paquete, también su modo oscuro. Si el portal vuelve a tener marca propia, hay que decidirlo (y redefinir
 * todos los tokens, no algunos).
 */
describe("el portal usa el tema del design system sin redefinirlo", () => {
  const portal = readFileSync(resolve(__dirname, "../app/globals.css"), "utf8");
  // Se ignoran los comentarios: el que explica la regla también escribe «:root { … }».
  const sinComentarios = portal.replace(/\/\*[\s\S]*?\*\//g, "");

  it("globals.css no declara :root ni .dark", () => {
    expect(sinComentarios).not.toMatch(/(^|\n)\s*(?::root|\.dark|html\.dark|html\[data-[\w-]+[^\]]*\])\s*[,{]/);
  });

  it("globals.css importa el tema del paquete después de Tailwind", () => {
    const tailwind = sinComentarios.indexOf('@import "tailwindcss"');
    const paquete = sinComentarios.indexOf('@import "khipu-design-system/styles.css"');
    expect(tailwind).toBeGreaterThanOrEqual(0);
    expect(paquete).toBeGreaterThan(tailwind);
  });
});
