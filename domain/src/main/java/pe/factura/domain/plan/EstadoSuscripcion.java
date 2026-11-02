package pe.factura.domain.plan;

/** Dónde está una suscripción en un instante: {@code REEMPLAZADA} es la que ya no manda (la cuenta cambió de plan), aunque no hubiera vencido. */
public enum EstadoSuscripcion { VIGENTE, EN_GRACIA, VENCIDA, REEMPLAZADA }
