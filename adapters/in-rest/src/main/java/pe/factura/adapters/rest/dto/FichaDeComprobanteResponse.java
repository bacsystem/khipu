package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ConsultarComprobanteAdminUseCase.Ficha;

import java.time.LocalDate;
import java.util.UUID;

/** La ficha de un comprobante en el backoffice (#251). Sin el contenido de sus archivos: solo si están guardados. */
public record FichaDeComprobanteResponse(
        UUID id,
        @Schema(description = "La empresa dueña del comprobante") UUID empresaId,
        @Schema(example = "20100066603") String ruc,
        @Schema(example = "COMERCIAL ANDINA SAC") String razonSocial,
        @Schema(description = "La cuenta dueña de la empresa; ausente en una empresa dada de alta por una integración") UUID cuentaId,
        @Schema(example = "20100066603-01-F001-7", description = "RUC-tipo-serie-número: la identidad del comprobante ante SUNAT") String nombreArchivo,
        @Schema(example = "01") String tipo,
        @Schema(example = "F001") String serie,
        @Schema(example = "7") Long numero,
        @Schema(example = "2026-10-12") LocalDate fechaEmision,
        @Schema(example = "ERROR_ENVIO") String estado,
        @Schema(example = "3", description = "Cuántas veces falló el envío") int intentos,
        @Schema(example = "0109 - El sistema no puede responder", description = "El último fallo de envío; ausente si no hubo") String ultimoError,
        @Schema(description = "Lo que respondió SUNAT en el CDR; ausente si todavía no respondió") RespuestaSunat respuestaSunat,
        @Schema(description = "Si el XML firmado está guardado") boolean tieneXml,
        @Schema(description = "Si el CDR de SUNAT está guardado") boolean tieneCdr) {

    public record RespuestaSunat(@Schema(example = "0") String codigo, @Schema(example = "La Factura numero F001-7, ha sido aceptada") String descripcion) {}

    public static FichaDeComprobanteResponse de(Ficha f) {
        return new FichaDeComprobanteResponse(f.id(), f.empresaId(), f.ruc(), f.razonSocial(), f.cuentaId(), f.nombreArchivo(), f.tipo(), f.serie(), f.numero(), f.fechaEmision(),
                f.estado().name(), f.intentos(), f.ultimoError(), f.cdr() == null ? null : new RespuestaSunat(f.cdr().codigo(), f.cdr().descripcion()), f.tieneXml(), f.tieneCdr());
    }
}
