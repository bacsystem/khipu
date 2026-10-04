import { POR_PAGINA_DEFECTO, porPaginaValido } from "@/lib/paginacion";
import type { EstadoCertificado } from "./admin-cuenta-detalle";
import { backendFetchConHeaders } from "./client";
import { totalDesdeHeaders } from "./facturas";

export const ENTORNOS = ["BETA", "PRODUCCION"] as const;
export type EntornoAdmin = (typeof ENTORNOS)[number];

/** El estado del certificado tal como lo calcula el backend (#185): la columna y el filtro comparten esa única regla. */
export const ESTADOS_CERTIFICADO = ["SIN_CERTIFICADO", "SIN_FECHA", "VIGENTE", "POR_VENCER", "VENCIDO"] as const;
export type EstadoCertificadoAdmin = (typeof ESTADOS_CERTIFICADO)[number];

/**
 * Una empresa del listado del backoffice (#185), con la forma real del JSON. La API omite los campos sin valor: la cuenta falta en
 * las empresas de integración, y la fecha y los días del certificado si no hay certificado o no se conoce su vigencia.
 */
export type EmpresaAdmin = {
  id: string;
  ruc: string;
  razon_social: string;
  cuenta_id?: string;
  cuenta_nombre?: string;
  entorno: EntornoAdmin;
  certificado: EstadoCertificadoAdmin;
  /** `YYYY-MM-DD`. */
  certificado_vigente_hasta?: string;
  /** Hasta la fecha de arriba, contando desde hoy (Lima); negativo si ya venció. */
  certificado_dias_restantes?: number;
  tiene_credenciales_sol: boolean;
  /** Series activas. */
  series: number;
  /** Documentos con fecha de emisión en el mes de hoy. */
  comprobantes_del_mes: number;
  /** `YYYY-MM-DD`; ausente si nunca emitió. */
  ultima_emision?: string;
};

export type PaginaEmpresasAdmin = { datos: EmpresaAdmin[]; total: number };

export type ParamsEmpresas = { entorno?: EntornoAdmin; certificado?: EstadoCertificadoAdmin; pagina: number; porPagina: number };

const RUTA_PANTALLA = "/admin/empresas";

function unoDe<T extends string>(opciones: readonly T[], valor: string | undefined): T | undefined {
  return opciones.find((o) => o === valor);
}

/**
 * Sanea lo que llega por la URL. Un filtro que el backend no conoce (o escrito en minúsculas) se descarta en vez de mandarlo: respondería 400
 * y el listado entero se vería roto por un parámetro de más.
 */
export function paramsEmpresasDesdeUrl(p: { entorno?: string; certificado?: string; pagina?: string; por_pagina?: string }): ParamsEmpresas {
  const pagina = Number(p.pagina);
  return {
    entorno: unoDe(ENTORNOS, p.entorno),
    certificado: unoDe(ESTADOS_CERTIFICADO, p.certificado),
    pagina: Number.isInteger(pagina) && pagina >= 1 ? pagina : 1,
    porPagina: porPaginaValido(p.por_pagina),
  };
}

/** Query string de la llamada al backend: los filtros solo si los hay; página y tamaño siempre. */
export function queryEmpresas(p: ParamsEmpresas): URLSearchParams {
  const qs = new URLSearchParams();
  if (p.entorno) qs.set("entorno", p.entorno);
  if (p.certificado) qs.set("certificado", p.certificado);
  qs.set("pagina", String(p.pagina));
  qs.set("por_pagina", String(p.porPagina));
  return qs;
}

/** URL de la pantalla con su estado (compartible): solo lo que se aparta del defecto, para que la ruta base quede limpia. */
export function hrefEmpresas(p: ParamsEmpresas): string {
  const qs = new URLSearchParams();
  if (p.entorno) qs.set("entorno", p.entorno);
  if (p.certificado) qs.set("certificado", p.certificado);
  if (p.pagina > 1) qs.set("pagina", String(p.pagina));
  if (p.porPagina !== POR_PAGINA_DEFECTO) qs.set("por_pagina", String(p.porPagina));
  const texto = qs.toString();
  return texto ? `${RUTA_PANTALLA}?${texto}` : RUTA_PANTALLA;
}

/**
 * Si la página pedida pasa de la última (URL editada a mano, marcador viejo), la URL de la última; si no, `null`. Sin esto el backend
 * devuelve una página vacía con el total intacto. Sin resultados la última es la primera, así que una página > 1 vuelve a la 1.
 */
export function hrefEmpresasSiFueraDeRango(p: ParamsEmpresas, total: number): string | null {
  const ultima = Math.max(1, Math.ceil(total / p.porPagina));
  return p.pagina > ultima ? hrefEmpresas({ ...p, pagina: ultima }) : null;
}

/**
 * El estado del certificado en la forma que muestra `CertificadoEtiqueta` (la misma del detalle de una cuenta). Si el backend dijera «por
 * vencer» sin fecha o sin días, no se inventa nada: se muestra como «sin fecha», no como «Vence el undefined».
 */
export function estadoCertificadoDeEmpresa(e: Pick<EmpresaAdmin, "certificado" | "certificado_vigente_hasta" | "certificado_dias_restantes">): EstadoCertificado {
  switch (e.certificado) {
    case "SIN_CERTIFICADO":
      return { tipo: "ninguno" };
    case "SIN_FECHA":
      return { tipo: "sin_fecha" };
    case "VIGENTE":
    case "POR_VENCER":
    case "VENCIDO": {
      if (!e.certificado_vigente_hasta || e.certificado_dias_restantes === undefined) return { tipo: "sin_fecha" };
      const tipo = e.certificado === "VIGENTE" ? "vigente" : e.certificado === "POR_VENCER" ? "por_vencer" : "vencido";
      return { tipo, dias: e.certificado_dias_restantes, hasta: e.certificado_vigente_hasta };
    }
  }
}

/** Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. */
export async function listarEmpresasAdmin(access: string, params: ParamsEmpresas): Promise<PaginaEmpresasAdmin> {
  const { datos, headers } = await backendFetchConHeaders<EmpresaAdmin[]>(`/v1/admin/empresas?${queryEmpresas(params)}`, {
    headers: { Authorization: `Bearer ${access}` },
  });
  return { datos, total: totalDesdeHeaders(headers, datos.length) };
}
