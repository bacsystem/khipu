package pe.factura.domain.plan;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lo que se vende: precio y límites. Es un dato —no un texto de la página de precios— para poder asignarlo a una cuenta, cobrarlo y hacerlo valer.
 * {@code porDefecto}: el plan con el que nace toda cuenta; no puede estar inactivo, porque sin él una cuenta nueva quedaría sin plan.
 * El precio es en soles, con como mucho dos decimales (se rechaza en vez de redondear en silencio).
 */
public record Plan(UUID id, String nombre, BigDecimal precioMensual, Limite documentosAlMes, int rucs, Limite usuarios, Limite apiKeys,
                   int retencionAnios, EstadoPlan estado, boolean porDefecto) {
    private static final int NOMBRE_MAX = 40;

    public Plan {
        if (nombre == null || nombre.isBlank()) throw new DomainException("NOMBRE_REQUERIDO", "El nombre del plan es obligatorio");
        nombre = nombre.strip();
        if (nombre.length() > NOMBRE_MAX) throw new DomainException("NOMBRE_INVALIDO", "El nombre del plan admite hasta " + NOMBRE_MAX + " caracteres");
        if (precioMensual == null || precioMensual.signum() < 0 || precioMensual.stripTrailingZeros().scale() > 2)
            throw new DomainException("PRECIO_INVALIDO", "El precio mensual debe ser cero o más, con hasta dos decimales: " + precioMensual);
        precioMensual = precioMensual.setScale(2);
        if (documentosAlMes == null || usuarios == null || apiKeys == null) throw new DomainException("LIMITE_INVALIDO", "Faltan límites del plan");
        if (rucs <= 0) throw new DomainException("LIMITE_INVALIDO", "Un plan debe permitir al menos un RUC: " + rucs);
        if (retencionAnios <= 0) throw new DomainException("RETENCION_INVALIDA", "La retención debe ser de al menos un año: " + retencionAnios);
        if (estado == null) throw new DomainException("ESTADO_INVALIDO", "El estado del plan es obligatorio");
        if (porDefecto && estado != EstadoPlan.ACTIVO) throw new DomainException("PLAN_POR_DEFECTO", "El plan por defecto de las cuentas nuevas no puede estar inactivo");
    }

    public boolean activo() { return estado == EstadoPlan.ACTIVO; }

    /** Lo saca de la oferta. El de las cuentas nuevas no: dejaría a las próximas sin plan. */
    public Plan desactivar() {
        if (porDefecto) throw new DomainException("PLAN_POR_DEFECTO", "El plan por defecto de las cuentas nuevas no se puede desactivar");
        return new Plan(id, nombre, precioMensual, documentosAlMes, rucs, usuarios, apiKeys, retencionAnios, EstadoPlan.INACTIVO, false);
    }
}
