import type { FaultDeSunat, ResultadoDeReintento } from "@/lib/api/admin-errores";
import { messages } from "@/lib/messages";

const r = messages.admin.errores.resultado;

/** El fault en una línea: «0109 - El sistema no puede responder», o solo el mensaje si no hay código. Sin fault, vacío. */
export function faultEnPalabras(fault: FaultDeSunat | undefined): string {
  if (!fault) return "";
  if (fault.codigo && fault.mensaje) return `${fault.codigo} - ${fault.mensaje}`;
  return fault.mensaje ?? fault.codigo ?? "";
}

/**
 * Qué le pasó a un comprobante al reintentar su envío, dicho para el administrador. Que SUNAT vuelva a fallar no es un error de la acción: el comprobante sigue en la cola y
 * el mensaje dice por qué (con el intento en que va).
 */
export function mensajeDeReintento(res: ResultadoDeReintento, comprobante: string, etiquetaDelEstado: (estado: string) => string): string {
  switch (res.estado) {
    case "ACEPTADO":
    case "ACEPTADO_CON_OBS":
      return r.aceptado.replace("{comprobante}", comprobante);
    case "RECHAZADO":
      return r.rechazado.replace("{comprobante}", comprobante).replace("{codigo}", res.fault?.codigo ? ` (${res.fault.codigo})` : "");
    case "ERROR_ENVIO": {
      const detalle = faultEnPalabras(res.fault);
      return r.vuelveAFallar.replace("{comprobante}", comprobante).replace("{n}", String(res.intentos)).replace("{detalle}", detalle ? ` ${detalle}` : "");
    }
    default:
      return r.otro.replace("{comprobante}", comprobante).replace("{estado}", etiquetaDelEstado(res.estado));
  }
}

export function mensajeDeDescarte(comprobante: string): string {
  return r.descartado.replace("{comprobante}", comprobante);
}
