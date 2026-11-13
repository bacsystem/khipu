#!/usr/bin/env node
// Un solo dashboard para todas las corridas de k6/login.js. Lee el reporte que exporta k6, suma la corrida a k6/resultados/corridas.json y regenera
// k6/resultados/dashboard-login.html, con un selector por cantidad de usuarios. Una corrida con los mismos usuarios y conexión reemplaza a la anterior.
//
//   node k6/dashboard/agregar.mjs --usuarios 20
//   node k6/dashboard/agregar.mjs --usuarios 100 --ruta "Red interna" --principal --reporte k6/resultados/.ultima.html --entorno "k6 contra staging"
//   node k6/dashboard/agregar.mjs --usuarios 20 --recursos k6/resultados/.recursos.txt
//   node k6/dashboard/agregar.mjs --usuarios 100 --tipo resistencia --linea k6/resultados/.linea.txt
//   node k6/dashboard/agregar.mjs --solo-render
//
// --tipo es el tipo de prueba (humo, carga, estres, quiebre, pico, resistencia; carga por defecto): el dashboard agrupa y compara las corridas por tipo.
// --linea es una muestra de `docker stats` con los segundos al inicio de cada línea («120;…»): dibuja la memoria a lo largo de la prueba. Con --reposo,
// su última línea es una muestra tomada en reposo, después de terminar.
// k6/resultados/conclusiones.json, opcional, guarda lo encontrado en cada tipo ({"estres": "…"}) para el catálogo del dashboard.
// --recursos es la muestra de `docker stats` tomada durante la corrida (ver k6/README.md): sin ella, el dashboard dice que el consumo no se midió.
import { execSync } from "node:child_process";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { cpus, totalmem } from "node:os";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { gunzipSync } from "node:zlib";

const AQUI = dirname(fileURLToPath(import.meta.url));
const RESULTADOS = resolve(AQUI, "../resultados");
const ALMACEN = resolve(RESULTADOS, "corridas.json");
const SALIDA = resolve(RESULTADOS, "dashboard-login.html");
const AGREGADOS = { counter: ["count", "rate"], gauge: ["value"], rate: ["rate"], trend: ["avg", "max", "med", "min", "p90", "p95", "p99"] };
const ENTORNO = "k6 contra develop en local, con backend y base de datos reales.";
const ALCANCE = "Alcance: k6 y el servidor corrieron en la misma PC, sin Cloudflare ni la red real. Estos números hablan de esta máquina, no de producción.";
const CONTENEDORES = { backend: "Backend", portal: "Portal", postgres: "Postgres" };
const CONCLUSIONES = resolve(RESULTADOS, "conclusiones.json");
/** Los tipos clásicos de prueba de rendimiento, en el orden en que conviene hacerlos. */
const TIPOS = {
  humo: { nombre: "Humo", ingles: "smoke", pregunta: "¿Funciona todo con muy pocos usuarios?", forma: "1 a 10 usuarios durante unos minutos. Va primero: si falla aquí, no tiene sentido cargar más." },
  carga: { nombre: "Carga", ingles: "load", pregunta: "¿Aguanta el uso esperado?", forma: "La carga normal, sostenida. Incluye el patrón realista: usuarios distintos que llegan repartidos y a veces vuelven a entrar." },
  estres: { nombre: "Estrés", ingles: "stress", pregunta: "¿Qué pasa por encima de lo normal?", forma: "Escalones que suben, con usuarios que repiten login cada pocos segundos: mucho más exigente que el uso real." },
  quiebre: { nombre: "Quiebre", ingles: "breakpoint", pregunta: "¿Dónde se rompe exactamente?", forma: "Usuarios distintos, un login cada uno, en escalones que suben hasta que aparecen fallos." },
  pico: { nombre: "Pico", ingles: "spike", pregunta: "¿Sobrevive a una avalancha repentina?", forma: "De casi cero a miles de usuarios en segundos, como un lunes a las 9:00." },
  resistencia: { nombre: "Resistencia", ingles: "soak", pregunta: "¿Se degrada con las horas?", forma: "Carga media durante 1 a 2 horas, mirando si la memoria o los tiempos crecen sin parar." },
};

const comando = (c) => { try { return execSync(c, { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }).trim(); } catch { return ""; } };

/** La máquina de la corrida: sin esto, los números no se pueden comparar con otra PC ni con producción. */
function detectarMaquina() {
  const nucleos = Number(comando('powershell -NoProfile -Command "(Get-CimInstance Win32_Processor).NumberOfCores"')) || null;
  const [dHilos, dBytes] = comando('docker info --format "{{.NCPU}} {{.MemTotal}}"').split(" ").map(Number);
  return { cpu: cpus()[0].model.replace(/\(R\)|\(TM\)|CPU|@.*$/g, "").replace(/\s+/g, " ").trim(), nucleos, hilos: cpus().length, ramGB: totalmem() / 2 ** 30, dockerHilos: dHilos || null, dockerGB: dBytes ? dBytes / 2 ** 30 : null };
}

/** Memoria de cada contenedor a lo largo de la prueba, desde líneas «segundos;docker stats». */
function leerLinea(ruta, reposo) {
  const t = [], m = { backend: [], portal: [], postgres: [] };
  for (const linea of readFileSync(ruta, "utf8").split("\n")) {
    const i = linea.indexOf(";");
    if (i < 0) continue;
    const fila = {};
    for (const [, n, , v, u] of linea.slice(i + 1).matchAll(/-(backend|portal|postgres)-1 ([\d.]+)% ([\d.]+)(\w+)/g)) fila[n] = Number(v) * (u.startsWith("Gi") ? 1024 : 1);
    if (!fila.portal) continue;
    t.push(Number(linea.slice(0, i)));
    for (const n of Object.keys(m)) m[n].push(fila[n] ?? null);
  }
  if (t.length < 2) throw new Error(`${ruta} no tiene muestras con segundos`);
  return { t, ...m, reposo: Boolean(reposo) };
}

/** Pico y uso sostenido (p90) por contenedor, desde las líneas de `docker stats`. */
function leerRecursos(ruta) {
  const m = {};
  for (const [, n, c, v, u] of readFileSync(ruta, "utf8").matchAll(/-(backend|portal|postgres)-1 ([\d.]+)% ([\d.]+)(\w+)/g)) {
    (m[n] ??= []).push({ cpu: Number(c), mem: Number(v) * (u.startsWith("Gi") ? 1024 : 1) });
  }
  if (!m.backend) throw new Error(`${ruta} no tiene muestras de docker stats`);
  return Object.fromEntries(Object.entries(m).map(([n, v]) => {
    const o = v.map((x) => x.cpu).sort((a, b) => a - b);
    return [n, { cpuP90: o[Math.floor(o.length * 0.9)], cpuMax: o.at(-1), memMax: Math.max(...v.map((x) => x.mem)) }];
  }));
}

const argumentos = (lista) => {
  const r = {};
  for (let i = 0; i < lista.length; i++) {
    if (!lista[i].startsWith("--")) throw new Error(`Argumento inesperado: ${lista[i]}`);
    const clave = lista[i].slice(2);
    if (lista[i + 1] === undefined || lista[i + 1].startsWith("--")) r[clave] = true;
    else r[clave] = lista[++i];
  }
  return r;
};

const mediana = (v) => { const o = [...v].sort((a, b) => a - b), m = o.length >> 1; return o.length % 2 ? o[m] : (o[m - 1] + o[m]) / 2; };
const ms = (v) => `${Math.round(v).toLocaleString("es-ES", { useGrouping: "always" })} ms`;
const dec = (v, d = 1) => v.toFixed(d).replace(".", ",");
const miles = (v) => Math.round(v).toLocaleString("es-ES", { useGrouping: "always" });
const plural = (n) => `${n} ${n === 1 ? "usuario" : "usuarios"}`;

/** El reporte de k6 guarda sus datos en JSON por líneas, comprimido; cada fila trae una lista por métrica, en orden alfabético de nombre. */
function leerReporte(ruta) {
  const html = readFileSync(ruta, "utf8");
  const m = html.match(/<script[^>]*id="data"[^>]*>([\s\S]*?)<\/script>/);
  if (!m) throw new Error(`${ruta} no parece un reporte exportado por k6`);
  const eventos = gunzipSync(Buffer.from(m[1].trim(), "base64")).toString("utf8").split("\n").filter(Boolean).map((l) => JSON.parse(l));
  const metricas = {};
  let filas = [], total = null, param = {};
  for (const e of eventos) {
    if (e.event === "metric") Object.assign(metricas, e.data);
    if (e.event === "param") param = e.data;
    if (e.event === "snapshot") filas.push(e.data);
    if (e.event === "cumulative") total = e.data;
  }
  const nombres = Object.keys(metricas).sort();
  const nombra = (fila) => Object.fromEntries(nombres.map((n, i) => [n, Object.fromEntries(AGREGADOS[metricas[n].type].map((k, j) => [k, fila[i][j]]))]));
  // Las filas de antes de la carga no traen todas las métricas: la serie cubre solo la carga.
  filas = filas.filter((f) => f.length === nombres.length).map(nombra);
  if (!filas.length || !total) throw new Error(`${ruta} no tiene intervalos de carga`);
  return { total: nombra(total), filas, umbral: Number(/p\(95\)<(\d+)/.exec(param.thresholds?.login_ms?.[0] ?? "")?.[1] ?? 2000) };
}

function corridaDesde(ruta, op) {
  const { total, filas, umbral } = leerReporte(ruta);
  const t0 = filas[0].time.value;
  const reqs = total.http_reqs.count;
  const usuarios = Number(op.usuarios);
  if (!Number.isInteger(usuarios) || usuarios < 1) throw new Error("Define --usuarios con la cantidad de usuarios de la corrida");
  const tipo = op.tipo ?? "carga";
  if (!TIPOS[tipo]) throw new Error(`--tipo ${tipo} no existe: usa ${Object.keys(TIPOS).join(", ")}`);
  return {
    id: `u${usuarios}${tipo === "carga" ? "" : "-" + tipo}${op.ruta ? "-" + String(op.ruta).toLowerCase().replace(/[^a-z0-9]+/g, "-") : ""}`,
    usuarios, tipo, ruta: op.ruta ?? null, principal: Boolean(op.principal) || !op.ruta, fecha: new Date(t0).toISOString().slice(0, 10), umbral,
    entorno: op.entorno ?? ENTORNO, alcance: op.alcance ?? ALCANCE, notas: {}, maquina: detectarMaquina(), recursos: op.recursos ? leerRecursos(resolve(op.recursos)) : null,
    memoria: op.linea ? leerLinea(resolve(op.linea), op.reposo) : null,
    reqs, fallidas: Math.round(total.http_req_failed.rate * reqs), logins: total.logins_correctos?.count ?? 0, ocupados: total.login_ocupado?.count ?? 0, loginP95: total.login_ms.p95,
    t: filas.map((r) => Math.round((r.time.value - t0) / 1000)),
    tasa: filas.map((r) => r.http_reqs.rate),
    login: filas.map((r) => r.login_ms?.p95 ?? null),
    http: filas.map((r) => r.http_req_duration.p95),
    loginsSeg: filas.map((r) => r.logins_correctos?.rate ?? 0),
    fallos: filas.map((r) => Math.round(r.http_req_failed.rate * r.http_reqs.count)),
  };
}

/** El primer tramo arranca escalonado y el último suele quedar incompleto: los rangos se miden sin ellos. */
const cuerpo = (v) => (v.length >= 4 ? v.slice(1, -1) : v);

function textoTasa(c) {
  if (c.notas.tasa) return c.notas.tasa;
  const v = cuerpo(c.tasa), med = mediana(v), mn = Math.min(...v), mx = Math.max(...v);
  let s = `La carga se mantuvo en torno a ${dec(med)} peticiones por segundo (entre ${dec(mn)} y ${dec(mx)}).`;
  const primera = c.tasa[0] < 0.85 * med, ultima = c.tasa.at(-1) < 0.7 * med;
  if (primera && ultima) s += " El primer tramo sale bajo porque los usuarios arrancan escalonados, y el último porque la prueba termina a mitad de tramo: ninguno es un problema.";
  else if (primera) s += " El primer tramo sale más bajo porque los usuarios arrancan escalonados, no por un problema.";
  else if (ultima) s += " El último tramo sale bajo porque la prueba termina a mitad de tramo, no por un problema.";
  if (["Saturada", "Sobrepasada"].includes(zona(c))) s += " Aquí la línea plana no es buena señal: es el techo de la PC. Los usuarios esperan su respuesta antes de volver a pedir, así que la tasa no sube aunque haya más gente: lo que crece es la espera.";
  else if (c.usuarios <= 5) s += " Con tan pocos usuarios, las subidas y bajadas dependen de cuántas sesiones caen en cada tramo.";
  else if ((mx - mn) / med <= 0.35) s += " La línea plana indica que el sistema atendió lo que se le pedía sin frenarse: si estuviera saturado, la tasa caería con el tiempo.";
  else s += " La tasa varió bastante entre tramos: conviene ver si coincide con una subida de los tiempos.";
  return s;
}

function textoLatencia(c, base) {
  const v = cuerpo(c.login).filter((x) => x != null), med = mediana(v), mn = Math.min(...v), mx = Math.max(...v);
  let s = `El login tardó entre ${ms(mn)} y ${ms(mx)} según el tramo (p95 de toda la corrida: ${ms(c.loginP95)}).`;
  if (c.login[0] != null && c.login[0] > 1.5 * med) s += ` Al arrancar llegó a ${ms(c.login[0])} y enseguida bajó.`;
  if (mediana(c.loginsSeg) * 10 < 5) s += " Hay pocos logins por tramo, así que cada punto es casi una medición suelta: importa más el nivel que los picos.";
  else {
    const altos = v.filter((x) => x > 1.25 * med).length;
    if (altos === 1) s += ` El pico de ${ms(mx)} dura un solo tramo: es un salto aislado, no una degradación.`;
    if (altos > 1) s += ` Hay ${altos} tramos bastante por encima del resto: conviene revisarlos.`;
  }
  if (base && base !== c) {
    const dif = c.loginP95 - base.loginP95;
    s += Math.abs(dif) < 0.15 * base.loginP95 ? ` Se mantiene en un nivel parecido al de ${plural(base.usuarios)} (${ms(base.loginP95)}).` : ` Frente a ${plural(base.usuarios)}, el p95 ${dif > 0 ? "subió" : "bajó"} ${ms(Math.abs(dif))}.`;
  }
  s += c.loginP95 > c.umbral ? ` Supera el umbral de ${miles(c.umbral)} ms.` : ` Sigue por debajo del umbral de ${miles(c.umbral)} ms.`;
  if (c.notas.extraLatencia) s += ` ${c.notas.extraLatencia}`;
  return s + " Que el login sea la línea más alta es lo esperado: comprobar la contraseña con bcrypt es deliberadamente lento.";
}

function textoFallos(c) {
  if (!c.fallidas) return `Ninguna de las ${miles(c.reqs)} peticiones falló. No hay nada que graficar: es el resultado esperado.`;
  let s = c.notas.fallos ?? `Fallaron ${c.fallidas} de ${miles(c.reqs)} peticiones. Este dashboard guarda cuántas fueron, no el tipo de error: revisa la salida de k6 y los logs del portal y del backend.`;
  const vistos = c.fallos.reduce((a, b) => a + b, 0);
  if (vistos < c.fallidas) s += ` El gráfico muestra ${vistos} de ${c.fallidas}: el resto cayó en los últimos segundos, un tramo incompleto que k6 no guarda como intervalo.`;
  return s;
}

function veredicto(c) {
  const correctas = (1 - c.fallidas / c.reqs) * 100;
  const supera = c.loginP95 > c.umbral, muchos = c.fallidas / c.reqs >= 0.01;
  const tono = supera || muchos ? "mal" : c.fallidas ? "aviso" : "ok";
  const estado = supera ? "Superó el umbral de tiempo" : muchos ? "Demasiados fallos" : c.fallidas ? `${c.fallidas} fallos por revisar` : "Sin fallos";
  const texto = c.fallidas ? `${dec(correctas, 2)} % de peticiones correctas y ${ms(c.loginP95)} de p95 en el login, con ${c.fallidas} fallos por explicar.` : `Ningún fallo en ${miles(c.reqs)} peticiones, con ${miles(c.logins)} logins correctos y ${ms(c.loginP95)} de p95.`;
  return { tono: c.notas.tono ?? tono, estado: c.notas.estado ?? estado, veredicto: c.notas.veredicto ?? texto };
}

const usoCpu = (c) => (c.recursos ? Math.min(100, c.recursos.backend.cpuP90 / c.maquina.hilos) : null);

/** Qué tan cerca estuvo esta PC de su tope, con los umbrales de la prueba y el uso de CPU sostenido del backend. */
function zona(c) {
  if (c.notas.zona) return c.notas.zona;
  if (c.fallidas / c.reqs >= 0.01) return "Sobrepasada";
  if (c.loginP95 > c.umbral || usoCpu(c) >= 85) return "Saturada";
  return usoCpu(c) >= 50 ? "Con carga" : "Holgada";
}

function textoMaquina(m) {
  const nucleos = m.nucleos ? `${m.nucleos} núcleos físicos, ${m.hilos} hilos` : `${m.hilos} hilos`;
  const docker = m.dockerHilos ? ` · Docker: ${m.dockerHilos} hilos, ${dec(m.dockerGB)} GB` : "";
  return `Esta PC: ${m.cpu} · ${nucleos} · ${dec(m.ramGB, 0)} GB de RAM${docker}`;
}

function textoRecursos(c) {
  const m = c.maquina;
  if (!c.recursos) return `En esta corrida no se registró el consumo. ${c.notas.recursos ?? "Para verlo, repite la prueba con --recursos."}`;
  const b = c.recursos.backend, gb = (mb) => `${dec(mb / 1024)} GB`;
  let s = `El backend usó de forma sostenida el ${Math.round(usoCpu(c))} % de la CPU de la PC, unos ${dec(b.cpuP90 / 100)} de sus ${m.hilos} hilos. La memoria no limitó: ${gb(b.memMax)} el backend y ${gb(c.recursos.portal.memMax)} el portal, de ${dec(m.dockerGB ?? m.ramGB)} GB disponibles. Postgres quedó en ${Math.round(c.recursos.postgres.cpuP90)} % de un hilo.`;
  const z = zona(c);
  if (z === "Sobrepasada") s += " La CPU ya estaba agotada: lo que no entra se acumula hasta que las conexiones vencen, y por eso fallan peticiones.";
  else if (z === "Saturada") s += " La CPU es el tope: con ella agotada, más usuarios solo alargan la cola.";
  else if (z === "Con carga") s += " Todavía queda margen, pero esta carga ya usa buena parte de la máquina.";
  else s += " Sobra margen.";
  return s;
}

const mb = (v) => (v >= 1024 ? `${dec(v / 1024, 2)} GB` : `${Math.round(v)} MB`);

/** ¿La memoria del portal se estabiliza o sigue creciendo? Compara la mitad y el final de la carga, sin la muestra de reposo. */
function textoMemoria(c) {
  if (c.notas.memoria) return c.notas.memoria;
  const { t, portal, reposo } = c.memoria;
  const carga = reposo ? portal.slice(0, -1) : portal, n = carga.length, mitad = n >> 1;
  const prom = (v) => v.reduce((a, b) => a + b, 0) / v.length;
  const crecimiento = carga.at(-1) - carga[mitad], horas = (t[n - 1] - t[mitad]) / 3600, pico = Math.max(...carga);
  let s = `El portal empezó en ${mb(carga[0])}, llegó a ${mb(pico)} y terminó la carga en ${mb(carga.at(-1))}.`;
  s += ` En la segunda mitad ${crecimiento >= 0 ? "creció" : "bajó"} ${mb(Math.abs(crecimiento))}${horas > 0.2 ? `, unos ${mb(Math.abs(crecimiento) / horas)} por hora` : ""}.`;
  if (reposo) s += ` Dos minutos después de terminar, en reposo, quedó en ${mb(portal.at(-1))}.`;
  s += crecimiento > 0.1 * prom(carga.slice(mitad))
    ? " Sigue creciendo con la misma carga: es la señal típica de una fuga de memoria. Si no se corrige, el portal termina reiniciándose por falta de memoria."
    : " Con la misma carga, la memoria deja de crecer y se estabiliza, que es lo esperado en un servidor sin fuga.";
  if (reposo) s += portal.at(-1) < 0.5 * pico ? " Además la devolvió al terminar." : " Y no la devolvió al terminar.";
  return s;
}

function capacidad(corridas) {
  const seg = (c) => mediana(cuerpo(c.loginsSeg));
  const tope = corridas.filter((c) => c.recursos && usoCpu(c) >= 85 && c.fallidas / c.reqs < 0.01);
  const sana = corridas.filter((c) => !["Saturada", "Sobrepasada"].includes(zona(c))).sort((a, b) => b.usuarios - a.usuarios)[0];
  if (!tope.length) return "Todavía no se llegó al tope de esta PC, así que su capacidad no está medida.";
  const c = tope.sort((a, b) => seg(b) - seg(a))[0], por = c.maquina.nucleos ? seg(c) / c.maquina.nucleos : null;
  return `Capacidad medida en esta PC: unos ${Math.round(seg(c))} logins por segundo${por ? `, cerca de ${dec(por)} por núcleo físico` : ""}, compartiendo la CPU con k6.${sana ? ` Para pruebas válidas en esta máquina, con este ritmo de logins, conviene no pasar de unos ${sana.usuarios} usuarios: más allá se mide sobre todo el tope de la PC.` : ""}`;
}

const principales = (corridas) => {
  const porUsuarios = new Map();
  for (const c of corridas) if (!porUsuarios.has(c.usuarios) || c.principal) porUsuarios.set(c.usuarios, c);
  return [...porUsuarios.values()].sort((a, b) => a.usuarios - b.usuarios);
};

function resumen(corridas) {
  const p = principales(corridas), a = p[0], z = p.at(-1);
  const seg = (c) => mediana(cuerpo(c.loginsSeg));
  const r = { ids: p.map((c) => c.id), etiquetas: p.map((c) => String(c.usuarios)), login: p.map((c) => c.loginP95), logins: p.map(seg) };
  if (p.length < 2) return { ...r, txtLogin: "Con una sola corrida todavía no hay con qué comparar.", txtLogins: "Con una sola corrida todavía no hay con qué comparar." };
  const sube = Math.round((z.loginP95 / a.loginP95 - 1) * 100);
  r.txtLogin = `De ${plural(a.usuarios)} a ${plural(z.usuarios)}, el p95 del login pasó de ${ms(a.loginP95)} a ${ms(z.loginP95)}: ${sube >= 200 ? `se multiplicó por ${Math.round(z.loginP95 / a.loginP95)}` : `${sube >= 0 ? "subió" : "bajó"} un ${Math.abs(sube)} %`}. ${z.loginP95 > z.umbral ? `Ya supera el umbral de ${miles(z.umbral)} ms.` : `Sigue por debajo del umbral de ${miles(z.umbral)} ms, así que no se llegó al límite del sistema.`}`;
  const proporcion = seg(z) / z.usuarios / (seg(a) / a.usuarios);
  r.txtLogins = proporcion >= 0.7
    ? `Los logins por segundo crecen casi en proporción a los usuarios, hasta unos ${dec(seg(z))} por segundo con ${z.usuarios}. Mientras eso se mantiene, el sistema no está saturado: si lo estuviera, la barra dejaría de crecer aunque se agreguen usuarios. Es la mediana durante la carga, por eso es mayor que el promedio del resumen de k6, que incluye los casos iniciales.`
    : `Los logins por segundo ya no crecen en proporción a los usuarios: con ${z.usuarios} llegan a ${dec(seg(z))} por segundo. Es una señal de que el sistema se acerca a su límite.`;
  return r;
}

function paraPantalla(c, base) {
  const memoria = c.memoria ? { t: c.memoria.t, series: Object.entries(CONTENEDORES).map(([k, nombre]) => ({ nombre, v: c.memoria[k] })), reposo: c.memoria.reposo, txt: textoMemoria(c) } : null;
  const pct = c.fallidas ? `${c.fallidas} (${dec((c.fallidas / c.reqs) * 100, 2)} %)` : "0";
  return {
    id: c.id, usuarios: c.usuarios, tipo: c.tipo, ruta: c.ruta, memoria, principal: c.principal, entorno: `${c.entorno} Corrida del ${c.fecha}.`, alcance: c.alcance,
    titulo: `${TIPOS[c.tipo].nombre} · ${plural(c.usuarios)}${c.ruta ? ` · ${c.ruta}` : ""}`, ...veredicto(c), zona: zona(c), maquina: textoMaquina(c.maquina), txtRecursos: textoRecursos(c),
    recursos: c.recursos ? Object.entries(CONTENEDORES).map(([k, nombre]) => ({ nombre, cpu: Math.min(100, c.recursos[k].cpuP90 / c.maquina.hilos), cpuMax: c.recursos[k].cpuMax, mem: c.recursos[k].memMax })) : null,
    tiles: [["Peticiones", miles(c.reqs)], ["Logins correctos", miles(c.logins)], ["Login p95", ms(c.loginP95)], ["Fallidas", pct], ...(c.ocupados ? [["Rechazados con 503", miles(c.ocupados)]] : [])],
    t: c.t, tasa: c.tasa, login: c.login, http: c.http, fallos: c.fallidas ? c.fallos : null,
    txtTasa: textoTasa(c), txtLat: textoLatencia(c, base), txtFallos: textoFallos(c),
  };
}

/** Cada tipo de prueba: cuántas corridas tiene, su peor resultado y lo que se encontró. */
function catalogo(corridas, conclusiones) {
  return Object.entries(TIPOS).map(([clave, t]) => {
    const de = corridas.filter((c) => c.tipo === clave), us = de.map((c) => c.usuarios);
    const tonos = de.map((c) => veredicto(c).tono), peor = tonos.includes("mal") ? "mal" : tonos.includes("aviso") ? "aviso" : de.length ? "ok" : null;
    const rango = !de.length ? null : Math.min(...us) === Math.max(...us) ? plural(us[0]) : `${miles(Math.min(...us))} a ${miles(Math.max(...us))} usuarios`;
    return { clave, ...t, corridas: de.length, rango, peor, conclusion: conclusiones[clave] ?? null };
  });
}

function render(corridas) {
  const conclusiones = existsSync(CONCLUSIONES) ? JSON.parse(readFileSync(CONCLUSIONES, "utf8")) : {};
  const delTipo = (t) => corridas.filter((c) => c.tipo === t);
  const ordenadas = [...corridas].sort((a, b) => a.usuarios - b.usuarios);
  const resumenes = Object.fromEntries(Object.keys(TIPOS).filter((t) => delTipo(t).length).map((t) => [t, resumen(delTipo(t))]));
  const pantalla = ordenadas.map((c) => paraPantalla(c, principales(delTipo(c.tipo))[0]));
  const datos = JSON.stringify({ corridas: pantalla, resumenes, txtCapacidad: capacidad(corridas), tipos: catalogo(corridas, conclusiones) }).replace(/</g, "\\u003c");
  writeFileSync(SALIDA, readFileSync(resolve(AQUI, "plantilla.html"), "utf8").replace("/*DATOS*/null", () => datos));
}

const op = argumentos(process.argv.slice(2));
const corridas = existsSync(ALMACEN) ? JSON.parse(readFileSync(ALMACEN, "utf8")) : [];
if (!op["solo-render"]) {
  const nueva = corridaDesde(resolve(op.reporte ?? resolve(RESULTADOS, ".ultima.html")), op);
  const i = corridas.findIndex((c) => c.id === nueva.id);
  if (i >= 0) { nueva.notas = {}; corridas[i] = nueva; } else corridas.push(nueva);
  if (op.principal) for (const c of corridas) if (c.usuarios === nueva.usuarios && c.tipo === nueva.tipo && c.id !== nueva.id) c.principal = false;
  writeFileSync(ALMACEN, JSON.stringify(corridas));
}
if (!corridas.length) throw new Error("Todavía no hay corridas");
render(corridas);
console.log(`${corridas.length} corridas en ${SALIDA}`);
