// Sesiones del portal: casos de login y N usuarios entrando a la vez, por el mismo camino que el navegador (portal, no la API directa).
// Primero corre `casos` (una vez) y después `carga`: VUS usuarios haciendo login, abriendo el panel, leyendo sus empresas y cerrando sesión.
// Solo lee: no emite comprobantes, no crea cuentas y no cambia datos.
//
// Uso:
//   PORTAL_URL=http://localhost:3000 USUARIOS=/ruta/usuarios.json k6 run k6/login.js
//
// MODO=unico: cada usuario entra UNA sola vez, con una cuenta que nadie más usa, y las llegadas se reparten al azar en VENTANA_S segundos (60 por
// defecto). Con P_REPETIR (0 por defecto), tras cerrar sesión el usuario vuelve a entrar con esa probabilidad, después de una espera al azar entre
// REPETIR_MIN_S y REPETIR_MAX_S, y puede repetir otra vez. DESDE elige desde qué cuenta empezar (1 por defecto: la 0 es de `casos`), para que escalones seguidos no repitan cuentas.
//
// MODO=navega: usuarios conectados a la vez. Cada uno entra UNA sola vez (llegadas repartidas en VENTANA_S) y después recorre el panel como una
// persona: una página o lectura al azar, una pausa entre PIENSA_MIN_S y PIENSA_MAX_S (10 y 30 por defecto), y otra, hasta NAVEGA_S segundos después
// de que terminó la ventana (300 por defecto). Las cuentas necesitan una empresa: sin ella el panel manda a /onboarding y la prueba lo cuenta como fallo.
//
// USUARIOS es un JSON con una cuenta de prueba por usuario (ver k6/usuarios.ejemplo.json). Tiene que haber VUS correos distintos: el backend reserva el
// intento ANTES de comprobar la contraseña y lo devuelve al acertar (`LimiteDeIntentos`), así que de seis logins simultáneos del mismo correo, el sexto
// recibe 429 aunque la contraseña sea correcta.
//
// Contra un servidor que no sea local hace falta CONFIRMAR_SERVIDOR_REAL=si. Todo sale de una sola IP y 20 fallos en 15 minutos la bloquean para cualquier
// cuenta, así que la corrida se detiene ante el primer 401 inesperado, 429 o bloqueo del proxy. Ver k6/README.md.
import http from "k6/http";
import exec from "k6/execution";
import { check, group, sleep } from "k6";
import { SharedArray } from "k6/data";
import { Counter, Trend } from "k6/metrics";

const BASE_URL = (__ENV.PORTAL_URL || "http://localhost:3000").replace(/\/+$/, "");
const VUS = Number(__ENV.VUS || 10);
const DURACION = __ENV.DURACION || "2m";
const PAUSA_S = Number(__ENV.PAUSA_S || 5);
const UMBRAL_LOGIN_MS = Number(__ENV.UMBRAL_LOGIN_MS || 2000);
const PROBAR_BLOQUEO = __ENV.PROBAR_BLOQUEO === "si";
const CASOS_S = 30;
const NAVEGA = __ENV.MODO === "navega";
const UNICO = __ENV.MODO === "unico" || NAVEGA;
const NAVEGA_S = Number(__ENV.NAVEGA_S || 300);
const PIENSA_MIN_S = Number(__ENV.PIENSA_MIN_S || 10);
const PIENSA_MAX_S = Number(__ENV.PIENSA_MAX_S || 30);
const UMBRAL_PAGINA_MS = Number(__ENV.UMBRAL_PAGINA_MS || 2000);
const VENTANA_S = Number(__ENV.VENTANA_S || 60);
const DESDE = UNICO ? Number(__ENV.DESDE || 1) : 0;
const P_REPETIR = Number(__ENV.P_REPETIR || 0);
const REPETIR_MIN_S = Number(__ENV.REPETIR_MIN_S || 30);
const REPETIR_MAX_S = Number(__ENV.REPETIR_MAX_S || 120);

const esLocal = /^https?:\/\/(localhost|127\.0\.0\.1|\[::1\]|host\.docker\.internal)(:\d+)?$/.test(BASE_URL);
if (!esLocal && __ENV.CONFIRMAR_SERVIDOR_REAL !== "si") {
  throw new Error(`${BASE_URL} no es local: define CONFIRMAR_SERVIDOR_REAL=si si de verdad quieres correr contra ese servidor`);
}
if (!__ENV.USUARIOS) {
  throw new Error("Define USUARIOS con la ruta del JSON de cuentas de prueba (ver k6/usuarios.ejemplo.json)");
}

const usuarios = new SharedArray("usuarios", () => JSON.parse(open(__ENV.USUARIOS)));
const correos = new Set(usuarios.map((u) => String(u.email).trim().toLowerCase()));
if (correos.size !== usuarios.length || usuarios.length < DESDE + VUS) {
  throw new Error(`Hacen falta ${DESDE + VUS} cuentas con correos distintos y hay ${correos.size} distintos en ${usuarios.length}: con un correo compartido, los logins simultáneos reciben 429`);
}

const loginMs = new Trend("login_ms", true);
const logins = new Counter("logins_correctos");
const bloqueos = new Counter("login_bloqueado");
const ocupados = new Counter("login_ocupado");
const reingresos = new Counter("reingresos");
const paginaMs = new Trend("pagina_ms", true);
const paginas = new Counter("paginas_correctas");

/** Lo que abre una persona en el panel, con su peso: el inicio y los comprobantes son lo más visitado. */
const RECORRIDO = [
  ["inicio", "/inicio", 3], ["comprobantes", "/comprobantes", 4], ["clientes", "/clientes", 2], ["productos", "/productos", 2],
  ["series", "/series", 1], ["empresa", "/empresa", 1], ["empresas-api", "/api/proxy/empresas", 2],
];
const PESO_TOTAL = RECORRIDO.reduce((s, [, , p]) => s + p, 0);
const alAzar = () => { let x = Math.random() * PESO_TOTAL; for (const paso of RECORRIDO) if ((x -= paso[2]) < 0) return paso; return RECORRIDO[0]; };

export const options = {
  scenarios: {
    casos: { executor: "shared-iterations", vus: 1, iterations: 1, maxDuration: `${CASOS_S}s`, exec: "casos" },
    // Empieza cuando `casos` ya terminó: los dos usan la primera cuenta y no pueden entrar con ella a la vez.
    carga: UNICO
      ? { executor: "per-vu-iterations", vus: VUS, iterations: 1, maxDuration: NAVEGA ? `${VENTANA_S + NAVEGA_S + 60}s` : DURACION, startTime: `${CASOS_S + 2}s`, exec: "sesion" }
      : { executor: "constant-vus", vus: VUS, duration: DURACION, startTime: `${CASOS_S + 2}s`, exec: "sesion" },
  },
  thresholds: {
    "checks{scenario:casos}": ["rate==1"],
    "checks{scenario:carga}": ["rate>0.99"],
    http_req_failed: ["rate<0.01"],
    login_ms: [`p(95)<${UMBRAL_LOGIN_MS}`],
    login_bloqueado: ["count==0"],
    ...(NAVEGA ? { pagina_ms: [`p(95)<${UMBRAL_PAGINA_MS}`] } : {}),
  },
};

const JSON_HEADERS = { "Content-Type": "application/json" };
const espera = (...estados) => ({ responseCallback: http.expectedStatuses(...estados) });

const codigoDe = (res) => {
  try {
    return res.json("codigo");
  } catch {
    return null;
  }
};

const entrar = (email, password, extra = {}) =>
  http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email, password }), { headers: JSON_HEADERS, tags: { name: "login" }, ...extra });

const salir = () => http.post(`${BASE_URL}/api/auth/logout`, null, { tags: { name: "logout" } });

/** Lo que no se debe insistir: seguir gastaría el cupo de fallos de la IP o martillaría un bloqueo. */
function detenerSiHayBloqueo(res) {
  const proxy = res.status === 403 && codigoDe(res) === null;
  if (res.status !== 429 && !proxy && res.status !== 401) return;
  if (res.status === 429) bloqueos.add(1);
  exec.test.abort(`Corrida detenida: el login respondió ${res.status} ${codigoDe(res) ?? "(sin código: posible bloqueo de Cloudflare)"}`);
}

const redirigeALogin = (r) => [302, 303, 307, 308].includes(r.status) && String(r.headers.Location).includes("/login");

export function casos() {
  const correoInexistente = `k6-${Date.now()}@ejemplo.invalid`;
  const cuenta = usuarios[0];
  const seguro = BASE_URL.startsWith("https://");

  group("páginas públicas", () => {
    check(http.get(`${BASE_URL}/`, { tags: { name: "portada" } }), { "la portada responde": (r) => r.status === 200 && r.body.includes("khipu") });
    check(http.get(`${BASE_URL}/login`, { tags: { name: "pagina-login" } }), { "la página de login responde": (r) => r.status === 200 });
  });

  group("sin sesión", () => {
    check(http.get(`${BASE_URL}/inicio`, { redirects: 0, tags: { name: "inicio-sin-sesion" } }), { "una página privada manda al login": redirigeALogin });
    check(http.get(`${BASE_URL}/api/proxy/empresas`, { tags: { name: "proxy-sin-sesion" }, ...espera(401) }), { "el proxy responde 401": (r) => r.status === 401 });
    check(http.get(`${BASE_URL}/api/proxy/auth/me`, { tags: { name: "proxy-auth" }, ...espera(404) }), { "el proxy no reenvía /auth": (r) => r.status === 404 });
  });

  group("credenciales inválidas", () => {
    const res = entrar(correoInexistente, "no-es-la-clave-k6", espera(401));
    check(res, {
      "un correo inexistente responde 401": (r) => r.status === 401 && codigoDe(r) === "CREDENCIALES_INVALIDAS",
      "un login fallido no deja sesión": (r) => !r.cookies.khipu_access && !r.cookies.khipu_refresh,
    });
    const vacio = http.post(`${BASE_URL}/api/auth/login`, "{}", { headers: JSON_HEADERS, tags: { name: "login-vacio" }, responseCallback: http.expectedStatuses({ min: 400, max: 499 }) });
    check(vacio, { "un login vacío se rechaza sin error del servidor": (r) => r.status >= 400 && r.status < 500 });
  });

  group("sesión completa", () => {
    const res = entrar(cuenta.email, cuenta.password, espera(200, 401, 403, 429));
    detenerSiHayBloqueo(res);
    const refresh = res.cookies.khipu_refresh?.[0];
    check(res, {
      "el login correcto responde 200": (r) => r.status === 200 && r.json("estado") === "exito",
      "los tokens no llegan al navegador": (r) => !r.body.includes("access_token") && !r.body.includes("refresh_token"),
      "las cookies son httpOnly": (r) => r.cookies.khipu_access?.[0]?.http_only === true && refresh?.http_only === true,
      "las cookies son Secure por HTTPS": (r) => !seguro || (r.cookies.khipu_access?.[0]?.secure === true && refresh?.secure === true),
    });

    const inicio = http.get(`${BASE_URL}/inicio`, { tags: { name: "inicio" } });
    check(inicio, { "con sesión entra al panel": (r) => r.status === 200 && !r.url.includes("/login") });
    check(http.get(`${BASE_URL}/api/proxy/empresas`, { tags: { name: "empresas" } }), { "con sesión lee sus empresas": (r) => r.status === 200 });

    check(salir(), { "el logout responde 200": (r) => r.status === 200 });
    check(http.get(`${BASE_URL}/inicio`, { redirects: 0, tags: { name: "inicio-tras-logout" } }), { "tras el logout vuelve al login": redirigeALogin });

    // La cookie vieja reenviada a mano, sin el access: el middleware intenta renovarla y el backend ya la invalidó.
    http.cookieJar().clear(BASE_URL);
    const revocado = http.get(`${BASE_URL}/inicio`, { redirects: 0, headers: { Cookie: `khipu_refresh=${refresh?.value}` }, tags: { name: "refresh-revocado" } });
    check(revocado, { "el refresh de una sesión cerrada ya no sirve": redirigeALogin });
  });

  if (PROBAR_BLOQUEO) {
    group("bloqueo por intentos", () => {
      // Correo inventado: bloquea solo a ese correo. Gasta 5 de los 20 fallos de la IP (el anterior usó otro correo: 6 en total).
      const correo = `k6-bloqueo-${Date.now()}@ejemplo.invalid`;
      for (let i = 0; i < 5; i++) entrar(correo, "no-es-la-clave-k6", espera(401));
      check(entrar(correo, "no-es-la-clave-k6", espera(429)), { "el sexto intento responde 429": (r) => r.status === 429 && codigoDe(r) === "DEMASIADOS_INTENTOS_LOGIN" });
    });
  }
}

export function sesion() {
  // idInTest puede empezar en 1 o en 2 según si k6 reusó el VU de `casos`: el módulo reparte igual una cuenta distinta a cada uno.
  const usuario = usuarios[DESDE + ((exec.vu.idInTest - 1) % VUS)];
  // La primera vuelta se reparte para que los VUS no pidan el login en el mismo instante.
  if (exec.vu.iterationInScenario === 0) sleep(Math.random() * (UNICO ? VENTANA_S : PAUSA_S));
  if (NAVEGA) return navegar(usuario);
  if (!UNICO) {
    // Como antes de MODO: tras un login fallido no hay pausa.
    if (unaSesion(usuario) !== false) sleep(PAUSA_S * (0.7 + Math.random() * 0.6));
    return;
  }
  unaSesion(usuario);
  while (Math.random() < P_REPETIR) {
    sleep(REPETIR_MIN_S + Math.random() * (REPETIR_MAX_S - REPETIR_MIN_S));
    reingresos.add(1);
    unaSesion(usuario);
  }
}

/** Entra una vez y recorre el panel con pausas de persona hasta el final de la prueba. */
function navegar(usuario) {
  const fin = exec.scenario.startTime + (VENTANA_S + NAVEGA_S) * 1000;
  const res = entrar(usuario.email, usuario.password, espera(200, 401, 403, 429, 503));
  detenerSiHayBloqueo(res);
  if (res.status === 503 && codigoDe(res) === "SERVICIO_OCUPADO") {
    ocupados.add(1);
    return;
  }
  if (!check(res, { "login correcto": (r) => r.status === 200 })) return;
  loginMs.add(res.timings.duration);
  logins.add(1);
  while (Date.now() < fin) {
    const [nombre, ruta] = alAzar();
    const r = http.get(`${BASE_URL}${ruta}`, { tags: { name: nombre } });
    // Una página que manda a /login o /onboarding respondió 200 al final de la redirección, pero no es la página pedida.
    if (check(r, { "abre la página": (x) => x.status === 200 && !x.url.includes("/login") && !x.url.includes("/onboarding") })) {
      paginaMs.add(r.timings.duration, { pagina: nombre });
      paginas.add(1);
    }
    sleep(PIENSA_MIN_S + Math.random() * (PIENSA_MAX_S - PIENSA_MIN_S));
  }
}

/** Login, panel, empresas y logout. Un 503 SERVICIO_OCUPADO es un rechazo a propósito por saturación: se cuenta aparte. */
function unaSesion(usuario) {
  const res = entrar(usuario.email, usuario.password, espera(200, 401, 403, 429, 503));
  detenerSiHayBloqueo(res);
  if (res.status === 503 && codigoDe(res) === "SERVICIO_OCUPADO") {
    ocupados.add(1);
    return;
  }
  if (!check(res, { "login correcto": (r) => r.status === 200 })) return false;
  loginMs.add(res.timings.duration);
  logins.add(1);

  check(http.get(`${BASE_URL}/inicio`, { tags: { name: "inicio" } }), { "entra al panel": (r) => r.status === 200 && !r.url.includes("/login") });
  check(http.get(`${BASE_URL}/api/proxy/empresas`, { tags: { name: "empresas" } }), { "lee sus empresas": (r) => r.status === 200 });
  check(salir(), { "cierra sesión": (r) => r.status === 200 });
}
