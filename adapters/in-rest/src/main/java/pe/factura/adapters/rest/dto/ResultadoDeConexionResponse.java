package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.AccionesDeEmpresaUseCase.Resultado;
import pe.factura.application.port.in.AccionesDeEmpresaUseCase.ResultadoDeConexion;
import pe.factura.domain.tenant.Entorno;

/** Cómo contestó SUNAT a la prueba de conexión de una empresa (#187). Nunca lleva las credenciales SOL. */
public record ResultadoDeConexionResponse(
        @Schema(description = "`CONECTADO`: contestó con normalidad. `RECHAZADO`: contestó con un error definitivo. `SIN_RESPUESTA`: red, tiempo de espera, error del servicio o de autenticación HTTP") Resultado resultado,
        @Schema(description = "Contra qué entorno de SUNAT se probó: el que la empresa tiene ahora") Entorno entorno,
        @Schema(example = "1033", description = "El código de SUNAT; ausente si contestó con normalidad") String codigo,
        @Schema(example = "El ticket no existe", description = "El mensaje de SUNAT, tal cual; ausente si contestó con normalidad") String mensaje) {
    public static ResultadoDeConexionResponse de(ResultadoDeConexion r) {
        return new ResultadoDeConexionResponse(r.resultado(), r.entorno(), r.codigo(), r.mensaje());
    }
}
