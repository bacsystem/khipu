import type { EstadoEntrega } from "@/lib/api/webhooks";
import type { Mensajes } from "@/i18n/mensajes";
import es from "@/messages/es.json";

/**
 * Los eventos a los que se puede suscribir un webhook (#19), con el estado del comprobante que los dispara. Tiene que coincidir con `EventoWebhook` del backend.
 * `clave` apunta a su etiqueta y su detalle en `messages.webhooks.eventos`.
 */
export const EVENTOS_WEBHOOK = [
  { evento: "comprobante.aceptado", clave: "aceptado" },
  { evento: "comprobante.aceptado_con_obs", clave: "aceptadoConObs" },
  { evento: "comprobante.rechazado", clave: "rechazado" },
  { evento: "comprobante.error_envio", clave: "errorEnvio" },
  { evento: "comprobante.fuera_de_plazo", clave: "fueraDePlazo" },
  { evento: "comprobante.anulado", clave: "anulado" },
  { evento: "comprobante.descartado", clave: "descartado" },
] as const;

export function etiquetaDeEvento(evento: string, messages: Mensajes = es): string {
  if (evento === "webhook.prueba") return messages.webhooks.eventoPrueba;
  const conocido = EVENTOS_WEBHOOK.find((e) => e.evento === evento);
  return conocido ? messages.webhooks.eventos[conocido.clave].etiqueta : evento;
}

/** «Todos los eventos» si están los siete; si no, sus etiquetas. */
export function resumenDeEventos(eventos: string[], messages: Mensajes = es): string {
  if (EVENTOS_WEBHOOK.every((e) => eventos.includes(e.evento))) return messages.webhooks.todosLosEventos;
  return eventos.map((e) => etiquetaDeEvento(e, messages)).join(", ");
}

/** Las etiquetas de los estados de una entrega, en castellano; la UI las lee de `messages.webhooks.estadoEntrega` en el idioma de la sesión. */
export const ETIQUETA_ESTADO_ENTREGA: Record<EstadoEntrega, string> = es.webhooks.estadoEntrega;

/** Con cuántos intentos fallidos seguidos khipu apaga el endpoint; coincide con `EntregarWebhooksService.PAUSA_TRAS_FALLOS`. */
export const PAUSA_TRAS_FALLOS = 10;

/** Lo que se muestra del último intento de una entrega: el código HTTP o el error de red. */
export function resultadoDelIntento(ultimoHttp: number | null | undefined, ultimoError: string | null | undefined): string {
  if (ultimoHttp != null && ultimoError == null) return `HTTP ${ultimoHttp}`;
  if (ultimoError) return ultimoError;
  return "—";
}

/** Una URL https válida para un webhook; devuelve el mensaje si no lo es. Es la misma regla de forma que `UrlDeWebhook` (el backend decide además si apunta a una red interna). */
export function errorDeUrlWebhook(url: string, messages: Mensajes = es): string | null {
  const t = messages.webhooks.url;
  const limpia = url.trim();
  if (limpia === "") return t.vacia;
  if (limpia.length > 500) return t.larga;
  let u: URL;
  try {
    u = new URL(limpia);
  } catch {
    return t.invalida;
  }
  if (u.protocol !== "https:") return t.https;
  if (u.username !== "" || u.password !== "") return t.credenciales;
  if (u.hash !== "") return t.fragmento;
  return null;
}
