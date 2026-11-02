package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ConsultarAvisosUseCase.CertificadoEnRiesgo;
import pe.factura.application.port.in.ConsultarAvisosUseCase.PaginaDeCertificados;
import pe.factura.application.port.in.ConsultarAvisosUseCase.PaginaDeSol;
import pe.factura.application.port.in.ConsultarAvisosUseCase.SolFallando;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;
import pe.factura.application.port.out.AvisosRepository.AvisoRegistrado;
import pe.factura.application.port.out.AvisosRepository.Destino;
import pe.factura.application.port.out.AvisosRepository.FilaCertificado;
import pe.factura.application.port.out.AvisosRepository.FilaSol;
import pe.factura.domain.plataforma.MotivoDeAviso;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las listas de avisos del backoffice (#197): qué motivo corresponde a cada empresa, si ya se le avisó, desde cuándo se puede repetir y si hay a quién escribirle. Todo
 * con la hora del reloj: lo que se ve es lo que pasaría al hacer clic.
 */
class ConsultarAvisosServiceTest {
    static final Instant AHORA = Fakes.CLOCK.instant();
    static final Destino CUENTA = new Destino(UUID.randomUUID(), "Ana", "ana@negocio.pe");
    static final Destino SIN_CUENTA = new Destino(null, null, null);

    AvisosFake avisos = new AvisosFake();
    ConsultarAvisosService service = new ConsultarAvisosService(avisos, Fakes.CLOCK);

    static FilaCertificado cert(UUID id, EstadoCertificado estado, int dias, Destino cuenta) {
        return new FilaCertificado(id, "20100066603", "COMERCIAL ANDINA SAC", cuenta, estado, LocalDate.of(2026, 9, 13).plusDays(dias), dias);
    }

    static FilaSol sol(UUID id, Destino cuenta) { return new FilaSol(id, "20100066603", "COMERCIAL ANDINA SAC", cuenta, 4, AHORA.minusSeconds(600), "0102 - Usuario o contraseña incorrectos"); }

    void aviso(UUID empresa, MotivoDeAviso motivo, Duration hace) {
        avisos.avisos.add(new AvisoRegistrado(UUID.randomUUID(), empresa, CUENTA.cuentaId(), motivo, "ana@negocio.pe", AHORA.minus(hace), null));
    }

    // --- certificados -----------------------------------------------------------------------------------------------------------------

    @Test void unCertificadoVencidoYUnoPorVencerTienenCadaUnoSuMotivo() {
        UUID vencido = UUID.randomUUID(), porVencer = UUID.randomUUID();
        avisos.certificados = java.util.List.of(cert(vencido, EstadoCertificado.VENCIDO, -3, CUENTA), cert(porVencer, EstadoCertificado.POR_VENCER, 12, CUENTA));

        PaginaDeCertificados p = service.certificados(1, 20);

        assertThat(p.filas()).extracting(CertificadoEnRiesgo::motivo).containsExactly(MotivoDeAviso.CERTIFICADO_VENCIDO, MotivoDeAviso.CERTIFICADO_POR_VENCER);
        assertThat(p.filas()).extracting(CertificadoEnRiesgo::diasRestantes).containsExactly(-3, 12);
        assertThat(p.total()).isEqualTo(2);
    }

    @Test void trasladaLaEmpresaLaCuentaYLaVigencia() {
        UUID id = UUID.randomUUID();
        avisos.certificados = java.util.List.of(cert(id, EstadoCertificado.POR_VENCER, 12, CUENTA));

        CertificadoEnRiesgo c = service.certificados(1, 20).filas().get(0);

        assertThat(c.empresaId()).isEqualTo(id);
        assertThat(c.ruc()).isEqualTo("20100066603");
        assertThat(c.razonSocial()).isEqualTo("COMERCIAL ANDINA SAC");
        assertThat(c.cuenta().id()).isEqualTo(CUENTA.cuentaId());
        assertThat(c.cuenta().nombre()).isEqualTo("Ana");
        assertThat(c.cuenta().email()).isEqualTo("ana@negocio.pe");
        assertThat(c.vigenteHasta()).isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test void sinAvisoPrevioSePuedeAvisarYNoHayFechaDeEspera() {
        avisos.certificados = java.util.List.of(cert(UUID.randomUUID(), EstadoCertificado.POR_VENCER, 12, CUENTA));

        CertificadoEnRiesgo c = service.certificados(1, 20).filas().get(0);

        assertThat(c.ultimoAviso()).isNull();
        assertThat(c.avisarDesde()).isNull();
        assertThat(c.puedeAvisar()).isTrue();
    }

    @Test void unAvisoDeHaceUnDiaBloqueaHastaCumplirseLaSemana() {
        UUID id = UUID.randomUUID();
        avisos.certificados = java.util.List.of(cert(id, EstadoCertificado.POR_VENCER, 12, CUENTA));
        aviso(id, MotivoDeAviso.CERTIFICADO_POR_VENCER, Duration.ofDays(1));

        CertificadoEnRiesgo c = service.certificados(1, 20).filas().get(0);

        assertThat(c.ultimoAviso().enviadoEn()).isEqualTo(AHORA.minus(Duration.ofDays(1)));
        assertThat(c.ultimoAviso().destinatario()).isEqualTo("ana@negocio.pe");
        assertThat(c.avisarDesde()).isEqualTo(AHORA.plus(Duration.ofDays(6)));
        assertThat(c.puedeAvisar()).isFalse();
    }

    @Test void alCumplirseLaSemanaJustoYaSePuedeAvisarDeNuevo() {
        UUID id = UUID.randomUUID();
        avisos.certificados = java.util.List.of(cert(id, EstadoCertificado.POR_VENCER, 12, CUENTA));
        aviso(id, MotivoDeAviso.CERTIFICADO_POR_VENCER, Duration.ofDays(7));

        CertificadoEnRiesgo c = service.certificados(1, 20).filas().get(0);

        assertThat(c.puedeAvisar()).isTrue();
        assertThat(c.avisarDesde()).as("ya pasó: no hay fecha pendiente").isNull();
        assertThat(c.ultimoAviso()).isNotNull();
    }

    @Test void unAvisoDeHaceUnPocoMenosDeUnaSemanaTodaviaBloquea() {
        UUID id = UUID.randomUUID();
        avisos.certificados = java.util.List.of(cert(id, EstadoCertificado.POR_VENCER, 12, CUENTA));
        aviso(id, MotivoDeAviso.CERTIFICADO_POR_VENCER, Duration.ofDays(7).minusSeconds(1));

        assertThat(service.certificados(1, 20).filas().get(0).puedeAvisar()).isFalse();
    }

    /** Pasar de «por vencer» a «vencido» es otro motivo: el aviso de antes no lo bloquea. */
    @Test void elAvisoDePorVencerNoBloqueaElDeVencido() {
        UUID id = UUID.randomUUID();
        avisos.certificados = java.util.List.of(cert(id, EstadoCertificado.VENCIDO, -1, CUENTA));
        aviso(id, MotivoDeAviso.CERTIFICADO_POR_VENCER, Duration.ofHours(2));

        CertificadoEnRiesgo c = service.certificados(1, 20).filas().get(0);

        assertThat(c.motivo()).isEqualTo(MotivoDeAviso.CERTIFICADO_VENCIDO);
        assertThat(c.ultimoAviso()).as("el último aviso que se muestra es el de este motivo").isNull();
        assertThat(c.puedeAvisar()).isTrue();
    }

    @Test void unAvisoDeOtraEmpresaNoCuenta() {
        UUID id = UUID.randomUUID();
        avisos.certificados = java.util.List.of(cert(id, EstadoCertificado.POR_VENCER, 12, CUENTA));
        aviso(UUID.randomUUID(), MotivoDeAviso.CERTIFICADO_POR_VENCER, Duration.ofHours(1));

        assertThat(service.certificados(1, 20).filas().get(0).puedeAvisar()).isTrue();
    }

    @Test void sinCuentaNoSePuedeAvisarAunqueNuncaSeHayaAvisado() {
        avisos.certificados = java.util.List.of(cert(UUID.randomUUID(), EstadoCertificado.VENCIDO, -2, SIN_CUENTA));

        CertificadoEnRiesgo c = service.certificados(1, 20).filas().get(0);

        assertThat(c.cuenta()).isNull();
        assertThat(c.puedeAvisar()).isFalse();
    }

    @Test void unaCuentaSinCorreoTampocoTieneAQuienAvisarle() {
        avisos.certificados = java.util.List.of(cert(UUID.randomUUID(), EstadoCertificado.VENCIDO, -2, new Destino(UUID.randomUUID(), "Ana", null)));

        assertThat(service.certificados(1, 20).filas().get(0).puedeAvisar()).isFalse();
    }

    @Test void laPaginaLlegaAlRepositorioTalCualYElDiaEsElDelReloj() {
        service.certificados(3, 50);

        assertThat(avisos.consultas.get(0)).containsExactly("certificados", 3, 50);
        assertThat(avisos.hoyVisto).isEqualTo(LocalDate.of(2026, 9, 13));
    }

    @Test void unEstadoQueNoEsDeRiesgoEsUnError() {
        avisos.certificados = java.util.List.of(cert(UUID.randomUUID(), EstadoCertificado.VIGENTE, 90, CUENTA));

        assertThatThrownBy(() -> service.certificados(1, 20)).isInstanceOf(IllegalStateException.class);
    }

    @Test void unaListaVaciaEsUnaPaginaVacia() {
        PaginaDeCertificados p = service.certificados(1, 20);

        assertThat(p.filas()).isEmpty();
        assertThat(p.total()).isZero();
    }

    // --- credenciales SOL -------------------------------------------------------------------------------------------------------------

    @Test void unaEmpresaConLaSolRechazadaDiceCuantosComprobantesTieneAtascadosYQueDijoSunat() {
        UUID id = UUID.randomUUID();
        avisos.sol = java.util.List.of(sol(id, CUENTA));

        PaginaDeSol p = service.credencialesSol(1, 20);

        SolFallando s = p.filas().get(0);
        assertThat(s.empresaId()).isEqualTo(id);
        assertThat(s.comprobantesAfectados()).isEqualTo(4);
        assertThat(s.ultimoFallo()).isEqualTo(AHORA.minusSeconds(600));
        assertThat(s.ultimoError()).isEqualTo("0102 - Usuario o contraseña incorrectos");
        assertThat(s.cuenta().email()).isEqualTo("ana@negocio.pe");
        assertThat(s.puedeAvisar()).isTrue();
        assertThat(p.total()).isEqualTo(1);
    }

    @Test void elAvisoDeLasCredencialesTieneSuPropioEnfriamiento() {
        UUID id = UUID.randomUUID();
        avisos.sol = java.util.List.of(sol(id, CUENTA));
        aviso(id, MotivoDeAviso.CREDENCIALES_SOL_INVALIDAS, Duration.ofDays(2));

        SolFallando s = service.credencialesSol(1, 20).filas().get(0);

        assertThat(s.ultimoAviso().enviadoEn()).isEqualTo(AHORA.minus(Duration.ofDays(2)));
        assertThat(s.avisarDesde()).isEqualTo(AHORA.plus(Duration.ofDays(5)));
        assertThat(s.puedeAvisar()).isFalse();
    }

    @Test void unAvisoDelCertificadoNoBloqueaElDeLasCredenciales() {
        UUID id = UUID.randomUUID();
        avisos.sol = java.util.List.of(sol(id, CUENTA));
        aviso(id, MotivoDeAviso.CERTIFICADO_VENCIDO, Duration.ofHours(1));

        assertThat(service.credencialesSol(1, 20).filas().get(0).puedeAvisar()).isTrue();
    }

    @Test void sinCuentaLaSolTampocoSePuedeAvisar() {
        avisos.sol = java.util.List.of(sol(UUID.randomUUID(), SIN_CUENTA));

        assertThat(service.credencialesSol(1, 20).filas().get(0).puedeAvisar()).isFalse();
        assertThat(service.credencialesSol(1, 20).filas().get(0).cuenta()).isNull();
    }

    @Test void laPaginaDeSolLlegaAlRepositorioTalCual() {
        service.credencialesSol(2, 10);

        assertThat(avisos.consultas.get(0)).containsExactly("sol", 2, 10);
    }
}
