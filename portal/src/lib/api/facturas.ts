import { backendFetch } from "./client";
import { tenantHeaders } from "./tenant";

export const ESTADOS_FINALES = ["ACEPTADO", "ACEPTADO_CON_OBS", "RECHAZADO", "ANULADO", "INVALIDO"] as const;

export type EstadoDocumento =
  | "RECIBIDO"
  | "INVALIDO"
  | "FIRMADO"
  | "ERROR_ENVIO"
  | "PENDIENTE_AGRUPACION"
  | "ENVIADO"
  | "ACEPTADO"
  | "ACEPTADO_CON_OBS"
  | "RECHAZADO"
  | "ANULADO";

export type Comprobante = {
  id: string;
  tipo: string;
  serie: string;
  numero: number;
  fecha_emision: string;
  moneda: string;
  estado_documento: EstadoDocumento;
  hash: string;
  intentos: number;
  ultimo_error: string | null;
  cdr: { codigo: string; descripcion: string; observaciones: string[] } | null;
  totales: { gravado: number; exonerado: number; inafecto: number; igv: number; total: number };
  enlaces: { xml: string; cdr: string };
};

export function esEstadoFinal(estado: EstadoDocumento): boolean {
  return (ESTADOS_FINALES as readonly string[]).includes(estado);
}

export function listarFacturas(
  access: string,
  empresaId: string,
  params: { estado?: EstadoDocumento; pagina?: number; porPagina?: number } = {},
) {
  const qs = new URLSearchParams();
  if (params.estado) qs.set("estado", params.estado);
  qs.set("pagina", String(params.pagina ?? 1));
  qs.set("por_pagina", String(params.porPagina ?? 20));
  return backendFetch<Comprobante[]>(`/v1/facturas?${qs}`, { headers: tenantHeaders(access, empresaId) });
}

export function obtenerFactura(access: string, empresaId: string, id: string) {
  return backendFetch<Comprobante>(`/v1/facturas/${id}`, { headers: tenantHeaders(access, empresaId) });
}
