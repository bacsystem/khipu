// npm run audit (CAL-02): `npm audit --omit=dev` en JSON y falla con cualquier aviso high/critical de las dependencias de producción, salvo los que
// audit-excepciones.json acepta explícitamente, con motivo y fecha de vencimiento. Una excepción vencida vuelve a fallar: lo aceptado se revisa, no se olvida.
// Un script y no `npm audit --audit-level=high` a secas porque npm no permite excluir avisos: Next 15.5 lleva un postcss 8.4.31 anidado con cuatro avisos
// que solo aplican al procesar CSS ajeno en build, y sin excepciones el job estaría rojo hasta Next 16.
import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";

const excepciones = JSON.parse(readFileSync(new URL("../audit-excepciones.json", import.meta.url), "utf-8"));
const hoy = new Date().toISOString().slice(0, 10);

const npm = process.platform === "win32" ? "npm.cmd" : "npm";
const salida = spawnSync(npm, ["audit", "--omit=dev", "--json"], { encoding: "utf-8", shell: process.platform === "win32", maxBuffer: 64 * 1024 * 1024 });
if (!salida.stdout) {
  console.error("npm audit no devolvió JSON:", salida.stderr);
  process.exit(2);
}
const informe = JSON.parse(salida.stdout);

const vencidas = excepciones.filter((e) => e.vence < hoy);
const aceptadas = new Set(excepciones.filter((e) => e.vence >= hoy).map((e) => e.aviso));
const graves = [];
for (const [paquete, v] of Object.entries(informe.vulnerabilities ?? {})) {
  if (v.severity !== "high" && v.severity !== "critical") continue;
  const avisos = v.via.filter((x) => typeof x === "object");
  if (avisos.length === 0) continue; // vulnerable solo por transitividad: el aviso real se evalúa en el paquete que lo origina
  const sinExcepcion = avisos.filter((a) => !aceptadas.has(a.url));
  if (sinExcepcion.length > 0) graves.push({ paquete, severidad: v.severity, avisos: sinExcepcion.map((a) => `${a.url} (${a.title})`), fix: v.fixAvailable });
}

const meta = informe.metadata?.vulnerabilities ?? {};
console.log(`npm audit (producción): ${meta.total ?? 0} avisos — critical ${meta.critical ?? 0}, high ${meta.high ?? 0}, moderate ${meta.moderate ?? 0}, low ${meta.low ?? 0}; excepciones vigentes: ${aceptadas.size}`);
if (vencidas.length > 0) {
  console.error("Excepciones vencidas (revisar o renovar en portal/audit-excepciones.json):");
  for (const e of vencidas) console.error(`  - ${e.aviso} (${e.paquete}) vencía el ${e.vence}: ${e.motivo}`);
}
if (graves.length > 0) {
  console.error("Avisos high/critical sin excepción:");
  for (const g of graves) console.error(`  - ${g.paquete} [${g.severidad}] fix: ${JSON.stringify(g.fix)}\n      ${g.avisos.join("\n      ")}`);
}
process.exit(vencidas.length > 0 || graves.length > 0 ? 1 : 0);
