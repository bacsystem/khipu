import { diasEntre, sumarDias } from "@/lib/formato";

/**
 * Plazos de envío a SUNAT en días calendario contados desde el día siguiente a la emisión, en hora de Lima. Espeja `PlazoEnvio` del backend (#290, #292):
 * la factura (y sus notas) hasta el 3.er día (2108); la boleta va sola hasta el 5.º y en el resumen diario hasta el 7.º (1079). Lo usan el formulario de
 * emisión y el mock, para que los dos apliquen la misma regla que el backend.
 */
export const PLAZO_ENVIO = {
  factura: { dias: 3, envioIndividual: 3 },
  boleta: { dias: 7, envioIndividual: 5 },
} as const;

export type TipoConPlazo = keyof typeof PLAZO_ENVIO;

/** La fecha de emisión más antigua que todavía se puede enviar a SUNAT. */
export function fechaMinimaDeEmision(tipo: TipoConPlazo, hoy: string): string {
  return sumarDias(hoy, -PLAZO_ENVIO[tipo].dias);
}

/** Una boleta que ya no va sola (pasó el 5.º día) pero todavía se informa en el resumen diario (hasta el 7.º). */
export function soloPorResumen(tipo: TipoConPlazo, fecha: string, hoy: string): boolean {
  const dias = diasEntre(fecha, hoy);
  return tipo === "boleta" && dias > PLAZO_ENVIO.boleta.envioIndividual && dias <= PLAZO_ENVIO.boleta.dias;
}
