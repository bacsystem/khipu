/**
 * La sesión murió con la página ya cargada (#654): una acción pasó por el proxy, el backend dijo 401 y el refresh ya no sirve. El proxy lo marca con una cabecera, el envoltorio del `fetch`
 * del navegador (`fetchDelPortal`) la lee y avisa acá, y el diálogo «Tu sesión terminó» (montado una sola vez en `(privado)` y `/onboarding`) se abre. Sin dependencias de React ni del
 * navegador al importarlo, para que el proxy (servidor) comparta las constantes; el hook que lo lee vive en `components/sesion/use-sesion-terminada.ts`.
 */

/** La cabecera con la que el proxy marca un 401 que ya no se puede recuperar. Solo el proxy la emite: el backend nunca. */
export const CABECERA_SESION = "x-khipu-sesion";
export const SESION_TERMINADA = "terminada";

const HTTP_NO_AUTORIZADO = 401;

/** Si la respuesta es el 401 definitivo del proxy. El status va primero: no toca las cabeceras de una respuesta que no es un 401. */
export function esSesionTerminada(res: Response): boolean {
  return res.status === HTTP_NO_AUTORIZADO && res.headers.get(CABECERA_SESION) === SESION_TERMINADA;
}

/**
 * Un booleano que, una vez activado, no vuelve atrás hasta que empiece una sesión nueva: varios 401 en vuelo abren un solo diálogo sin importar en qué orden lleguen. Es un estado y no un
 * evento porque el diálogo se monta antes que cualquier `fetch` de la página y se entera aunque el aviso haya llegado primero.
 */
let terminada = false;
let dialogosMontados = 0;
const oyentes = new Set<() => void>();

const avisarALosOyentes = () => oyentes.forEach((oyente) => oyente());

export function suscribirseASesionTerminada(oyente: () => void): () => void {
  oyentes.add(oyente);
  return () => void oyentes.delete(oyente);
}

export const leerSesionTerminada = () => terminada;

/**
 * A dónde lleva «Volver a ingresar»: el login con la ruta actual (ruta y consulta) en `next`, que el login valida con `rutaSegura`. Desde el propio login no se manda `next`: volvería a él.
 */
export function rutaDeLoginDeVuelta(): string {
  const aqui = window.location.pathname + window.location.search;
  return aqui.startsWith("/login") ? "/login" : `/login?next=${encodeURIComponent(aqui)}`;
}

/**
 * Idempotente. Si no hay un diálogo montado donde explicarlo se navega directo al login: ninguna página del portal que llame al proxy queda fuera de `(privado)` y `/onboarding`,
 * salvo `/developers` con la sesión viva, así que es un respaldo y no un camino normal.
 */
export function avisarSesionTerminada(): void {
  if (terminada) return;
  terminada = true;
  if (dialogosMontados === 0) {
    window.location.assign(rutaDeLoginDeVuelta());
    return;
  }
  avisarALosOyentes();
}

/** El diálogo se declara montado mientras exista; devuelve la función que lo desmonta. */
export function montarDialogo(): () => void {
  dialogosMontados += 1;
  let montado = true;
  return () => {
    if (!montado) return;
    montado = false;
    dialogosMontados -= 1;
  };
}

/**
 * Una sesión nueva (el login salió bien) empieza de cero. Sin esto, un 401 que llegara justo después de cerrar la sesión con la navegación del cliente (que no recarga la página) dejaría
 * el estado activo y el diálogo se abriría sobre la sesión siguiente.
 */
export function empezarSesionNueva(): void {
  if (!terminada) return;
  terminada = false;
  avisarALosOyentes();
}

export function reiniciarSesionTerminadaParaPruebas(): void {
  terminada = false;
  dialogosMontados = 0;
  oyentes.clear();
}
