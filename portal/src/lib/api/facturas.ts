import { backendFetch, backendFetchConHeaders } from "./client";
import { tenantHeaders } from "./tenant";

export const ESTADOS_FINALES = ["ACEPTADO", "ACEPTADO_CON_OBS", "RECHAZADO", "ANULADO", "INVALIDO"] as const;

export const ETIQUETAS_TIPO: Record<string, string> = {
  "01": "Factura",
  "03": "Boleta",
  "07": "Nota de crédito",
  "08": "Nota de débito",
};

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

export type Receptor = {
  tipo_doc: string;
  num_doc: string;
  razon_social: string;
  direccion: string | null;
};

/** Descuento aplicado (catálogo 53): lo enviado, el monto resultante y el código SUNAT. */
export type DescuentoAplicado = { tipo: "PORCENTAJE" | "MONTO"; valor: number; monto: number; afecta_base_igv: boolean; codigo: string };

export type ItemComprobante = {
  codigo: string | null;
  descripcion: string;
  unidad: string;
  cantidad: number;
  precio_unitario: number;
  tipo_afectacion_igv: string;
  /** Valor de venta sin IGV neto de descuento que afecta la base; ausente en backends anteriores. */
  valor_venta?: number;
  igv?: number;
  /** Lo que paga el cliente por la línea (0 en gratuitas). */
  precio_venta?: number;
  gratuita?: boolean;
  descuento?: DescuentoAplicado | null;
  /** ISC de la línea (sistema del catálogo 08, tasa aplicada y monto). */
  isc?: { sistema: string; tasa: number; monto: number } | null;
  /** ICBPER de la línea (bolsas × monto vigente). */
  icbper?: number;
};

export const ETIQUETAS_TIPO_DOC: Record<string, string> = {
  "1": "DNI",
  "4": "Carné de extranjería",
  "6": "RUC",
  "7": "Pasaporte",
  "0": "Sin documento",
};

/** Catálogo 07 (afectación del IGV) con las etiquetas cortas que muestra el portal; los códigos gratuitos no se cobran. */
export const ETIQUETAS_AFECTACION: Record<string, string> = {
  "10": "Gravado · Op. onerosa",
  "11": "Gravado · Retiro por premio (gratuita)",
  "12": "Gravado · Retiro por donación (gratuita)",
  "13": "Gravado · Retiro (gratuita)",
  "14": "Gravado · Retiro por publicidad (gratuita)",
  "15": "Gravado · Bonificación (gratuita)",
  "16": "Gravado · Retiro a trabajadores (gratuita)",
  "20": "Exonerado · Op. onerosa",
  "21": "Exonerado · Transferencia gratuita",
  "30": "Inafecto · Op. onerosa",
  "31": "Inafecto · Retiro por bonificación (gratuita)",
  "32": "Inafecto · Retiro (gratuita)",
  "33": "Inafecto · Muestras médicas (gratuita)",
  "34": "Inafecto · Convenio colectivo (gratuita)",
  "35": "Inafecto · Retiro por premio (gratuita)",
  "36": "Inafecto · Retiro por publicidad (gratuita)",
  "37": "Inafecto · Transferencia gratuita",
};

/** Forma de pago (RS 193-2020): al contado, o al crédito con el neto pendiente y sus cuotas (`id` = Cuota001…). */
export type FormaPago = {
  tipo: "contado" | "credito";
  monto_pendiente: number | null;
  cuotas: Array<{ id: string; monto: number; vencimiento: string }>;
};

/** Detracción (SPOT), solo en operaciones 1001–1004; el monto se deposita en soles. */
export type Detraccion = {
  codigo_bien_servicio: string;
  descripcion: string;
  porcentaje: number;
  monto: number;
  cuenta_banco_nacion: string;
  medio_pago: string;
};

/** Retención del IGV informada (código 62): el cliente paga total − monto. */
export type RetencionIgv = { porcentaje: number; monto: number; neto_cobrar: number };
/** Percepción cobrada (51/52/53): el cliente paga total + monto. */
export type Percepcion = { regimen: string; descripcion: string; porcentaje: number; base: number; monto: number; total_con_percepcion: number };

/** Factura de anticipo regularizada: `monto` sin IGV reduce la base (código 04/05/06); `importe_pagado` (con IGV) se resta del total. */
export type Anticipo = {
  comprobante: string;
  serie: string;
  numero: number;
  monto: number;
  importe_pagado: number;
  afectacion: "gravado" | "exonerado" | "inafecto";
  codigo_sunat: string;
  fecha_pago: string | null;
};

export const FORMA_PAGO_CONTADO: FormaPago = { tipo: "contado", monto_pendiente: null, cuotas: [] };

export type Comprobante = {
  id: string;
  tipo: string;
  serie: string;
  numero: number;
  fecha_emision: string;
  moneda: string;
  tipo_operacion: string | null;
  receptor: Receptor | null;
  items: ItemComprobante[];
  estado_documento: EstadoDocumento;
  hash: string;
  nombre_archivo: string | null;
  intentos: number;
  ultimo_error: string | null;
  cdr: { codigo: string; descripcion: string; observaciones: string[] } | null;
  totales: {
    gravado: number;
    exonerado: number;
    inafecto: number;
    igv: number;
    total: number;
    total_valor_venta?: number;
    total_precio_venta?: number;
    total_descuentos?: number;
    total_anticipos?: number;
    gratuito?: number;
    igv_gratuitas?: number;
    isc?: number;
    icbper?: number;
    descuento_global?: DescuentoAplicado | null;
  };
  forma_pago: FormaPago;
  detraccion?: Detraccion | null;
  retencion_igv?: RetencionIgv | null;
  percepcion?: Percepcion | null;
  anticipos?: Anticipo[] | null;
  /** `cdr` solo cuando SUNAT emitió la constancia; un rechazo por fault tiene `cdr.codigo` pero no archivo. */
  enlaces: { xml: string; cdr?: string };
};

export function esEstadoFinal(estado: EstadoDocumento): boolean {
  return (ESTADOS_FINALES as readonly string[]).includes(estado);
}

// Un backend anterior a la exposición de receptor/items responde sin esos campos.
export function normalizarComprobante(c: Partial<Comprobante> & Pick<Comprobante, "id">): Comprobante {
  return {
    ...(c as Comprobante),
    tipo_operacion: c.tipo_operacion ?? null,
    receptor: c.receptor ?? null,
    items: c.items ?? [],
    nombre_archivo: c.nombre_archivo ?? null,
    forma_pago: c.forma_pago ?? FORMA_PAGO_CONTADO,
    cdr: c.cdr ? { ...c.cdr, observaciones: c.cdr.observaciones ?? [] } : null,
  };
}

/** Hay constancia descargable (ZIP/XML del CDR), no solo un código de respuesta. */
export function tieneConstanciaCdr(c: Pick<Comprobante, "enlaces">): boolean {
  return Boolean(c.enlaces?.cdr);
}

export const TOTAL_HEADER = "x-total-count";

export type PaginaComprobantes = { datos: Comprobante[]; total: number };

export function totalDesdeHeaders(headers: Headers, fallback: number): number {
  const total = Number(headers.get(TOTAL_HEADER));
  return Number.isFinite(total) && headers.has(TOTAL_HEADER) ? total : fallback;
}

export async function listarFacturas(
  access: string,
  empresaId: string,
  params: { estado?: EstadoDocumento; pagina?: number; porPagina?: number } = {},
): Promise<PaginaComprobantes> {
  const qs = new URLSearchParams();
  if (params.estado) qs.set("estado", params.estado);
  qs.set("pagina", String(params.pagina ?? 1));
  qs.set("por_pagina", String(params.porPagina ?? 10));
  const { datos, headers } = await backendFetchConHeaders<Comprobante[]>(`/v1/facturas?${qs}`, {
    headers: tenantHeaders(access, empresaId),
  });
  return { datos: datos.map(normalizarComprobante), total: totalDesdeHeaders(headers, datos.length) };
}

export async function obtenerFactura(access: string, empresaId: string, id: string) {
  const c = await backendFetch<Comprobante>(`/v1/facturas/${id}`, { headers: tenantHeaders(access, empresaId) });
  return normalizarComprobante(c);
}
