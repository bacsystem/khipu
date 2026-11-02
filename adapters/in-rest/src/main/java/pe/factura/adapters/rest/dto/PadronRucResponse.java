package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ConsultarPadronRucUseCase.Consulta;

import java.time.Instant;

public record PadronRucResponse(
        @Schema(example = "20100066603") String ruc,
        @Schema(example = "TIENDAS DEL SUR S.A.C.") String razonSocial,
        @Schema(example = "ACTIVO", description = "Estado del contribuyente, tal como lo publica SUNAT (`ACTIVO`, `BAJA DE OFICIO`, `BAJA DEFINITIVA`, `SUSPENSION TEMPORAL`…; el archivo los trunca a 20 caracteres)") String estado,
        @Schema(example = "HABIDO", description = "Condición del domicilio fiscal, tal como la publica SUNAT (`HABIDO`, `NO HABIDO`, `PENDIENTE`, `NO HALLADO …`, `-`)") String condicion,
        @Schema(description = "`estado` es `ACTIVO`") boolean activo,
        @Schema(description = "`condicion` es `HABIDO`") boolean habido,
        @Schema(example = "150101", nullable = true, description = "Ubigeo del domicilio fiscal; casi nunca viene para un RUC 10") String ubigeo,
        @Schema(example = "AV. REPUBLICA DE PANAMA NRO. 2457", nullable = true, description = "Domicilio fiscal armado con las partes que publica SUNAT; sin departamento, provincia ni distrito (solo el `ubigeo`)") String direccion,
        @Schema(description = "Cuándo terminó la carga del padrón que respondió. Un RUC creado después de esa fecha todavía no aparece") Instant actualizadoAl) {

    public static PadronRucResponse de(Consulta c) {
        var x = c.contribuyente();
        return new PadronRucResponse(x.ruc(), x.razonSocial(), x.estado(), x.condicion(), x.activo(), x.habido(), x.ubigeo(), x.direccion(), c.actualizadoAl());
    }
}
