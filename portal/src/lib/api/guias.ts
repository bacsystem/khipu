import { backendFetch, backendFetchConHeaders } from "./client";
import { totalDesdeHeaders } from "./facturas";
import { tenantHeaders } from "./tenant";

/**
 * Guías de remisión electrónicas (#39), con la forma real del JSON de `/v1/guias-remision`: snake_case, y la API omite las claves nulas. La remitente (09) lleva
 * `motivo` y `modalidad` y, en transporte público, `transportista`; la transportista (31) lleva `remitente` y `registro_mtc`. Los bloques que no son de su tipo no vienen.
 *
 * **EXPERIMENTAL en el servidor**: el contrato con la API REST de SUNAT sale de su manual y no se probó contra SUNAT; el envío viene apagado (`422 GRE_NO_DISPONIBLE`)
 * hasta que el operador lo encienda, y SUNAT no publica una API de pruebas para la GRE. Mientras tanto se emite sin enviar para revisar el XML y el PDF.
 */

/** `RECIBIDO` solo existe en `POST /v1/guias-remision/validar`, que no guarda nada: ninguna guía guardada lo tiene. */
export const ESTADOS_DE_GUIA = ["FIRMADO", "ERROR_ENVIO", "ENVIANDO", "ENVIADO_PENDIENTE_TICKET", "ACEPTADO", "ACEPTADO_CON_OBS", "RECHAZADO"] as const;
export type EstadoDeGuia = (typeof ESTADOS_DE_GUIA)[number] | "RECIBIDO";

export const TIPOS_DE_GUIA = ["09", "31"] as const;
export type TipoDeGuia = (typeof TIPOS_DE_GUIA)[number];

export type ParteDeGuia = { tipo_doc: string; num_doc: string; nombre: string };
export type TransportistaDeGuia = { ruc: string; razon_social: string; registro_mtc?: string };
export type PuntoDeGuia = { ubigeo?: string; direccion?: string; establecimiento?: string; ruc?: string };
export type VehiculoDeGuia = { placa: string; principal: boolean };
export type ConductorDeGuia = { tipo_doc: string; num_doc: string; nombres: string; apellidos: string; licencia: string; principal: boolean };
export type TrasladoDeGuia = {
  fecha_inicio: string;
  peso_bruto: number;
  unidad_peso: string;
  partida: PuntoDeGuia;
  llegada: PuntoDeGuia;
  vehiculos: VehiculoDeGuia[];
  conductores: ConductorDeGuia[];
  indicadores: string[];
};
export type ItemDeGuia = { codigo?: string; descripcion: string; unidad: string; cantidad: number; codigo_sunat?: string };
export type DocumentoDeGuia = { tipo: string; descripcion?: string; numero: string; ruc_emisor?: string };
export type CdrDeGuia = { codigo: string; descripcion: string; observaciones?: string[] };
export type EnlacesDeGuia = { xml: string; pdf: string; cdr?: string };

export type Guia = {
  id: string;
  tipo: TipoDeGuia;
  serie: string;
  numero: number;
  nombre_archivo: string;
  fecha_emision: string;
  hora_emision?: string;
  estado: EstadoDeGuia;
  destinatario: ParteDeGuia;
  motivo?: string;
  descripcion_motivo?: string;
  modalidad?: string;
  transportista?: TransportistaDeGuia;
  remitente?: ParteDeGuia;
  registro_mtc?: string;
  traslado: TrasladoDeGuia;
  items: ItemDeGuia[];
  documentos_relacionados: DocumentoDeGuia[];
  observaciones?: string;
  hash?: string;
  ticket?: string;
  fecha_recepcion?: string;
  intentos: number;
  consultas_ticket: number;
  reintento_automatico?: boolean;
  ultimo_error?: string;
  cdr?: CdrDeGuia;
  enlace_consulta?: string;
  enlaces?: EnlacesDeGuia;
};

/** El número de la guía como lo escribe SUNAT: serie, guion y correlativo (hasta 8 dígitos). */
export const numeroDeGuia = (g: Pick<Guia, "serie" | "numero">) => `${g.serie}-${String(g.numero).padStart(8, "0")}`;

/** Qué puede hacer el usuario con una guía según su estado (`POST /enviar` y `POST /ticket` responden `409` en cualquier otro). */
export const puedeEnviarGuia = (g: Pick<Guia, "estado">) => g.estado === "FIRMADO" || g.estado === "ERROR_ENVIO";
export const puedeConsultarTicketDeGuia = (g: Pick<Guia, "estado">) => g.estado === "ENVIADO_PENDIENTE_TICKET";
/** Un envío que quedó en curso sin respuesta: el backend responde `409 ENVIO_EN_CURSO` y solo se retoma, tras revisar en SOL, con `forzar=true`. */
export const estaEnviandoGuia = (g: Pick<Guia, "estado">) => g.estado === "ENVIANDO";
/** La guía ya tiene la respuesta definitiva de SUNAT. */
export const esGuiaResuelta = (g: Pick<Guia, "estado">) => g.estado === "ACEPTADO" || g.estado === "ACEPTADO_CON_OBS" || g.estado === "RECHAZADO";

export type FiltrosDeGuias = { estado?: string; tipo?: string; serie?: string; desde?: string; hasta?: string };
export type PaginaDeGuias = { datos: Guia[]; total: number };

const FECHA = /^\d{4}-\d{2}-\d{2}$/;
const SERIE_DE_GUIA = /^[TV][A-Z0-9]{3}$/;

/**
 * Los filtros de la URL, con solo lo que la API acepta: un valor que no es un estado, un tipo, una serie T###/V### o una fecha `YYYY-MM-DD` se descarta en vez de
 * viajar a la API (que respondería `400`) y dejar la lista vacía por un enlace mal armado.
 */
export function filtrosDeGuiasDesdeParams(raw: Record<string, string | undefined>): FiltrosDeGuias {
  const filtros: FiltrosDeGuias = {};
  if (raw.estado && (ESTADOS_DE_GUIA as readonly string[]).includes(raw.estado)) filtros.estado = raw.estado;
  if (raw.tipo && (TIPOS_DE_GUIA as readonly string[]).includes(raw.tipo)) filtros.tipo = raw.tipo;
  const serie = raw.serie?.trim().toUpperCase();
  if (serie && SERIE_DE_GUIA.test(serie)) filtros.serie = serie;
  if (raw.desde && FECHA.test(raw.desde)) filtros.desde = raw.desde;
  if (raw.hasta && FECHA.test(raw.hasta)) filtros.hasta = raw.hasta;
  // `desde` posterior a `hasta` es `400 RANGO_INVALIDO`: se intercambian en vez de mostrar una lista vacía sin explicación.
  if (filtros.desde && filtros.hasta && filtros.desde > filtros.hasta) [filtros.desde, filtros.hasta] = [filtros.hasta, filtros.desde];
  return filtros;
}

export function paramsDeFiltrosDeGuias(filtros: FiltrosDeGuias, base = new URLSearchParams()): URLSearchParams {
  for (const [clave, valor] of Object.entries(filtros)) if (valor) base.set(clave, valor);
  return base;
}

export async function listarGuias(
  access: string,
  empresaId: string,
  params: FiltrosDeGuias & { pagina?: number; porPagina?: number } = {},
): Promise<PaginaDeGuias> {
  const qs = paramsDeFiltrosDeGuias(params);
  qs.set("pagina", String(params.pagina ?? 1));
  qs.set("por_pagina", String(params.porPagina ?? 10));
  const { datos, headers } = await backendFetchConHeaders<Guia[]>(`/v1/guias-remision?${qs}`, { headers: tenantHeaders(access, empresaId) });
  return { datos, total: totalDesdeHeaders(headers, datos.length) };
}

export function obtenerGuia(access: string, empresaId: string, id: string) {
  return backendFetch<Guia>(`/v1/guias-remision/${encodeURIComponent(id)}`, { headers: tenantHeaders(access, empresaId) });
}

/**
 * Lo que se manda a `POST /v1/guias-remision` (y a `/validar`, que recibe el mismo cuerpo y no guarda nada). La letra de la serie decide el tipo: `T###` remitente,
 * `V###` transportista; cada una lleva solo sus datos (la remitente `motivo`, `modalidad` y `transportista`; la transportista `remitente` y `registro_mtc`) o responde `422 GUIA_INVALIDA`.
 */
export type CuerpoDeGuia = {
  serie: string;
  fecha_emision?: string;
  destinatario: ParteDeGuia;
  motivo?: string;
  descripcion_motivo?: string;
  modalidad?: string;
  transportista?: TransportistaDeGuia;
  remitente?: ParteDeGuia;
  registro_mtc?: string;
  traslado: {
    fecha_inicio: string;
    peso_bruto: number;
    unidad_peso?: string;
    partida: PuntoDeGuia;
    llegada: PuntoDeGuia;
    vehiculos?: Array<{ placa: string }>;
    conductores?: Array<Omit<ConductorDeGuia, "principal">>;
    indicadores?: string[];
  };
  items: Array<Omit<ItemDeGuia, "cantidad"> & { cantidad: number }>;
  documentos_relacionados?: Array<{ tipo: string; numero: string; ruc_emisor?: string }>;
  observaciones?: string;
  enviar_automatico?: boolean;
};
