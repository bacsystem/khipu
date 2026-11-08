"use strict";
// Punto de entrada de la imagen del portal (#618). Un proceso de Node usa un solo núcleo: con carga, el portal se quedaba en unas 31 páginas por segundo
// mientras el resto de la máquina estaba libre. Con PORTAL_PROCESOS=N, un proceso principal levanta N procesos del servidor de Next (server.js, el de
// `output: "standalone"`) que comparten el puerto: Node reparte las conexiones entre ellos. Con 1, el valor por defecto, carga server.js tal cual, como antes.
//
// El número no se deduce de los núcleos a propósito: dentro de un contenedor, Node puede ver los del servidor físico y no los que asigna el plan, y
// levantaría decenas de procesos que agotan la memoria. Cada proceso usa unos 400 a 650 MB con carga (pruebas de k6 del 2026-11-08).
//
// Cada proceso corre su propio register() (instrumentation) y su propio estado en memoria. El único que importa es el de las renovaciones de sesión del
// proxy (refrescarUnaVez), y el backend ya acepta un refresh recién rotado durante 30 segundos (S6) para las carreras entre procesos.
const MAXIMO = 32;
const VIDA_MINIMA_MS = 10_000;
const ESPERA_MAXIMA_MS = 30_000;
const CIERRE_MAXIMO_MS = 10_000;

/** PORTAL_PROCESOS como entero de 1 a 32. Vacía es 1; un valor inválido avisa y también es 1: una variable opcional no puede tumbar el portal. */
function procesosConfigurados(valor, avisar) {
  const texto = (valor ?? "").trim();
  if (texto === "") return 1;
  if (/^\d+$/.test(texto)) {
    const n = Number(texto);
    if (n >= 1 && n <= MAXIMO) return n;
  }
  avisar(`[procesos] PORTAL_PROCESOS=${JSON.stringify(valor)} no es un entero de 1 a ${MAXIMO}: el portal arranca con 1 proceso.`);
  return 1;
}

/** Cuánto esperar para reemplazar un proceso caído: nada si vivió lo suficiente; si no, 1 s, 2 s, 4 s… hasta 30 s, para no entrar en un bucle. */
function esperaAntesDeReiniciar(vivioMs, caidasSeguidas) {
  if (vivioMs >= VIDA_MINIMA_MS) return 0;
  return Math.min(ESPERA_MAXIMA_MS, 1_000 * 2 ** caidasSeguidas);
}

/** El proceso principal: mantiene `procesos` servidores vivos y los cierra en orden con SIGTERM o SIGINT. */
function orquestar({ cluster, procesos, ahora, avisar, salir, proceso }) {
  const vivos = new Map(); // id -> { worker, nacio }
  let caidasSeguidas = 0;
  let cerrando = false;
  let plazoDeCierre = null;

  const levantar = () => {
    const worker = cluster.fork();
    vivos.set(worker.id, { worker, nacio: ahora() });
  };

  cluster.on("exit", (w, codigo, senal) => {
    const vivio = ahora() - (vivos.get(w.id)?.nacio ?? ahora());
    vivos.delete(w.id);
    if (cerrando) {
      if (vivos.size === 0) {
        clearTimeout(plazoDeCierre);
        salir(0);
      }
      return;
    }
    const espera = esperaAntesDeReiniciar(vivio, caidasSeguidas);
    caidasSeguidas = espera > 0 ? caidasSeguidas + 1 : 0;
    avisar(`[procesos] el proceso ${w.id} terminó (código ${codigo}, señal ${senal}) tras ${Math.round(vivio / 1000)} s: se reemplaza${espera ? ` en ${espera / 1000} s` : ""}.`);
    setTimeout(() => {
      if (!cerrando) levantar();
    }, espera);
  });

  const cerrar = (senal) => {
    if (cerrando) return;
    cerrando = true;
    if (vivos.size === 0) return salir(0);
    // El plazo va antes de matar: un proceso que termina enseguida lo cancela al salir el último.
    plazoDeCierre = setTimeout(() => {
      avisar(`[procesos] ${senal}: no todos los procesos terminaron en ${CIERRE_MAXIMO_MS / 1000} s; se sale igual.`);
      salir(0);
    }, CIERRE_MAXIMO_MS);
    for (const { worker } of [...vivos.values()]) worker.kill("SIGTERM");
  };
  proceso.on("SIGTERM", () => cerrar("SIGTERM"));
  proceso.on("SIGINT", () => cerrar("SIGINT"));

  for (let i = 0; i < procesos; i++) levantar();
}

module.exports = { procesosConfigurados, esperaAntesDeReiniciar, orquestar };

// CommonJS a propósito: corre sin compilar junto al server.js de standalone, que también lo es.
/* eslint-disable @typescript-eslint/no-require-imports */
if (require.main === module) {
  const cluster = require("node:cluster");
  const procesos = procesosConfigurados(process.env.PORTAL_PROCESOS, console.error);
  if (procesos === 1 || !cluster.isPrimary) {
    require("./server.js");
  } else {
    console.log(`[procesos] portal con ${procesos} procesos`);
    orquestar({ cluster, procesos, ahora: Date.now, avisar: console.error, salir: (codigo) => process.exit(codigo), proceso: process });
  }
}
