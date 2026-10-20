// npm run generate:api: baja el OpenAPI de un backend en marcha (API_BASE_URL, por defecto http://localhost:8001) a src/lib/api/openapi.json y regenera de él src/lib/api/openapi.d.ts.
// npm run generate:types: solo regenera el d.ts desde el openapi.json commiteado (sin backend). El json es lo que compara DeveloperPortalE2ETest con la API de verdad.
// Un script de Node y no `openapi-typescript ${API_BASE_URL:-…}` en package.json: esa expansión es de shell POSIX y no funciona en cmd de Windows.
import { readFileSync, writeFileSync } from "node:fs";
import openapiTS, { astToString } from "openapi-typescript";

const jsonUrl = new URL("../src/lib/api/openapi.json", import.meta.url);
const tiposUrl = new URL("../src/lib/api/openapi.d.ts", import.meta.url);
const soloTipos = process.argv.includes("--solo-tipos");

if (!soloTipos) {
  const base = (process.env.API_BASE_URL || "http://localhost:8001").replace(/\/+$/, "");
  let respuesta;
  try {
    respuesta = await fetch(`${base}/openapi.json`);
  } catch (e) {
    console.error(`No se pudo llegar a ${base}/openapi.json (${e.cause?.code ?? e.message}). Levante el backend o defina API_BASE_URL.`);
    process.exit(1);
  }
  if (!respuesta.ok) {
    console.error(`${base}/openapi.json respondió ${respuesta.status}.`);
    process.exit(1);
  }
  const doc = await respuesta.json();
  // `servers` trae el host desde el que se pidió el documento: cambia entre máquinas y entornos sin que la API cambie.
  delete doc.servers;
  writeFileSync(jsonUrl, JSON.stringify(doc, null, 2) + "\n");
}

const doc = JSON.parse(readFileSync(jsonUrl, "utf-8"));
const ast = await openapiTS(doc);
const cabecera = `/**
 * Generado por \`npm run generate:api\` (o \`generate:types\`) desde openapi.json: no se edita a mano.
 * No lo importa ningún componente: lo usa \`contrato-openapi.ts\` para que \`tsc\` avise si la API cambia el contrato (#5, ver \`contrato.ts\`).
 */

`;
writeFileSync(tiposUrl, cabecera + astToString(ast));
console.log(soloTipos ? "openapi.d.ts regenerado desde openapi.json" : "openapi.json y openapi.d.ts regenerados");
