import type { Mensajes } from "@/i18n/mensajes";
import es from "@/messages/es.json";

/**
 * #358: el redondeo del importe total (`PayableRoundingAmount`) lo valida el dominio igual (3303): hasta 2 decimales y entre −1.00 y 1.00. El formulario lo pide como
 * texto para dejar escribir «-» o «0.» sin reescribirlo; esto dice qué número es y, si no vale, por qué. Vacío, o 0, es «sin redondeo».
 */
export function redondeoDe(texto: string, messages: Mensajes = es): { valor: number | null; error: string | null } {
  const m = messages.comprobantes.validacion.redondeo;
  const t = texto.trim();
  if (t === "") return { valor: null, error: null };
  if (!/^-?\d+(\.\d{1,2})?$|^-?\.\d{1,2}$/.test(t)) return { valor: null, error: m.formato };
  const valor = Number(t);
  if (Math.abs(valor) > 1) return { valor: null, error: m.tope };
  return { valor: valor === 0 ? null : valor, error: null };
}
