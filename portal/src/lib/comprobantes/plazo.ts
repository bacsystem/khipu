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

/** Una nota sobre boleta que va en el resumen diario: la NC 10 y la ND 03 no (guía, p. 19), y pasado su envío individual no les queda canal. */
function vaEnResumen(c: { tipo: string; nota?: { tipo_afectado: string; motivo: string } | null }): boolean {
  if (c.tipo === "03") return true;
  if (c.nota?.tipo_afectado !== "03") return false;
  return !((c.tipo === "07" && c.nota.motivo === "10") || (c.tipo === "08" && c.nota.motivo === "03"));
}

/**
 * Qué le pasa a lo de boleta que sigue sin informarse a SUNAT (#356), según el día. `limite` es el último día en plazo del resumen diario y `diasRestantes` lo que falta para él
 * (0 es hoy, negativo que ya pasó).
 */
export type AvisoDePlazo = {
  fase: "ultimo-dia-individual" | "solo-resumen" | "ultimo-dia-resumen" | "fuera-de-plazo";
  limite: string;
  diasRestantes: number;
};

/**
 * Lo de boleta (boleta o nota que la modifica y va en el resumen) que sigue por enviar —FIRMADO, o con el envío fallando— desde el 5.º día, o `null` si todavía no hace falta
 * avisar. Espeja `Comprobante.porInformar` y `porVencerElPlazo` del backend; `hoy` es la fecha de Lima.
 */
export function avisoDePlazo(
  c: { tipo: string; estado_documento: string; fecha_emision: string; nota?: { tipo_afectado: string; motivo: string } | null },
  hoy: string,
): AvisoDePlazo | null {
  if (c.estado_documento !== "FIRMADO" && c.estado_documento !== "ERROR_ENVIO") return null;
  if (!vaEnResumen(c)) return null;
  const dias = diasEntre(c.fecha_emision, hoy);
  if (dias < PLAZO_ENVIO.boleta.envioIndividual) return null;
  const limite = sumarDias(c.fecha_emision, PLAZO_ENVIO.boleta.dias);
  const diasRestantes = PLAZO_ENVIO.boleta.dias - dias;
  const fase = dias === PLAZO_ENVIO.boleta.envioIndividual ? "ultimo-dia-individual" : dias < PLAZO_ENVIO.boleta.dias ? "solo-resumen" : dias === PLAZO_ENVIO.boleta.dias ? "ultimo-dia-resumen" : "fuera-de-plazo";
  return { fase, limite, diasRestantes };
}
