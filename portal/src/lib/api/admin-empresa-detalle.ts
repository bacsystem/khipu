import { esUuid } from "@/lib/uuid";
import { backendFetch } from "./client";
import type { EntornoAdmin, EstadoCertificadoAdmin } from "./admin-empresas";
import type { EstadoDocumento } from "./facturas";

/**
 * Detalle de una empresa del backoffice (#186), con la forma real del JSON. La API omite los campos sin valor (`non_null`): por eso son
 * opcionales. Solo lectura y sin secretos: del certificado y de la clave SOL solo se sabe si están; de las API keys, el prefijo; del logo,
 * si hay uno.
 */
export type EmpresaDetalleAdmin = {
  id: string;
  ruc: string;
  razon_social: string;
  nombre_comercial?: string;
  entorno: EntornoAdmin;
  creada_en: string;
  /** Ausente en las empresas de integración, que no tienen cuenta. */
  cuenta_id?: string;
  cuenta_nombre?: string;
  certificado: EstadoCertificadoAdmin;
  certificado_vigente_hasta?: string;
  certificado_dias_restantes?: number;
  tiene_credenciales_sol: boolean;
  /** Ausente si la empresa todavía no declaró su domicilio fiscal. */
  domicilio?: DomicilioEmpresa;
  cuenta_detracciones?: string;
  padron_tasa_especial_igv: boolean;
  pdf: PdfEmpresa;
  series: SerieEmpresa[];
  establecimientos: EstablecimientoEmpresa[];
  api_keys: ApiKeyEmpresa[];
  comprobantes: ComprobanteEmpresa[];
  eventos: EventoEmpresa[];
  outbox: OutboxEmpresa;
};

export type DomicilioEmpresa = {
  ubigeo: string;
  direccion: string;
  urbanizacion?: string;
  distrito?: string;
  provincia?: string;
  departamento?: string;
  codigo_establecimiento?: string;
};

export type PdfEmpresa = {
  plantilla: string;
  color_primario: string;
  tiene_logo: boolean;
  pie_de_pagina?: string;
  observaciones_por_defecto?: string;
};

export type SerieEmpresa = { tipo: string; codigo: string; ultimo_numero: number; activa: boolean; establecimiento: string };

export type EstablecimientoEmpresa = { codigo: string; nombre: string; domicilio: DomicilioEmpresa; activo: boolean };

export type ApiKeyEmpresa = { id: string; prefijo: string; activa: boolean; creada_en: string; revocada_en?: string };

export type CdrEmpresa = { codigo: string; descripcion: string; observaciones: string[] };

export type ComprobanteEmpresa = {
  id: string;
  tipo: string;
  serie: string;
  numero: number;
  fecha_emision: string;
  estado: EstadoDocumento;
  moneda: string;
  total: number;
  intentos: number;
  ultimo_error?: string;
  /** Ausente si SUNAT todavía no respondió. */
  cdr?: CdrEmpresa;
};

export type EventoEmpresa = { comprobante: string; estado_anterior?: string; estado_nuevo: string; detalle?: string; ocurrido_en: string };

export type TareaEmpresa = { agregado: string; agregado_id: string; accion: string; intentos: number; siguiente_intento: string; ultimo_error?: string };

export type OutboxEmpresa = { total: number; proximas: TareaEmpresa[] };

/** Lo que llega por la URL no se pega en la llamada al backend sin mirarlo: `../auth/me` o un espacio no son un id de empresa. */
export function esIdDeEmpresa(id: string): boolean {
  return esUuid(id);
}

export function hrefDetalleEmpresa(id: string): string {
  return `/admin/empresas/${id}`;
}

/** Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. */
export function obtenerEmpresaAdmin(access: string, id: string) {
  return backendFetch<EmpresaDetalleAdmin>(`/v1/admin/empresas/${id}`, { headers: { Authorization: `Bearer ${access}` } });
}
