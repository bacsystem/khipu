// @vitest-environment node
import { RuleTester } from "eslint";
import { describe, it } from "vitest";
import regla from "./no-literal-de-ui.mjs";

// RuleTester arma sus propios describe/it; con Vitest hay que dárselos.
RuleTester.describe = describe;
RuleTester.it = it;
RuleTester.itOnly = it.only;

const tester = new RuleTester({
  languageOptions: { ecmaVersion: "latest", sourceType: "module", parserOptions: { ecmaFeatures: { jsx: true } } },
});

const error = { messageId: "texto" };

tester.run("khipu/no-literal-de-ui", regla, {
  valid: [
    // Lo técnico no cuenta.
    'const c = cn("flex items-center gap-2");',
    'if (estado === "ACEPTADO") hacer();',
    'throw new Error("API_BASE_URL missing");',
    'throw new Error(`HTTP ${res.status}`);',
    'new Error("Invalid response");',
    'console.error("No se pudo leer el catálogo");',
    // Un template con clases o una ruta después de la interpolación no es texto.
    "const clase = `${base} flex items-center`;",
    "const url = `${apiBaseUrl()}/v1/catalogos`;",
    "const id = `${prefijo}-lista`;",
    // `value` de un input que no es botón es un dato, no una etiqueta.
    'const a = <input type="text" value="Juan Pérez" readOnly />;',
    'const b = <input value="abc" />;',
    // Textos que vienen de messages.
    "const c = <button>{t.guardar}</button>;",
  ],
  invalid: [
    { code: "const a = <p>Hola</p>;", errors: [error] },
    { code: 'const a = <input placeholder="Buscar cliente" />;', errors: [error] },
    { code: 'toast("Guardado.");', errors: [error] },
    // H5: lo que la regla no veía.
    { code: "const resumen = `${n} facturas pendientes`;", errors: [error] },
    { code: "const resumen = `Hay ${n} de ${total} facturas pendientes de envío`;", errors: [error, error] },
    { code: 'throw new Error("No se pudo guardar el cliente");', errors: [error] },
    { code: 'const e = Error("Falta la serie del comprobante");', errors: [error] },
    { code: "throw new Error(`No se pudo leer ${ruta}`);", errors: [error] },
    { code: 'const a = <input type="submit" value="Guardar cambios" />;', errors: [error] },
    { code: 'const a = <input type="button" value="Cancelar" />;', errors: [error] },
  ],
});
