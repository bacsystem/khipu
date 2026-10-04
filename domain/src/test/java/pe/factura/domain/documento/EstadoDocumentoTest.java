package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.factura.domain.documento.EstadoDocumento.*;

class EstadoDocumentoTest {
    @Test void transicionesValidas() {
        assertThat(RECIBIDO.puedeTransitarA(FIRMADO)).isTrue();
        assertThat(FIRMADO.puedeTransitarA(ENVIADO)).isTrue();
        assertThat(FIRMADO.puedeTransitarA(ERROR_ENVIO)).isTrue();
        assertThat(ERROR_ENVIO.puedeTransitarA(ENVIADO)).isTrue();
        assertThat(ENVIADO.puedeTransitarA(ACEPTADO)).isTrue();
        assertThat(ENVIADO.puedeTransitarA(ACEPTADO_CON_OBS)).isTrue();
        assertThat(ENVIADO.puedeTransitarA(RECHAZADO)).isTrue();
        assertThat(ACEPTADO.puedeTransitarA(ANULADO)).isTrue();
    }
    @Test void transicionesInvalidas() {
        assertThat(RECIBIDO.puedeTransitarA(ACEPTADO)).isFalse();
        assertThat(RECHAZADO.puedeTransitarA(ENVIADO)).isFalse();
        assertThat(ACEPTADO.puedeTransitarA(ENVIADO)).isFalse();
    }
    /**
     * El consumo de un plan (#192) cuenta solo los comprobantes que SUNAT aceptó. Cada estado se clasifica a propósito: un estado nuevo no puede colarse en el
     * cobro (ni quedarse fuera) sin que este test obligue a decidir. {@code ANULADO} **sí** cuenta: solo se llega a él desde un aceptado, y lo aceptado ya
     * consumió; dar de baja no devuelve el documento al cupo ni cambia el consumo de un mes ya cerrado. «Las bajas no cuentan» es la comunicación de baja, que no
     * suma otro documento.
     */
<<<<<<< HEAD
    @Test void loQueSunatAceptoCuentaParaElConsumoAunqueDespuesSeDeDeBaja() {
        assertThat(EnumSet.allOf(EstadoDocumento.class).stream().filter(EstadoDocumento::cuentaParaElConsumo).toList()).containsExactlyInAnyOrder(ACEPTADO, ACEPTADO_CON_OBS, ANULADO);
        for (EstadoDocumento e : new EstadoDocumento[]{RECIBIDO, INVALIDO, FIRMADO, ERROR_ENVIO, PENDIENTE_AGRUPACION, ENVIADO, RECHAZADO, FUERA_DE_PLAZO})
=======
    @Test void soloLoAceptadoCuentaParaElConsumo() {
        assertThat(EnumSet.allOf(EstadoDocumento.class).stream().filter(EstadoDocumento::cuentaParaElConsumo).toList()).containsExactlyInAnyOrder(ACEPTADO, ACEPTADO_CON_OBS);
        for (EstadoDocumento e : new EstadoDocumento[]{RECIBIDO, INVALIDO, FIRMADO, ERROR_ENVIO, PENDIENTE_AGRUPACION, ENVIADO, RECHAZADO, ANULADO, FUERA_DE_PLAZO, DESCARTADO})
>>>>>>> dadeacb9 (feat(admin): cola global de errores con reintento y descarte (#196))
            assertThat(e.cuentaParaElConsumo()).as(e.name()).isFalse();
    }

    /** El resumen del portal (#15): cada estado cae en un lugar y los tres criterios no se pisan por accidente. Si se agrega un estado, este test obliga a decidir dónde cuenta. */
    @Test void soloLosFirmadosEnAdelanteSeEmitieron() {
        assertThat(EnumSet.allOf(EstadoDocumento.class).stream().filter(EstadoDocumento::fueEmitido).toList())
                .containsExactlyInAnyOrder(FIRMADO, ERROR_ENVIO, PENDIENTE_AGRUPACION, ENVIADO, ACEPTADO, ACEPTADO_CON_OBS, RECHAZADO, ANULADO, FUERA_DE_PLAZO, DESCARTADO);
        assertThat(RECIBIDO.fueEmitido()).isFalse();
        assertThat(INVALIDO.fueEmitido()).isFalse();
    }

    @Test void pideAtencionLoRechazadoLoQueNoLlegoYLoQueSePasoDelPlazo() {
        assertThat(EnumSet.allOf(EstadoDocumento.class).stream().filter(EstadoDocumento::requiereAtencion).toList()).containsExactlyInAnyOrder(RECHAZADO, ERROR_ENVIO, FUERA_DE_PLAZO);
        for (EstadoDocumento e : new EstadoDocumento[]{RECIBIDO, INVALIDO, FIRMADO, PENDIENTE_AGRUPACION, ENVIADO, ACEPTADO, ACEPTADO_CON_OBS, ANULADO, DESCARTADO})
            assertThat(e.requiereAtencion()).as(e.name()).isFalse();
    }

    @Test void facturaLoAceptadoYLoQueEstaEnCaminoPeroNoLoRechazadoNiLoAnulado() {
        assertThat(EnumSet.allOf(EstadoDocumento.class).stream().filter(EstadoDocumento::cuentaComoFacturado).toList())
                .containsExactlyInAnyOrder(FIRMADO, ERROR_ENVIO, PENDIENTE_AGRUPACION, ENVIADO, ACEPTADO, ACEPTADO_CON_OBS);
        for (EstadoDocumento e : new EstadoDocumento[]{RECIBIDO, INVALIDO, RECHAZADO, ANULADO, FUERA_DE_PLAZO, DESCARTADO})
            assertThat(e.cuentaComoFacturado()).as(e.name()).isFalse();
    }

    /** Lo facturado siempre fue emitido: no puede haber plata facturada de un documento que nunca se firmó. */
    @Test void loFacturadoSiempreFueEmitido() {
        for (EstadoDocumento e : EstadoDocumento.values()) if (e.cuentaComoFacturado()) assertThat(e.fueEmitido()).as(e.name()).isTrue();
    }

    /** El monitor (#195) reparte cada estado en una categoría: aceptado, en camino, atención o «otros». Las tres primeras no se pisan entre sí. */
    @Test void soloLoFirmadoEnviadoOEnAgrupacionEstaEnCamino() {
        assertThat(EnumSet.allOf(EstadoDocumento.class).stream().filter(EstadoDocumento::estaEnCamino).toList()).containsExactlyInAnyOrder(FIRMADO, ENVIADO, PENDIENTE_AGRUPACION);
        for (EstadoDocumento e : new EstadoDocumento[]{RECIBIDO, INVALIDO, ERROR_ENVIO, ACEPTADO, ACEPTADO_CON_OBS, RECHAZADO, ANULADO, FUERA_DE_PLAZO, DESCARTADO})
            assertThat(e.estaEnCamino()).as(e.name()).isFalse();
    }

    @Test void lasCategoriasDelMonitorNoSePisan() {
        for (EstadoDocumento e : EstadoDocumento.values()) {
            int categorias = (e.esFinalAceptado() ? 1 : 0) + (e.estaEnCamino() ? 1 : 0) + (e.requiereAtencion() ? 1 : 0);
            assertThat(categorias).as(e.name()).isLessThanOrEqualTo(1);
        }
    }

    /**
     * Descartar (#196) es dejar de intentar un envío que falla: solo se descarta lo que está en error de envío, y de ahí no se vuelve. No se descarta lo que SUNAT ya
     * resolvió, lo que está en camino ni lo que ya es terminal.
     */
    @Test void soloUnErrorDeEnvioSePuedeDescartarYDeAhiNoSeVuelve() {
        assertThat(EnumSet.allOf(EstadoDocumento.class).stream().filter(e -> e.puedeTransitarA(DESCARTADO)).toList()).containsExactly(ERROR_ENVIO);
        assertThat(EnumSet.allOf(EstadoDocumento.class).stream().filter(e -> DESCARTADO.puedeTransitarA(e)).toList()).isEmpty();
        assertThat(DESCARTADO.esEnviable()).isFalse();
    }

    @Test void enviable() {
        assertThat(FIRMADO.esEnviable()).isTrue();
        assertThat(ERROR_ENVIO.esEnviable()).isTrue();
        assertThat(ACEPTADO.esEnviable()).isFalse();
    }
}
