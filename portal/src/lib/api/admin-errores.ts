import { POR_PAGINA_DEFECTO, porPaginaValido } from "@/lib/paginacion";
import { esUuid } from "@/lib/uuid";
import { backendFetch, backendFetchConHeaders } from "./client";
import { totalDesdeHeaders } from "./facturas";

/**
 * Los tres tipos de problema de la cola global de errores (#196). `ERROR_DE_FORMATO` es un rechazo por fault de SUNAT 1000–1999: terminal, no se reintenta. `FUERA_DE_PLAZO`
 * también es terminal. Solo `ERROR_DE_ENVIO` se puede reintentar o descartar.
 */
export const CLASES_DE_ERROR = ["ERROR_DE_ENVIO", "ERROR_DE_FORMATO", "FUERA_DE_PLAZO"] as const;
export type ClaseDeError = (typeof CLASES_DE_ERROR)[number];

/** El fault de SUNAT. La API omite el código en un fallo propio (`INFRA - …`) o un texto sin código. */
export type FaultDeSunat = { codigo?: string; mensaje?: string };

/**
 * Un comprobante de la cola, con la forma real del JSON. `nombre_archivo` es su identidad ante SUNAT (`RUC-tipo-serie-número`). La API omite lo que no aplica: sin cuenta
 * (una empresa de integración), sin fault, o sin reintento programado. Lo omitido nunca se lee como cero.
 */
export type ErrorDeEmision = {
  comprobante_id: string;
  empresa_id: string;
  ruc: string;
  razon_social: string;
  cuenta_id?: string;
  cuenta_nombre?: string;
  nombre_archivo: string;
  tipo: string;
  serie: string;
  numero: number;
  fecha_emision: string;
  estado: string;
  clase: ClaseDeError;
  intentos: number;
  fault?: FaultDeSunat;
  proximo_intento?: string;
  actualizado_en: string;
  /** Un administrador puede reintentarlo o descartarlo (solo un error de envío). */
  accionable: boolean;
};

export type PaginaDeErrores = { errores: ErrorDeEmision[]; total: number };

/** Cómo terminó un reintento. Que SUNAT vuelva a fallar no es un error: `estado` vuelve como `ERROR_ENVIO` y trae el `fault`. */
export type ResultadoDeReintento = { comprobante_id: string; estado: string; intentos: number; fault?: FaultDeSunat };

export type ResultadoDeDescarte = { comprobante_id: string; estado: string };

export type ParamsErrores = { clase?: ClaseDeError; empresa?: string; q?: string; pagina: number; porPagina: number };

/** Tope del texto de búsqueda: lo que llega por la URL no se manda sin acotar al backend. */
export const MAX_BUSQUEDA = 100;

const RUTA_PANTALLA = "/admin/errores";

/** Sanea lo que llega por la URL: una clase o una empresa que no existen se ignoran, el texto se recorta, y página y tamaño se acotan como en los demás listados. */
export function paramsErroresDesdeUrl(p: { clase?: string; empresa_id?: string; q?: string; pagina?: string; por_pagina?: string }): ParamsErrores {
  const pagina = Number(p.pagina);
  const q = p.q?.trim().slice(0, MAX_BUSQUEDA);
  return {
    clase: CLASES_DE_ERROR.find((c) => c === p.clase),
    empresa: p.empresa_id !== undefined && esUuid(p.empresa_id) ? p.empresa_id : undefined,
    q: q ? q : undefined,
    pagina: Number.isInteger(pagina) && pagina >= 1 ? pagina : 1,
    porPagina: porPaginaValido(p.por_pagina),
  };
}

/** Query string de la llamada al backend: los filtros solo si se pidieron; la página y el tamaño siempre. */
export function queryErrores(p: ParamsErrores): URLSearchParams {
  const qs = new URLSearchParams();
  if (p.clase) qs.set("clase", p.clase);
  if (p.empresa) qs.set("empresa_id", p.empresa);
  if (p.q) qs.set("q", p.q);
  qs.set("pagina", String(p.pagina));
  qs.set("por_pagina", String(p.porPagina));
  return qs;
}

/** URL de la pantalla con su estado (compartible): solo lo que se aparta del defecto, para que la ruta base quede limpia. */
export function hrefErrores(p: ParamsErrores): string {
  const qs = new URLSearchParams();
  if (p.clase) qs.set("clase", p.clase);
  if (p.empresa) qs.set("empresa_id", p.empresa);
  if (p.q) qs.set("q", p.q);
  if (p.pagina > 1) qs.set("pagina", String(p.pagina));
  if (p.porPagina !== POR_PAGINA_DEFECTO) qs.set("por_pagina", String(p.porPagina));
  const texto = qs.toString();
  return texto ? `${RUTA_PANTALLA}?${texto}` : RUTA_PANTALLA;
}

/** Si la página pedida pasa de la última (URL editada a mano, marcador viejo, o la cola se vació), la URL de la última; si no, `null`. */
export function hrefSiFueraDeRango(p: ParamsErrores, total: number): string | null {
  const ultima = Math.max(1, Math.ceil(total / p.porPagina));
  return p.pagina > ultima ? hrefErrores({ ...p, pagina: ultima }) : null;
}

/** Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. */
export async function listarErrores(access: string, params: ParamsErrores): Promise<PaginaDeErrores> {
  const { datos, headers } = await backendFetchConHeaders<ErrorDeEmision[]>(`/v1/admin/errores?${queryErrores(params)}`, { headers: { Authorization: `Bearer ${access}` } });
  return { errores: datos, total: totalDesdeHeaders(headers, datos.length) };
}

/**
 * Solo desde el servidor. `origen` es la IP del administrador ya resuelta por el BFF (`cabecerasDeOrigen`, #208) para que la bitácora registre la suya y no la del portal.
 * `id` va a la URL del backend: quien llama ya lo validó como UUID.
 */
export function reintentarEnvio(access: string, id: string, origen: Record<string, string> = {}) {
  return backendFetch<ResultadoDeReintento>(`/v1/admin/comprobantes/${id}/reintento`, { method: "POST", headers: { Authorization: `Bearer ${access}`, ...origen } });
}

export function descartarComprobante(access: string, id: string, motivo: string, origen: Record<string, string> = {}) {
  return backendFetch<ResultadoDeDescarte>(`/v1/admin/comprobantes/${id}/descarte`, { method: "POST", body: { motivo }, headers: { Authorization: `Bearer ${access}`, ...origen } });
}
