package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.factura.application.port.in.AdministrarTenantUseCase.TenantCreado;
import pe.factura.application.port.out.ApiKeyRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.ApiKey;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Establecimiento;
import pe.factura.domain.tenant.Serie;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class AdministrarTenantServiceTest {
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Series series = new Fakes.Series();
    Fakes.Establecimientos establecimientos = new Fakes.Establecimientos(series);
    Map<String, ApiKey> keys = new HashMap<>();
    ApiKeyRepository apiKeys = new ApiKeyRepository() {
        public void guardar(ApiKey k) { keys.put(k.hash(), k); }
        public Optional<ApiKey> buscarPorHash(String h) { return Optional.ofNullable(keys.get(h)); }
        public Optional<ApiKey> buscar(UUID id) { return keys.values().stream().filter(k -> k.id().equals(id)).findFirst(); }
        public List<ApiKey> listarPorTenant(UUID tenantId) { return keys.values().stream().filter(k -> k.tenantId().equals(tenantId)).toList(); }
    };
    AdministrarTenantService service = new AdministrarTenantService(tenants, series, apiKeys, Fakes.UOW, "pepper", Fakes.CLOCK, establecimientos);

    @Test void crearTenantDevuelveApiKeyUnaVez() {
        TenantCreado r = service.crearTenant("20100066603", "EMPRESA SAC", Entorno.BETA);
        assertThat(r.apiKeyEnClaro()).startsWith("fk_").hasSize(43);
        assertThat(keys).containsKey(ApiKeyGenerator.hash(r.apiKeyEnClaro(), "pepper"));
        assertThat(tenants.buscarPorRuc("20100066603")).isPresent();
    }

    @Test void rucDuplicadoFalla() {
        service.crearTenant("20100066603", "A", Entorno.BETA);
        assertThatThrownBy(() -> service.crearTenant("20100066603", "B", Entorno.BETA)).extracting("codigo").isEqualTo("DUPLICADO");
    }

    @Test void credencialesYSerie() {
        UUID id = service.crearTenant("20100066603", "A", Entorno.BETA).tenant().id();
        service.cargarCredencialesSol(id, "MODDATOS", "moddatos");
        assertThat(tenants.buscar(id).get().sol().usernameToken("20100066603")).isEqualTo("20100066603MODDATOS");
        service.crearSerie(id, TipoDocumento.FACTURA, "F001", 10);
        assertThat(series.siguienteNumero(id, TipoDocumento.FACTURA, "F001")).isEqualTo(11);
        assertThatThrownBy(() -> service.crearSerie(id, TipoDocumento.FACTURA, "B001", 0)).extracting("codigo").isEqualTo("SERIE_INVALIDA");
    }

    @Test void establecimientosAnexosYSeriesPorEstablecimiento() {
        UUID id = service.crearTenant("20100066603", "A", Entorno.BETA).tenant().id();
        Domicilio dom = Domicilio.de("150122", "Av. Larco 345");
        // Serie en un anexo que no existe: se rechaza antes de crearla.
        assertThatThrownBy(() -> service.crearSerie(id, TipoDocumento.FACTURA, "F002", 0, "0002")).extracting("codigo").isEqualTo("ESTABLECIMIENTO_INVALIDO");
        Establecimiento e = service.guardarEstablecimiento(id, "0002", "Tienda Miraflores", dom);
        assertThat(e.activo()).isTrue();
        assertThat(service.listarEstablecimientos(id)).extracting(Establecimiento::codigo).containsExactly("0002");
        service.crearSerie(id, TipoDocumento.FACTURA, "F002", 0, "0002");
        service.crearSerie(id, TipoDocumento.FACTURA, "F001", 0, null);
        assertThat(service.listarSeries(id)).extracting(Serie::codigo, Serie::establecimiento).containsExactlyInAnyOrder(tuple("F002", "0002"), tuple("F001", "0000"));
        // El 0000 no se registra como anexo.
        assertThatThrownBy(() -> service.guardarEstablecimiento(id, "0000", "Principal", dom)).hasMessageContaining("domicilio fiscal");
        // Con una serie activa no se puede dar de baja; editar conserva el estado.
        assertThatThrownBy(() -> service.desactivarEstablecimiento(id, "0002")).extracting("codigo").isEqualTo("ESTABLECIMIENTO_EN_USO");
        assertThat(service.guardarEstablecimiento(id, "0002", "Tienda Larco", dom).nombre()).isEqualTo("Tienda Larco");
        Establecimiento otro = service.guardarEstablecimiento(id, "0003", "Almacén", dom);
        assertThat(service.desactivarEstablecimiento(id, "0003").activo()).isFalse();
        assertThatThrownBy(() -> service.crearSerie(id, TipoDocumento.FACTURA, "F003", 0, "0003")).hasMessageContaining("dado de baja");
        assertThatThrownBy(() -> service.desactivarEstablecimiento(id, "0009")).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThat(otro.codigo()).isEqualTo("0003");
    }

    @Test void certificadoInvalidoFalla() {
        UUID id = service.crearTenant("20100066603", "A", Entorno.BETA).tenant().id();
        assertThatThrownBy(() -> service.cargarCertificado(id, new byte[]{1, 2, 3}, "x"))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("CERTIFICADO_INVALIDO");
    }

    /**
     * Las tres ventanas de vigencia contra el reloj fijo de los tests (2026-09-13). El caso del medio es el que
     * importa: una CA suele emitir la renovación arrancando el día que expira el certificado anterior, así que un
     * .p12 que llega hoy puede recién servir el mes que viene. Firmarlo antes hace que SUNAT devuelva 2327 con el
     * correlativo ya gastado.
     */
    @Test void vigenciaDelCertificado(@TempDir Path dir) throws Exception {
        UUID id = service.crearTenant("20100066603", "A", Entorno.BETA).tenant().id();

        byte[] vigente = p12(dir, "vigente", "2026/09/01 00:00:00", 365);
        byte[] futuro = p12(dir, "futuro", "2026/10/23 00:00:00", 365);
        byte[] vencido = p12(dir, "vencido", "2025/01/01 00:00:00", 30);

        service.cargarCertificado(id, vigente, CLAVE_P12);
        assertThat(tenants.buscar(id).get().certificado().vigenciaHasta()).isEqualTo(LocalDate.of(2027, 9, 1));

        assertThatThrownBy(() -> service.cargarCertificado(id, futuro, CLAVE_P12))
                .extracting("codigo").isEqualTo("CERTIFICADO_NO_VIGENTE");
        assertThatThrownBy(() -> service.cargarCertificado(id, vencido, CLAVE_P12))
                .extracting("codigo").isEqualTo("CERTIFICADO_VENCIDO");

        // Ninguno de los dos rechazos pisó al que sí servía.
        assertThat(tenants.buscar(id).get().certificado().vigenciaHasta()).isEqualTo(LocalDate.of(2027, 9, 1));
    }

    private static final String CLAVE_P12 = "secreto";

    /** Genera un PKCS#12 autofirmado con el RUC en el OU y una ventana de vigencia exacta. `keytool` viene con el JDK. */
    private static byte[] p12(Path dir, String nombre, String desde, int dias) throws Exception {
        Path archivo = dir.resolve(nombre + ".p12");
        Process p = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", nombre, "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=TEST,OU=20100066603,O=Khipu,C=PE",
                "-keystore", archivo.toString(), "-storetype", "PKCS12",
                "-storepass", CLAVE_P12, "-keypass", CLAVE_P12,
                "-startdate", desde, "-validity", String.valueOf(dias))
                .redirectErrorStream(true).start();
        String salida = new String(p.getInputStream().readAllBytes());
        assertThat(p.waitFor()).as("keytool: %s", salida).isZero();
        return Files.readAllBytes(archivo);
    }

    @Test void listaYRevocaApiKeys() {
        UUID id = service.crearTenant("20100066603", "A", Entorno.BETA).tenant().id();
        String segunda = service.crearApiKey(id);
        List<ApiKey> lista = service.listarApiKeys(id);
        assertThat(lista).hasSize(2).allSatisfy(k -> {
            assertThat(k.activa()).isTrue();
            assertThat(k.creadaEn()).isEqualTo(Fakes.CLOCK.instant());
            assertThat(k.revocadaEn()).isNull();
        });
        ApiKey k2 = keys.get(ApiKeyGenerator.hash(segunda, "pepper"));
        assertThat(k2.prefijo()).isEqualTo(segunda.substring(0, 10));

        service.revocarApiKey(id, k2.id());
        ApiKey revocada = keys.get(k2.hash());
        assertThat(revocada.activa()).isFalse();
        assertThat(revocada.revocadaEn()).isEqualTo(Fakes.CLOCK.instant());
        assertThat(service.listarApiKeys(id)).filteredOn(ApiKey::activa).hasSize(1);
    }

    @Test void revocarApiKeyDeOtroTenantEsNoEncontrado() {
        UUID a = service.crearTenant("20100066603", "A", Entorno.BETA).tenant().id();
        UUID b = service.crearTenant("20100066611", "B", Entorno.BETA).tenant().id();
        UUID keyDeB = service.listarApiKeys(b).get(0).id();
        assertThatThrownBy(() -> service.revocarApiKey(a, keyDeB)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.revocarApiKey(a, UUID.randomUUID())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThat(keys.get(service.listarApiKeys(b).get(0).hash()).activa()).isTrue();
    }

    @Test void ouDebeCoincidirExactamente() {
        assertThat(AdministrarTenantService.ouContieneRuc("CN=X,OU=20100066603,O=Y", "20100066603")).isTrue();
        assertThat(AdministrarTenantService.ouContieneRuc("CN=X,OU=201000666035,O=Y", "20100066603")).isFalse();
        assertThat(AdministrarTenantService.ouContieneRuc("CN=20100066603,OU=Otro,O=Y", "20100066603")).isFalse();
    }
}
