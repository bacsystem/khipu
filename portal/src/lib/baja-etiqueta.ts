import type { EstadoBaja } from "@/lib/api/facturas";

const ETIQUETAS: Record<EstadoBaja, string> = {
  GENERADA: "Generada, pendiente de envío",
  ENVIADA: "Enviada: SUNAT la está procesando",
  ERROR_ENVIO: "Error de envío: se reintentará",
  ACEPTADA: "Aceptada: comprobante anulado",
  RECHAZADA: "Rechazada por SUNAT",
  // #338: el RC caducó sin enviarse (código 05 de las gratuitas) y lo reemplazó otro; `ultimo_error` dice cuál.
  DESCARTADA: "Reemplazada por un resumen nuevo",
};

/** Un resumen de alta informa la boleta (o su nota, #294) en vez de anularla: lo que cambia es qué significa que SUNAT lo acepte (274-H1). */
const etiquetasAlta = (que: string): Record<EstadoBaja, string> => ({
  ...ETIQUETAS,
  ACEPTADA: `Aceptado: ${que} informada a SUNAT`,
  RECHAZADA: `Rechazado por SUNAT: la ${que} no quedó informada`,
});

/**
 * El aviso de un resumen de alta generado pasado el 7.º día. Lo comparten la ficha del cliente (`BajaEstado`) y la del backoffice (`BajasDeComprobante`): el 4443 es lo que la
 * norma prevé y e-beta no devolvió, y un cambio de redacción tiene que llegar a las dos (#362 H3).
 */
export const AVISO_ALTA_FUERA_DE_PLAZO =
  "Informada fuera de plazo: pasó el 7.º día desde la emisión. SUNAT no la rechaza, pero puede observarla con la 4443 «El comprobante fue informado fuera del plazo establecido» (en e-beta no la observó). Lo que cuenta es la respuesta de SUNAT del comprobante.";

/** #338: una baja cuyo RC caducó con los 7 días ya vencidos no se envía; khipu la cierra RECHAZADA con 2957. No hay CDR de SUNAT detrás. */
export function bajaVencida(b: { estado: EstadoBaja; condicion?: "ALTA" | "BAJA"; cdr?: { codigo: string } | null }): boolean {
  return b.condicion !== "ALTA" && b.estado === "RECHAZADA" && b.cdr?.codigo === "2957";
}

/**
 * El estado de una baja (RA) o de un resumen diario (RC) con las palabras del portal. Lo comparten la ficha del cliente (`BajaEstado`) y la del backoffice
 * (#362 H9), para que las dos digan lo mismo del mismo trámite. `que` es qué se informa en un alta: la «boleta» o una «nota» sobre boleta.
 */
export function etiquetaDeBaja(b: { estado: EstadoBaja; condicion?: "ALTA" | "BAJA"; cdr?: { codigo: string } | null }, que: "boleta" | "nota"): string {
  if (bajaVencida(b)) return "Vencida: pasaron los 7 días para anularla";
  return (b.condicion === "ALTA" ? etiquetasAlta(que) : ETIQUETAS)[b.estado];
}
