import type { EmpresaDetalle } from "@/lib/api/empresas";

export type Faltante = "certificado" | "certificado-vencido" | "credenciales-sol";

/**
 * Lo que le falta a la empresa para emitir, con las reglas del backend (`Tenant.exigirListoParaEmitir`, `exigirCredencialesSol`).
 * Sirve para avisar antes de que el usuario llene un comprobante que el backend va a rechazar (C2). `hoy` en `YYYY-MM-DD` (Lima):
 * el certificado que vence hoy todavía firma.
 */
export function faltaParaEmitir(
  empresa: Pick<EmpresaDetalle, "tiene_certificado" | "certificado_vigencia_hasta" | "tiene_credenciales_sol">,
  hoy: string,
): Faltante[] {
  const faltan: Faltante[] = [];
  if (!empresa.tiene_certificado) faltan.push("certificado");
  else if (empresa.certificado_vigencia_hasta && empresa.certificado_vigencia_hasta < hoy) faltan.push("certificado-vencido");
  if (!empresa.tiene_credenciales_sol) faltan.push("credenciales-sol");
  return faltan;
}
