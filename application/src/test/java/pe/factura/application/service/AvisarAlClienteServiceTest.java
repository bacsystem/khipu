package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.AvisarAlClienteUseCase.AvisoEnviado;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.AvisosRepository.AvisoRegistrado;
import pe.factura.application.port.out.AvisosRepository.Destino;
import pe.factura.application.port.out.AvisosRepository.Situacion;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.MotivoDeAviso;
import pe.factura.domain.plataforma.RegistroAuditoria;
import pe.factura.domain.plataforma.TipoDeAviso;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Avisarle a un cliente desde el backoffice (#197): solo lo que es cierto, solo a quien tiene cuenta, sin repetirlo antes de una semana ni aunque dos administradores hagan clic a la
 * vez, y con el correo y la bitácora en el orden que no deja ni avisos fantasma ni avisos sin rastro.
 */
class AvisarAlClienteServiceTest {
    static final ActorAdmin ACTOR = ActorAdmin.administrador(UUID.randomUUID(), "203.0.113.7");
    static final Instant AHORA = Fakes.CLOCK.instant();
    static final String PORTAL = "https://app.khipu.pe";

    UUID empresa = UUID.randomUUID();
    UUID cuenta = UUID.randomUUID();
    AvisosFake avisos = new AvisosFake();
    Fakes.UowTransaccional uow = new Fakes.UowTransaccional();
    Fakes.Auditoria auditoria = new Fakes.Auditoria();
    Correo correo = new Correo();
    ConfiguracionFake.Plantillas plantillasGuardadas = new ConfiguracionFake.Plantillas();
    AvisarAlClienteService service;

    /** Guarda lo que se mandó; puede fallar, o decir que no entrega (sin SMTP). */
    static class Correo implements CorreoSender {
        final List<String[]> enviados = new ArrayList<>();
        final List<Boolean> avisosAlEnviar = new ArrayList<>();
        RuntimeException falla;
        boolean entrega = true;
        AvisosFake avisos;
        public void enviar(String para, String asunto, String cuerpo) {
            avisosAlEnviar.add(avisos.avisos.size() == 1);
            if (falla != null) throw falla;
            enviados.add(new String[]{para, asunto, cuerpo});
        }
        public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) { throw new AssertionError("los avisos no llevan adjuntos"); }
        public boolean entregaDeVerdad() { return entrega; }
    }

    {
        correo.avisos = avisos;
        avisos.uow = uow;
        auditoria.uow = uow;
        service = new AvisarAlClienteService(avisos, correo, auditoria, uow, Fakes.CLOCK, new PlantillasDeCorreo(plantillasGuardadas));
    }

    Situacion situacion(EstadoCertificado cert, Integer dias, long fallosDeSol, Destino destino) {
        Situacion s = new Situacion(empresa, "20100066603", "COMERCIAL ANDINA SAC", destino, cert, dias == null ? null : LocalDate.of(2026, 9, 13).plusDays(dias), dias, fallosDeSol);
        avisos.situaciones.put(empresa, s);
        return s;
    }

    Destino destino() { return new Destino(cuenta, "Ana", "ana@negocio.pe"); }

    void avisoPrevio(MotivoDeAviso motivo, Duration hace) {
        avisos.avisos.add(new AvisoRegistrado(UUID.randomUUID(), empresa, cuenta, motivo, "ana@negocio.pe", AHORA.minus(hace), null));
    }

    // --- #199: el texto de los avisos lo edita un administrador ----------------------------------------------------------------------

    @Test void elAvisoSaleConElTextoQueUnAdministradorEdito() {
        situacion(EstadoCertificado.POR_VENCER, 10, 0, destino());
        plantillasGuardadas.filas.put(pe.factura.domain.plataforma.PlantillaDeCorreo.AVISO_CERTIFICADO_POR_VENCER,
                new pe.factura.application.port.out.PlantillasRepository.Guardada(new pe.factura.domain.plataforma.PlantillaDeCorreo.Texto("Ojo con {razon_social}", "Vence {cuando} ({fecha}). Renueva en {enlace}"), java.time.Instant.EPOCH));

        service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL);

        String[] c = correo.enviados.get(0);
        assertThat(c[1]).isEqualTo("Ojo con COMERCIAL ANDINA SAC");
        assertThat(c[2]).isEqualTo("Vence en 10 días (23/09/2026). Renueva en " + PORTAL);
    }

    // --- avisar de un certificado -----------------------------------------------------------------------------------------------------

    @Test void unCertificadoPorVencerSeAvisaPorCorreoALaCuentaConSuTexto() {
        situacion(EstadoCertificado.POR_VENCER, 12, 0, destino());

        AvisoEnviado r = service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL);

        assertThat(correo.enviados).hasSize(1);
        String[] c = correo.enviados.get(0);
        assertThat(c[0]).isEqualTo("ana@negocio.pe");
        assertThat(c[1]).isEqualTo("Tu certificado digital de COMERCIAL ANDINA SAC vence en 12 días");
        assertThat(c[2]).contains("vence el 25/09/2026").contains(PORTAL);
        assertThat(r.empresaId()).isEqualTo(empresa);
        assertThat(r.motivo()).isEqualTo(MotivoDeAviso.CERTIFICADO_POR_VENCER);
        assertThat(r.destinatario()).isEqualTo("ana@negocio.pe");
        assertThat(r.enviadoEn()).isEqualTo(AHORA);
        assertThat(r.avisarDesde()).isEqualTo(AHORA.plus(Duration.ofDays(7)));
    }

    @Test void unCertificadoVencidoTieneSuPropioMotivoYSuPropioTexto() {
        situacion(EstadoCertificado.VENCIDO, -4, 0, destino());

        AvisoEnviado r = service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL);

        assertThat(r.motivo()).isEqualTo(MotivoDeAviso.CERTIFICADO_VENCIDO);
        assertThat(correo.enviados.get(0)[1]).isEqualTo("El certificado digital de COMERCIAL ANDINA SAC venció");
    }

    @Test void lasCredencialesSolSeAvisanSoloSiFallan() {
        situacion(EstadoCertificado.VIGENTE, 200, 3, destino());

        AvisoEnviado r = service.avisar(ACTOR, empresa, TipoDeAviso.CREDENCIALES_SOL, PORTAL);

        assertThat(r.motivo()).isEqualTo(MotivoDeAviso.CREDENCIALES_SOL_INVALIDAS);
        assertThat(correo.enviados.get(0)[1]).isEqualTo("SUNAT no acepta las credenciales SOL de COMERCIAL ANDINA SAC");
    }

    // --- solo lo que es cierto ----------------------------------------------------------------------------------------------------------

    @Test void noSeAvisaDeUnCertificadoQueEstaBien() {
        for (EstadoCertificado e : new EstadoCertificado[]{EstadoCertificado.VIGENTE, EstadoCertificado.SIN_CERTIFICADO, EstadoCertificado.SIN_FECHA}) {
            situacion(e, e == EstadoCertificado.VIGENTE ? 200 : null, 0, destino());
            assertThatThrownBy(() -> service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL)).isInstanceOf(DomainException.class).extracting("codigo").as(e.name()).isEqualTo("AVISO_SIN_MOTIVO");
        }
        assertThat(correo.enviados).isEmpty();
        assertThat(avisos.avisos).isEmpty();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void noSeAvisaDeCredencialesQueNoFallan() {
        situacion(EstadoCertificado.VENCIDO, -4, 0, destino());

        assertThatThrownBy(() -> service.avisar(ACTOR, empresa, TipoDeAviso.CREDENCIALES_SOL, PORTAL)).extracting("codigo").isEqualTo("AVISO_SIN_MOTIVO");

        assertThat(correo.enviados).isEmpty();
    }

    @Test void unCertificadoVencidoNoSeAvisaComoCredenciales() {
        situacion(EstadoCertificado.VENCIDO, -4, 0, destino());

        service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL);

        assertThat(avisos.avisos).extracting(AvisoRegistrado::motivo).containsExactly(MotivoDeAviso.CERTIFICADO_VENCIDO);
    }

    @Test void unaEmpresaQueNoExisteEs404() {
        assertThatThrownBy(() -> service.avisar(ACTOR, UUID.randomUUID(), TipoDeAviso.CERTIFICADO, PORTAL)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }

    @Test void sinTipoNoSeSabeQueAvisar() {
        situacion(EstadoCertificado.VENCIDO, -4, 0, destino());

        assertThatThrownBy(() -> service.avisar(ACTOR, empresa, null, PORTAL)).extracting("codigo").isEqualTo("TIPO_INVALIDO");
    }

    // --- a quién ------------------------------------------------------------------------------------------------------------------------

    @Test void sinCuentaNoHayAQuienEscribirle() {
        situacion(EstadoCertificado.VENCIDO, -4, 0, new Destino(null, null, null));

        assertThatThrownBy(() -> service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL)).extracting("codigo").isEqualTo("EMPRESA_SIN_CUENTA");

        assertThat(correo.enviados).isEmpty();
        assertThat(avisos.avisos).isEmpty();
    }

    @Test void unaCuentaSinCorreoTampoco() {
        situacion(EstadoCertificado.VENCIDO, -4, 0, new Destino(cuenta, "Ana", null));

        assertThatThrownBy(() -> service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL)).extracting("codigo").isEqualTo("EMPRESA_SIN_CUENTA");
    }

    // --- sin repetirlo ---------------------------------------------------------------------------------------------------------------------

    @Test void elMismoAvisoNoSeRepiteAntesDeUnaSemanaYNoSeMandaNiSeAnotaNada() {
        situacion(EstadoCertificado.POR_VENCER, 12, 0, destino());
        avisoPrevio(MotivoDeAviso.CERTIFICADO_POR_VENCER, Duration.ofDays(2));

        assertThatThrownBy(() -> service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("AVISO_RECIENTE");

        assertThat(correo.enviados).isEmpty();
        assertThat(avisos.avisos).hasSize(1);
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void elMensajeDelAvisoRecienteDiceCuandoSePuedeRepetir() {
        situacion(EstadoCertificado.POR_VENCER, 12, 0, destino());
        avisoPrevio(MotivoDeAviso.CERTIFICADO_POR_VENCER, Duration.ofDays(2));

        assertThatThrownBy(() -> service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL)).hasMessageContaining("2026-09-18");
    }

    @Test void alCumplirseLaSemanaJustoSePuedeAvisarDeNuevo() {
        situacion(EstadoCertificado.POR_VENCER, 12, 0, destino());
        avisoPrevio(MotivoDeAviso.CERTIFICADO_POR_VENCER, Duration.ofDays(7));

        service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL);

        assertThat(correo.enviados).hasSize(1);
        assertThat(avisos.avisos).hasSize(2);
    }

    @Test void unAvisoDeOtroMotivoNoBloquea() {
        situacion(EstadoCertificado.VENCIDO, -1, 0, destino());
        avisoPrevio(MotivoDeAviso.CERTIFICADO_POR_VENCER, Duration.ofHours(3));

        service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL);

        assertThat(correo.enviados).hasSize(1);
    }

    @Test void dosAvisosSeguidosDejanSoloElPrimero() {
        situacion(EstadoCertificado.POR_VENCER, 12, 0, destino());

        service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL);
        assertThatThrownBy(() -> service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL)).extracting("codigo").isEqualTo("AVISO_RECIENTE");

        assertThat(correo.enviados).hasSize(1);
        assertThat(auditoria.registros).hasSize(1);
    }

    @Test void laReservaSeHaceDentroDeUnaTransaccionYElCorreoSaleFueraDeElla() {
        situacion(EstadoCertificado.POR_VENCER, 12, 0, destino());

        service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL);

        assertThat(avisos.reservadoDentro).containsExactly(true);
        assertThat(correo.avisosAlEnviar).as("al mandar el correo el aviso ya está reservado").containsExactly(true);
        assertThat(uow.dentro).isFalse();
    }

    @Test void laReservaTomaLaFechaDeCorteDeUnaSemanaAtras() {
        situacion(EstadoCertificado.POR_VENCER, 12, 0, destino());
        avisoPrevio(MotivoDeAviso.CERTIFICADO_POR_VENCER, Duration.ofDays(7).minusSeconds(1));

        assertThatThrownBy(() -> service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL)).extracting("codigo").isEqualTo("AVISO_RECIENTE");
    }

    // --- el correo --------------------------------------------------------------------------------------------------------------------------

    @Test void sinSmtpNoSeMandaNadaNiSeReservaNada() {
        situacion(EstadoCertificado.POR_VENCER, 12, 0, destino());
        correo.entrega = false;

        assertThatThrownBy(() -> service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL)).extracting("codigo").isEqualTo("CORREO_NO_CONFIGURADO");

        assertThat(correo.avisosAlEnviar).isEmpty();
        assertThat(avisos.avisos).isEmpty();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void siElCorreoFallaLaReservaSeAnulaYSePuedeReintentar() {
        situacion(EstadoCertificado.POR_VENCER, 12, 0, destino());
        correo.falla = new IllegalStateException("SMTP caído");

        assertThatThrownBy(() -> service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("CORREO_NO_ENVIADO");

        assertThat(avisos.avisos).as("no quedó registrado un aviso que no salió").isEmpty();
        assertThat(avisos.anulados).hasSize(1);
        assertThat(auditoria.registros).isEmpty();

        correo.falla = null;
        service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL);
        assertThat(correo.enviados).hasSize(1);
    }

    // --- el registro y la bitácora ----------------------------------------------------------------------------------------------------------

    @Test void elAvisoQuedaRegistradoConElDestinatarioElMotivoYQuienLoMando() {
        situacion(EstadoCertificado.POR_VENCER, 12, 0, destino());

        service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL);

        assertThat(avisos.avisos).hasSize(1);
        AvisoRegistrado a = avisos.avisos.get(0);
        assertThat(a.empresaId()).isEqualTo(empresa);
        assertThat(a.cuentaId()).isEqualTo(cuenta);
        assertThat(a.motivo()).isEqualTo(MotivoDeAviso.CERTIFICADO_POR_VENCER);
        assertThat(a.destinatario()).isEqualTo("ana@negocio.pe");
        assertThat(a.enviadoEn()).isEqualTo(AHORA);
        assertThat(a.enviadoPor()).isEqualTo(ACTOR.administradorId());
    }

    @Test void conLaClaveDeLaPlataformaNoHayAdministradorQueLoMandara() {
        situacion(EstadoCertificado.POR_VENCER, 12, 0, destino());

        service.avisar(ActorAdmin.clavePlataforma("203.0.113.9"), empresa, TipoDeAviso.CERTIFICADO, PORTAL);

        assertThat(avisos.avisos.get(0).enviadoPor()).isNull();
    }

    @Test void elAvisoQuedaEnLaBitacoraSinElCorreoDelCliente() {
        situacion(EstadoCertificado.POR_VENCER, 12, 0, destino());

        service.avisar(ACTOR, empresa, TipoDeAviso.CERTIFICADO, PORTAL);

        assertThat(auditoria.registros).hasSize(1);
        RegistroAuditoria r = auditoria.registros.get(0);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.accion()).isEqualTo(AccionAdmin.AVISAR_AL_CLIENTE);
        assertThat(r.cuentaId()).isEqualTo(cuenta);
        assertThat(r.tenantId()).isEqualTo(empresa);
        assertThat(r.detalle()).isEqualTo("motivo=CERTIFICADO_POR_VENCER");
        assertThat(r.detalle()).doesNotContain("ana@negocio.pe");
        assertThat(r.ocurridoEn()).isEqualTo(AHORA);
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
    }
}
