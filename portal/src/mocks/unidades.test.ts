import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { CODIGOS_UNIDAD, UNIDADES_MAS_USADAS } from "./data";

/**
 * #308: el mock valida la unidad con su copia del catálogo 03; si el TSV del backend cambia y la copia no, el e2e daría por buena (o
 * rechazaría) una unidad distinta que producción. Se lee el TSV del propio repo, como `catalogo-bitacora.test.ts` lee el enum.
 */
describe("catálogo 03 del mock", () => {
  const filas = readFileSync(resolve(__dirname, "../../../domain/src/main/resources/catalogos/03.tsv"), "utf8")
    .split(/\r?\n/)
    .slice(1)
    .filter((l) => l.trim())
    .map((l) => l.split("\t"));

  it("tiene los mismos códigos que el backend", () => {
    expect(filas.length).toBeGreaterThan(2000);
    expect([...CODIGOS_UNIDAD].sort()).toEqual(filas.map(([codigo]) => codigo).sort());
  });

  it("y las mismas más usadas, en el mismo orden y con la misma descripción", () => {
    expect(UNIDADES_MAS_USADAS).toEqual(filas.filter(([, , masUsada]) => masUsada).map(([codigo, descripcion]) => ({ codigo, descripcion })));
  });
});
