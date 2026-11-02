package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ConsultarValidezUseCase.Criterios;
import pe.factura.application.port.in.ConsultarValidezUseCase.Fuente;
import pe.factura.application.port.in.ConsultarValidezUseCase.Validez;
import pe.factura.application.port.out.SunatApiValidezGateway;
import pe.factura.application.port.out.SunatCredencialesException;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.CredencialesApiSunat;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #318: la validez elige la API REST si hay credenciales API, cae a SOAP solo si la API no está disponible, y nunca ante un rechazo de la consulta. */
class ConsultarValidezServiceTest {
    static final CredencialesApiSunat API = new CredencialesApiSunat("d20b421e-4ace-46d8-ac8c-4fbb44cfde17", "secreto");
    static final Criterios Q = new Criterios("20100066603", "01", "F001", 125, null, null, LocalDate.of(2026, 9, 14), new BigDecimal("1180.00"));

    /** La API de prueba: respuesta fija o falla, y cuántas veces se la llamó. */
    static final class ApiFalsa implements SunatApiValidezGateway {
        Validez respuesta = new Validez("1", "00", "00", List.of("- Fecha de emisión ingresada no coincide con la registrada en la SUNAT."));
        RuntimeException falla; int llamadas; Object[] criterios;
        public Validez validar(Tenant t, String ruc, String tipo, String serie, long numero, LocalDate f, BigDecimal m) {
            llamadas++; criterios = new Object[]{ruc, tipo, serie, numero, f, m};
            if (falla != null) throw falla;
            return respuesta;
        }
    }

    final Fakes.Tenants tenants = new Fakes.Tenants();
    final Fakes.Consultas soap = new Fakes.Consultas();
    final ApiFalsa api = new ApiFalsa();
    final ConsultarValidezService service = new ConsultarValidezService(tenants, soap, api);
    final UUID id = UUID.randomUUID();

    Tenant conSol() { Tenant t = Fakes.tenantListo(id); tenants.guardar(t); return t; }
    Tenant conApiYSol() { Tenant t = Fakes.tenantListo(id).conCredencialesApiSunat(API); tenants.guardar(t); return t; }
    Tenant soloApi() { Tenant t = Fakes.tenantListo(id).conCredencialesSol(null).conCredencialesApiSunat(API); tenants.guardar(t); return t; }

    @Test void conCredencialesApiPreguntaPorLaApiYNoPorSoap() {
        conApiYSol();

        Validez v = service.consultar(id, Q);

        assertThat(v.fuente()).isEqualTo(Fuente.API_REST);
        assertThat(v.estado()).isEqualTo("ACEPTADO");
        assertThat(v.codigo()).isEqualTo("1");
        assertThat(v.estadoRuc()).isEqualTo("00");
        assertThat(v.condicionDomicilio()).isEqualTo("00");
        assertThat(v.observaciones()).hasSize(1);
        assertThat(v.aviso()).isNull();
        assertThat(api.criterios).containsExactly("20100066603", "01", "F001", 125L, LocalDate.of(2026, 9, 14), new BigDecimal("1180.00"));
        assertThat(soap.llamadas).isZero();
    }

    @Test void cadaEstadoDeLaApiTieneSuEstado() {
        soloApi();
        for (String[] par : new String[][]{{"0", "NO_EXISTE"}, {"1", "ACEPTADO"}, {"2", "DE_BAJA"}, {"3", "AUTORIZADO"}, {"4", "NO_AUTORIZADO"}, {"9", "ERROR_CONSULTA"}}) {
            api.respuesta = new SunatApiValidezGateway.Validez(par[0], null, null, List.of());
            Validez v = service.consultar(id, Q);
            assertThat(v.estado()).as(par[0]).isEqualTo(par[1]);
            assertThat(v.mensaje()).as(par[0]).isNotBlank();
        }
    }

    @Test void sinCredencialesApiVaPorSoapComoAntes() {
        conSol();

        Validez v = service.consultar(id, Q);

        assertThat(v.fuente()).isEqualTo(Fuente.SOAP);
        assertThat(v.estado()).isEqualTo("ACEPTADO");
        assertThat(v.codigo()).isEqualTo("0001");
        assertThat(api.llamadas).isZero();
    }

    @Test void siLaApiNoRespondeYHayClaveSolCaeASoapYLoDice() {
        conApiYSol();
        api.falla = new SunatTransientException("0000", "SUNAT respondió HTTP 503");

        Validez v = service.consultar(id, Q);

        assertThat(v.fuente()).isEqualTo(Fuente.SOAP);
        assertThat(v.estado()).isEqualTo("ACEPTADO");
        assertThat(v.aviso()).contains("La API de SUNAT no respondió").contains("503").contains("billValidService");
        assertThat(soap.llamadas).isEqualTo(1);
    }

    @Test void siLaApiRechazaLasCredencialesYHayClaveSolCaeASoap() {
        conApiYSol();
        api.falla = new SunatCredencialesException("401", "SUNAT no acepta el client_id o el client_secret");

        Validez v = service.consultar(id, Q);

        assertThat(v.fuente()).isEqualTo(Fuente.SOAP);
        assertThat(v.aviso()).contains("no aceptó las credenciales API SUNAT");
    }

    /**
     * H1: mientras el contrato con la API no se verifique contra SUNAT real (deploy/README.md §13), un rechazo de la consulta también cae a SOAP si hay clave SOL:
     * si SUNAT no aceptara el formato que se le manda, una empresa que hoy valida por SOAP no pierde la consulta al cargar credenciales API.
     */
    @Test void unRechazoDeLaConsultaCaeASoapMientrasElContratoNoEsteVerificado() {
        conApiYSol();
        api.falla = new SunatRechazoException("422", "El número de serie no tiene un formato válido");

        Validez v = service.consultar(id, Q);

        assertThat(v.fuente()).isEqualTo(Fuente.SOAP);
        assertThat(v.aviso()).contains("La API de SUNAT rechazó la consulta").contains("formato válido").contains("billValidService");
        assertThat(soap.llamadas).isEqualTo(1);
    }

    @Test void sinClaveSolUnRechazoDeLaConsultaSeDevuelveTalCual() {
        soloApi();
        api.falla = new SunatRechazoException("422", "El número de serie no tiene un formato válido");

        assertThatThrownBy(() -> service.consultar(id, Q)).isInstanceOf(DomainException.class)
                .extracting("codigo").isEqualTo("CONSULTA_RECHAZADA");
        assertThat(soap.llamadas).isZero();
    }

    @Test void unRechazoQueTampocoPuedeIrPorSoapEnBetaSeDevuelveComoRechazo() {
        conApiYSol();
        api.falla = new SunatRechazoException("422", "formato");
        soap.falla = new DomainException("NO_DISPONIBLE_EN_BETA", "SUNAT no publica la consulta de validez en e-beta");

        assertThatThrownBy(() -> service.consultar(id, Q)).extracting("codigo").isEqualTo("CONSULTA_RECHAZADA");
    }

    @Test void sinClaveSolElFalloDeLaApiSeTraduceSinFallback() {
        soloApi();
        api.falla = new SunatTransientException("0109", "Tiempo de espera agotado llamando a SUNAT");
        assertThatThrownBy(() -> service.consultar(id, Q)).extracting("codigo").isEqualTo("SUNAT_NO_DISPONIBLE");

        api.falla = new SunatCredencialesException("401", "SUNAT no acepta el client_id o el client_secret");
        assertThatThrownBy(() -> service.consultar(id, Q)).extracting("codigo").isEqualTo("CREDENCIALES_API_SUNAT_RECHAZADAS");
        assertThat(soap.llamadas).isZero();
    }

    /** En BETA billValidService no existe: tras un fallo de la API, el error útil es el de la API y no «no disponible en beta». */
    @Test void siElFallbackNoExisteEnBetaSeDevuelveElErrorDeLaApi() {
        conApiYSol();
        api.falla = new SunatTransientException("0000", "SUNAT respondió HTTP 500");
        soap.falla = new DomainException("NO_DISPONIBLE_EN_BETA", "SUNAT no publica la consulta de validez en e-beta");

        assertThatThrownBy(() -> service.consultar(id, Q)).extracting("codigo").isEqualTo("SUNAT_NO_DISPONIBLE");
    }

    @Test void siTambienFallaSoapSeDicenLosDos() {
        conApiYSol();
        api.falla = new SunatTransientException("0000", "SUNAT respondió HTTP 500");
        soap.falla = new SunatTransientException("0109", "Tiempo de espera agotado llamando a SUNAT");

        assertThatThrownBy(() -> service.consultar(id, Q)).extracting("codigo").isEqualTo("SUNAT_NO_DISPONIBLE");
        assertThatThrownBy(() -> service.consultar(id, Q)).hasMessageContaining("HTTP 500").hasMessageContaining("Tiempo de espera");
    }

    @Test void sinFechaLaApiNoConsultaYSeVaPorSoapSiHayClaveSol() {
        conApiYSol();
        Criterios sinFecha = new Criterios("20100066603", "01", "F001", 125, null, null, null, null);

        Validez v = service.consultar(id, sinFecha);

        assertThat(v.fuente()).isEqualTo(Fuente.SOAP);
        assertThat(v.aviso()).contains("fecha");
        assertThat(api.llamadas).isZero();
    }

    /** H3: en BETA sin fecha, ir a SOAP daba «no disponible en beta»; lo que falta es la fecha. */
    @Test void enBetaSinFechaNiSoapLoQueFaltaEsLaFecha() {
        conApiYSol();
        soap.falla = new DomainException("NO_DISPONIBLE_EN_BETA", "SUNAT no publica la consulta de validez en e-beta");

        assertThatThrownBy(() -> service.consultar(id, new Criterios("20100066603", "01", "F001", 125, null, null, null, null)))
                .isInstanceOf(DomainException.class).hasMessageContaining("fecha").extracting("codigo").isEqualTo("PARAMETRO_INVALIDO");
        assertThat(api.llamadas).isZero();
    }

    @Test void sinFechaNiClaveSolEsUnParametroInvalido() {
        soloApi();
        assertThatThrownBy(() -> service.consultar(id, new Criterios("20100066603", "01", "F001", 125, null, null, null, null)))
                .extracting("codigo").isEqualTo("PARAMETRO_INVALIDO");
    }

    @Test void sinNingunaCredencialElMensajeDiceQueConfigurar() {
        tenants.guardar(Fakes.tenantListo(id).conCredencialesSol(null));
        assertThatThrownBy(() -> service.consultar(id, Q)).extracting("codigo").isEqualTo("CREDENCIALES_SUNAT_NO_CARGADAS");
        assertThatThrownBy(() -> service.consultar(id, Q)).hasMessageContaining("credenciales API SUNAT").hasMessageContaining("credenciales SOL");
    }

    @Test void losFallosDeSoapYaNoSonUnErrorInterno() {
        conSol();
        soap.falla = new SunatTransientException("0109", "Tiempo de espera agotado llamando a SUNAT");
        assertThatThrownBy(() -> service.consultar(id, Q)).extracting("codigo").isEqualTo("SUNAT_NO_DISPONIBLE");
        soap.falla = new SunatCredencialesException("0102", "Usuario o contraseña incorrectos");
        assertThatThrownBy(() -> service.consultar(id, Q)).extracting("codigo").isEqualTo("CREDENCIALES_SOL_RECHAZADAS");
        soap.falla = new SunatRechazoException("1033", "Registrado previamente");
        assertThatThrownBy(() -> service.consultar(id, Q)).extracting("codigo").isEqualTo("CONSULTA_RECHAZADA");
    }

    @Test void unNumeroDeMasDeOchoDigitosEsInvalido() {
        conApiYSol();
        assertThatThrownBy(() -> service.consultar(id, new Criterios("20100066603", "01", "F001", 100_000_000L, null, null, Q.fechaEmision(), null)))
                .extracting("codigo").isEqualTo("PARAMETRO_INVALIDO");
    }

    @Test void sinElAdaptadorDeLaApiSeIgnoranLasCredencialesApi() {
        conApiYSol();
        Validez v = new ConsultarValidezService(tenants, soap).consultar(id, Q);
        assertThat(v.fuente()).isEqualTo(Fuente.SOAP);
    }
}
