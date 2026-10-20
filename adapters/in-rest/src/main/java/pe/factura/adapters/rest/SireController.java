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
import pe.factura.domain.DomainException;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/v1/sire")
@RequiredArgsConstructor
@Tag(name = "SIRE", description = """
        **EXPERIMENTAL.** Registro de Ventas e Ingresos Electrónico (RVIE) del SIRE: el archivo de reemplazo de la propuesta, armado con los comprobantes que khipu emitió y
        SUNAT tiene, y la conciliación de esos comprobantes con la propuesta que usted descarga del SIRE. khipu no se conecta al SIRE: el archivo lo sube usted (o su contador)
        en SUNAT Operaciones en Línea. **El formato está reconstruido** del Anexo III de la RS 000138-2023 (que cubre solo una parte de los campos) y del control de cambios
        del Aplicativo Cliente SIRE: **no lo use para reemplazar una propuesta real antes de validarlo con el Programa Validador SIRE (PVSIRE)**. **Cubre solo lo emitido en
        khipu**: reemplazar la propuesta con él saca las ventas emitidas por otros medios (SEE-SOL, comprobantes físicos, otro proveedor); antes de reemplazar, concilie y
        exija `solo_en_sunat` vacío. Autenticación: `X-Api-Key` o sesión del portal.""")
public class SireController {
    public static final String CABECERA_REGISTROS = "X-Rvie-Registros";
    public static final String CABECERA_PENDIENTES = "X-Rvie-Pendientes-Sunat";
    public static final String CABECERA_SIN_TIPO_CAMBIO = "X-Rvie-Sin-Tipo-Cambio";
    /** H3: el archivo trae solo lo emitido en khipu, no todo lo que SUNAT tiene de la empresa. */
    public static final String CABECERA_ALCANCE = "X-Rvie-Alcance";
    public static final String SOLO_KHIPU = "solo-khipu";

    private final RegistroDeVentasSireUseCase registro;

    @GetMapping("/rvie")
    @Operation(operationId = "exportarRvie", summary = "Descargar el registro de ventas (RVIE) de un periodo (experimental)", description = """
            **EXPERIMENTAL: formato reconstruido; no lo use para reemplazar una propuesta real antes de validarlo con el PVSIRE.** **Cubre solo lo emitido en khipu**
            (cabecera `X-Rvie-Alcance: solo-khipu`): antes de reemplazar la propuesta, concilie con `POST /v1/sire/rvie/conciliacion` y exija `solo_en_sunat` vacío.

            Un ZIP `LE<RUC><AAAAMM>00140400021112.zip` con el `.txt` del mismo nombre: una fila por comprobante emitido en el periodo que SUNAT tiene —aceptado, con o sin
            observaciones, o anulado después—, en el orden fecha, tipo, serie y número. 40 campos separados por `|` (también al final de la fila), fin de línea CRLF, UTF-8,
            fechas `dd/mm/aaaa`, importes con 2 decimales en la moneda del comprobante y tipo de cambio con 3 (`0.000` en soles). Cada boleta va en su propia fila; la
            nota de crédito va en negativo y lleva en los campos 29–32 el comprobante que modifica y en el 34 su motivo; un anulado va con estado `2` e importes en cero.

            Lo que todavía va en camino a SUNAT (firmado, enviado o con error de envío) no entra: cuántos son lo dice la cabecera `X-Rvie-Pendientes-Sunat`, y
            `X-Rvie-Registros` cuántas filas trae el archivo (contadas al prepararlo: si un comprobante cambia de estado durante la descarga puede ir uno más o menos). Errores, siempre antes de empezar a escribir el archivo: `400 PARAMETRO_INVALIDO` (periodo que no es `AAAAMM`
            o que todavía no empieza), `404 NO_ENCONTRADO` (nada en el periodo), `422 RVIE_EXCEDE_EL_TOPE` (más de 100 000 comprobantes) y `422 RVIE_SIN_TIPO_CAMBIO`
            (comprobantes en otra moneda sin tipo de cambio, que el campo 28 pide: con `permitir_sin_tipo_cambio=true` salen con el campo vacío y la cuenta va en
            `X-Rvie-Sin-Tipo-Cambio`). Si la lectura falla a mitad de camino, la descarga se corta con un ZIP inválido, nunca con un archivo que parezca completo.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "El ZIP del registro de ventas", content = @Content(mediaType = "application/zip",
                    schema = @Schema(type = "string", format = "binary")), headers = {
                    @Header(name = CABECERA_REGISTROS, description = "Filas del archivo, contadas al prepararlo (aproximado si un comprobante cambia de estado durante la descarga)", schema = @Schema(type = "integer")),
                    @Header(name = CABECERA_ALCANCE, description = "`solo-khipu`: el archivo trae solo lo emitido en khipu", schema = @Schema(type = "string")),
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
                .header(CABECERA_ALCANCE, SOLO_KHIPU)
                .header(CABECERA_PENDIENTES, String.valueOf(e.pendientesDeSunat()))
                .header(CABECERA_SIN_TIPO_CAMBIO, String.valueOf(e.sinTipoCambio()))
                .body(salida -> registro.escribir(e, salida));
    }

    @PostMapping("/rvie/conciliacion")
    @Operation(operationId = "conciliarRvie", summary = "Conciliar el registro de ventas con la propuesta de SUNAT (experimental)", description = """
            **EXPERIMENTAL: formato reconstruido, no validado con el PVSIRE.** Es el paso obligado antes de reemplazar una propuesta con el archivo de `GET /v1/sire/rvie`, que
            **cubre solo lo emitido en khipu**: `solo_en_sunat` debe estar vacío, o el reemplazo sacaría esas ventas del registro.

            Compare lo que khipu emitió con la propuesta del RVIE que descarga del SIRE: envíe el archivo tal como lo descargó, `.txt` o `.zip` con un `.txt`, como cuerpo
            de la petición (no multipart), hasta 20 MB (64 MB descomprimido). La respuesta dice qué comprobantes están solo en SUNAT, solo en khipu, y cuáles están en los
            dos con algún dato distinto (fecha, moneda, estado, base gravada, IGV, exonerado, inafecto o total), comparados por tipo, serie y número. No guarda nada.

            La propuesta se lee con la misma estructura de 40 campos que el archivo de `GET /v1/sire/rvie` (una fila de títulos al principio se ignora; una fila con el campo 10, un rango
            consolidado, no se concilia y se cuenta en `filas_consolidadas_no_conciliadas`): **supuesto sin verificar contra el SIRE**. Errores: `400 PARAMETRO_INVALIDO` (periodo, o la propuesta enviada como formulario), `422 RVIE_ARCHIVO_INVALIDO` (cuerpo vacío o sin filas de datos, ZIP dañado, una línea de más de 16 KB, una fila que no se puede leer, de otro RUC o de otro
            periodo, o un comprobante repetido; el mensaje dice la línea y el campo), `422 RVIE_PROPUESTA_DEMASIADO_GRANDE` y `422 RVIE_EXCEDE_EL_TOPE` (más de 100 000 filas).""")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "La propuesta del RVIE descargada del SIRE (.txt o .zip)", content = {
            @Content(mediaType = "application/zip", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "text/plain", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary"))})
    public ApiResponse<ConciliacionRvieResponse> conciliar(HttpServletRequest req,
                                                           @Parameter(description = "Periodo tributario, `AAAAMM`", example = "202610") @RequestParam String periodo) throws IOException {
        // Un formulario (lo que curl manda con --data-binary si no se le da Content-Type) lo lee el contenedor como parámetros al buscar `periodo`: el cuerpo llegaría
        // vacío y la conciliación diría que SUNAT no tiene nada. Se rechaza en vez de responder un reporte falso.
        String tipo = req.getContentType() == null ? "" : req.getContentType().toLowerCase(java.util.Locale.ROOT);
        if (tipo.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE) || tipo.startsWith("multipart/"))
            throw new DomainException("PARAMETRO_INVALIDO", "Envíe la propuesta como cuerpo de la petición con Content-Type application/zip, text/plain o application/octet-stream, no como formulario"
                    + " (con curl: -H \"Content-Type: application/zip\" --data-binary @propuesta.zip)");
        if (req.getContentLengthLong() == 0)
            throw new DomainException("RVIE_ARCHIVO_INVALIDO", "La propuesta llegó vacía: envíe el archivo descargado del SIRE como cuerpo de la petición");
        try (InputStream cuerpo = req.getInputStream()) {
            return ApiResponse.ok(ConciliacionRvieResponse.de(registro.conciliar(TenantActual.id(req), periodo, cuerpo)));
        }
    }
}
