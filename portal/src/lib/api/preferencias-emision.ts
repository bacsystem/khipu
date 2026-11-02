import { apiRequest } from "./browser";
import type { ApiEnvelope } from "./types";

/**
 * Las preferencias de emisión de la empresa (`GET/PUT /v1/empresa/preferencias-emision`). `opciones_avanzadas`: si el modal de emisión ofrece las opciones avanzadas
 * (tipo de operación/exportación, anticipos, percepción, retención, más opciones y referencias). Apagada por defecto; el crédito, la detracción y el correo se ofrecen siempre.
 * Leerla es de cualquier rol; cambiarla, solo de un administrador (`403 ROL_INSUFICIENTE`).
 */
export type PreferenciasDeEmision = { opciones_avanzadas: boolean };

/** El cuerpo de `PUT /v1/empresa/preferencias-emision`. */
export type CuerpoDePreferenciasDeEmision = { opciones_avanzadas: boolean };

const RUTA = "/api/proxy/empresa/preferencias-emision";

/** Desde el navegador, por el proxy. No lanza: un corte o un rechazo vuelven como sobre de error. */
export function leerPreferenciasDeEmision(): Promise<ApiEnvelope<PreferenciasDeEmision>> {
  return apiRequest<PreferenciasDeEmision>(RUTA, { method: "GET" });
}

/** Desde el navegador, por el proxy. Solo un administrador (los demás roles reciben `403 ROL_INSUFICIENTE`). */
export function guardarPreferenciasDeEmision(opcionesAvanzadas: boolean): Promise<ApiEnvelope<PreferenciasDeEmision>> {
  const body: CuerpoDePreferenciasDeEmision = { opciones_avanzadas: opcionesAvanzadas };
  return apiRequest<PreferenciasDeEmision>(RUTA, { method: "PUT", body });
}
