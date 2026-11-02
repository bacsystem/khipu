import { totalDesdeHeaders } from "./facturas";
import { backendFetch, backendFetchConHeaders } from "./client";

/** Cómo pagó el cliente (#194). Es un registro manual: no hay pasarela, así que solo dice por dónde llegó la plata. */
export const MEDIOS_DE_PAGO = ["TRANSFERENCIA", "DEPOSITO", "YAPE", "PLIN", "TARJETA", "EFECTIVO", "OTRO"] as const;
export type MedioDePagoAdmin = (typeof MEDIOS_DE_PAGO)[number];

/**
 * Un pago registrado a mano, con la forma real del JSON. La API omite lo que no se dio: `referencia`, `nota` y `extendio_hasta` faltan si no se dieron; lo omitido
 * nunca se lee como vacío ni como cero. `monto` es en soles; `periodo_hasta` es el último día cubierto (inclusive); `extendio_hasta` es el nuevo vencimiento de la
 * suscripción (exclusivo: la medianoche de Lima del día siguiente) si este pago lo movió.
 */
export type PagoAdmin = {
  id: string;
  cuenta_id: string;
  periodo_desde: string;
  periodo_hasta: string;
  monto: number;
  medio: MedioDePagoAdmin;
  fecha_de_pago: string;
  referencia?: string;
  nota?: string;
  registrado_en: string;
  extendio_hasta?: string;
};

export type PaginaPagos = { datos: PagoAdmin[]; total: number };

/** Lo que se manda para registrar un pago. `referencia` y `nota` solo van si el administrador las escribió. */
export type CuerpoDePago = {
  periodo_desde: string;
  periodo_hasta: string;
  monto: number;
  medio: MedioDePagoAdmin;
  fecha_de_pago: string;
  referencia?: string;
  nota?: string;
  extender_vencimiento: boolean;
};

/** Cuántos pagos recientes muestra la ficha de la cuenta. */
export const PAGOS_EN_FICHA = 10;

/** Solo desde el servidor: usan el JWT del administrador, que el navegador nunca ve; `origen` es la IP real ya resuelta por el BFF (#208). */
const conSesion = (access: string, origen: Record<string, string> = {}) => ({ Authorization: `Bearer ${access}`, ...origen });

export async function listarPagosDeCuenta(access: string, cuentaId: string, porPagina = PAGOS_EN_FICHA): Promise<PaginaPagos> {
  const { datos, headers } = await backendFetchConHeaders<PagoAdmin[]>(`/v1/admin/cuentas/${cuentaId}/pagos?pagina=1&por_pagina=${porPagina}`, { headers: conSesion(access) });
  return { datos, total: totalDesdeHeaders(headers, datos.length) };
}

export function registrarPago(access: string, cuentaId: string, cuerpo: CuerpoDePago, origen: Record<string, string> = {}) {
  return backendFetch<PagoAdmin>(`/v1/admin/cuentas/${cuentaId}/pagos`, { method: "POST", body: cuerpo, headers: conSesion(access, origen) });
}
