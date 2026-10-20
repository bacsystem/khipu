import { diasEntre, hoyLima } from "@/lib/formato";
import { backendFetch, backendFetchConHeaders } from "./client";
import { tenantHeaders } from "./tenant";

export const ESTADOS_FINALES = ["ACEPTADO", "ACEPTADO_CON_OBS", "RECHAZADO", "ANULADO", "INVALIDO"] as const;

export const ETIQUETAS_TIPO: Record<string, string> = {
  "01": "Factura",
  "03": "Boleta",
  "04": "Liquidación de compra",
  "07": "Nota de crédito",
  "08": "Nota de débito",
  "20": "Retención",
  "40": "Percepción",
};

/** #40: comprobante de retención (20) o de percepción (40), que solo emite una empresa designada agente por SUNAT. */
export function esDeAgente(c: { tipo: string }): boolean {
  return c.tipo === "20" || c.tipo === "40";
}

/**
 * #40: lo propio de una retención o una percepción (`agente` de la respuesta): régimen y tasa, cada documento pagado o cobrado con lo retenido o percibido y el neto (en
 * soles), y los totales. `totales.total` del comprobante es lo retenido o percibido.
 */
export type ComprobanteDeAgente = {
  regimen: string;
  descripcion_regimen: string;
  tasa: number;
  importe_total: number;
  redondeo: number;
  importe_neto: number;
  documentos: Array<{
    tipo: string;
    numero: string;
    fecha_emision: string;
    importe_total: number;
    moneda: string;
    pago: { numero: number; fecha: string; importe: number };
    tipo_cambio?: { factor: number; fecha: string } | null;
    importe_agente: number;
    neto: number;
  }>;
};

export const ESTADOS_DOCUMENTO = [
  "RECIBIDO",
  "INVALIDO",
  "FIRMADO",
  "ERROR_ENVIO",
  "PENDIENTE_AGRUPACION",
  "ENVIADO",
  "ACEPTADO",
  "ACEPTADO_CON_OBS",
  "RECHAZADO",
  "ANULADO",
  "FUERA_DE_PLAZO",
  "DESCARTADO",
] as const;

export type EstadoDocumento = (typeof ESTADOS_DOCUMENTO)[number];

export type Receptor = {
  tipo_doc: string;
  num_doc: string;
  razon_social: string;
  direccion: string | null;
  /** País (ISO 3166-1, catálogo 04): obligatorio en exportaciones, ausente en el resto. */
  pais?: string | null;
  /** #325: correo del cliente indicado al emitir (una nota hereda el de su comprobante); no va al XML. */
  email?: string | null;
};

/**
 * #325: el envío automático del comprobante a su cliente, si se anotó (la empresa lo tenía activado y el comprobante traía correo cuando SUNAT lo aceptó). `PENDIENTE` en cola o
 * esperando un reintento; `FALLIDO` agotó los reintentos o los adjuntos pesan demasiado; `OMITIDO` no correspondía mandarlo (sin correo en el servidor, comprobante anulado).
 */
export type EnvioAlCliente = {
  destinatario: string;
  estado: "PENDIENTE" | "ENVIADO" | "FALLIDO" | "OMITIDO";
  intentos: number;
  ultimo_error?: string | null;
  siguiente_intento?: string | null;
  enviado_en?: string | null;
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
  /** ISC de la línea (sistema del catálogo 08, tasa aplicada y monto). `monto_unitario` solo en el sistema 02 (monto fijo), `base_pvp` solo en el 03. */
  isc?: { sistema: string; tasa: number; monto: number; base?: number; base_pvp?: number | null; monto_unitario?: number | null } | null;
  /** ICBPER de la línea (bolsas × monto vigente). */
  icbper?: number;
  /** Código de producto SUNAT (catálogo 25, UNSPSC) y GTIN, si el emisor los informó. */
  codigo_sunat?: string | null;
  gtin?: { tipo: string; codigo: string } | null;
  /** Detracción 1002: datos de la embarcación y la especie (catálogo 55, conceptos 3001–3006). */
  hidrobiologico?: Hidrobiologico | null;
  /** Detracción 1004: origen, destino, detalle del viaje y valores referenciales del transporte de carga. */
  transporte?: TransporteCarga | null;
  /** Otros tributos (9999): la tasa pedida y el monto calculado sobre el valor de venta. Suman al precio de la línea y no a la base del IGV. */
  otros_tributos?: { tasa: number; monto: number } | null;
  /** Placa del vehículo (propiedad 7000 del catálogo 55). */
  placa?: string | null;
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
  /** Fecha de emisión del comprobante tal como se comunicó a SUNAT (cbc:ReferenceDate). */
  fecha_referencia?: string | null;
  motivo: string;
  /** `BAJA` anula el comprobante; `ALTA` informa en el resumen diario una boleta que pasó el envío individual (274-H1). Ausente en respuestas viejas: baja. */
  condicion?: "ALTA" | "BAJA";
  /**
   * #331: un `ALTA` generada pasado el 7.º día desde la emisión. SUNAT no la rechaza: la norma prevé que la observe con la 4443 (entonces la boleta queda `ACEPTADO_CON_OBS`); e-beta no lo hizo.
   * Es la previsión de khipu: si el envío del resumen se reintentó otro día, SUNAT puede observarla aunque diga `false` (manda el CDR). Ausente en respuestas
   * viejas: en plazo.
   */
  fuera_de_plazo?: boolean;
  estado: EstadoBaja;
  /** #301: posición de este comprobante en su resumen diario y cuántas líneas tiene. Las boletas de una misma fecha que se informan juntas comparten identificador, ticket y CDR. Ausentes en respuestas viejas: 1 de 1. */
  linea?: number;
  lineas?: number;
  ticket: string | null;
  cdr: { codigo: string; descripcion: string; observaciones: string[] } | null;
  intentos: number;
  ultimo_error: string | null;
};

/**
 * Plazo legal para la comunicación de baja: 7 días calendario desde la emisión (regla 2957), también para la boleta y sus notas. Es la regla de SUNAT
 * desde 2027-01-01 (RS 000048-2026); hasta 2026-12-31 la boleta lo cuenta desde la CDR, que nunca es anterior: contar desde la emisión cumple las dos (#302).
 */
export const PLAZO_BAJA_DIAS = 7;

/**
 * Un comprobante aceptado (factura, boleta o nota), emitido hace 7 días o menos y sin baja en curso, puede darse de baja: la boleta en el resumen
 * diario (RC, #20), los demás en una comunicación de baja (RA). `hoy` es la fecha de Lima (la misma zona con la que el backend aplica la regla 2957),
 * no la del servidor del portal.
 */
export function admiteBaja(c: Pick<Comprobante, "tipo" | "estado_documento" | "fecha_emision" | "baja">, hoy: string = hoyLima()): boolean {
  if (c.estado_documento !== "ACEPTADO" && c.estado_documento !== "ACEPTADO_CON_OBS") return false;
  if (bajaEnCurso(c)) return false;
  // #40: una retención o una percepción se revierte en un resumen de reversiones (RR); SUNAT no lo valida contra la emisión; la norma exige presentarlo dentro de 7 días calendario desde que se comunicó la reversión (khipu no conoce esa fecha).
  if (esDeAgente(c)) return true;
  return diasEntre(c.fecha_emision, hoy) <= PLAZO_BAJA_DIAS;
}

/** Una comunicación de baja generada, enviada o con error de envío sigue en curso: puede terminar anulando el comprobante. */
function bajaEnCurso(c: Pick<Comprobante, "baja">): boolean {
  return !!c.baja && (c.baja.estado === "ENVIADA" || c.baja.estado === "GENERADA" || c.baja.estado === "ERROR_ENVIO");
}

/** Solo lo que SUNAT ya aceptó se envía al cliente por correo (PDF + XML + CDR). */
export function admiteCorreo(c: Pick<Comprobante, "estado_documento">): boolean {
  return c.estado_documento === "ACEPTADO" || c.estado_documento === "ACEPTADO_CON_OBS";
}

/**
 * Una factura o una boleta aceptada por SUNAT (con o sin observaciones; la boleta, también la informada en el resumen diario)
 * admite notas de crédito/débito (#293), salvo que tenga una baja en curso: si SUNAT la acepta, el documento queda anulado y
 * la nota, sobre un documento anulado (2120).
 */
export function admiteNotas(c: Pick<Comprobante, "tipo" | "estado_documento" | "baja">): boolean {
  return (c.tipo === "01" || c.tipo === "03") && (c.estado_documento === "ACEPTADO" || c.estado_documento === "ACEPTADO_CON_OBS") && !bajaEnCurso(c);
}

/**
 * Una boleta o una nota sobre boleta (#293): sigue el régimen de la boleta, así que su baja va en el resumen diario (RC), nunca en
 * una comunicación de baja (RA, 2310). Es el `Comprobante.esDeBoleta()` del backend.
 */
export function esDeBoleta(c: { tipo: string; nota?: { tipo_afectado: string } | null }): boolean {
  return c.tipo === "03" || c.nota?.tipo_afectado === "03";
}

/**
 * #331: una boleta, o una nota sobre boleta que va en el resumen diario, todavía sin llegar a SUNAT y pasado su plazo (el 7.º día). No vence: al enviarla
 * khipu la informa en un resumen diario fuera de plazo; SUNAT no la rechaza y puede observarla con la 4443. La NC 10 y la ND 03 de boleta no van en el resumen (guía,
 * p. 19): esas sí vencen. Es el `Comprobante.informaFueraDePlazo` del backend; `hoy` es la fecha de Lima.
 */
export function seInformaFueraDePlazo(
  c: Pick<Comprobante, "tipo" | "estado_documento" | "fecha_limite_envio"> & { nota?: { tipo_afectado: string; motivo: string } | null },
  hoy: string = hoyLima(),
): boolean {
  if (c.estado_documento !== "FIRMADO" && c.estado_documento !== "ERROR_ENVIO") return false;
  if (!esDeBoleta(c) || !c.fecha_limite_envio) return false;
  const sinResumen = (c.tipo === "07" && c.nota?.motivo === "10") || (c.tipo === "08" && c.nota?.motivo === "03");
  return !sinResumen && diasEntre(c.fecha_limite_envio, hoy) > 0;
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
  // Una boleta de hasta S/ 700 sin comprador identificado (#20): no es del catálogo 06.
  "-": "Sin documento",
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
  /** Soles por unidad de `moneda` si el emisor lo informó (nulo en soles); SUNAT no lo recibe. */
  tipo_cambio?: number | null;
  /** Importe total en soles al `tipo_cambio`; nulo si no hay tipo de cambio. */
  total_en_soles?: number | null;
  receptor: Receptor | null;
  items: ItemComprobante[];
  estado_documento: EstadoDocumento;
  hash: string;
  nombre_archivo: string | null;
  intentos: number;
  ultimo_error: string | null;
  /**
   * `resumen` (#362 B10): solo en una boleta (o nota de boleta) informada por resumen diario. SUNAT no emite un CDR por boleta en el resumen: el del comprobante es el
   * del resumen, y esto dice cuál (`RC-aaaammdd-n`). El nombre del archivo descargado no cambia.
   */
  cdr: { codigo: string; descripcion: string; observaciones: string[]; resumen?: string } | null;
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
    /** Valor de venta de exportación (tributo 9995, sin IGV): solo en comprobantes 0200–0208 (factura o boleta). */
    exportacion?: number;
    isc?: number;
    icbper?: number;
    /** Total de otros tributos (9999): suma al precio de venta y no entra en la base del IGV. */
    otros_tributos?: number;
    descuento_global?: DescuentoAplicado | null;
    /** Cargos globales aplicados (49 afecta la base del IGV; 46/50 no). */
    cargos?: CargoAplicado[] | null;
  };
  forma_pago: FormaPago;
  detraccion?: Detraccion | null;
  retencion_igv?: RetencionIgv | null;
  /** Solo en una retención (20) o una percepción (40), #40. */
  agente?: ComprobanteDeAgente | null;
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
  /** Historial de intentos y cambios de estado (#7), del más antiguo al más reciente; solo al consultar por id y ausente en backends anteriores. */
  eventos?: EventoComprobante[] | null;
  /** Solo al consultar: la comunicación de baja más reciente (en curso, aceptada o rechazada). */
  baja?: Baja | null;
  /** Observaciones propias del comprobante, impresas en el PDF (no van al XML). */
  observaciones?: string | null;
  /** Solo al consultar (#325): si el comprobante salió por correo a su cliente al aceptarse; ausente si no se anotó. */
  envio_al_cliente?: EnvioAlCliente | null;
  /** `cdr` solo cuando SUNAT emitió la constancia; un rechazo por fault tiene `cdr.codigo` pero no archivo. */
  enlaces: { xml: string; pdf?: string; cdr?: string };
};

export function esEstadoFinal(estado: EstadoDocumento): boolean {
  return (ESTADOS_FINALES as readonly string[]).includes(estado);
}

export type EventoComprobante = { fecha: string; estado_anterior: EstadoDocumento | null; estado_resultante: EstadoDocumento; mensaje: string | null };

// Un backend anterior a la exposición de receptor/items responde sin esos campos.
export function normalizarComprobante(c: Partial<Comprobante> & Pick<Comprobante, "id">): Comprobante {
  return {
    ...(c as Comprobante),
    eventos: c.eventos ?? null,
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

/**
 * Filtros del listado (#6, #326): estado SUNAT, rango de fecha de emisión (inclusive), serie exacta, tipo (01 factura, 03 boleta, 07 y 08 notas, 20 retención y 40 percepción), número y cliente (documento o
 * parte del nombre); todos opcionales y viajan en la URL.
 */
export type FiltrosComprobantes = { estado?: EstadoDocumento; desde?: string; hasta?: string; serie?: string; tipo?: string; cliente?: string; numero?: number };

export const TIPOS_FILTRABLES = ["01", "03", "04", "07", "08", "20", "40"] as const;
const MAX_CLIENTE = 100;

const FECHA_ISO = /^\d{4}-\d{2}-\d{2}$/;
const SERIE = /^[A-Z][A-Z0-9]{3}$/;

/** Sanea los filtros que llegan por query string: descarta lo que el backend rechazaría (400) y normaliza la serie. */
export function filtrosDesdeParams(p: { estado?: string; desde?: string; hasta?: string; serie?: string; tipo?: string; cliente?: string; numero?: string }): FiltrosComprobantes {
  const f: FiltrosComprobantes = {};
  if (p.estado && (ESTADOS_DOCUMENTO as readonly string[]).includes(p.estado)) f.estado = p.estado as EstadoDocumento;
  if (p.desde && FECHA_ISO.test(p.desde)) f.desde = p.desde;
  if (p.hasta && FECHA_ISO.test(p.hasta)) f.hasta = p.hasta;
  if (f.desde && f.hasta && f.desde > f.hasta) delete f.hasta;
  const serie = p.serie?.trim().toUpperCase();
  if (serie && SERIE.test(serie)) f.serie = serie;
  if (p.tipo && (TIPOS_FILTRABLES as readonly string[]).includes(p.tipo)) f.tipo = p.tipo;
  const cliente = p.cliente?.trim();
  if (cliente && cliente.length <= MAX_CLIENTE) f.cliente = cliente;
  if (p.numero && /^\d{1,12}$/.test(p.numero.trim()) && Number(p.numero) >= 1) f.numero = Number(p.numero);
  return f;
}

/** Los filtros como query string, en el orden en que la API los documenta; vacío si no hay ninguno. */
export function paramsDeFiltros(f: FiltrosComprobantes, qs = new URLSearchParams()) {
  if (f.estado) qs.set("estado", f.estado);
  if (f.desde) qs.set("desde", f.desde);
  if (f.hasta) qs.set("hasta", f.hasta);
  if (f.serie) qs.set("serie", f.serie);
  if (f.tipo) qs.set("tipo", f.tipo);
  if (f.cliente) qs.set("cliente", f.cliente);
  if (f.numero) qs.set("numero", String(f.numero));
  return qs;
}

export async function listarFacturas(
  access: string,
  empresaId: string,
  params: FiltrosComprobantes & { pagina?: number; porPagina?: number } = {},
): Promise<PaginaComprobantes> {
  const qs = paramsDeFiltros(params);
  qs.set("pagina", String(params.pagina ?? 1));
  qs.set("por_pagina", String(params.porPagina ?? 10));
  const { datos, headers } = await backendFetchConHeaders<Comprobante[]>(`/v1/facturas?${qs}`, {
    headers: tenantHeaders(access, empresaId),
  });
  return { datos: datos.map(normalizarComprobante), total: totalDesdeHeaders(headers, datos.length) };
}

/**
 * El resumen de los comprobantes de la empresa en un período (#15), con la forma real del JSON. `desde`/`hasta` faltan si ese lado del rango estaba abierto. `facturado`
 * va por moneda y vacío si no hay nada; es el neto: las notas de crédito restan.
 */
export type ResumenDeFacturas = {
  desde?: string;
  hasta?: string;
  /** Comprobantes firmados del período, en cualquier estado. */
  emitidos: number;
  /** Aceptados por SUNAT, con o sin observaciones: los que ya tienen su CDR. */
  aceptados_con_cdr: number;
  /**
   * Lo que pide que el emisor haga algo. `boletas_sin_enviar` (#356): boletas, o notas que van en el resumen diario, que quedaron FIRMADAS porque se emitieron sin enviarlas y
   * ya están en el 5.º día o más.
   */
  atencion_requerida: { total: number; rechazados: number; errores_de_envio: number; fuera_de_plazo: number; boletas_sin_enviar: number };
  facturado: Array<{ moneda: string; total: number }>;
};

/** El período que resumen las métricas: el rango de fechas que se está mirando o, si no hay ninguno, el mes en curso en Lima hasta hoy. */
export type PeriodoDelResumen = { desde?: string; hasta?: string; esElMesEnCurso: boolean };

/**
 * Qué período resume la franja de métricas de la página de comprobantes: el de los filtros de fecha, y sin ellos el mes en curso (de su día 1 a hoy, en Lima) y no todo el
 * historial: es «una foto del mes». Solo importan `desde` y `hasta`; los demás filtros (estado, serie) no acotan el resumen.
 */
export function periodoDelResumen(filtros: Pick<FiltrosComprobantes, "desde" | "hasta">, hoy: string): PeriodoDelResumen {
  if (filtros.desde || filtros.hasta) return { desde: filtros.desde, hasta: filtros.hasta, esElMesEnCurso: false };
  return { desde: `${hoy.slice(0, 7)}-01`, hasta: hoy, esElMesEnCurso: true };
}

/** Solo desde el servidor: usa el JWT del usuario y la empresa activa, que el navegador no manda a mano. */
export function resumirFacturas(access: string, empresaId: string, periodo: Pick<PeriodoDelResumen, "desde" | "hasta">) {
  const qs = new URLSearchParams();
  if (periodo.desde) qs.set("desde", periodo.desde);
  if (periodo.hasta) qs.set("hasta", periodo.hasta);
  const consulta = qs.toString();
  return backendFetch<ResumenDeFacturas>(`/v1/facturas/resumen${consulta ? `?${consulta}` : ""}`, { headers: tenantHeaders(access, empresaId) });
}

export async function obtenerFactura(access: string, empresaId: string, id: string) {
  const c = await backendFetch<Comprobante>(`/v1/facturas/${id}`, { headers: tenantHeaders(access, empresaId) });
  return normalizarComprobante(c);
}
