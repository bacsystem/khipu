package pe.factura.domain.plan;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lo que se vende: precio y límites. Es un dato —no un texto de la página de precios— para poder asignarlo a una cuenta, cobrarlo y hacerlo valer.
 * {@code porDefecto}: el plan con el que nace toda cuenta; no puede estar inactivo, porque sin él una cuenta nueva quedaría sin plan.
 * El precio es en soles, con como mucho dos decimales (se rechaza en vez de redondear en silencio).
 *
 * <p>{@code limites} son los que mandan en el ciclo en curso. Cambiarlos ({@link #editar}) no los toca: queda un {@code programado} que entra al inicio del ciclo
 * siguiente, así que subir un tope a mitad de mes no regala documentos del ciclo corriente ni bajarlo le corta a nadie. {@link #vigenteEn} aplica el cambio
 * cuando llega su fecha; quien lea los límites para hacerlos valer debe pasar por ahí.
 *
 * <p>{@code visibleEnPublicidad} (H20): si sale en la página de precios. Es aparte de {@code estado}: un plan a medida para un cliente está activo (se asigna y se
 * cobra) pero no se publica; uno inactivo no se asigna a nadie más, se publique o no.
 */
public record Plan(UUID id, String nombre, BigDecimal precioMensual, Limites limites, EstadoPlan estado, boolean porDefecto, CambioDeLimites programado,
                   boolean visibleEnPublicidad) {
    private static final int NOMBRE_MAX = 40;
    /** El mayor que cabe en {@code plan.precio_mensual NUMERIC(10,2)}. */
    public static final BigDecimal PRECIO_MAX = new BigDecimal("99999999.99");

    public Plan {
        if (id == null) throw new DomainException("PLAN_INVALIDO", "El plan necesita un id");
        if (nombre == null || nombre.isBlank()) throw new DomainException("NOMBRE_REQUERIDO", "El nombre del plan es obligatorio");
        nombre = nombre.strip();
        if (nombre.length() > NOMBRE_MAX) throw new DomainException("NOMBRE_INVALIDO", "El nombre del plan admite hasta " + NOMBRE_MAX + " caracteres");
        if (precioMensual == null || precioMensual.signum() < 0 || precioMensual.stripTrailingZeros().scale() > 2)
            throw new DomainException("PRECIO_INVALIDO", "El precio mensual debe ser cero o más, con hasta dos decimales: " + precioMensual);
        precioMensual = precioMensual.setScale(2);
        if (precioMensual.compareTo(PRECIO_MAX) > 0)
            throw new DomainException("PRECIO_INVALIDO", "El precio mensual admite hasta " + PRECIO_MAX.toPlainString() + ": " + precioMensual.toPlainString());
        if (limites == null) throw new DomainException("LIMITE_INVALIDO", "Faltan límites del plan");
        if (estado == null) throw new DomainException("ESTADO_INVALIDO", "El estado del plan es obligatorio");
        if (porDefecto && estado != EstadoPlan.ACTIVO) throw new DomainException("PLAN_POR_DEFECTO", "El plan por defecto de las cuentas nuevas no puede estar inactivo");
    }

    /** Un plan que se publica (lo de siempre, H20). */
    public Plan(UUID id, String nombre, BigDecimal precioMensual, Limites limites, EstadoPlan estado, boolean porDefecto, CambioDeLimites programado) {
        this(id, nombre, precioMensual, limites, estado, porDefecto, programado, true);
    }

    /** Un plan sin cambio de límites programado. */
    public Plan(UUID id, String nombre, BigDecimal precioMensual, Limites limites, EstadoPlan estado, boolean porDefecto) {
        this(id, nombre, precioMensual, limites, estado, porDefecto, null);
    }

    /** El mismo plan, publicado o no en la página de precios (H20). No cambia nada más: ni si se vende ni a quién lo tiene. */
    public Plan conVisibilidadEnPublicidad(boolean visible) {
        return new Plan(id, nombre, precioMensual, limites, estado, porDefecto, programado, visible);
    }

    public boolean activo() { return estado == EstadoPlan.ACTIVO; }

    /** Lo saca de la oferta (no se puede asignar a cuentas nuevas) sin tocar a las que ya lo tienen. El de las cuentas nuevas no se puede: dejaría a las próximas sin plan. */
    public Plan desactivar() {
        if (porDefecto) throw new DomainException("PLAN_POR_DEFECTO", "El plan por defecto de las cuentas nuevas no se puede desactivar");
        if (!activo()) throw new DomainException("PLAN_YA_INACTIVO", "El plan ya está inactivo");
        return new Plan(id, nombre, precioMensual, limites, EstadoPlan.INACTIVO, false, programado, visibleEnPublicidad);
    }

    public Plan activar() {
        if (activo()) throw new DomainException("PLAN_YA_ACTIVO", "El plan ya está activo");
        return new Plan(id, nombre, precioMensual, limites, EstadoPlan.ACTIVO, porDefecto, programado, visibleEnPublicidad);
    }

    /** El plan tal como manda en {@code ahora}: si su cambio de límites programado ya llegó, los límites nuevos pasan a vigentes. */
    public Plan vigenteEn(Instant ahora) {
        if (programado == null || ahora.isBefore(programado.aplicaDesde())) return this;
        return new Plan(id, nombre, precioMensual, programado.limites(), estado, porDefecto, null, visibleEnPublicidad);
    }

    /**
     * Nombre y precio cambian al instante. Los límites, no: si difieren de los vigentes quedan programados para el inicio del ciclo siguiente, y un segundo
     * cambio antes de que llegue lo reemplaza (nunca hay dos pendientes). Poner otra vez los límites vigentes cancela el programado. No muta este plan.
     */
    public Plan editar(String nuevoNombre, BigDecimal nuevoPrecio, Limites nuevosLimites, Instant ahora) {
        Plan vigente = vigenteEn(ahora);
        if (nuevosLimites == null) throw new DomainException("LIMITE_INVALIDO", "Faltan límites del plan");
        CambioDeLimites cambio = nuevosLimites.equals(vigente.limites) ? null : new CambioDeLimites(nuevosLimites, CicloMensual.inicioDelSiguiente(ahora));
        return new Plan(id, nuevoNombre, nuevoPrecio, vigente.limites, estado, porDefecto, cambio, visibleEnPublicidad);
    }
}
