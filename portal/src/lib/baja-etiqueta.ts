import { interpolar, type Mensajes } from "@/i18n/mensajes";
import type { EstadoBaja } from "@/lib/api/facturas";
import es from "@/messages/es.json";

/** Un resumen de alta informa la boleta (o su nota, #294) en vez de anularla: lo que cambia es qué significa que SUNAT lo acepte (274-H1). */
const etiquetasAlta = (que: string, t: Mensajes["comprobantes"]["estadoBaja"]): Record<EstadoBaja, string> => ({
  ...t.estados,
  ACEPTADA: interpolar(t.altaAceptada, { que }),
  RECHAZADA: interpolar(t.altaRechazada, { que }),
});

/**
 * El aviso de un resumen de alta generado pasado el 7.º día. Lo comparten la ficha del cliente (`BajaEstado`) y la del backoffice (`BajasDeComprobante`): el 4443 es lo que la
 * norma prevé y e-beta no devolvió, y un cambio de redacción tiene que llegar a las dos (#362 H3).
 */
export const avisoAltaFueraDePlazo = (messages: Mensajes = es): string => messages.comprobantes.estadoBaja.avisoAltaFueraDePlazo;
export const AVISO_ALTA_FUERA_DE_PLAZO = avisoAltaFueraDePlazo();

/** Una baja rechazada por SUNAT con 2957: pasaron los 7 días desde la emisión, así que ya no se puede anular (se corrige con una nota de crédito). */
export function bajaVencida(b: { estado: EstadoBaja; condicion?: "ALTA" | "BAJA"; cdr?: { codigo: string } | null }): boolean {
  return b.condicion !== "ALTA" && b.estado === "RECHAZADA" && b.cdr?.codigo === "2957";
}

/**
 * El estado de una baja (RA) o de un resumen diario (RC) con las palabras del portal. Lo comparten la ficha del cliente (`BajaEstado`) y la del backoffice
 * (#362 H9), para que las dos digan lo mismo del mismo trámite. `que` es qué se informa en un alta: la «boleta» o una «nota» sobre boleta.
 */
export function etiquetaDeBaja(
  b: { estado: EstadoBaja; condicion?: "ALTA" | "BAJA"; cdr?: { codigo: string } | null },
  que: "boleta" | "nota",
  messages: Mensajes = es,
): string {
  const t = messages.comprobantes.estadoBaja;
  if (bajaVencida(b)) return t.vencida;
  return (b.condicion === "ALTA" ? etiquetasAlta(t.que[que], t) : t.estados)[b.estado];
}
