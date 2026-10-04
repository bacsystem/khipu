import { POR_PAGINA_DEFECTO, porPaginaValido } from "@/lib/paginacion";
import { bajasDesdeUrl, type VisibilidadDeBajasAdmin } from "./admin-baja";
import type { EstadoCuentaAdmin } from "./admin-suspension";
import { backendFetchConHeaders } from "./client";
import { totalDesdeHeaders } from "./facturas";

/**
 * Una cuenta del listado del backoffice (#180), con la forma real del JSON. La API omite los campos sin valor:
 * `telefono` falta en cuentas anteriores a ese dato y `ultimo_acceso` si la cuenta nunca inició sesión en el portal.
 */
export type CuentaAdmin = {
  id: string;
  nombre: string;
  email: string;
  telefono?: string;
  creada_en: string;
  empresas: number;
  /** Sesión más reciente de sus usuarios en el portal; el uso por API key no cuenta. */
  ultimo_acceso?: string;
  /** Una cuenta suspendida (#182) no entra al portal ni emite por API, y sigue en el listado para poder reactivarla. */
  estado: EstadoCuentaAdmin;
  /** Desde cuándo está suspendida; falta si no lo está. */
  suspendida_en?: string;
  /** Desde cuándo está dada de baja (#201); falta si está en servicio. */
  baja_en?: string;
};

export type PaginaCuentasAdmin = { datos: CuentaAdmin[]; total: number };

/** `bajas` ausente = las cuentas dadas de baja (#201) no salen; `INCLUIDAS` las mezcla y `SOLO` muestra únicamente esas. */
export type ParamsCuentas = { q?: string; bajas?: VisibilidadDeBajasAdmin; pagina: number; porPagina: number };

const RUTA_PANTALLA = "/admin/cuentas";

/** Sanea lo que llega por la URL: lo que el backend acotaría igual (página, tamaño) o no tiene sentido (búsqueda en blanco). */
export function paramsCuentasDesdeUrl(p: { q?: string; bajas?: string; pagina?: string; por_pagina?: string }): ParamsCuentas {
  const pagina = Number(p.pagina);
  const q = p.q?.trim();
  return {
    q: q ? q : undefined,
    bajas: bajasDesdeUrl(p.bajas),
    pagina: Number.isInteger(pagina) && pagina >= 1 ? pagina : 1,
    porPagina: porPaginaValido(p.por_pagina),
  };
}

/** Query string de la llamada al backend: la búsqueda solo si la hay; página y tamaño siempre. */
export function queryCuentas(p: ParamsCuentas): URLSearchParams {
  const qs = new URLSearchParams();
  if (p.q) qs.set("q", p.q);
  if (p.bajas) qs.set("bajas", p.bajas);
  qs.set("pagina", String(p.pagina));
  qs.set("por_pagina", String(p.porPagina));
  return qs;
}

/** URL de la pantalla con su estado (compartible): solo lo que se aparta del defecto, para que la ruta base quede limpia. */
export function hrefCuentas(p: ParamsCuentas): string {
  const qs = new URLSearchParams();
  if (p.q) qs.set("q", p.q);
  if (p.bajas) qs.set("bajas", p.bajas);
  if (p.pagina > 1) qs.set("pagina", String(p.pagina));
  if (p.porPagina !== POR_PAGINA_DEFECTO) qs.set("por_pagina", String(p.porPagina));
  const texto = qs.toString();
  return texto ? `${RUTA_PANTALLA}?${texto}` : RUTA_PANTALLA;
}

/**
 * Si la página pedida pasa de la última (URL editada a mano, marcador viejo), la URL de la última; si no, `null`. Sin esto el
 * backend devuelve una página vacía con el total intacto y la tabla dice «Mostrando 981–980 de 12» y «Todavía no hay cuentas».
 * Sin resultados la última es la primera (`max(1, …)`), así que una página > 1 de una búsqueda vacía vuelve a la 1.
 */
export function hrefSiFueraDeRango(p: ParamsCuentas, total: number): string | null {
  const ultima = Math.max(1, Math.ceil(total / p.porPagina));
  return p.pagina > ultima ? hrefCuentas({ ...p, pagina: ultima }) : null;
}

/** Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. */
export async function listarCuentasAdmin(access: string, params: ParamsCuentas): Promise<PaginaCuentasAdmin> {
  const { datos, headers } = await backendFetchConHeaders<CuentaAdmin[]>(`/v1/admin/cuentas?${queryCuentas(params)}`, {
    headers: { Authorization: `Bearer ${access}` },
  });
  return { datos, total: totalDesdeHeaders(headers, datos.length) };
}
