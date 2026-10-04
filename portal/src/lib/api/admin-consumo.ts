import { POR_PAGINA_DEFECTO, porPaginaValido } from "@/lib/paginacion";
import { apiBaseUrl, backendFetchConHeaders } from "./client";
import { totalDesdeHeaders } from "./facturas";
import { ApiError, type ApiEnvelope } from "./types";

/** Qué cuentas mostrar (#193): todas, las que ya llegaron al umbral de alerta de su tope, o las que dejaron de pagar (en gracia o ya vencidas). */
export const FILTROS_DE_CONSUMO = ["TODAS", "CERCA_DEL_LIMITE", "PLAN_VENCIDO"] as const;
export type FiltroDeConsumo = (typeof FILTROS_DE_CONSUMO)[number];

/** Cómo ordenarlas: por el porcentaje del tope usado (los planes sin tope al final) o por documentos consumidos. */
export const ORDENES_DE_CONSUMO = ["PORCENTAJE", "DOCUMENTOS"] as const;
export type OrdenDeConsumo = (typeof ORDENES_DE_CONSUMO)[number];

/** El estado de pago de la suscripción vigente de una cuenta: al día, vencida pero aún servida (gracia) o ya sin servicio. */
export type EstadoDelPlan = "VIGENTE" | "EN_GRACIA" | "VENCIDA";

/**
 * Una cuenta con lo que consumió en el mes contra el tope de su plan de hoy (#193), con la forma real del JSON. La API omite lo que no aplica: un plan sin tope no
 * trae `limite` ni `porcentaje`, y un plan que no vence no trae fechas. Lo omitido nunca se lee como cero.
 */
export type CuentaConsumo = {
  cuenta_id: string;
  nombre: string;
  email: string;
  plan_id: string;
  plan: string;
  documentos: number;
  limite?: number;
  porcentaje?: number;
  en_alerta: boolean;
  estado_del_plan: EstadoDelPlan;
  /** Hasta cuándo está pagado el plan. */
  pagado_hasta?: string;
  /** Hasta cuándo se la sirve, con la gracia incluida. */
  se_sirve_hasta?: string;
};

export type ConsumoDeCuentas = { mes: string; umbral_de_alerta: number; cuentas: CuentaConsumo[] };

export type PaginaConsumo = { datos: ConsumoDeCuentas; total: number };

/** `mes` ausente es el mes en curso (el backend lo resuelve en hora de Lima). */
export type ParamsConsumo = { mes?: string; filtro: FiltroDeConsumo; orden: OrdenDeConsumo; pagina: number; porPagina: number };

const RUTA_PANTALLA = "/admin/consumo";
const RUTA_EXPORTACION = "/api/admin/consumo/exportacion";
const MES = /^\d{4}-(0[1-9]|1[0-2])$/;

/** `AAAA-MM` y nada más: es lo único que el backend acepta como mes. */
export function mesValido(texto: string | undefined): string | undefined {
  return texto !== undefined && MES.test(texto) ? texto : undefined;
}

function enumDesdeUrl<T extends string>(valores: readonly T[], texto: string | undefined, defecto: T): T {
  return valores.find((v) => v === texto) ?? defecto;
}

/** Sanea lo que llega por la URL: un mes, filtro u orden raro es el valor por defecto, y página y tamaño se acotan como en los demás listados. */
export function paramsConsumoDesdeUrl(p: { mes?: string; filtro?: string; orden?: string; pagina?: string; por_pagina?: string }): ParamsConsumo {
  const pagina = Number(p.pagina);
  return {
    mes: mesValido(p.mes),
    filtro: enumDesdeUrl(FILTROS_DE_CONSUMO, p.filtro, "TODAS"),
    orden: enumDesdeUrl(ORDENES_DE_CONSUMO, p.orden, "PORCENTAJE"),
    pagina: Number.isInteger(pagina) && pagina >= 1 ? pagina : 1,
    porPagina: porPaginaValido(p.por_pagina),
  };
}

/** Lo que se exporta es lo que se ve, completo: mes, filtro y orden, sin página. */
export function queryExportacion(p: ParamsConsumo): URLSearchParams {
  const qs = new URLSearchParams();
  if (p.mes) qs.set("mes", p.mes);
  qs.set("filtro", p.filtro);
  qs.set("orden", p.orden);
  return qs;
}

/** Query string de la llamada al backend: el mes solo si se pidió; el resto siempre. */
export function queryConsumo(p: ParamsConsumo): URLSearchParams {
  const qs = queryExportacion(p);
  qs.set("pagina", String(p.pagina));
  qs.set("por_pagina", String(p.porPagina));
  return qs;
}

/** URL de la pantalla con su estado (compartible): solo lo que se aparta del defecto, para que la ruta base quede limpia. */
export function hrefConsumo(p: ParamsConsumo): string {
  const qs = new URLSearchParams();
  if (p.mes) qs.set("mes", p.mes);
  if (p.filtro !== "TODAS") qs.set("filtro", p.filtro);
  if (p.orden !== "PORCENTAJE") qs.set("orden", p.orden);
  if (p.pagina > 1) qs.set("pagina", String(p.pagina));
  if (p.porPagina !== POR_PAGINA_DEFECTO) qs.set("por_pagina", String(p.porPagina));
  const texto = qs.toString();
  return texto ? `${RUTA_PANTALLA}?${texto}` : RUTA_PANTALLA;
}

/** El enlace de descarga: pasa por el BFF del portal, que pone la sesión del administrador; el navegador nunca ve el JWT. */
export function hrefExportacionConsumo(p: ParamsConsumo): string {
  return `${RUTA_EXPORTACION}?${queryExportacion(p)}`;
}

/** Si la página pedida pasa de la última (URL editada a mano, marcador viejo), la URL de la última; si no, `null`. */
export function hrefSiFueraDeRango(p: ParamsConsumo, total: number): string | null {
  const ultima = Math.max(1, Math.ceil(total / p.porPagina));
  return p.pagina > ultima ? hrefConsumo({ ...p, pagina: ultima }) : null;
}

/** Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. */
export async function listarConsumoDeCuentas(access: string, params: ParamsConsumo): Promise<PaginaConsumo> {
  const { datos, headers } = await backendFetchConHeaders<ConsumoDeCuentas>(`/v1/admin/consumo?${queryConsumo(params)}`, {
    headers: { Authorization: `Bearer ${access}` },
  });
  return { datos, total: totalDesdeHeaders(headers, datos.cuentas.length) };
}

/**
 * Solo desde el servidor. El CSV vuelve como bytes y no como texto: `Response.text()` quita la marca de orden de bytes UTF-8 del principio, y sin ella Excel lee mal
 * las tildes. Un fallo llega como JSON y sube como {@link ApiError}, igual que en las demás llamadas.
 */
export async function exportarConsumoDeCuentas(access: string, params: ParamsConsumo): Promise<{ cuerpo: ArrayBuffer; disposicion: string | null }> {
  const res = await fetch(`${apiBaseUrl()}/v1/admin/consumo/exportacion?${queryExportacion(params)}`, {
    headers: { Authorization: `Bearer ${access}` },
    cache: "no-store",
  });
  const cuerpo = await res.arrayBuffer();
  if (!res.ok) {
    const json = (() => {
      try {
        return JSON.parse(new TextDecoder().decode(cuerpo)) as ApiEnvelope<null>;
      } catch {
        return null;
      }
    })();
    throw new ApiError(res.status, json?.codigo ?? null, json?.mensaje ?? "Error de comunicación con la API", json?.errores ?? null);
  }
  return { cuerpo, disposicion: res.headers.get("content-disposition") };
}
