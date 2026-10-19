import type { EstadoEntrega } from "@/lib/api/webhooks";

/** Los eventos a los que se puede suscribir un webhook (#19), con el estado del comprobante que los dispara. Tiene que coincidir con `EventoWebhook` del backend. */
export const EVENTOS_WEBHOOK = [
  { evento: "comprobante.aceptado", etiqueta: "Aceptado", detalle: "SUNAT aceptó el comprobante" },
  { evento: "comprobante.aceptado_con_obs", etiqueta: "Aceptado con observaciones", detalle: "SUNAT lo aceptó y dejó observaciones" },
  { evento: "comprobante.rechazado", etiqueta: "Rechazado", detalle: "SUNAT lo rechazó: hay que corregir y emitir otro" },
  { evento: "comprobante.error_envio", etiqueta: "Error de envío", detalle: "No se pudo enviar a SUNAT; khipu reintenta" },
  { evento: "comprobante.fuera_de_plazo", etiqueta: "Fuera de plazo", detalle: "Pasó el plazo de envío: hay que emitir de nuevo" },
  { evento: "comprobante.anulado", etiqueta: "Anulado", detalle: "Se dio de baja ante SUNAT" },
  { evento: "comprobante.descartado", etiqueta: "Descartado", detalle: "Un administrador dejó de intentar el envío" },
] as const;

const ETIQUETAS = new Map<string, string>([...EVENTOS_WEBHOOK.map((e) => [e.evento, e.etiqueta] as [string, string]), ["webhook.prueba", "Prueba"]]);

export function etiquetaDeEvento(evento: string): string {
  return ETIQUETAS.get(evento) ?? evento;
}

/** «Todos los eventos» si están los siete; si no, sus etiquetas. */
export function resumenDeEventos(eventos: string[]): string {
  if (EVENTOS_WEBHOOK.every((e) => eventos.includes(e.evento))) return "Todos los eventos";
  return eventos.map(etiquetaDeEvento).join(", ");
}

export const ETIQUETA_ESTADO_ENTREGA: Record<EstadoEntrega, string> = {
  PENDIENTE: "Pendiente",
  EXITOSA: "Entregada",
  FALLIDA: "Fallida",
};

/** Con cuántos intentos fallidos seguidos khipu apaga el endpoint; coincide con `EntregarWebhooksService.PAUSA_TRAS_FALLOS`. */
export const PAUSA_TRAS_FALLOS = 10;

/** Lo que se muestra del último intento de una entrega: el código HTTP o el error de red. */
export function resultadoDelIntento(ultimoHttp: number | null | undefined, ultimoError: string | null | undefined): string {
  if (ultimoHttp != null && ultimoError == null) return `HTTP ${ultimoHttp}`;
  if (ultimoError) return ultimoError;
  return "—";
}

/** Una URL https válida para un webhook; devuelve el mensaje si no lo es. Es la misma regla de forma que `UrlDeWebhook` (el backend decide además si apunta a una red interna). */
export function errorDeUrlWebhook(url: string): string | null {
  const limpia = url.trim();
  if (limpia === "") return "Escribe la URL que recibirá los eventos";
  if (limpia.length > 500) return "La URL no puede pasar de 500 caracteres";
  let u: URL;
  try {
    u = new URL(limpia);
  } catch {
    return "No es una URL válida; por ejemplo https://mi-sistema.pe/khipu";
  }
  if (u.protocol !== "https:") return "La URL debe empezar con https://";
  if (u.username !== "" || u.password !== "") return "La URL no puede llevar usuario ni contraseña; usa el secreto de firma";
  if (u.hash !== "") return "La URL no puede llevar fragmento (#)";
  return null;
}
