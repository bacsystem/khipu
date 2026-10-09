import type { EmpresaDetalle } from "@/lib/api/empresas";
import { faltaParaEmitir } from "@/lib/comprobantes/listo-para-emitir";

/** Las pestañas de «Fiscal & certificado» (#276), en el orden en que se muestran. */
export const SECCIONES = ["datos", "certificado", "sol", "pdf", "empresas"] as const;
export type Seccion = (typeof SECCIONES)[number];

type EstadoEmpresa = Pick<EmpresaDetalle, "tiene_certificado" | "certificado_vigencia_hasta" | "tiene_credenciales_sol" | "credenciales_sol_rechazadas">;

function esSeccion(valor: string | undefined): valor is Seccion {
  return (SECCIONES as readonly string[]).includes(valor ?? "");
}

/**
 * Las pestañas con algo por hacer para poder emitir, en el orden de `SECCIONES`. Las credenciales que SUNAT rechazó también cuentan:
 * están guardadas, pero los envíos esperan a que se corrijan (#107).
 */
export function seccionesPendientes(empresa: EstadoEmpresa, hoy: string): Seccion[] {
  const faltan = faltaParaEmitir(empresa, hoy);
  const pendientes: Seccion[] = [];
  if (faltan.includes("certificado") || faltan.includes("certificado-vencido")) pendientes.push("certificado");
  if (faltan.includes("credenciales-sol") || empresa.credenciales_sol_rechazadas) pendientes.push("sol");
  return pendientes;
}

/** La pestaña que se abre: la pedida en `?seccion=` si existe; si no, la primera pendiente; si no falta nada, «Datos fiscales». */
export function seccionInicial(pedida: string | undefined, empresa: EstadoEmpresa, hoy: string): Seccion {
  if (esSeccion(pedida)) return pedida;
  return seccionesPendientes(empresa, hoy)[0] ?? "datos";
}

export function enlaceASeccion(seccion: Seccion): string {
  return `/empresa?seccion=${seccion}`;
}
