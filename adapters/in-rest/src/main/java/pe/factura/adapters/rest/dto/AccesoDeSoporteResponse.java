package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.AccesosDeSoporteUseCase.AccesoDeSoporte;

import java.time.Instant;

/** Un acceso del equipo de soporte a la cuenta del cliente (#184). A propósito sin el administrador: para el cliente es «el equipo de soporte». */
public record AccesoDeSoporteResponse(
        @Schema(example = "2026-10-04T10:00:00Z") Instant ocurridoEn,
        @Schema(example = "ana@negocio.pe", description = "A qué usuario de la cuenta se miró; ausente si el registro no tiene un formato que se entienda") String usuario,
        @Schema(example = "900", description = "Cuánto duró como máximo la sesión, en segundos; ausente si el registro no tiene un formato que se entienda") Long duracionSegundos) {
    public static AccesoDeSoporteResponse de(AccesoDeSoporte a) {
        return new AccesoDeSoporteResponse(a.ocurridoEn(), a.usuario(), a.duracionSegundos());
    }
}
