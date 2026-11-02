import { backendFetch } from "./client";
import type { LimiteAdmin, LimitesAdmin } from "./admin-planes";

export type EstadoDeSuscripcionAdmin = "VIGENTE" | "EN_GRACIA" | "VENCIDA";
export type DireccionDeCambioAdmin = "SUBIDA" | "BAJADA" | "RENOVACION";
export type EfectoDeCambioAdmin = "INMEDIATO" | "CICLO_SIGUIENTE";

/** Un plan a grandes rasgos: nombre, precio y los límites que mandan hoy. */
export type PlanResumenAdmin = { id: string; nombre: string; precio_mensual: number; limites: LimitesAdmin };

/** Una bajada de plan decidida que entra al inicio del ciclo siguiente (#191). */
export type ProgramadoAdmin = { plan: PlanResumenAdmin; aplica_desde: string; vence_en?: string; dias_de_gracia: number };

/** El plan de una cuenta hoy: cuál, en qué estado de pago está y si hay una bajada esperando. */
export type PlanDeCuentaAdmin = {
  cuenta_id: string;
  plan: PlanResumenAdmin;
  estado: EstadoDeSuscripcionAdmin;
  inicia_en: string;
  vence_en?: string;
  dias_de_gracia: number;
  hasta_cuando_cubre?: string;
  programado?: ProgramadoAdmin;
};

/** Lo que pasaría con un cambio, para mostrarlo antes de confirmar. */
export type PrevisualizacionDePlanAdmin = {
  cuenta_id: string;
  plan_actual: PlanResumenAdmin;
  plan_nuevo: PlanResumenAdmin;
  direccion: DireccionDeCambioAdmin;
  efecto: EfectoDeCambioAdmin;
  aplica_desde: string;
  mes: string;
  consumo_del_mes: number;
  limite_de_documentos: LimiteAdmin;
  supera_el_limite: boolean;
  /** La bajada que estaba esperando y que este cambio deja sin efecto: un cambio inmediato la cancela, otra bajada la reemplaza. */
  programado_que_se_descarta?: { plan: PlanResumenAdmin; aplica_desde: string };
};

/** Lo que se manda para cambiar el plan: `vence_en` es obligatorio en un plan de pago y opcional en el gratis. */
export type CuerpoDeCambioDePlan = { plan_id: string; vence_en?: string; dias_de_gracia?: number };

/** Los días de gracia que admite el backend. */
export const GRACIA_MAX_DIAS = 90;

/**
 * Solo desde el servidor: usan el JWT del administrador, que el navegador nunca ve. `origen` es la IP del cliente ya resuelta por el BFF
 * (`cabecerasDeOrigen`, #208) para que la bitácora registre la del administrador y no la del portal.
 */
const conSesion = (access: string, origen: Record<string, string> = {}) => ({ Authorization: `Bearer ${access}`, ...origen });

export function obtenerPlanDeCuenta(access: string, cuentaId: string) {
  return backendFetch<PlanDeCuentaAdmin>(`/v1/admin/cuentas/${cuentaId}/plan`, { headers: conSesion(access) });
}

export function previsualizarCambioDePlan(access: string, cuentaId: string, planId: string, origen: Record<string, string> = {}) {
  return backendFetch<PrevisualizacionDePlanAdmin>(`/v1/admin/cuentas/${cuentaId}/plan/previsualizacion?plan_id=${encodeURIComponent(planId)}`, { headers: conSesion(access, origen) });
}

export function cambiarPlanDeCuenta(access: string, cuentaId: string, cuerpo: CuerpoDeCambioDePlan, origen: Record<string, string> = {}) {
  return backendFetch<PlanDeCuentaAdmin>(`/v1/admin/cuentas/${cuentaId}/plan`, { method: "POST", body: cuerpo, headers: conSesion(access, origen) });
}
