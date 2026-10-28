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

    /** #40: la designación de agente se lee con la empresa, pero guardar la empresa no la escribe: la cambia solo el administrador, y unos datos fiscales viejos no la borran. */
    @Test void laDesignacionDeAgenteSeLeePeroGuardarLaEmpresaNoLaToca() {
        Tenant t = new Tenant(UUID.randomUUID(), "20100066603", "A", Entorno.BETA, null, null);
        repo.guardar(t.conAgente(new DesignacionDeAgente(true, true)));
        assertThat(repo.buscar(t.id()).orElseThrow().agente()).as("un alta nace sin designación").isEqualTo(DesignacionDeAgente.NINGUNA);
        jdbc.update("UPDATE tenant SET agente_retencion = true WHERE id = ?", t.id());
        assertThat(repo.buscar(t.id()).orElseThrow().agente()).isEqualTo(new DesignacionDeAgente(true, false));
        repo.guardar(t.conDatosFiscales(null, null, "Andina"));
        assertThat(repo.buscar(t.id()).orElseThrow().agente()).as("el tenant leído antes de la designación no la borra").isEqualTo(new DesignacionDeAgente(true, false));
    }

    @Test void guardaLaPersonalizacionDelPdfYPorDefectoEsLaClasica() {
        Tenant t = new Tenant(UUID.randomUUID(), "20100066603", "A", Entorno.BETA, null, null);
        repo.guardar(t);
        assertThat(repo.buscar(t.id()).orElseThrow().personalizacionPdf()).isEqualTo(PersonalizacionPdf.porDefecto());

        PersonalizacionPdf p = new PersonalizacionPdf(PlantillaPdf.CORPORATIVO, "#1f5f4a", t.id() + "/logo.png", "Gracias por su preferencia", "Entrega en 48 h\nSin devoluciones");
        repo.guardar(t.conPersonalizacionPdf(p));
        assertThat(repo.buscar(t.id()).orElseThrow().personalizacionPdf()).isEqualTo(p);
    }

    /** #318: el client_id y el client_secret se guardan cifrados (nunca en claro en la base), vuelven iguales y se borran sin tocar la clave SOL. */
    @Test void guardaLasCredencialesApiSunatCifradasYLasBorra() {
        Tenant t = new Tenant(UUID.randomUUID(), "20100066603", "A", Entorno.BETA, new CredencialesSol("MODDATOS", "moddatos"), null)
                .conCredencialesApiSunat(new CredencialesApiSunat("d20b421e-4ace-46d8-ac8c-4fbb44cfde17", "HhWwZVo8ZaWm4BZ7n/xEzw=="));
        repo.guardar(t);

        byte[] secretoEnBd = jdbc.queryForObject("SELECT api_sunat_client_secret_enc FROM tenant WHERE id = ?", byte[].class, t.id());
        byte[] idEnBd = jdbc.queryForObject("SELECT api_sunat_client_id_enc FROM tenant WHERE id = ?", byte[].class, t.id());
        assertThat(new String(secretoEnBd)).doesNotContain("HhWwZVo8");
        assertThat(new String(idEnBd)).doesNotContain("d20b421e");
        Tenant r = repo.buscar(t.id()).orElseThrow();
        assertThat(r.apiSunat()).isEqualTo(t.apiSunat());

        // Cambiar otro dato no las pierde (LOG-05: guardar ya no toca los secretos; cada sección escribe lo suyo).
        repo.guardar(r.conDatosFiscales(null, "00-000-123456", null, false));
        assertThat(repo.buscar(t.id()).orElseThrow().apiSunat()).isEqualTo(t.apiSunat());

        repo.guardarCredencialesApiSunat(t.id(), null);
        Tenant sin = repo.buscar(t.id()).orElseThrow();
        assertThat(sin.apiSunat()).isNull();
        assertThat(sin.sol().clave()).isEqualTo("moddatos");
        assertThat(jdbc.queryForObject("SELECT api_sunat_client_secret_enc IS NULL FROM tenant WHERE id = ?", Boolean.class, t.id())).isTrue();
    }

    /** La base no admite media credencial: un client_id sin secreto sería una empresa que «tiene» credenciales que nunca sirven. */
    @Test void laBaseNoAdmiteMediaCredencial() {
        Tenant t = new Tenant(UUID.randomUUID(), "20100066603", "A", Entorno.BETA, null, null);
        repo.guardar(t);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("UPDATE tenant SET api_sunat_client_id_enc = ? WHERE id = ?", new byte[]{1}, t.id()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void actualizaYPermiteNulos() {
        Tenant t = new Tenant(UUID.randomUUID(), "20100066603", "A", Entorno.BETA, null, null);
        repo.guardar(t);
        repo.guardarCredencialesSol(t.id(), new CredencialesSol("U", "C"));
        Tenant r = repo.buscar(t.id()).orElseThrow();
        assertThat(r.sol().usuario()).isEqualTo("U");
        assertThat(r.certificado()).isNull();
    }

    /**
     * LOG-05: sobre una fila existente, guardar no toca el entorno ni los secretos. Antes reescribía la fila entera con el objeto que el llamador había leído: unos datos
     * fiscales guardados con un tenant leído antes de un cambio de entorno devolvían la empresa a BETA sin bitácora, y un certificado guardado con uno leído antes de
     * que otro cargara las credenciales SOL las revertía.
     */
    @Test void guardarUnaFilaExistenteNoTocaElEntornoNiLosSecretos() {
        Tenant leidoAntes = new Tenant(UUID.randomUUID(), "20100066603", "A", Entorno.BETA, new CredencialesSol("VIEJO", "vieja"),
                new CertificadoDigital(new byte[]{1}, "c1", LocalDate.of(2030, 1, 1)));
        repo.guardar(leidoAntes);
        // Otro proceso: el administrador pasa la empresa a producción (UPDATE condicional de una columna) y un usuario carga credenciales SOL y API SUNAT nuevas
        jdbc.update("UPDATE tenant SET entorno = 'PRODUCCION' WHERE id = ? AND entorno = 'BETA'", leidoAntes.id());
        repo.guardarCredencialesSol(leidoAntes.id(), new CredencialesSol("NUEVO", "nueva"));
        repo.guardarCredencialesApiSunat(leidoAntes.id(), new CredencialesApiSunat("d20b421e-4ace-46d8-ac8c-4fbb44cfde17", "HhWwZVo8ZaWm4BZ7n/xEzw=="));
        repo.guardarCertificado(leidoAntes.id(), new CertificadoDigital(new byte[]{2}, "c2", LocalDate.of(2031, 1, 1)));

        // El primero confirma con lo que había leído
        repo.guardar(leidoAntes.conDatosFiscales(null, "00-000-123456", "Andina", true));

        Tenant r = repo.buscar(leidoAntes.id()).orElseThrow();
        assertThat(r.entorno()).as("sigue en producción").isEqualTo(Entorno.PRODUCCION);
        assertThat(r.sol().usuario()).as("las SOL nuevas").isEqualTo("NUEVO");
        assertThat(r.apiSunat().clientId()).isEqualTo("d20b421e-4ace-46d8-ac8c-4fbb44cfde17");
        assertThat(r.certificado().pkcs12()).containsExactly(2);
        assertThat(r.cuentaDetracciones()).as("lo suyo sí se guardó").isEqualTo("00-000-123456");
        assertThat(r.nombreComercial()).isEqualTo("Andina");
    }

    /** LOG-05: cada escritura por sección toca solo sus columnas y exige que la empresa exista. */
    @Test void lasEscriturasPorSeccionSoloTocanSuSeccion() {
        Tenant t = new Tenant(UUID.randomUUID(), "20100066603", "A", Entorno.BETA, new CredencialesSol("MODDATOS", "moddatos"), null)
                .conDatosFiscales(new Domicilio("150122", "Av. Larco 345", null, null, null, null, "0001"), "00-000-111111", "Tienda");
        repo.guardar(t);
        PersonalizacionPdf p = new PersonalizacionPdf(PlantillaPdf.CORPORATIVO, "#1f5f4a", t.id() + "/logo.png", "Gracias", null);
        repo.guardarPersonalizacionPdf(t.id(), p);
        repo.guardarDatosFiscales(t.id(), null, "00-000-222222", "Tienda 2", true);
        Tenant r = repo.buscar(t.id()).orElseThrow();
        assertThat(r.personalizacionPdf()).isEqualTo(p);
        assertThat(r.domicilio()).isNull();
        assertThat(r.cuentaDetracciones()).isEqualTo("00-000-222222");
        assertThat(r.padronTasaEspecialIgv()).isTrue();
        assertThat(r.sol().usuario()).as("los datos fiscales no tocan las SOL").isEqualTo("MODDATOS");
        repo.guardarCertificado(t.id(), new CertificadoDigital(new byte[]{9}, "c", LocalDate.of(2030, 1, 1)));
        assertThat(repo.buscar(t.id()).orElseThrow().personalizacionPdf()).as("el certificado no toca el PDF").isEqualTo(p);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repo.guardarCredencialesSol(UUID.randomUUID(), new CredencialesSol("U", "C")))
                .extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }
}
