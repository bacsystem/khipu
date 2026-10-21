import type { Mensajes } from "@/i18n/mensajes";

/**
 * Los datos de la página de errores (estados, códigos HTTP y códigos de khipu), aparte de la página para que el buscador del developer portal (#26) indexe los mismos que ella
 * muestra: una página de App Router no puede exportar nada más que su componente. Aquí quedan los códigos, que son técnicos; lo que significan y qué hacer está en
 * `messages.developers.errores` (#25), por código.
 */

type TextosDeErrores = Mensajes["developers"]["errores"];

const CODIGOS_DE_ESTADO: Array<keyof TextosDeErrores["estados"]> = [
  "RECIBIDO",
  "FIRMADO",
  "ENVIADO",
  "ACEPTADO",
  "ACEPTADO_CON_OBS",
  "RECHAZADO",
  "ERROR_ENVIO",
  "INVALIDO",
  "ANULADO",
  "FUERA_DE_PLAZO",
  "DESCARTADO",
];

const CODIGOS_HTTP: Array<[http: string, clave: keyof TextosDeErrores["http"]]> = [
  ["200 / 201 / 204", "200"],
  ["400", "400"],
  ["401", "401"],
  ["403", "403"],
  ["404", "404"],
  ["409", "409"],
  ["422", "422"],
  ["500", "500"],
  ["502 / 503", "502"],
];

/** El código (o los códigos de una misma fila), su HTTP y la clave de sus textos (el primer código de la fila). */
const CODIGOS_DE_ERROR: Array<[codigo: string, http: string, clave: keyof TextosDeErrores["codigos"]]> = [
  ["VALIDACION", "422", "VALIDACION"],
  ["SERIE_INVALIDA", "422", "SERIE_INVALIDA"],
  ["SERIE_NO_CONFIGURADA", "422", "SERIE_NO_CONFIGURADA"],
  ["DUPLICADO", "409", "DUPLICADO"],
  ["IDEMPOTENCIA_INVALIDA", "422", "IDEMPOTENCIA_INVALIDA"],
  ["NUMERO_YA_ASIGNADO", "422", "NUMERO_YA_ASIGNADO"],
  ["FECHA_INVALIDA", "422", "FECHA_INVALIDA"],
  ["MONEDA_INVALIDA", "422", "MONEDA_INVALIDA"],
  ["TIPO_CAMBIO_INVALIDO", "422", "TIPO_CAMBIO_INVALIDO"],
  ["TIPO_OPERACION_INVALIDO", "422", "TIPO_OPERACION_INVALIDO"],
  ["RECEPTOR_INVALIDO", "422", "RECEPTOR_INVALIDO"],
  ["RUC_INVALIDO", "422", "RUC_INVALIDO"],
  ["SIN_ITEMS", "422", "SIN_ITEMS"],
  ["FORMA_PAGO_INVALIDA", "422", "FORMA_PAGO_INVALIDA"],
  ["DESCUENTO_INVALIDO / CARGO_INVALIDO / DETRACCION_INVALIDA / RETENCION_INVALIDA / PERCEPCION_INVALIDA", "422", "DESCUENTO_INVALIDO"],
  ["ITEM_INVALIDO / REDONDEO_INVALIDO", "422", "ITEM_INVALIDO"],
  ["AFECTACION_INVALIDA", "422", "AFECTACION_INVALIDA"],
  ["EXPORTACION_INVALIDA", "422", "EXPORTACION_INVALIDA"],
  ["LEYENDA_INVALIDA", "422", "LEYENDA_INVALIDA"],
  ["NOMBRE_COMERCIAL_INVALIDO", "422", "NOMBRE_COMERCIAL_INVALIDO"],
  ["NOTA_INVALIDA", "422", "NOTA_INVALIDA"],
  ["BAJA_INVALIDA", "422", "BAJA_INVALIDA"],
  ["DOCUMENTO_RELACIONADO_INVALIDO", "422", "DOCUMENTO_RELACIONADO_INVALIDO"],
  ["OTROS_TRIBUTOS_INVALIDO", "422", "OTROS_TRIBUTOS_INVALIDO"],
  ["ANTICIPO_INVALIDO", "422", "ANTICIPO_INVALIDO"],
  ["FUERA_DE_PLAZO", "409", "FUERA_DE_PLAZO"],
  ["CDR_YA_DISPONIBLE", "409", "CDR_YA_DISPONIBLE"],
  ["CDR_NO_CONSULTABLE", "409", "CDR_NO_CONSULTABLE"],
  ["NO_DISPONIBLE_EN_BETA", "422", "NO_DISPONIBLE_EN_BETA"],
  ["CREDENCIALES_SUNAT_NO_CARGADAS", "422", "CREDENCIALES_SUNAT_NO_CARGADAS"],
  ["CREDENCIALES_API_SUNAT_INVALIDAS", "422", "CREDENCIALES_API_SUNAT_INVALIDAS"],
  ["CREDENCIALES_API_SUNAT_RECHAZADAS", "422", "CREDENCIALES_API_SUNAT_RECHAZADAS"],
  ["CONSULTA_RECHAZADA", "422", "CONSULTA_RECHAZADA"],
  ["SUNAT_NO_DISPONIBLE", "503", "SUNAT_NO_DISPONIBLE"],
  ["RVIE_SIN_TIPO_CAMBIO", "422", "RVIE_SIN_TIPO_CAMBIO"],
  ["RVIE_EXCEDE_EL_TOPE", "422", "RVIE_EXCEDE_EL_TOPE"],
  ["RVIE_ARCHIVO_INVALIDO", "422", "RVIE_ARCHIVO_INVALIDO"],
  ["RVIE_PROPUESTA_DEMASIADO_GRANDE", "422", "RVIE_PROPUESTA_DEMASIADO_GRANDE"],
  ["ESTABLECIMIENTO_INVALIDO", "422", "ESTABLECIMIENTO_INVALIDO"],
  ["ESTABLECIMIENTO_EN_USO", "409", "ESTABLECIMIENTO_EN_USO"],
  ["DOMICILIO_INVALIDO / CUENTA_DETRACCIONES_INVALIDA", "422", "DOMICILIO_INVALIDO"],
  ["CREDENCIALES_SOL_NO_CARGADAS", "422", "CREDENCIALES_SOL_NO_CARGADAS"],
  ["CERTIFICADO_NO_CARGADO / CERTIFICADO_VENCIDO / CERTIFICADO_INVALIDO", "422", "CERTIFICADO_NO_CARGADO"],
  ["LIMITE_PLAN", "429", "LIMITE_PLAN"],
  ["PLAN_VENCIDO", "402", "PLAN_VENCIDO"],
  ["XSD_INVALIDO / FIRMA_FALLIDA", "422", "XSD_INVALIDO"],
  ["NO_ACEPTADO", "409", "NO_ACEPTADO"],
  ["CORREO_NO_ENVIADO", "502", "CORREO_NO_ENVIADO"],
  ["CORREO_NO_CONFIGURADO", "503", "CORREO_NO_CONFIGURADO"],
  ["PERSONALIZACION_INVALIDA / LOGO_INVALIDO / OBSERVACIONES_INVALIDAS", "422", "PERSONALIZACION_INVALIDA"],
  ["EXPORTACION_EXCEDE_EL_TOPE", "422", "EXPORTACION_EXCEDE_EL_TOPE"],
  ["SIN_FIRMA", "422", "SIN_FIRMA"],
  ["ESTADO_NO_ENVIABLE", "409", "ESTADO_NO_ENVIABLE"],
  ["ESTADO_CONFLICTO", "409", "ESTADO_CONFLICTO"],
  ["SIN_CDR", "404", "SIN_CDR"],
  ["CDR_CORRUPTO", "500", "CDR_CORRUPTO"],
  ["NO_ENCONTRADO", "404", "NO_ENCONTRADO"],
  ["NO_AUTORIZADO", "401", "NO_AUTORIZADO"],
  ["REQUIERE_SESION", "403", "REQUIERE_SESION"],
  ["ROL_INSUFICIENTE", "403", "ROL_INSUFICIENTE"],
  ["DEMASIADAS_INVITACIONES", "429", "DEMASIADAS_INVITACIONES"],
  ["PARAMETRO_INVALIDO", "400", "PARAMETRO_INVALIDO"],
  ["JSON_INVALIDO", "400", "JSON_INVALIDO"],
  ["RUTA_INEXISTENTE", "404", "RUTA_INEXISTENTE"],
  ["INTERNO", "500", "INTERNO"],
];

/** Estados del comprobante con su significado y la acción esperada del integrador. */
export function estados(messages: Mensajes): Array<[string, string, string]> {
  const t = messages.developers.errores.estados;
  return CODIGOS_DE_ESTADO.map((estado) => [estado, t[estado][0], t[estado][1]]);
}

export function codigosHttp(messages: Mensajes): Array<[string, string]> {
  const t = messages.developers.errores.http;
  return CODIGOS_HTTP.map(([http, clave]) => [http, t[clave]]);
}

export function codigosDeError(messages: Mensajes): Array<[string, string, string, string]> {
  const t = messages.developers.errores.codigos;
  return CODIGOS_DE_ERROR.map(([codigo, http, clave]) => [codigo, http, t[clave][0], t[clave][1]]);
}
