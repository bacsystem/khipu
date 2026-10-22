// @vitest-environment node
import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

/**
 * H4 (#25): la línea base `eslint-suppressions.json` (los textos de UI escritos en el código que todavía no se migraron a
 * src/messages) solo puede bajar. ESLint ya falla con un literal nuevo, pero nada impedía regenerar la línea base con
 * `--suppress-all` y subirla. Este tope lo impide: al migrar literales, bájalo al número nuevo en el mismo cambio. **Nunca se sube
 * sin revisión**: si un cambio necesita subirlo, el texto va a messages, no a la línea base.
 */
const TOPE_DE_LITERALES = 9;
const TOPE_DE_ARCHIVOS = 3;

type LineaBase = Record<string, Record<string, { count: number }>>;

describe("línea base de literales de UI (eslint-suppressions.json)", () => {
  const lineaBase = JSON.parse(fs.readFileSync(path.resolve(__dirname, "../../eslint-suppressions.json"), "utf8")) as LineaBase;
  const conLiterales = Object.entries(lineaBase).filter(([, reglas]) => (reglas["khipu/no-literal-de-ui"]?.count ?? 0) > 0);
  const total = conLiterales.reduce((suma, [, reglas]) => suma + reglas["khipu/no-literal-de-ui"].count, 0);

  it(`no pasa de ${TOPE_DE_LITERALES} literales en ${TOPE_DE_ARCHIVOS} archivos`, () => {
    expect(total).toBeLessThanOrEqual(TOPE_DE_LITERALES);
    expect(conLiterales.length).toBeLessThanOrEqual(TOPE_DE_ARCHIVOS);
  });

  it("solo suprime la regla de literales: otra regla no se esconde en la línea base", () => {
    const otras = Object.entries(lineaBase).flatMap(([archivo, reglas]) =>
      Object.keys(reglas)
        .filter((r) => r !== "khipu/no-literal-de-ui")
        .map((r) => `${archivo}: ${r}`),
    );
    expect(otras).toEqual([]);
  });
});
