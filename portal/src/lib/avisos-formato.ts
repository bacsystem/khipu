import type { AvisoEnviado, CertificadoEnRiesgo, UltimoAviso } from "@/lib/api/admin-avisos";
import { formatearFecha, formatearFechaHora } from "@/lib/formato";
import { messages } from "@/lib/messages";

const t = messages.admin.avisos;

/** «1 día», «12 días»: nunca «1 días» ni negativos (el signo ya lo dice «hace» o «en»). */
function diasEnPalabras(dias: number): string {
  const n = Math.abs(dias);
  return n === 1 ? t.certificado.unDia : t.certificado.variosDias.replace("{n}", String(n));
}

/** Qué pasa con el certificado de una empresa, en una frase: cuándo vence y en cuántos días, o cuándo venció y hace cuántos. «Hoy» es el último día que vale. */
export function certificadoEnPalabras(c: Pick<CertificadoEnRiesgo, "vigente_hasta" | "dias_restantes">): string {
  const fecha = formatearFecha(c.vigente_hasta);
  if (c.dias_restantes === 0) return t.certificado.venceHoy.replace("{fecha}", fecha);
  return (c.dias_restantes > 0 ? t.certificado.vence : t.certificado.vencio).replace("{fecha}", fecha).replace("{dias}", diasEnPalabras(c.dias_restantes));
}

export function atascadosEnPalabras(comprobantes: number): string {
  return comprobantes === 1 ? t.sol.atascadoUno : t.sol.atascadoVarios.replace("{n}", String(comprobantes));
}

/** El último aviso de este motivo: cuándo y a quién, o que nunca se avisó. */
export function ultimoAvisoEnPalabras(ultimo: UltimoAviso | undefined): string {
  return ultimo ? t.avisado.replace("{fecha}", formatearFechaHora(ultimo.enviado_en)).replace("{correo}", ultimo.destinatario) : t.sinAvisos;
}

/** Lo que se le dice al administrador cuando el aviso salió. */
export function mensajeDeAvisoEnviado(aviso: Pick<AvisoEnviado, "destinatario">, razonSocial: string): string {
  return messages.admin.avisos.resultado.enviado.replace("{correo}", aviso.destinatario).replace("{empresa}", razonSocial);
}
