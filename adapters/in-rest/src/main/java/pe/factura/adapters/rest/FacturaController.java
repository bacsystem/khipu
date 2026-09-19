package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.ComprobanteResponse;
import pe.factura.adapters.rest.dto.CorreoRequest;
import pe.factura.adapters.rest.dto.FacturaRequest;
import pe.factura.application.port.in.CompartirComprobanteUseCase;
import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.in.DarDeBajaUseCase;
import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.in.RecuperarCdrUseCase;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/facturas")
@RequiredArgsConstructor
@Tag(name = "Facturas", description = """
        Emisión y consulta de facturas electrónicas (tipo 01). Una factura se emite en una sola llamada: khipu asigna el
        número correlativo de la serie, genera el XML UBL 2.1, lo firma con el certificado de la empresa, lo valida y lo
        envía a SUNAT; la respuesta trae el estado y, si SUNAT respondió, el CDR. Si SUNAT no está disponible, el
        comprobante queda en `ERROR_ENVIO` y khipu lo reintenta solo (ver *Estados del comprobante* en la introducción).

        Autenticación: `X-Api-Key` de la empresa (integradores) o sesión del portal. Todos los importes van con hasta
        2 decimales y en la moneda del comprobante; las fechas en `YYYY-MM-DD`.""")
public class FacturaController {
    private static final String BASE = "/v1/facturas";
    public static final String TOTAL_HEADER = "X-Total-Count";
    private final EmitirComprobanteUseCase emitir;
    private final EnviarDocumentoUseCase enviar;
    private final ConsultarComprobanteUseCase consultar;
    private final DarDeBajaUseCase bajas;
    private final CompartirComprobanteUseCase compartir;
    private final RecuperarCdrUseCase cdrs;


    @PostMapping
    @Operation(summary = "Emitir una factura", description = """
            Crea la factura, la numera, la firma y la envía a SUNAT en la misma llamada (salvo `enviar_automatico: false`,
            que la deja `FIRMADO` para enviarla después con `POST /v1/facturas/{id}/enviar`).

            **Qué validar antes de llamar**: la serie debe existir y ser de factura (`F###`, ver *Series*); el receptor de una
            factura siempre lleva RUC (`cliente.tipo_doc = "6"`); `tipo_afectacion_igv` de cada ítem viene del catálogo 07;
            `unidad` del catálogo 03; `tipo_operacion` del catálogo 51 (`0101` venta interna por defecto).

            **Precios**: `precio_unitario` es el precio de venta unitario **con IGV incluido** para ítems gravados; khipu
            calcula el valor unitario, el IGV y los totales (tolerancias SUNAT ±1).

            **Forma de pago** (obligatoria desde 2022): al contado por defecto; al crédito indique `monto_pendiente` y las
            `cuotas` con fecha posterior a la emisión.

            **Errores frecuentes**: `422 VALIDACION` (campos con formato inválido, detalle en `errores`),
            `422 FORMA_PAGO_INVALIDA` / `SERIE_INVALIDA` / `RECEPTOR_INVALIDO` (regla de negocio, el mensaje lleva el código
            SUNAT cuando aplica), `409 DUPLICADO` (ya existe ese `correlativo` en la serie), `422 CREDENCIALES_SOL_FALTAN`
            (la empresa aún no cargó usuario/clave SOL).""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Factura creada; `estado_documento` indica si SUNAT ya la aceptó"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Correlativo repetido en la serie (DUPLICADO)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Datos inválidos o regla de negocio incumplida; `codigo` y `mensaje` explican cuál")})
    public ResponseEntity<ApiResponse<ComprobanteResponse>> crear(HttpServletRequest req, @Valid @RequestBody FacturaRequest body) {
        Comprobante c = emitir.emitirFactura(TenantActual.id(req), body.aComando());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ComprobanteResponse.de(c, BASE)));
    }

    @GetMapping
    @Operation(summary = "Listar facturas", description = """
            Facturas de la empresa, de la más reciente a la más antigua, paginadas. El total de resultados va en la cabecera
            `X-Total-Count`. Filtre por `estado` para trabajar una cola (p. ej. `ERROR_ENVIO` para reintentar, `RECHAZADO`
            para corregir y reemitir).""")
    public ResponseEntity<ApiResponse<List<ComprobanteResponse>>> listar(HttpServletRequest req,
                                                                        @Parameter(description = "Estado del comprobante (ver *Estados del comprobante*)") @RequestParam(required = false) EstadoDocumento estado,
                                                                        @Parameter(description = "Página, desde 1") @RequestParam(defaultValue = "1") int pagina,
                                                                        @Parameter(description = "Resultados por página, 1–100") @RequestParam(name = "por_pagina", defaultValue = "20") int porPagina) {
        UUID t = TenantActual.id(req);
        List<ComprobanteResponse> datos = consultar.listar(t, estado, Math.max(1, pagina), Math.min(100, Math.max(1, porPagina)))
                .stream().map(c -> ComprobanteResponse.de(c, BASE)).toList();
        return ResponseEntity.ok().header(TOTAL_HEADER, String.valueOf(consultar.contar(t, estado))).body(ApiResponse.ok(datos));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar un comprobante", description = """
            Estado actual, respuesta de SUNAT (`cdr`), totales, forma de pago y enlaces de descarga. Úselo para hacer seguimiento
            de un comprobante que quedó en `ERROR_ENVIO` o `ENVIADO`. Sirve también para las notas de crédito/débito emitidas con
            `POST /v1/notas` (traen el bloque `nota`); una factura incluye en `notas` las notas emitidas sobre ella.""")
    public ApiResponse<ComprobanteResponse> obtener(HttpServletRequest req, @PathVariable UUID id) {
        UUID t = TenantActual.id(req);
        Comprobante c = consultar.obtener(t, id);
        return ApiResponse.ok(ComprobanteResponse.de(c, BASE, consultar.notasDe(t, c), bajas.deComprobante(t, id).stream().findFirst().orElse(null)));
    }

    @PostMapping("/{id}/enviar")
    @Operation(summary = "Enviar (o reintentar) a SUNAT", description = """
            Envía un comprobante `FIRMADO` (emitido con `enviar_automatico: false`) o reintenta uno en `ERROR_ENVIO` sin
            esperar al reintento automático. Un comprobante `ACEPTADO`, `RECHAZADO` o `ANULADO` responde `409 ESTADO_NO_ENVIABLE`.""")
    public ApiResponse<ComprobanteResponse> enviar(HttpServletRequest req, @PathVariable UUID id) {
        return ApiResponse.ok(ComprobanteResponse.de(enviar.enviar(TenantActual.id(req), id), BASE));
    }

    @GetMapping("/{id}/xml")
    @Operation(summary = "Descargar el XML firmado", description = "El XML UBL 2.1 exactamente como se envió a SUNAT (firma XML-DSig incluida). Nombre de archivo `RUC-01-SERIE-NUMERO.xml`.")
    public ResponseEntity<byte[]> xml(HttpServletRequest req, @PathVariable UUID id) {
        UUID t = TenantActual.id(req);
        Comprobante c = consultar.obtener(t, id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + c.nombreArchivo() + ".xml\"")
                .body(consultar.xml(t, id));
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Descargar la representación impresa (PDF)", description = """
            PDF con los datos del emisor y del adquirente, ítems, totales, el código QR (RUC|tipo|serie|número|IGV|total|fecha|
            tipo y número de documento del adquirente|hash) y el valor resumen (hash de la firma). Se genera la primera vez que se
            pide y se guarda junto al XML. Disponible desde que el comprobante está `FIRMADO`; `422 SIN_FIRMA` antes.""")
    public ResponseEntity<byte[]> pdf(HttpServletRequest req, @PathVariable UUID id) {
        UUID t = TenantActual.id(req);
        Comprobante c = consultar.obtener(t, id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + c.nombreArchivo() + ".pdf\"")
                .body(consultar.pdf(t, id));
    }

    @PostMapping("/{id}/correo")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Enviar el comprobante por correo al adquirente", description = """
            Envía al correo indicado la representación impresa (PDF), el XML firmado y la constancia de recepción (CDR) como
            adjuntos, con un mensaje opcional del emisor. Solo comprobantes `ACEPTADO` o `ACEPTADO_CON_OBS`: `409 NO_ACEPTADO`
            en cualquier otro estado. Si el servidor de correo rechaza el envío, `502 CORREO_NO_ENVIADO` (reintente más tarde).""")
    public ApiResponse<Void> correo(HttpServletRequest req, @PathVariable UUID id, @Valid @RequestBody CorreoRequest body) {
        compartir.enviarPorCorreo(TenantActual.id(req), id, body.email(), body.mensaje());
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/cdr")
    @Operation(summary = "Descargar la constancia de recepción (CDR)", description = """
            El CDR es la prueba de que SUNAT recibió y aceptó (o rechazó) el comprobante. Por defecto se entrega el ZIP tal
            como lo devolvió SUNAT; con `formato=xml` se extrae el `ApplicationResponse`. Responde `404 SIN_CDR` mientras
            SUNAT no haya respondido.""")
    public ResponseEntity<byte[]> cdr(HttpServletRequest req, @PathVariable UUID id,
                                      @Parameter(description = "`zip` (por defecto) o `xml`") @RequestParam(required = false) String formato) {
        UUID t = TenantActual.id(req);
        if (formato != null && !formato.equalsIgnoreCase("zip") && !formato.equalsIgnoreCase("xml")) {
            throw new DomainException("PARAMETRO_INVALIDO", "formato debe ser 'zip' (por defecto) o 'xml'");
        }
        Comprobante c = consultar.obtener(t, id);
        if ("xml".equalsIgnoreCase(formato)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"R-" + c.nombreArchivo() + ".xml\"")
                    .body(consultar.cdrXml(t, id));
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"R-" + c.nombreArchivo() + ".zip\"")
                .body(consultar.cdr(t, id));
    }

    @PostMapping("/{id}/cdr/recuperar")
    @Operation(summary = "Recuperar el CDR desde SUNAT", description = """
            `getStatusCdr` de `billConsultService`: pide a SUNAT la constancia de un comprobante propio que quedó `ENVIADO` o en
            `ERROR_ENVIO` (por ejemplo, la conexión se cortó después de que SUNAT lo aceptara) o que ya está resuelto pero perdió
            su CDR en el storage. Si SUNAT lo tiene, lo guarda y aplica el resultado (`ACEPTADO`/`RECHAZADO`) sin reenviar;
            si no, devuelve el comprobante sin cambios. Un barrido horario hace lo mismo para todas las empresas en producción.
            Errores: `404 NO_ENCONTRADO`, `422 SIN_FIRMA`, `409 CDR_YA_DISPONIBLE`, `422 NO_DISPONIBLE_EN_BETA`, `422 CREDENCIALES_SOL_NO_CARGADAS`.""")
    public ApiResponse<ComprobanteResponse> recuperarCdr(HttpServletRequest req, @PathVariable UUID id) {
        return ApiResponse.ok(ComprobanteResponse.de(cdrs.recuperar(TenantActual.id(req), id), BASE));
    }
}
