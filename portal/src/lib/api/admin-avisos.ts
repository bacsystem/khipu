import { POR_PAGINA_DEFECTO, porPaginaValido } from "@/lib/paginacion";
import { backendFetch, backendFetchConHeaders } from "./client";
import { totalDesdeHeaders } from "./facturas";

/** Las dos listas de avisos del backoffice (#197): empresas con el certificado en riesgo y empresas cuyas credenciales SOL SUNAT no acepta. */
export const VISTAS_DE_AVISOS = ["CERTIFICADOS", "CREDENCIALES_SOL"] as const;
export type VistaDeAvisos = (typeof VISTAS_DE_AVISOS)[number];

/** Lo que se le avisa a un cliente: el backend decide el motivo exacto (por vencer o vencido) según la situación de la empresa. */
export type TipoDeAviso = "CERTIFICADO" | "CREDENCIALES_SOL";

export type MotivoDeCertificado = "CERTIFICADO_POR_VENCER" | "CERTIFICADO_VENCIDO";
export type MotivoDeAviso = MotivoDeCertificado | "CREDENCIALES_SOL_INVALIDAS";

/** A quién le llegaría el aviso: la cuenta dueña de la empresa. La API la omite en una empresa de integración (sin cuenta). */
export type CuentaDelAviso = { id: string; nombre: string; email: string };

/** El último aviso de **ese motivo** a la empresa. */
export type UltimoAviso = { enviado_en: string; destinatario: string };

/**
 * Una empresa con el certificado vencido o por vencer, con la forma real del JSON. `dias_restantes` es negativo si ya venció. `avisar_desde` solo viene si hay una espera
 * pendiente; `puede_avisar` ya incluye que haya a quién escribirle. Lo omitido nunca se lee como cero.
 */
export type CertificadoEnRiesgo = {
  empresa_id: string;
  ruc: string;
  razon_social: string;
  cuenta?: CuentaDelAviso;
  motivo: MotivoDeCertificado;
  vigente_hasta: string;
  dias_restantes: number;
  ultimo_aviso?: UltimoAviso;
  avisar_desde?: string;
  puede_avisar: boolean;
};

/** Una empresa con comprobantes atascados en error de envío porque SUNAT rechaza su usuario o clave SOL. */
export type SolFallando = {
  empresa_id: string;
  ruc: string;
  razon_social: string;
  cuenta?: CuentaDelAviso;
  comprobantes_afectados: number;
  ultimo_fallo: string;
  ultimo_error: string;
  ultimo_aviso?: UltimoAviso;
  avisar_desde?: string;
  puede_avisar: boolean;
};

export type AvisoEnviado = { empresa_id: string; motivo: MotivoDeAviso; destinatario: string; enviado_en: string; avisar_desde: string };

export type PaginaDeAvisos<T> = { filas: T[]; total: number };

export type ParamsAvisos = { vista: VistaDeAvisos; pagina: number; porPagina: number };

const RUTA_PANTALLA = "/admin/avisos";

/** Sanea lo que llega por la URL: una vista que no existe es la de certificados, y página y tamaño se acotan como en los demás listados. */
export function paramsAvisosDesdeUrl(p: { vista?: string; pagina?: string; por_pagina?: string }): ParamsAvisos {
  const pagina = Number(p.pagina);
  return {
    vista: VISTAS_DE_AVISOS.find((v) => v === p.vista) ?? "CERTIFICADOS",
    pagina: Number.isInteger(pagina) && pagina >= 1 ? pagina : 1,
    porPagina: porPaginaValido(p.por_pagina),
  };
}

/** Query string de la llamada al backend: la página y el tamaño siempre. */
export function queryAvisos(p: ParamsAvisos): URLSearchParams {
  const qs = new URLSearchParams();
  qs.set("pagina", String(p.pagina));
  qs.set("por_pagina", String(p.porPagina));
  return qs;
}

/** URL de la pantalla con su estado (compartible): solo lo que se aparta del defecto, para que la ruta base quede limpia. */
export function hrefAvisos(p: ParamsAvisos): string {
  const qs = new URLSearchParams();
  if (p.vista !== "CERTIFICADOS") qs.set("vista", p.vista);
  if (p.pagina > 1) qs.set("pagina", String(p.pagina));
  if (p.porPagina !== POR_PAGINA_DEFECTO) qs.set("por_pagina", String(p.porPagina));
  const texto = qs.toString();
  return texto ? `${RUTA_PANTALLA}?${texto}` : RUTA_PANTALLA;
}

/** Si la página pedida pasa de la última (URL editada a mano, o la lista se vació), la URL de la última; si no, `null`. */
export function hrefSiFueraDeRango(p: ParamsAvisos, total: number): string | null {
  const ultima = Math.max(1, Math.ceil(total / p.porPagina));
  return p.pagina > ultima ? hrefAvisos({ ...p, pagina: ultima }) : null;
}

/** Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. */
export async function listarCertificadosEnRiesgo(access: string, params: ParamsAvisos): Promise<PaginaDeAvisos<CertificadoEnRiesgo>> {
  const { datos, headers } = await backendFetchConHeaders<CertificadoEnRiesgo[]>(`/v1/admin/avisos/certificados?${queryAvisos(params)}`, { headers: { Authorization: `Bearer ${access}` } });
  return { filas: datos, total: totalDesdeHeaders(headers, datos.length) };
}

export async function listarCredencialesSolFallando(access: string, params: ParamsAvisos): Promise<PaginaDeAvisos<SolFallando>> {
  const { datos, headers } = await backendFetchConHeaders<SolFallando[]>(`/v1/admin/avisos/credenciales-sol?${queryAvisos(params)}`, { headers: { Authorization: `Bearer ${access}` } });
  return { filas: datos, total: totalDesdeHeaders(headers, datos.length) };
}

/**
 * Solo desde el servidor. `origen` es la IP del administrador ya resuelta por el BFF (`cabecerasDeOrigen`, #208) para que la bitácora registre la suya y no la del portal.
 * `empresaId` va a la URL del backend: quien llama ya lo validó como UUID.
 */
export function avisarAlCliente(access: string, empresaId: string, tipo: TipoDeAviso, origen: Record<string, string> = {}) {
  return backendFetch<AvisoEnviado>(`/v1/admin/empresas/${empresaId}/avisos`, { method: "POST", body: { tipo }, headers: { Authorization: `Bearer ${access}`, ...origen } });
}
