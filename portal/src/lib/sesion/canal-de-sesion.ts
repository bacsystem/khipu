/** El canal por el que las pestañas de un mismo navegador se avisan de lo que le pasó a la sesión (#637, #653). */
export const CANAL_SESION = "khipu-sesion";

/**
 * `inactividad`: el portal cerró la sesión por inactividad. `salir`: la persona cerró la sesión a propósito. El formato es un string, y un valor que una pestaña
 * con otra versión del JS no conozca se ignora, así que un despliegue en curso no rompe a las pestañas abiertas.
 */
export type AvisoDeSesion = "inactividad" | "salir";

const AVISOS = ["inactividad", "salir"] as const satisfies readonly AvisoDeSesion[];

function esAviso(dato: unknown): dato is AvisoDeSesion {
  return (AVISOS as readonly unknown[]).includes(dato);
}

/**
 * Un solo canal por pestaña, y no uno por quien lo necesite: un `BroadcastChannel` no se recibe a sí mismo, pero sí recibe lo que publica otro objeto del mismo nombre
 * en el mismo origen, incluso de la misma pestaña. Con un canal para publicar y otro para escuchar, la pestaña que cierra la sesión se navegaría también a sí misma.
 * Se crea la primera vez que se usa y no se cierra: muere con la página. `null` si el navegador no lo ofrece.
 */
let canal: BroadcastChannel | null | undefined;

function canalDeLaPestana(): BroadcastChannel | null {
  if (canal !== undefined) return canal;
  try {
    canal = typeof BroadcastChannel === "undefined" ? null : new BroadcastChannel(CANAL_SESION);
  } catch {
    canal = null; // p. ej. un contexto sin origen: el cierre local funciona igual
  }
  return canal;
}

/** Les cuenta a las demás pestañas lo que pasó. Sin `BroadcastChannel` no hace nada y no lanza. */
export function avisarALasDemasPestanas(aviso: AvisoDeSesion): void {
  canalDeLaPestana()?.postMessage(aviso);
}

/** Escucha los avisos de las demás pestañas (nunca los de esta). Devuelve la función que quita el oyente. */
export function escucharAvisosDeSesion(alRecibir: (aviso: AvisoDeSesion) => void): () => void {
  const actual = canalDeLaPestana();
  if (!actual) return () => undefined;
  const oyente = (evento: MessageEvent<unknown>) => {
    if (esAviso(evento.data)) alRecibir(evento.data);
  };
  actual.addEventListener("message", oyente);
  return () => actual.removeEventListener("message", oyente);
}

/** Descarta el canal de la pestaña para que la prueba siguiente cree el suyo (con el `BroadcastChannel` que esa prueba simule). */
export function reiniciarCanalParaPruebas(): void {
  canal?.close();
  canal = undefined;
}
