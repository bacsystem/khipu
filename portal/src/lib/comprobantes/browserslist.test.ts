import browserslist from "browserslist";
import { describe, expect, it } from "vitest";

/**
 * `lib/comprobantes/totales.ts` calcula los importes con `bigint` para igualar al `BigDecimal` del backend hasta el
 * céntimo. **BigInt es sintaxis, no una API**: no se transpila ni se polirellena, así que SWC lo emite tal cual y un
 * navegador sin soporte no parsea el chunk —y el código vive en el layout de `(privado)`, o sea en toda página
 * autenticada, con lo cual el portal no hidrataría y quedaría sin interactividad.
 *
 * Por eso el `browserslist` de `package.json` dice `defaults and supports bigint` y no `defaults` a secas. Este test
 * existe para que volver a la versión corta falle acá en vez de descubrirse en producción.
 *
 * `browserslist` no está en las dependencias declaradas: llega con Next, que lo usa para lo mismo. Si algún día
 * desaparece, este test falla al importar, que es exactamente el aviso que corresponde.
 */
describe("browserslist del portal", () => {
  const opciones = { path: process.cwd() };

  it("todos los navegadores soportados pueden ejecutar BigInt", () => {
    const soportados = browserslist(undefined, opciones);
    const conBigInt = new Set(browserslist("supports bigint", opciones));

    expect(soportados.length).toBeGreaterThan(0);
    expect(soportados.filter((navegador) => !conBigInt.has(navegador))).toEqual([]);
  });

  it("la lista está declarada en el proyecto y no heredada del default de Next", () => {
    // Sin declararla, `defaults` arrastra `op_mini all` y `kaios 2.5`, que no soportan BigInt.
    const sinFiltrar = browserslist("defaults", opciones);
    const conBigInt = new Set(browserslist("supports bigint", opciones));
    expect(sinFiltrar.filter((navegador) => !conBigInt.has(navegador)).length).toBeGreaterThan(0);
  });
});
