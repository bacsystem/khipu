import { diasEntre, hoyLima } from "@/lib/formato";
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
  | "ANULADO"
  | "FUERA_DE_PLAZO";

export type Receptor = {
  tipo_doc: string;
  num_doc: string;
  razon_social: string;
  direccion: string | null;
  /** País (ISO 3166-1, catálogo 04): obligatorio en exportaciones, ausente en el resto. */
  pais?: string | null;
};

/** Datos de una factura de exportación (0200–0208): Incoterm y, en servicios 0201/0208, país de uso. */
export type Exportacion = { incoterm: string | null; pais_uso: string | null };

/** Descuento aplicado (catálogo 53): lo enviado, el monto resultante y el código SUNAT. */
export type DescuentoAplicado = { tipo: "PORCENTAJE" | "MONTO"; valor: number; monto: number; afecta_base_igv: boolean; codigo: string };

/** Cargo aplicado: lo enviado, el monto resultante, si suma a la base del IGV, el motivo (`recargo_consumo` = 46) y el código SUNAT derivado (47/48 línea, 46/49/50 global). */
export type CargoAplicado = { tipo: "PORCENTAJE" | "MONTO"; valor: number; monto: number; afecta_base_igv: boolean; motivo?: string | null; codigo: string };

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
  /** Cargos de la línea (47 suma al valor de venta y paga IGV; 48 se cobra sin IGV). */
  cargos?: CargoAplicado[] | null;
  /** ISC de la línea (sistema del catálogo 08, tasa aplicada y monto). */
  isc?: { sistema: string; tasa: number; monto: number; base?: number; base_pvp?: number | null } | null;
  /** ICBPER de la línea (bolsas × monto vigente). */
  icbper?: number;
  /** Código de producto SUNAT (catálogo 25, UNSPSC) y GTIN, si el emisor los informó. */
  codigo_sunat?: string | null;
  gtin?: { tipo: string; codigo: string } | null;
  /** Detracción 1002: datos de la embarcación y la especie (catálogo 55, conceptos 3001–3006). */
  hidrobiologico?: Hidrobiologico | null;
  /** Detracción 1004: origen, destino, detalle del viaje y valores referenciales del transporte de carga. */
  transporte?: TransporteCarga | null;
};

export type Hidrobiologico = { matricula: string; nombre_embarcacion: string; especie: string; lugar_descarga: string; fecha_descarga: string; cantidad: number };

export type TransporteCarga = {
  origen: { ubigeo: string; direccion: string };
  destino: { ubigeo: string; direccion: string };
  detalle_viaje: string;
  valor_referencial: { servicio: number; carga_efectiva: number; carga_util_nominal: number };
  tramos?: Array<{
    origen_ubigeo?: string | null;
    destino_ubigeo?: string | null;
    descripcion?: string | null;
    valor_carga_efectiva?: number | null;
    valor_carga_util_nominal?: number | null;
    vehiculos?: Array<{ configuracion?: string | null; carga_util_tm?: number | null; carga_efectiva_tm?: number | null }>;
  }>;
};

export const ETIQUETAS_GUIA: Record<string, string> = { "09": "Guía de remisión remitente", "31": "Guía de remisión transportista" };

/** Catálogo 12 (tipos admitidos como "otro documento relacionado"). */
export const ETIQUETAS_DOC_RELACIONADO: Record<string, string> = {
  "04": "Ticket de salida ENAPU",
  "05": "Código SCOP",
  "06": "Factura electrónica remitente",
  "07": "Guía de remisión remitente",
  "08": "Declaración de salida del depósito franco",
  "09": "Declaración simplificada de importación",
  "99": "Otros",
};

export type NotaResumen = {
  id: string;
  tipo: "07" | "08";
  comprobante: string;
  fecha_emision: string;
  motivo: string;
  motivo_descripcion: string;
  estado_documento: EstadoDocumento;
  total: number;
};

export type EstadoBaja = "GENERADA" | "ENVIADA" | "ERROR_ENVIO" | "ACEPTADA" | "RECHAZADA";

/** Comunicación de baja (RA-yyyymmdd-N) de un comprobante. */
export type Baja = {
  id: string;
  identificador: string;
  comprobante: string;
  tipo_comprobante: string;
  fecha_generacion: string;
  motivo: string;
  estado: EstadoBaja;
  ticket: string | null;
  cdr: { codigo: string; descripcion: string; observaciones: string[] } | null;
  intentos: number;
  ultimo_error: string | null;
};

/** Plazo legal para la comunicación de baja: 7 días calendario desde la emisión (regla 2957). */
export const PLAZO_BAJA_DIAS = 7;

/**
 * Un comprobante aceptado (factura o nota), emitido hace 7 días o menos y sin baja en curso, puede darse de baja.
 * `hoy` es la fecha de Lima (la misma zona con la que el backend aplica la regla 2957), no la del servidor del portal.
 */
export function admiteBaja(c: Pick<Comprobante, "tipo" | "estado_documento" | "fecha_emision" | "baja">, hoy: string = hoyLima()): boolean {
  if (c.tipo === "03") return false;
  if (c.estado_documento !== "ACEPTADO" && c.estado_documento !== "ACEPTADO_CON_OBS") return false;
  if (c.baja && (c.baja.estado === "ENVIADA" || c.baja.estado === "GENERADA" || c.baja.estado === "ERROR_ENVIO")) return false;
  return diasEntre(c.fecha_emision, hoy) <= PLAZO_BAJA_DIAS;
}

/** Solo lo que SUNAT ya aceptó se envía al cliente por correo (PDF + XML + CDR). */
export function admiteCorreo(c: Pick<Comprobante, "estado_documento">): boolean {
  return c.estado_documento === "ACEPTADO" || c.estado_documento === "ACEPTADO_CON_OBS";
}

/** Una factura aceptada por SUNAT (con o sin observaciones) admite notas de crédito/débito. */
export function admiteNotas(c: Pick<Comprobante, "tipo" | "estado_documento">): boolean {
  return c.tipo === "01" && (c.estado_documento === "ACEPTADO" || c.estado_documento === "ACEPTADO_CON_OBS");
}

export const ETIQUETAS_TIPO_DOC: Record<string, string> = {
  "1": "DNI",
  "4": "Carné de extranjería",
  "6": "RUC",
  "7": "Pasaporte",
  "0": "Doc. tributario no domiciliado",
  A: "Cédula diplomática",
  B: "Doc. identidad país de residencia",
  C: "TIN (persona natural)",
  D: "IN (persona jurídica)",
  E: "Tarjeta Andina de Migración",
  G: "Salvoconducto",
};

/** Catálogo 07 (afectación del IGV) con las etiquetas cortas que muestra el portal; los códigos gratuitos no se cobran. */
export const ETIQUETAS_AFECTACION: Record<string, string> = {
  "10": "Gravado · Op. onerosa",
  "17": "Gravado · IVAP",
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
  "40": "Exportación",
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
  /** Fecha de vencimiento informada (cbc:DueDate), o ausente. */
  fecha_vencimiento?: string | null;
  /** Leyendas del catálogo 52 declaradas por el emisor (2001–2005, 2008…), con el texto que va al XML. */
  leyendas?: Array<{ codigo: string; texto: string }>;
  /** Solo en exportaciones (0200–0208). */
  exportacion?: Exportacion | null;
  /** Último día en que SUNAT acepta recibirlo (3 días calendario desde la emisión); ausente en backends anteriores. */
  fecha_limite_envio?: string;
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
    /** Tasa del IGV del comprobante en porcentaje (18.00 o la reducida del padrón de tasa especial); ausente en backends anteriores. */
    tasa_igv?: number;
    total: number;
    total_valor_venta?: number;
    total_precio_venta?: number;
    total_descuentos?: number;
    /** Cargos que no afectan la base del IGV (línea 48 + globales 46/50): ChargeTotalAmount. */
    total_cargos?: number;
    total_anticipos?: number;
    /** Redondeo del importe total (PayableRoundingAmount), entre −1 y 1. */
    redondeo?: number;
    gratuito?: number;
    igv_gratuitas?: number;
    /** IVAP (tributo 1016, 4 % en vez del IGV): solo en comprobantes con afectación 17. */
    ivap?: number;
    /** Valor de venta de exportación (tributo 9995, sin IGV): solo en facturas 0200–0208. */
    exportacion?: number;
    isc?: number;
    icbper?: number;
    descuento_global?: DescuentoAplicado | null;
    /** Cargos globales aplicados (49 afecta la base del IGV; 46/50 no). */
    cargos?: CargoAplicado[] | null;
  };
  forma_pago: FormaPago;
  detraccion?: Detraccion | null;
  retencion_igv?: RetencionIgv | null;
  percepcion?: Percepcion | null;
  anticipos?: Anticipo[] | null;
  /** Orden de compra, guías de remisión (catálogo 01: 09/31) y otros documentos (catálogo 12); ausente si no hay ninguno. */
  referencias?: {
    orden_compra?: string | null;
    guias?: Array<{ tipo: string; numero: string }> | null;
    documentos_relacionados?: Array<{ tipo: string; numero: string }> | null;
  } | null;
  /** Solo en notas de crédito/débito (tipo 07/08): factura que modifican y motivo (catálogo 09/10). */
  nota?: { tipo_afectado: string; documento_afectado: string; motivo: string; motivo_descripcion: string; descripcion: string } | null;
  /** Solo al consultar una factura: notas emitidas sobre ella, con su estado. */
  notas?: NotaResumen[] | null;
  /** Solo al consultar: la comunicación de baja más reciente (en curso, aceptada o rechazada). */
  baja?: Baja | null;
  /** Observaciones propias del comprobante, impresas en el PDF (no van al XML). */
  observaciones?: string | null;
  /** `cdr` solo cuando SUNAT emitió la constancia; un rechazo por fault tiene `cdr.codigo` pero no archivo. */
  enlaces: { xml: string; pdf?: string; cdr?: string };
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
