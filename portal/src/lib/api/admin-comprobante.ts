import { esUuid } from "@/lib/uuid";
import { backendFetch } from "./client";
import type { EstadoBaja, EstadoDocumento } from "./facturas";

/**
 * La ficha de un comprobante en el backoffice (#251), con la forma real del JSON. La API omite los campos sin valor (`non_null`): por eso son opcionales.
 * Solo lectura y sin el contenido de los archivos: del XML y del CDR solo se sabe si están guardados.
 */
export type ComprobanteAdmin = {
  id: string;
  empresa_id: string;
  ruc: string;
  razon_social: string;
  /** Ausente en una empresa de integración, que no tiene cuenta. */
  cuenta_id?: string;
  /** RUC-tipo-serie-número: la identidad del comprobante ante SUNAT. */
  nombre_archivo: string;
  tipo: string;
  serie: string;
  numero?: number;
  fecha_emision: string;
  estado: EstadoDocumento;
  intentos: number;
  /** El último fallo de envío; ausente si no hubo. */
  ultimo_error?: string;
  /** Lo que respondió SUNAT en el CDR; ausente si todavía no respondió. */
  respuesta_sunat?: { codigo: string; descripcion?: string };
  tiene_xml: boolean;
  tiene_cdr: boolean;
  /** #362 H9: sus comunicaciones de baja (RA) y resúmenes diarios (RC), de la más reciente a la más antigua; vacía si no tiene. */
  bajas: BajaAdmin[];
};

/**
 * La baja o el resumen diario de un comprobante en la ficha del backoffice (#362). Lo que SUNAT todavía no dio (ticket, CDR, último error) llega ausente.
 * El `motivo` lo pidió quien anuló; en un RC (boleta o nota de boleta) no viaja a SUNAT, queda solo en khipu.
 */
export type BajaAdmin = {
  id: string;
  /** `RA-yyyymmdd-N` (comunicación de baja) o `RC-yyyymmdd-N` (resumen diario). */
  identificador: string;
  /** `BAJA` anula; `ALTA` informa en el resumen diario una boleta que pasó el envío individual. */
  condicion: "ALTA" | "BAJA";
  estado: EstadoBaja;
  ticket?: string;
  motivo: string;
  /** La fecha del identificador. */
  fecha_generacion: string;
  /** La emisión del comprobante tal como se comunicó a SUNAT. */
  fecha_referencia: string;
  /** Solo un `ALTA`: generada pasado el 7.º día. Es previsión de khipu; la que cuenta es la respuesta de SUNAT. */
  fuera_de_plazo: boolean;
  cdr?: { codigo: string; descripcion?: string };
  intentos: number;
  ultimo_error?: string;
};

/** Lo que llega por la URL no se pega en la llamada al backend sin mirarlo. */
export function esIdDeComprobante(id: string | undefined | null): id is string {
  return typeof id === "string" && esUuid(id);
}

export function hrefDetalleComprobante(id: string): string {
  return `/admin/comprobantes/${id}`;
}

/** Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. */
export function obtenerComprobanteAdmin(access: string, id: string) {
  return backendFetch<ComprobanteAdmin>(`/v1/admin/comprobantes/${id}`, { headers: { Authorization: `Bearer ${access}` } });
}
