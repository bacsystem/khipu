package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.AvisarAlClienteUseCase;
import pe.factura.application.port.out.AuditoriaAdminRepository;
import pe.factura.application.port.out.AvisosRepository;
import pe.factura.application.port.out.AvisosRepository.AvisoRegistrado;
import pe.factura.application.port.out.AvisosRepository.Situacion;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.MotivoDeAviso;
import pe.factura.domain.plataforma.RegistroAuditoria;
import pe.factura.domain.plataforma.TipoDeAviso;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Avisarle a un cliente desde el backoffice (#197). Se avisa solo lo que es cierto ahora (el motivo sale de la situación de la empresa, no de lo que pida el administrador), solo a
 * quien tiene una cuenta con correo, y no dos veces lo mismo en {@link MotivoDeAviso#ENFRIAMIENTO}. El orden importa: primero se **reserva** el aviso en una transacción (atómica
 * entre administradores), después sale el correo —fuera de la transacción: es lento y externo—, y solo si salió queda la bitácora; si el correo falla, la reserva se anula y se
 * puede reintentar. Así no hay avisos fantasma (registrados y no enviados) ni avisos repetidos.
 */
@RequiredArgsConstructor
public class AvisarAlClienteService implements AvisarAlClienteUseCase {
    private final AvisosRepository avisos;
    private final CorreoSender correo;
    private final AuditoriaAdminRepository auditoria;
    private final UnitOfWork uow;
    private final Clock clock;
    private final PlantillasDeCorreo plantillas;

    @Override public AvisoEnviado avisar(ActorAdmin actor, UUID empresaId, TipoDeAviso tipo, String urlPortal) {
        if (tipo == null) throw new DomainException("TIPO_INVALIDO", "Indica qué se le avisa al cliente");
        Instant ahora = clock.instant();
        Situacion s = avisos.situacionDe(empresaId, LocalDate.now(clock)).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "La empresa no existe"));
        MotivoDeAviso motivo = motivoDe(s, tipo);
        if (motivo == null) throw new DomainException("AVISO_SIN_MOTIVO", "No hay nada que avisar: la empresa no está en ese problema");
        if (s.cuenta().email() == null) throw new DomainException("EMPRESA_SIN_CUENTA", "La empresa no tiene una cuenta con correo a quien avisarle");
        if (!correo.entregaDeVerdad()) throw new DomainException("CORREO_NO_CONFIGURADO", "El envío de correos no está habilitado en el servidor: no se mandó nada");

        AvisoRegistrado aviso = new AvisoRegistrado(UUID.randomUUID(), empresaId, s.cuenta().cuentaId(), motivo, s.cuenta().email(), ahora, actor.administradorId());
        Optional<AvisoRegistrado> previo = uow.ejecutar(() -> avisos.reservar(aviso, ahora.minus(MotivoDeAviso.ENFRIAMIENTO)));
        if (previo.isPresent())
            throw new DomainException("AVISO_RECIENTE", "Ya se avisó lo mismo el " + previo.get().enviadoEn() + ": se puede repetir desde el " + motivo.avisarDesde(previo.get().enviadoEn()));

        var mensaje = MensajeDeAviso.de(plantillas, motivo, s.razonSocial(), s.ruc(), s.vigenteHasta(), s.diasRestantes(), urlPortal);
        try {
            correo.enviar(s.cuenta().email(), mensaje.asunto(), mensaje.cuerpo());
        } catch (RuntimeException e) {
            uow.ejecutar(() -> avisos.anular(aviso.id()));
            throw new DomainException("CORREO_NO_ENVIADO", "No se pudo enviar el aviso: " + e.getMessage(), e);
        }
        uow.ejecutar(() -> auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.AVISAR_AL_CLIENTE, s.cuenta().cuentaId(), empresaId, "motivo=" + motivo, ahora)));
        return new AvisoEnviado(empresaId, motivo, s.cuenta().email(), ahora, motivo.avisarDesde(ahora));
    }

    /** El motivo que corresponde a lo que se quiere avisar, o nulo si la empresa no está en ese problema. */
    private static MotivoDeAviso motivoDe(Situacion s, TipoDeAviso tipo) {
        return switch (tipo) {
            case CERTIFICADO -> switch (s.certificado()) {
                case VENCIDO -> MotivoDeAviso.CERTIFICADO_VENCIDO;
                case POR_VENCER -> MotivoDeAviso.CERTIFICADO_POR_VENCER;
                default -> null;
            };
            case CREDENCIALES_SOL -> s.fallosDeSol() > 0 ? MotivoDeAviso.CREDENCIALES_SOL_INVALIDAS : null;
        };
    }
}
