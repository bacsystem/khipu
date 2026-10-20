package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ConsultarValidezUseCase;

import java.util.List;

public record ValidezResponse(
        @Schema(example = "ACEPTADO", description = "`ACEPTADO`, `RECHAZADO`, `DE_BAJA`, `NO_EXISTE`, `AJENO`, `AUTORIZADO`, `NO_AUTORIZADO` o `ERROR_CONSULTA`") String estado,
        @Schema(example = "0001", description = "Código de retorno de SUNAT en su fuente: `statusCode` de billValidService (`0001`…) o `estadoCp` de la API REST (`0`…`4`)") String codigo,
        @Schema(example = "El comprobante existe y está aceptado.") String mensaje,
        @Schema(example = "API_REST", description = "Por dónde respondió SUNAT: `API_REST` (credenciales API SUNAT) o `SOAP` (billValidService, clave SOL)") String fuente,
        @Schema(example = "00", description = "Solo por la API REST: estado del RUC del emisor (`00` activo, `01` baja provisional, `02` baja prov. por oficio, `03` suspensión temporal, `10` baja definitiva, `11` baja de oficio, `22` inhabilitado-vent. única)")
        String estadoRuc,
        @Schema(example = "00", description = "Solo por la API REST: condición de domicilio del emisor (`00` habido, `09` pendiente, `11` por verificar, `12` no habido, `20` no hallado)")
        String condicionDomicilio,
        @Schema(description = "Solo por la API REST: observaciones de SUNAT sobre los datos consultados (p. ej. la fecha no coincide); ausente si no hay")
        List<String> observaciones,
        @Schema(example = "La API de SUNAT no respondió (HTTP 503): se usó billValidService con la clave SOL", description = "Por qué se respondió por SOAP teniendo credenciales API SUNAT; ausente si no")
        String aviso) {

    public static ValidezResponse de(ConsultarValidezUseCase.Validez v) {
        return new ValidezResponse(v.estado(), v.codigo(), v.mensaje(), v.fuente() == null ? null : v.fuente().name(), v.estadoRuc(), v.condicionDomicilio(),
                v.observaciones().isEmpty() ? null : v.observaciones(), v.aviso());
    }
}
