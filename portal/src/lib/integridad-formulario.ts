import { fechaValida } from "@/lib/cambio-de-plan";
import { diasEntre } from "@/lib/formato";
import { messages } from "@/lib/messages";

const t = messages.admin.integridad.errores;

/**
 * Cuántos días como mucho se verifican por vez. Cada comprobante firmado del rango se lee del almacenamiento (el XML y, si hay, el CDR): un rango de años por error
 * sería un barrido enorme sobre el almacenamiento de producción. El backend no lo limita (un operador puede llamarlo a mano); la pantalla y su BFF sí. El barrido de
 * todos los días corre aparte, sobre los últimos.
 */
export const MAX_DIAS_DE_INTEGRIDAD = 92;

export type ErroresDeRango = Partial<Record<"desde" | "hasta", string>>;

/**
 * Valida el rango antes de pedir la verificación: las dos fechas existen, `desde` ≤ `hasta` y no pasan de {@link MAX_DIAS_DE_INTEGRIDAD} días (los dos extremos cuentan).
 * Lo comparten el formulario y el BFF, que arma con estas fechas la URL del backend: por eso solo salen de acá fechas `YYYY-MM-DD` que existen.
 */
export function validarRango(desde: string, hasta: string): { errores: ErroresDeRango } | { desde: string; hasta: string } {
  const d = desde.trim();
  const h = hasta.trim();
  const errores: ErroresDeRango = {};
  if (!d) errores.desde = t.desdeRequerido;
  else if (!fechaValida(d)) errores.desde = t.fechaInvalida;
  if (!h) errores.hasta = t.hastaRequerido;
  else if (!fechaValida(h)) errores.hasta = t.fechaInvalida;
  if (!errores.desde && !errores.hasta) {
    if (h < d) errores.hasta = t.alReves;
    else if (diasEntre(d, h) + 1 > MAX_DIAS_DE_INTEGRIDAD) errores.hasta = t.demasiadoLargo.replace("{max}", String(MAX_DIAS_DE_INTEGRIDAD));
  }
  return Object.keys(errores).length > 0 ? { errores } : { desde: d, hasta: h };
}
