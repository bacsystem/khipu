package pe.factura.domain.plataforma;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Por qué se le avisa a un cliente (#197). Es más fino que el tipo a propósito: «por vencer» y «vencido» son avisos distintos, así que pasar de uno al otro habilita un aviso
 * nuevo aunque no haya pasado la semana; repetir el **mismo** motivo, no. El aviso lo manda un administrador a mano, y el registro de lo enviado es lo que impide mandarlo
 * todos los días. Los motivos de boletas (#356) son la excepción: los manda el sistema solo ({@link #automatico()}) porque el plazo es de días y nadie los pediría a tiempo.
 */
public enum MotivoDeAviso {
    CERTIFICADO_POR_VENCER(TipoDeAviso.CERTIFICADO),
    CERTIFICADO_VENCIDO(TipoDeAviso.CERTIFICADO),
    CREDENCIALES_SOL_INVALIDAS(TipoDeAviso.CREDENCIALES_SOL),
    /** Hay boletas sin informar a SUNAT desde el 5.º día: ya no se pueden enviar solas, pero se informan en el resumen diario hasta el 7.º (#356). */
    BOLETAS_POR_INFORMAR(null),
    /** La más antigua de esas boletas está en el 7.º día, el último del resumen diario, o ya lo pasó (#356). */
    BOLETAS_POR_VENCER_PLAZO(null);

    /** Lo que hay que esperar para repetir el mismo aviso a la misma empresa. */
    public static final Duration ENFRIAMIENTO = Duration.ofDays(7);

    private final TipoDeAviso tipo;

    MotivoDeAviso(TipoDeAviso tipo) { this.tipo = tipo; }

    /** El tipo que un administrador puede mandar a mano; nulo en los avisos que manda el sistema solo ({@link #automatico()}). */
    public TipoDeAviso tipo() { return tipo; }

    /** Si lo manda el sistema solo y no un administrador (#356). */
    public boolean automatico() { return tipo == null; }

    /** Desde cuándo se puede repetir el aviso que se mandó en {@code ultimoAviso}. */
    public Instant avisarDesde(Instant ultimoAviso) { return ultimoAviso.plus(ENFRIAMIENTO); }

    /** Los motivos de un tipo, en el orden en que se declaran. */
    public static List<MotivoDeAviso> de(TipoDeAviso tipo) { return Arrays.stream(values()).filter(m -> m.tipo == tipo).toList(); }
}
