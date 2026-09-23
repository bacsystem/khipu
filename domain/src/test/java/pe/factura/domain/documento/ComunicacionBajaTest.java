package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guardas de la comunicación de baja (hoja Comunicación de Baja1_0) que la auditoría del flujo encontró sin test: 2308 (boleta),
 * 2315 (motivo hasta 100, sin caracteres de control), frontera del plazo 2957 (día 7 sí, día 8 no), correlativo y la
 * irreversibilidad de la máquina de estados (una baja ACEPTADA o RECHAZADA no vuelve a transitar).
 */
class ComunicacionBajaTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T15:00:00Z"), ZoneId.of("America/Lima")); // 2026-09-20 en Lima
    static final Receptor RECEPTOR = new Receptor("6", "20601234565", "CLIENTE SAC", null);

    static Comprobante aceptado(TipoDocumento tipo, String serie, LocalDate emision) {
        return Comprobante.persistido(UUID.randomUUID(), UUID.randomUUID(), tipo, serie, 1L, emision, EstadoDocumento.ACEPTADO, RECEPTOR,
                List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).firma("h", "n", "k").rehidratar();
    }

    @Test void plazoDeSieteDiasCalendario() {
        assertThat(ComunicacionBaja.crear(aceptado(TipoDocumento.FACTURA, "F001", LocalDate.of(2026, 9, 13)), 1, "Error", CLOCK).estado()).isEqualTo(ComunicacionBaja.EstadoBaja.GENERADA);
        assertThatThrownBy(() -> ComunicacionBaja.crear(aceptado(TipoDocumento.FACTURA, "F001", LocalDate.of(2026, 9, 12)), 1, "Error", CLOCK)).hasMessageContaining("2957");
    }

    @Test void lasBoletasNoVanEnComunicacionDeBaja() {
        assertThatThrownBy(() -> ComunicacionBaja.crear(aceptado(TipoDocumento.BOLETA, "B001", LocalDate.of(2026, 9, 20)), 1, "Error", CLOCK))
                .isInstanceOf(DomainException.class).hasMessageContaining("2308");
    }

    @Test void elMotivoVaDe3A100SinCaracteresDeControl() {
        Comprobante c = aceptado(TipoDocumento.FACTURA, "F001", LocalDate.of(2026, 9, 20));
        assertThatThrownBy(() -> ComunicacionBaja.crear(c, 1, "a".repeat(101), CLOCK)).hasMessageContaining("2315");
        assertThatThrownBy(() -> ComunicacionBaja.crear(c, 1, "Error\tRUC", CLOCK)).hasMessageContaining("2315");
        assertThatThrownBy(() -> ComunicacionBaja.crear(c, 1, "ab", CLOCK)).hasMessageContaining("2315");
        assertThat(ComunicacionBaja.crear(c, 1, "a".repeat(100), CLOCK).motivo()).hasSize(100);
    }

    @Test void elCorrelativoDelDiaEmpiezaEnUno() {
        Comprobante c = aceptado(TipoDocumento.FACTURA, "F001", LocalDate.of(2026, 9, 20));
        assertThatThrownBy(() -> ComunicacionBaja.crear(c, 0, "Error", CLOCK)).hasMessageContaining("correlativo");
        assertThat(ComunicacionBaja.crear(c, 12, "Error", CLOCK).identificador()).isEqualTo("RA-20260920-12");
    }

    @Test void unaBajaResueltaNoVuelveATransitar() {
        ComunicacionBaja aceptada = ComunicacionBaja.crear(aceptado(TipoDocumento.FACTURA, "F001", LocalDate.of(2026, 9, 20)), 1, "Error", CLOCK);
        aceptada.marcarEnviada("T-1");
        aceptada.aplicarCdr(new Cdr("0", "aceptada", List.of()), "R.zip");
        assertThat(aceptada.pendiente()).isFalse();
        assertThatThrownBy(() -> aceptada.aplicarCdr(new Cdr("2323", "ya informado", List.of()), "R.zip")).hasMessageContaining("no puede pasar");
        assertThatThrownBy(() -> aceptada.marcarEnviada("T-2")).hasMessageContaining("no puede pasar");
        ComunicacionBaja rechazada = ComunicacionBaja.crear(aceptado(TipoDocumento.FACTURA, "F001", LocalDate.of(2026, 9, 20)), 2, "Error", CLOCK);
        rechazada.marcarEnviada("T-3");
        rechazada.rechazarPorFault("0127", "El ticket no existe");
        assertThatThrownBy(() -> rechazada.marcarEnviada("T-4")).hasMessageContaining("no puede pasar");
        assertThatThrownBy(() -> rechazada.aplicarCdr(new Cdr("0", "aceptada", List.of()), "R.zip")).hasMessageContaining("no puede pasar");
    }
}
