package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import pe.factura.adapters.rest.dto.ConciliacionRvieResponse;
import pe.factura.application.port.in.RegistroDeVentasSireUseCase;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/v1/sire")
@RequiredArgsConstructor
@Tag(name = "SIRE", description = """
        Registro de Ventas e Ingresos Electrónico (RVIE) del SIRE: el archivo de reemplazo de la propuesta, armado con los comprobantes que khipu emitió y SUNAT tiene, y la
        conciliación de esos comprobantes con la propuesta que usted descarga del SIRE. khipu no se conecta al SIRE: el archivo lo sube usted (o su contador) en SUNAT Operaciones
        en Línea. **El formato sigue la estructura de 40 campos de la propuesta del RVIE y todavía no se validó contra el Anexo 3 vigente ni contra el SIRE**: revise el archivo antes
        de reemplazar una propuesta. Autenticación: `X-Api-Key` o sesión del portal.""")
public class SireController {
    public static final String CABECERA_REGISTROS = "X-Rvie-Registros";
    public static final String CABECERA_PENDIENTES = "X-Rvie-Pendientes-Sunat";
    public static final String CABECERA_SIN_TIPO_CAMBIO = "X-Rvie-Sin-Tipo-Cambio";

    private final RegistroDeVentasSireUseCase registro;

    @GetMapping("/rvie")
    @Operation(operationId = "exportarRvie", summary = "Descargar el registro de ventas (RVIE) de un periodo", description = """
            Un ZIP `LE<RUC><AAAAMM>00140400021112.zip` con el `.txt` del mismo nombre: una fila por comprobante emitido en el periodo que SUNAT tiene —aceptado, con o sin
            observaciones, o anulado después—, en el orden fecha, tipo, serie y número. 40 campos separados por `|` (también al final de la fila), fin de línea CRLF, UTF-8,
            fechas `dd/mm/aaaa`, importes con 2 decimales en la moneda del comprobante y tipo de cambio con 3 (`1.000` en soles). Cada boleta va en su propia fila; la
            nota de crédito va en negativo y lleva en los campos 29–32 el comprobante que modifica y en el 34 su motivo; un anulado va con estado `2` e importes en cero.

            Lo que todavía va en camino a SUNAT (firmado, enviado o con error de envío) no entra: cuántos son lo dice la cabecera `X-Rvie-Pendientes-Sunat`, y
            `X-Rvie-Registros` cuántas filas trae el archivo. Errores, siempre antes de empezar a escribir el archivo: `400 PARAMETRO_INVALIDO` (periodo que no es `AAAAMM`
            o que todavía no empieza), `404 NO_ENCONTRADO` (nada en el periodo), `422 RVIE_EXCEDE_EL_TOPE` (más de 100 000 comprobantes) y `422 RVIE_SIN_TIPO_CAMBIO`
            (comprobantes en otra moneda sin tipo de cambio, que el campo 28 pide: con `permitir_sin_tipo_cambio=true` salen con el campo vacío y la cuenta va en
            `X-Rvie-Sin-Tipo-Cambio`). Si la lectura falla a mitad de camino, la descarga se corta con un ZIP inválido, nunca con un archivo que parezca completo.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "El ZIP del registro de ventas", content = @Content(mediaType = "application/zip",
                    schema = @Schema(type = "string", format = "binary")), headers = {
                    @Header(name = CABECERA_REGISTROS, description = "Filas del archivo", schema = @Schema(type = "integer")),
                    @Header(name = CABECERA_PENDIENTES, description = "Comprobantes del periodo que todavía no resolvió SUNAT y no entran", schema = @Schema(type = "integer")),
                    @Header(name = CABECERA_SIN_TIPO_CAMBIO, description = "Filas fuera de soles con el tipo de cambio vacío (solo con permitir_sin_tipo_cambio)", schema = @Schema(type = "integer"))}),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No hay comprobantes en el periodo (NO_ENCONTRADO)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "RVIE_EXCEDE_EL_TOPE o RVIE_SIN_TIPO_CAMBIO")})
    public ResponseEntity<StreamingResponseBody> exportar(HttpServletRequest req,
                                                          @Parameter(description = "Periodo tributario, `AAAAMM`", example = "202610") @RequestParam String periodo,
                                                          @Parameter(description = "Escribir aun si hay comprobantes en otra moneda sin tipo de cambio (campo 28 vacío)")
                                                          @RequestParam(name = "permitir_sin_tipo_cambio", defaultValue = "false") boolean permitirSinTipoCambio) {
        RegistroDeVentasSireUseCase.Exportacion e = registro.preparar(TenantActual.id(req), periodo, permitirSinTipoCambio);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + e.nombreArchivo() + ".zip\"")
                .header(CABECERA_REGISTROS, String.valueOf(e.registros()))
                .header(CABECERA_PENDIENTES, String.valueOf(e.pendientesDeSunat()))
                .header(CABECERA_SIN_TIPO_CAMBIO, String.valueOf(e.sinTipoCambio()))
                .body(salida -> registro.escribir(e, salida));
    }

    @PostMapping("/rvie/conciliacion")
    @Operation(operationId = "conciliarRvie", summary = "Conciliar el registro de ventas con la propuesta de SUNAT", description = """
            Compare lo que khipu emitió con la propuesta del RVIE que descarga del SIRE: envíe el archivo tal como lo descargó, `.txt` o `.zip` con un `.txt`, como cuerpo
            de la petición (no multipart), hasta 20 MB (64 MB descomprimido). La respuesta dice qué comprobantes están solo en SUNAT, solo en khipu, y cuáles están en los
            dos con algún dato distinto (fecha, moneda, estado, base gravada, IGV, exonerado, inafecto o total), comparados por tipo, serie y número. No guarda nada.

            La propuesta se lee con la misma estructura de 40 campos que el archivo de `GET /v1/sire/rvie` (una fila de títulos al principio se ignora): **supuesto sin
            verificar contra el SIRE**. Errores: `400 PARAMETRO_INVALIDO` (periodo), `422 RVIE_ARCHIVO_INVALIDO` (una fila que no se puede leer, de otro RUC o de otro
            periodo, o un comprobante repetido; el mensaje dice la línea y el campo), `422 RVIE_ARCHIVO_DEMASIADO_GRANDE` y `422 RVIE_EXCEDE_EL_TOPE` (más de 100 000 filas).""")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "La propuesta del RVIE descargada del SIRE (.txt o .zip)", content = {
            @Content(mediaType = "application/zip", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "text/plain", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary"))})
    public ApiResponse<ConciliacionRvieResponse> conciliar(HttpServletRequest req,
                                                           @Parameter(description = "Periodo tributario, `AAAAMM`", example = "202610") @RequestParam String periodo) throws IOException {
        try (InputStream cuerpo = req.getInputStream()) {
            return ApiResponse.ok(ConciliacionRvieResponse.de(registro.conciliar(TenantActual.id(req), periodo, cuerpo)));
        }
    }
}
