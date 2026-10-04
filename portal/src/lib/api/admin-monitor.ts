import { backendFetch } from "./client";

/** Los servicios de SUNAT que el monitor sondea (#195). */
export const SERVICIOS_DE_SUNAT = ["ENVIO_PRODUCCION", "ENVIO_BETA", "CONSULTA_DE_CDR", "CONSULTA_DE_VALIDEZ"] as const;
export type ServicioDeSunat = (typeof SERVICIOS_DE_SUNAT)[number];

/**
 * Lo que pasó con los comprobantes creados en una hora (o en el día), con la forma real del JSON. `desde` es el inicio de la hora. `total` es la suma de las cinco
 * categorías, que no se pisan. La API omite `tasa_de_rechazo` si SUNAT todavía no resolvió ninguno: omitido no es cero.
 */
export type Franja = {
  desde: string;
  total: number;
  aceptados: number;
  rechazados: number;
  con_error: number;
  en_camino: number;
  otros: number;
  tasa_de_rechazo?: number;
};

/** La cola de envíos a SUNAT. `alerta`: hay envíos vencidos hace más de 5 minutos. La API omite lo que no aplica (cola vacía, ningún vencido). */
export type ColaDeEnvios = { pendientes: number; vencidos: number; mas_viejo_desde?: string; vencido_hace_segundos?: number; alerta: boolean };

/** Si un servicio de SUNAT contesta. La API omite `milisegundos` si no contestó y `detalle` si está disponible. */
export type EstadoDeServicio = { servicio: ServicioDeSunat; disponible: boolean; milisegundos?: number; detalle?: string };

export type MonitorDeEmision = {
  generado_en: string;
  /** Las últimas 24 horas, de la más vieja a la actual, con las vacías en cero. */
  horas: Franja[];
  /** El día de Lima hasta ahora. */
  hoy: Franja;
  outbox: ColaDeEnvios;
  sunat: EstadoDeServicio[];
};

/** Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. */
export function obtenerMonitor(access: string) {
  return backendFetch<MonitorDeEmision>("/v1/admin/monitor", { headers: { Authorization: `Bearer ${access}` } });
}
