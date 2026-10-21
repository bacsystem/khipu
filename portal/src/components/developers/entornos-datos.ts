import type { Mensajes } from "@/i18n/mensajes";
import es from "@/messages/es.json";

/**
 * Los datos de la página «Entornos y límites» (#26), aparte de la página para que el buscador de la documentación indexe lo mismo que ella muestra: cada fila de las tablas es un
 * resultado de búsqueda. Una página de App Router no puede exportar nada más que su componente. Los textos están en `messages.developers.entornos` (#25).
 */

export function indiceEntornos(messages: Mensajes = es) {
  const t = messages.developers.entornos.indice;
  return [
    { id: "entornos", label: t.entornos },
    { id: "limites", label: t.limites },
    { id: "reintentos", label: t.reintentos },
    { id: "pendiente", label: t.pendiente },
  ];
}

/** Aspecto, cómo es en BETA y cómo en producción. */
export function entornos(messages: Mensajes = es): Array<[string, string, string]> {
  return messages.developers.entornos.filas.entornos as Array<[string, string, string]>;
}

/** Qué, el límite y qué pasa al pasarlo. */
export function limites(messages: Mensajes = es): Array<[string, string, string]> {
  return messages.developers.entornos.filas.limites as Array<[string, string, string]>;
}

/** Qué y cuánto. */
export function reintentos(messages: Mensajes = es): Array<[string, string]> {
  return messages.developers.entornos.filas.reintentos as Array<[string, string]>;
}
