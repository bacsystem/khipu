package pe.factura.application.port.out;

import pe.factura.domain.plataforma.MotivoDeAviso;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que leen y escriben los avisos a los clientes del backoffice (#197): las empresas con el certificado en riesgo, las que SUNAT rechaza por sus credenciales SOL, y el registro
 * de lo que ya se avisó para no repetirlo todos los días. Solo datos crudos: qué es «en riesgo» lo decide {@code EstadoCertificado} y qué es un fallo de credenciales,
 * {@code FalloDeAutenticacionSol}.
 */
public interface AvisosRepository {
    /** A quién se le avisa: la cuenta dueña de la empresa. Todo nulo en una empresa dada de alta por una integración, que no tiene cuenta. */
    record Destino(UUID cuentaId, String nombre, String email) {}

    /** Una empresa con el certificado vencido o por vencer. {@code diasRestantes} es negativo si ya venció (el último día todavía vale). */
    record FilaCertificado(UUID empresaId, String ruc, String razonSocial, Destino cuenta, EstadoCertificado estado, LocalDate vigenteHasta, int diasRestantes) {}

    /** Una empresa con envíos que fallan por sus credenciales SOL: cuántos comprobantes están atascados por eso, cuándo falló el último y qué dijo SUNAT. */
    record FilaSol(UUID empresaId, String ruc, String razonSocial, Destino cuenta, long comprobantesAfectados, Instant ultimoFallo, String ultimoError) {}

    /** Todo lo que hace falta saber de una empresa para decidir si el aviso es cierto. {@code vigenteHasta} y {@code diasRestantes} son nulos si no tiene certificado o fecha. */
    record Situacion(UUID empresaId, String ruc, String razonSocial, Destino cuenta, EstadoCertificado certificado, LocalDate vigenteHasta, Integer diasRestantes, long fallosDeSol) {}

    /** Un aviso mandado (o reservado, mientras el correo sale). {@code enviadoPor} es el administrador; nulo si lo mandó la clave de la plataforma. */
    record AvisoRegistrado(UUID id, UUID empresaId, UUID cuentaId, MotivoDeAviso motivo, String destinatario, Instant enviadoEn, UUID enviadoPor) {}

    /** De la que vence antes (o ya venció hace más) a la que vence después; paginado desde 1. */
    List<FilaCertificado> certificados(LocalDate hoy, int pagina, int porPagina);

    long contarCertificados(LocalDate hoy);

    /** De la que más comprobantes tiene atascados a la que menos; paginado desde 1. */
    List<FilaSol> credencialesSol(int pagina, int porPagina);

    long contarCredencialesSol();

    /** Vacío si la empresa no existe. */
    Optional<Situacion> situacionDe(UUID empresaId, LocalDate hoy);

    /** El último aviso de ese motivo a cada una de esas empresas (solo las que tienen alguno). */
    Map<UUID, AvisoRegistrado> ultimosAvisos(Collection<UUID> empresas, MotivoDeAviso motivo);

    /**
     * Reserva el aviso: lo registra solo si no hay ya uno del mismo motivo para esa empresa enviado después de {@code desde}; si lo hay, devuelve ese y no registra nada. Es atómico
     * entre administradores (dos clics a la vez dejan un solo aviso). Debe correr dentro de una transacción.
     */
    Optional<AvisoRegistrado> reservar(AvisoRegistrado aviso, Instant desde);

    /** Quita una reserva cuyo correo no salió, para que se pueda volver a intentar. */
    void anular(UUID avisoId);
}
