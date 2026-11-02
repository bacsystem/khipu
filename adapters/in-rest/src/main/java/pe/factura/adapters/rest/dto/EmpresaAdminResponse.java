package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EmpresaResumen;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;
import pe.factura.domain.tenant.Entorno;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Una empresa en el listado del backoffice (#185). Del certificado y de la clave SOL solo se sabe su estado, nunca su contenido. */
public record EmpresaAdminResponse(
        @Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID id,
        @Schema(example = "20100066603") String ruc,
        @Schema(example = "COMERCIAL ANDINA SAC") String razonSocial,
        @Schema(description = "Cuenta dueña de la empresa; ausente en las empresas dadas de alta por una integración, que no tienen cuenta") UUID cuentaId,
        @Schema(example = "Mi negocio", description = "Nombre de esa cuenta") String cuentaNombre,
        Entorno entorno,
        @Schema(description = "Estado del certificado hoy (fecha de Lima). `POR_VENCER`: menos de 30 días; el último día de vigencia todavía vale y `VENCIDO` empieza el día siguiente. `SIN_FECHA`: cargado, sin vigencia conocida") EstadoCertificado certificado,
        @Schema(example = "2027-03-01", description = "Hasta cuándo vale el certificado; ausente si no hay certificado o no se conoce") LocalDate certificadoVigenteHasta,
        @Schema(example = "17", description = "Días hasta esa fecha; negativo si ya venció; ausente si no hay fecha") Integer certificadoDiasRestantes,
        @Schema(description = "Credenciales SOL cargadas (usuario y clave); no se sabe si son válidas") boolean tieneCredencialesSol,
        @Schema(example = "2", description = "Series activas") int series,
        @Schema(example = "31", description = "Documentos con fecha de emisión en el mes de hoy (Lima)") int comprobantesDelMes,
        @Schema(example = "2026-10-02", description = "Fecha de emisión del documento más reciente; ausente si nunca emitió") LocalDate ultimaEmision,
        @Schema(example = "2026-10-03T09:00:00Z", description = "Desde cuándo su cuenta está dada de baja (#201); ausente si está en servicio o si la empresa no tiene cuenta") Instant cuentaDeBajaEn) {
    public static EmpresaAdminResponse de(EmpresaResumen e) {
        return new EmpresaAdminResponse(e.id(), e.ruc(), e.razonSocial(), e.cuentaId(), e.cuentaNombre(), e.entorno(), e.certificado(),
                e.certificadoVigenteHasta(), e.certificadoDiasRestantes(), e.tieneCredencialesSol(), e.series(), e.comprobantesDelMes(), e.ultimaEmision(), e.cuentaDeBajaEn());
    }
}
