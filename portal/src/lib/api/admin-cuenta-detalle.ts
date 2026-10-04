import { diasEntre } from "@/lib/formato";
import { esUuid } from "@/lib/uuid";
import type { EstadoCuentaAdmin } from "./admin-suspension";
import { backendFetch } from "./client";
import type { EstadoDocumento } from "./facturas";

/**
 * Detalle de una cuenta del backoffice (#181), con la forma real del JSON. La API omite los campos sin valor (`non_null`): por eso son
 * opcionales. Solo lectura y sin secretos: del certificado y de la clave SOL solo se sabe si existen.
 */
export type CuentaDetalleAdmin = {
  id: string;
  nombre: string;
  email: string;
  telefono?: string;
  creada_en: string;
  /** ACTIVA, SUSPENDIDA (#182) o BAJA (#201); la baja manda sobre la suspensión. */
  estado: EstadoCuentaAdmin;
  /** Desde cuándo está suspendida; falta si no lo está. */
  suspendida_en?: string;
  /** Desde cuándo está dada de baja; falta si está en servicio. Una cuenta de baja se abre igual, con todo lo suyo. */
  baja_en?: string;
  usuarios: UsuarioCuenta[];
  empresas: EmpresaCuenta[];
  comprobantes: ComprobanteReciente[];
  eventos: EventoReciente[];
};

export type UsuarioCuenta = {
  id: string;
  email: string;
  rol: string;
  activo: boolean;
  /** Cuándo verificó su correo (#22); ausente si todavía no. */
  correo_verificado_en?: string;
  /** Su sesión más reciente en el portal; el uso por API key no cuenta. Ausente si nunca inició sesión. */
  ultimo_acceso?: string;
};

export type EmpresaCuenta = {
  id: string;
  ruc: string;
  razon_social: string;
  entorno: "BETA" | "PRODUCCION";
  tiene_certificado: boolean;
  /** `YYYY-MM-DD`; ausente si no hay certificado. */
  certificado_vigente_hasta?: string;
  tiene_credenciales_sol: boolean;
};

export type ComprobanteReciente = {
  id: string;
  empresa_id: string;
  ruc: string;
  tipo: string;
  serie: string;
  numero: number;
  fecha_emision: string;
  estado: EstadoDocumento;
  moneda: string;
  total: number;
};

/** Una acción del administrador sobre esta cuenta, de la bitácora. */
export type EventoReciente = {
  accion: string;
  actor: "ADMINISTRADOR" | "CLAVE_PLATAFORMA";
  ocurrido_en: string;
  detalle?: string;
};

/** Lo que llega por la URL no se pega en la llamada al backend sin mirarlo: `../auth/me` o un espacio no son un id de cuenta. */
export function esIdDeCuenta(id: string): boolean {
  return esUuid(id);
}

export function hrefDetalleCuenta(id: string): string {
  return `/admin/cuentas/${id}`;
}

/** Un certificado con menos de estos días de vigencia está «por vencer»: el umbral de la épica #11 («< 30 días»). */
export const DIAS_POR_VENCER = 30;

export type EstadoCertificado =
  | { tipo: "ninguno" }
  | { tipo: "sin_fecha" }
  | { tipo: "vigente" | "por_vencer" | "vencido"; dias: number; hasta: string };

/** `hoy` es `YYYY-MM-DD` (la fecha de Lima); `dias` negativo si ya venció. El último día de vigencia todavía vale. */
export function estadoCertificado(e: Pick<EmpresaCuenta, "tiene_certificado" | "certificado_vigente_hasta">, hoy: string): EstadoCertificado {
  if (!e.tiene_certificado) return { tipo: "ninguno" };
  if (!e.certificado_vigente_hasta) return { tipo: "sin_fecha" };
  const dias = diasEntre(hoy, e.certificado_vigente_hasta);
  const hasta = e.certificado_vigente_hasta;
  if (dias < 0) return { tipo: "vencido", dias, hasta };
  return { tipo: dias < DIAS_POR_VENCER ? "por_vencer" : "vigente", dias, hasta };
}

/** Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. */
export function obtenerCuentaAdmin(access: string, id: string) {
  return backendFetch<CuentaDetalleAdmin>(`/v1/admin/cuentas/${id}`, { headers: { Authorization: `Bearer ${access}` } });
}
