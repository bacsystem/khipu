// npm run generate:api: regenera src/lib/api/openapi.d.ts desde el OpenAPI de un backend en marcha (API_BASE_URL, por defecto http://localhost:8001).
// Un script de Node y no `openapi-typescript ${API_BASE_URL:-…}` en package.json: esa expansión es de shell POSIX y no funciona en cmd de Windows.
import { writeFileSync } from "node:fs";
import openapiTS, { astToString } from "openapi-typescript";

const base = (process.env.API_BASE_URL || "http://localhost:8001").replace(/\/$/, "");
const salida = new URL("../src/lib/api/openapi.d.ts", import.meta.url);

const ast = await openapiTS(new URL(`${base}/openapi.json`));
const cabecera = `/**
 * Generado por \`npm run generate:api\` desde ${base}/openapi.json: no se edita a mano.
 * No lo importa ningún componente: lo usa \`contrato-openapi.ts\` para que \`tsc\` avise si la API cambia el contrato (#5, ver \`contrato.ts\`).
 */

`;
writeFileSync(salida, cabecera + astToString(ast));
console.log(`openapi.d.ts regenerado desde ${base}`);
