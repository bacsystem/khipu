package pe.factura.domain.plataforma;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Por qué se le avisa a un cliente (#197). Es más fino que el tipo a propósito: «por vencer» y «vencido» son avisos distintos, así que pasar de uno al otro habilita un aviso
 * nuevo aunque no haya pasado la semana; repetir el **mismo** motivo, no. El aviso lo manda un administrador a mano (no hay envío automático), y el registro de lo enviado es
 * lo que impide mandarlo todos los días.
 */
public enum MotivoDeAviso {
    CERTIFICADO_POR_VENCER(TipoDeAviso.CERTIFICADO),
    CERTIFICADO_VENCIDO(TipoDeAviso.CERTIFICADO),
    CREDENCIALES_SOL_INVALIDAS(TipoDeAviso.CREDENCIALES_SOL);

    /** Lo que hay que esperar para repetir el mismo aviso a la misma empresa. */
    public static final Duration ENFRIAMIENTO = Duration.ofDays(7);

    private final TipoDeAviso tipo;

    MotivoDeAviso(TipoDeAviso tipo) { this.tipo = tipo; }

    public TipoDeAviso tipo() { return tipo; }

    /** Desde cuándo se puede repetir el aviso que se mandó en {@code ultimoAviso}. */
    public Instant avisarDesde(Instant ultimoAviso) { return ultimoAviso.plus(ENFRIAMIENTO); }

    /** Los motivos de un tipo, en el orden en que se declaran. */
    public static List<MotivoDeAviso> de(TipoDeAviso tipo) { return Arrays.stream(values()).filter(m -> m.tipo == tipo).toList(); }
}
