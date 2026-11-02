package pe.factura.application.port.in;

import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.MotivoDeAviso;
import pe.factura.domain.plataforma.TipoDeAviso;

import java.time.Instant;
import java.util.UUID;

/**
 * Mandarle a un cliente, desde el backoffice, el aviso de que su certificado está por vencer o vencido, o de que SUNAT no acepta sus credenciales SOL (#197). Solo se avisa lo
 * que es cierto ahora, solo a quien tiene cuenta, y el mismo aviso no se repite antes de {@code MotivoDeAviso.ENFRIAMIENTO}: el registro de lo enviado lo impide aunque dos
 * administradores hagan clic a la vez. Queda en la bitácora a nombre de {@code actor}, sin el correo del cliente.
 */
public interface AvisarAlClienteUseCase {
    record AvisoEnviado(UUID empresaId, MotivoDeAviso motivo, String destinatario, Instant enviadoEn, Instant avisarDesde) {}

    /**
     * {@code NO_ENCONTRADO}: la empresa no existe. {@code TIPO_INVALIDO}: falta el tipo. {@code AVISO_SIN_MOTIVO}: la empresa no está en ese problema (el certificado está vigente, o sus
     * credenciales no fallan): no se avisa lo que no es cierto. {@code EMPRESA_SIN_CUENTA}: no hay a quién escribirle. {@code AVISO_RECIENTE}: se avisó lo mismo hace menos de una
     * semana. {@code CORREO_NO_CONFIGURADO}: el servidor no manda correos. {@code CORREO_NO_ENVIADO}: el correo falló; no queda registrado y se puede reintentar.
     */
    AvisoEnviado avisar(ActorAdmin actor, UUID empresaId, TipoDeAviso tipo, String urlPortal);
}
