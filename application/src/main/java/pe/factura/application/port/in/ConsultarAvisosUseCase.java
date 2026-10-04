package pe.factura.application.port.in;

import pe.factura.domain.plataforma.MotivoDeAviso;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Las empresas a las que hay que avisarles algo (#197): las que van a dejar de poder firmar porque su certificado vence en menos de {@link ListarEmpresasAdminUseCase#DIAS_POR_VENCER}
 * días (o ya venció), y las que SUNAT no deja enviar por sus credenciales SOL. Cada una dice si ya se le avisó, cuándo y desde cuándo se puede repetir. Lectura pura.
 */
public interface ConsultarAvisosUseCase {
    /** A quién le llegaría el aviso: la cuenta dueña. Es nulo en una empresa de integración (sin cuenta): ahí no hay a quién avisarle. */
    record CuentaDelCliente(UUID id, String nombre, String email) {}

    /** El último aviso de **este** motivo a esta empresa. */
    record UltimoAviso(Instant enviadoEn, String destinatario) {}

    /**
     * {@code motivo}: {@code CERTIFICADO_VENCIDO} o {@code CERTIFICADO_POR_VENCER}. {@code diasRestantes} es negativo si ya venció. {@code avisarDesde}: cuándo se puede repetir el aviso
     * (nulo si nunca se avisó o ya pasó). {@code puedeAvisar}: hay a quién avisarle y no se avisó lo mismo hace menos de una semana.
     */
    record CertificadoEnRiesgo(UUID empresaId, String ruc, String razonSocial, CuentaDelCliente cuenta, MotivoDeAviso motivo, LocalDate vigenteHasta, int diasRestantes, UltimoAviso ultimoAviso,
                               Instant avisarDesde, boolean puedeAvisar) {}

    /** {@code comprobantesAfectados}: cuántos están en error de envío por este motivo ahora mismo; si se arregla y se reenvían, la empresa sale sola de la lista. */
    record SolFallando(UUID empresaId, String ruc, String razonSocial, CuentaDelCliente cuenta, long comprobantesAfectados, Instant ultimoFallo, String ultimoError, UltimoAviso ultimoAviso,
                       Instant avisarDesde, boolean puedeAvisar) {}

    record PaginaDeCertificados(List<CertificadoEnRiesgo> filas, long total) {}

    record PaginaDeSol(List<SolFallando> filas, long total) {}

    /** Por urgencia: la que venció hace más, o vence antes, primero. */
    PaginaDeCertificados certificados(int pagina, int porPagina);

    /** Por cuántos comprobantes tiene atascados, de más a menos. */
    PaginaDeSol credencialesSol(int pagina, int porPagina);
}
