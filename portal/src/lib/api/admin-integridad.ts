import { backendFetch } from "./client";

/** Qué se encontró mal con un comprobante firmado (#198): su XML falta o no es el que se firmó, su CDR falta, o no se pudo leer el almacenamiento. */
export const TIPOS_DE_PROBLEMA = ["XML_FALTANTE", "XML_CORRUPTO", "CDR_FALTANTE", "STORAGE_INACCESIBLE"] as const;
export type TipoDeProblema = (typeof TIPOS_DE_PROBLEMA)[number];

/**
 * Un problema de integridad, con la forma real del JSON. `nombre_archivo` es la identidad del comprobante (`RUC-tipo-serie-número`); `tenant_id` es su empresa. La API
 * omite `detalle` si no hay.
 */
export type ProblemaDeIntegridad = { comprobante_id: string; tenant_id: string; nombre_archivo: string; tipo: TipoDeProblema; detalle?: string };

/** El resultado de un barrido: cuántos comprobantes se verificaron y los problemas encontrados (una lista vacía, si no hubo). */
export type InformeDeIntegridad = { desde: string; hasta: string; verificados: number; problemas: ProblemaDeIntegridad[] };

/**
 * Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. `desde` y `hasta` van a la URL del backend: quien llama ya las validó como
 * `YYYY-MM-DD` que existen (`validarRango`); acá igual se codifican.
 */
export function verificarIntegridad(access: string, desde: string, hasta: string) {
  return backendFetch<InformeDeIntegridad>(`/v1/admin/integridad?desde=${encodeURIComponent(desde)}&hasta=${encodeURIComponent(hasta)}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${access}` },
  });
}
