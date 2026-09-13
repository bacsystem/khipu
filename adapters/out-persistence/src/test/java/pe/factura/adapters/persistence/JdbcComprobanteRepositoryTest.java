package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JdbcComprobanteRepositoryTest extends PersistenciaTestBase {
    JdbcComprobanteRepository repo = new JdbcComprobanteRepository(jdbc);
    Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    private Comprobante factura(UUID t, long numero) {
        Comprobante c = Comprobante.crearFactura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)), clock);
        c.asignarNumero(numero, "20100066603");
        return c;
    }

    @Test void guardaYRehidrataCompleto() {
        UUID t = tenantDePrueba();
        Comprobante c = factura(t, 1);
        c.firmar("HASH==", t + "/2026/09/20100066603-01-F001-1.xml");
        repo.guardar(c);
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "aceptada", List.of("4252 - obs")), "k/cdr.zip");
        repo.guardar(c);

        Comprobante r = repo.buscar(t, c.id()).orElseThrow();
        assertThat(r.estado()).isEqualTo(EstadoDocumento.ACEPTADO_CON_OBS);
        assertThat(r.numero()).isEqualTo(1L);
        assertThat(r.hash()).isEqualTo("HASH==");
        assertThat(r.items()).hasSize(2);
        assertThat(r.items().get(1).afectacion()).isEqualTo(TipoAfectacionIgv.EXONERADO);
        assertThat(r.totales().total()).isEqualByComparingTo("286.00");
        assertThat(r.cdr().observaciones()).containsExactly("4252 - obs");
        assertThat(r.cdrKey()).isEqualTo("k/cdr.zip");
        assertThat(r.receptor().razonSocial()).isEqualTo("CLIENTE SAC");
    }

    @Test void existeYUnicidad() {
        UUID t = tenantDePrueba();
        Comprobante c = factura(t, 5); c.firmar("h", "k"); repo.guardar(c);
        assertThat(repo.existe(t, TipoDocumento.FACTURA, "F001", 5)).isTrue();
        assertThat(repo.existe(t, TipoDocumento.FACTURA, "F001", 6)).isFalse();
        Comprobante dup = factura(t, 5); dup.firmar("h", "k");
        assertThatThrownBy(() -> repo.guardar(dup)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test void aislamientoPorTenant() {
        UUID t1 = tenantDePrueba(), t2 = tenantDePrueba();
        Comprobante c = factura(t1, 1); c.firmar("h", "k"); repo.guardar(c);
        assertThat(repo.buscar(t2, c.id())).isEmpty();
        assertThat(repo.listar(t1, null, 1, 10)).hasSize(1);
        assertThat(repo.listar(t2, null, 1, 10)).isEmpty();
    }

    @Test void observacionesConSaltosDeLineaSobrevivenAlRoundTrip() {
        UUID t = tenantDePrueba();
        Comprobante c = factura(t, 7);
        c.firmar("h", "k");
        repo.guardar(c);
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "aceptada", List.of("4252 - obs\ncon salto", "otra\tcon tab")), "k/cdr.zip");
        repo.guardar(c);

        Comprobante r = repo.buscar(t, c.id()).orElseThrow();
        assertThat(r.cdr().observaciones()).containsExactly("4252 - obs\ncon salto", "otra\tcon tab");
    }
}
