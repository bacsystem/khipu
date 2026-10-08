import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * `--success` y `--warning` son colores de FONDO (verde y ámbar muy claros, casi negros en modo oscuro); el texto va con su `-foreground`.
 * Un `text-success` suelto dejó invisible en modo oscuro el «Correo enviado» del backoffice (H8). `bg-success`, `border-success-border`,
 * `text-success-foreground` y `text-success-solid` sí valen.
 */
const TEXTO_CON_TOKEN_DE_FONDO = /(?<![\w-])(?:[a-z-]+:)*text-(?:success|warning)(?![-\w])/g;

function archivos(dir: string): string[] {
  return readdirSync(dir).flatMap((nombre) => {
    const ruta = join(dir, nombre);
    if (statSync(ruta).isDirectory()) return archivos(ruta);
    return /\.tsx?$/.test(nombre) && !/\.test\.tsx?$/.test(nombre) ? [ruta] : [];
  });
}

describe("tokens de color", () => {
  it("detecta un token de fondo usado como color de texto", () => {
    expect(`className="text-sm text-success outline-none"`.match(TEXTO_CON_TOKEN_DE_FONDO)).toHaveLength(1);
    expect(`className="dark:text-warning"`.match(TEXTO_CON_TOKEN_DE_FONDO)).toHaveLength(1);
    expect(`className="text-success-foreground bg-success text-warning-solid"`.match(TEXTO_CON_TOKEN_DE_FONDO)).toBeNull();
  });

  it("ningún componente pinta texto con un token de fondo", () => {
    const raiz = resolve(__dirname, "..");
    const encontrados = archivos(raiz).flatMap((ruta) =>
      [...readFileSync(ruta, "utf8").matchAll(TEXTO_CON_TOKEN_DE_FONDO)].map((m) => `${relative(raiz, ruta).replaceAll("\\", "/")}: ${m[0]}`),
    );
    expect(encontrados).toEqual([]);
  });
});
