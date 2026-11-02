package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.ClienteGuardado.Datos;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Un cliente guardado se valida como el comprador de una boleta, para que lo guardado siempre se pueda usar al emitir (#319). */
class ClienteGuardadoTest {
    static final Instant AHORA = Instant.parse("2026-10-19T15:00:00Z");
    static final UUID TENANT = UUID.randomUUID();

    static Datos datos(String tipo, String num, String nombre) { return new Datos(tipo, num, nombre, null, null, null); }

    @Test void normalizaLosEspaciosElPaisYElCorreo() {
        ClienteGuardado c = ClienteGuardado.nuevo(TENANT, new Datos(" 6 ", " 20601234565 ", "  COMERCIAL ANDINA S.A.C.  ", "  Av. Javier Prado 123  ", " pe ", "  Facturas@Andina.PE "), AHORA);

        assertThat(c.tipoDoc()).isEqualTo("6");
        assertThat(c.numDoc()).isEqualTo("20601234565");
        assertThat(c.razonSocial()).isEqualTo("COMERCIAL ANDINA S.A.C.");
        assertThat(c.direccion()).isEqualTo("Av. Javier Prado 123");
        assertThat(c.pais()).isEqualTo("PE");
        assertThat(c.email()).isEqualTo("facturas@andina.pe");
        assertThat(c.creadoEn()).isEqualTo(AHORA).isEqualTo(c.actualizadoEn());
        assertThat(c.tenantId()).isEqualTo(TENANT);
    }

    @Test void lasOpcionalesEnBlancoQuedanNulas() {
        ClienteGuardado c = ClienteGuardado.nuevo(TENANT, new Datos("1", "12345678", "JUAN PEREZ", "   ", "", " "), AHORA);

        assertThat(c.direccion()).isNull();
        assertThat(c.pais()).isNull();
        assertThat(c.email()).isNull();
    }

    @Test void elDocumentoSeValidaComoEnUnaBoleta() {
        assertThat(ClienteGuardado.nuevo(TENANT, datos("1", "12345678", "JUAN PEREZ"), AHORA).numDoc()).isEqualTo("12345678");
        assertThat(ClienteGuardado.nuevo(TENANT, datos("7", "AB123456", "ANNA MULLER"), AHORA).tipoDoc()).isEqualTo("7");
        assertThatThrownBy(() -> ClienteGuardado.nuevo(TENANT, datos("1", "1234567", "JUAN PEREZ"), AHORA)).isInstanceOf(DomainException.class).hasMessageContaining("4207");
        assertThatThrownBy(() -> ClienteGuardado.nuevo(TENANT, datos("6", "20601234560", "ACME SAC"), AHORA)).hasMessageContaining("2017");
        assertThatThrownBy(() -> ClienteGuardado.nuevo(TENANT, datos("7", "con espacio", "ANNA MULLER"), AHORA)).hasMessageContaining("4208");
        assertThatThrownBy(() -> ClienteGuardado.nuevo(TENANT, datos("9", "123", "RARO"), AHORA)).hasMessageContaining("catálogo 06");
        assertThatThrownBy(() -> ClienteGuardado.nuevo(TENANT, datos(null, "123", "RARO"), AHORA)).isInstanceOf(DomainException.class);
    }

    @Test void sinDocumentoNoSeGuarda() {
        assertThatThrownBy(() -> ClienteGuardado.nuevo(TENANT, datos("-", "-", "CLIENTES VARIOS"), AHORA)).hasMessageContaining("no identifica a nadie");
    }

    @Test void elNombreTieneDeTresAMilQuinientosCaracteres() {
        assertThatThrownBy(() -> ClienteGuardado.nuevo(TENANT, datos("1", "12345678", "JP"), AHORA)).hasMessageContaining("2022");
        assertThatThrownBy(() -> ClienteGuardado.nuevo(TENANT, datos("1", "12345678", null), AHORA)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ClienteGuardado.nuevo(TENANT, datos("1", "12345678", "A".repeat(1501)), AHORA)).hasMessageContaining("2022");
        assertThat(ClienteGuardado.nuevo(TENANT, datos("1", "12345678", "A".repeat(1500)), AHORA).razonSocial()).hasSize(1500);
    }

    @Test void laDireccionElCorreoYElPaisTienenSusLimites() {
        assertThatThrownBy(() -> ClienteGuardado.nuevo(TENANT, new Datos("1", "12345678", "JUAN PEREZ", "x".repeat(301), null, null), AHORA)).hasMessageContaining("dirección");
        assertThatThrownBy(() -> ClienteGuardado.nuevo(TENANT, new Datos("1", "12345678", "JUAN PEREZ", "Av 1\nLima", null, null), AHORA)).hasMessageContaining("dirección");
        assertThatThrownBy(() -> ClienteGuardado.nuevo(TENANT, new Datos("1", "12345678", "JUAN PEREZ", null, null, "sin-arroba"), AHORA)).hasMessageContaining("correo");
        assertThatThrownBy(() -> ClienteGuardado.nuevo(TENANT, new Datos("1", "12345678", "JUAN PEREZ", null, null, "a@b"), AHORA)).hasMessageContaining("correo");
        assertThatThrownBy(() -> ClienteGuardado.nuevo(TENANT, new Datos("1", "12345678", "JUAN PEREZ", null, null, "a@" + "b".repeat(150) + ".pe"), AHORA)).hasMessageContaining("correo");
        assertThatThrownBy(() -> ClienteGuardado.nuevo(TENANT, new Datos("1", "12345678", "JUAN PEREZ", null, "XX", null), AHORA)).hasMessageContaining("ISO");
    }

    @Test void conOtrosDatosConservaElIdYLaFechaDeAlta() {
        ClienteGuardado c = ClienteGuardado.nuevo(TENANT, datos("1", "12345678", "JUAN PEREZ"), AHORA);
        Instant despues = AHORA.plusSeconds(3600);

        ClienteGuardado otro = c.con(datos("1", "87654321", "JUAN PEREZ GOMEZ"), despues);

        assertThat(otro.id()).isEqualTo(c.id());
        assertThat(otro.creadoEn()).isEqualTo(AHORA);
        assertThat(otro.actualizadoEn()).isEqualTo(despues);
        assertThat(otro.numDoc()).isEqualTo("87654321");
        assertThatThrownBy(() -> c.con(datos("1", "1", "JUAN"), despues)).isInstanceOf(DomainException.class);
    }
}
