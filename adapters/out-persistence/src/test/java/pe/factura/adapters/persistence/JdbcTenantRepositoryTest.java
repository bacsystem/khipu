package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SecretCipher;
import pe.factura.domain.tenant.*;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcTenantRepositoryTest extends PersistenciaTestBase {
    SecretCipher cipher = new SecretCipher() {   // reversible y detectable en la BD
        public byte[] cifrar(byte[] p) { byte[] r = p.clone(); for (int i = 0; i < r.length; i++) r[i] ^= 0x5A; return r; }
        public byte[] descifrar(byte[] c) { return cifrar(c); }
    };
    JdbcTenantRepository repo = new JdbcTenantRepository(jdbc, cipher);

    @Test void guardaSecretosCifradosYLosRecupera() {
        Tenant t = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA SAC", Entorno.BETA,
                new CredencialesSol("MODDATOS", "moddatos"), new CertificadoDigital(new byte[]{1, 2, 3}, "clave", LocalDate.of(2030, 1, 1)));
        repo.guardar(t);
        byte[] enBd = jdbc.queryForObject("SELECT sol_clave_enc FROM tenant WHERE id = ?", byte[].class, t.id());
        assertThat(new String(enBd)).isNotEqualTo("moddatos");
        Tenant r = repo.buscar(t.id()).orElseThrow();
        assertThat(r.sol().clave()).isEqualTo("moddatos");
        assertThat(r.certificado().pkcs12()).containsExactly(1, 2, 3);
        assertThat(r.certificado().vigenciaHasta()).isEqualTo(LocalDate.of(2030, 1, 1));
        assertThat(repo.buscarPorRuc("20100066603")).isPresent();
    }

    @Test void guardaDomicilioFiscalYCuentaDeDetracciones() {
        Tenant t = new Tenant(UUID.randomUUID(), "20100066603", "A", Entorno.BETA, null, null)
                .conDatosFiscales(new Domicilio("150122", "Av. Larco 345 Of. 12", "Urb. Aurora", null, null, null, "0002"), "00-000-123456", "Andina Store");
        repo.guardar(t);
        Tenant r = repo.buscar(t.id()).orElseThrow();
        assertThat(r.domicilio()).isEqualTo(t.domicilio());
        assertThat(r.domicilio().distrito()).isEqualTo("MIRAFLORES");
        assertThat(r.cuentaDetracciones()).isEqualTo("00-000-123456");
        assertThat(r.nombreComercial()).isEqualTo("Andina Store");
        assertThat(r.padronTasaEspecialIgv()).isFalse();
        repo.guardar(r.conDatosFiscales(null, null, null, true));
        Tenant sin = repo.buscar(t.id()).orElseThrow();
        assertThat(sin.domicilio()).isNull();
        assertThat(sin.cuentaDetracciones()).isNull();
        assertThat(sin.padronTasaEspecialIgv()).isTrue();
    }

    @Test void guardaLaPersonalizacionDelPdfYPorDefectoEsLaClasica() {
        Tenant t = new Tenant(UUID.randomUUID(), "20100066603", "A", Entorno.BETA, null, null);
        repo.guardar(t);
        assertThat(repo.buscar(t.id()).orElseThrow().personalizacionPdf()).isEqualTo(PersonalizacionPdf.porDefecto());

        PersonalizacionPdf p = new PersonalizacionPdf(PlantillaPdf.CORPORATIVO, "#1f5f4a", t.id() + "/logo.png", "Gracias por su preferencia", "Entrega en 48 h\nSin devoluciones");
        repo.guardar(t.conPersonalizacionPdf(p));
        assertThat(repo.buscar(t.id()).orElseThrow().personalizacionPdf()).isEqualTo(p);
    }

    @Test void actualizaYPermiteNulos() {
        Tenant t = new Tenant(UUID.randomUUID(), "20100066603", "A", Entorno.BETA, null, null);
        repo.guardar(t);
        repo.guardar(t.conCredencialesSol(new CredencialesSol("U", "C")));
        Tenant r = repo.buscar(t.id()).orElseThrow();
        assertThat(r.sol().usuario()).isEqualTo("U");
        assertThat(r.certificado()).isNull();
    }
}
