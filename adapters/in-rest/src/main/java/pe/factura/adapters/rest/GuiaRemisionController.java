package pe.factura.adapters.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.GuiaRemisionRequest;
import pe.factura.adapters.rest.dto.GuiaRemisionResponse;
import pe.factura.application.port.in.ConsultarGuiaRemisionUseCase;
import pe.factura.application.port.in.EmitirGuiaRemisionUseCase;
import pe.factura.application.port.in.EnviarGuiaRemisionUseCase;
import pe.factura.application.port.out.GuiaRemisionRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.EstadoGuia;
import pe.factura.domain.documento.GuiaRemision;
import pe.factura.domain.documento.TipoDocumento;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/guias-remision")
@RequiredArgsConstructor
@Tag(name = "Guías de remisión", description = """
        Guías de remisión electrónicas (GRE): la **remitente** (tipo 09, serie `T###`), que emite quien envía los bienes, y la **transportista** (tipo 31,
        serie `V###`), que emite la empresa de transporte. khipu valida el traslado con las reglas de SUNAT, numera, genera el XML UBL 2.1 `DespatchAdvice`, lo
        firma y lo envía por la **API REST de SUNAT** (no por SOAP): SUNAT responde un ticket y la constancia (CDR) llega al consultarlo; khipu lo consulta solo.

        **Requisitos de la empresa**: el certificado, las credenciales SOL **y** las credenciales API SUNAT (`PUT /v1/empresa/credenciales-api-sunat`: el
        `client_id` y `client_secret` de la aplicación registrada en SOL, opción «Credenciales de API SUNAT»). Sin las dos, la guía se puede emitir con
        `enviar_automatico: false` pero no enviar. SUNAT no publica una API de pruebas para la GRE: una empresa en `BETA` no envía guías (`422 GRE_NO_DISPONIBLE`)
        salvo que el servidor configure una.

        Autenticación: `X-Api-Key` de la empresa o sesión del portal.""")
public class GuiaRemisionController {
    static final String BASE = "/v1/guias-remision";
    private final EmitirGuiaRemisionUseCase emitir;
    private final EnviarGuiaRemisionUseCase enviar;
    private final ConsultarGuiaRemisionUseCase consultar;
    private final ObjectMapper json;

    @PostMapping
    @Operation(summary = "Emitir una guía de remisión", description = """
            Valida, numera en la serie (`T###` remitente, `V###` transportista), firma y —salvo `enviar_automatico: false`— envía la guía a SUNAT en la misma
            llamada. La respuesta trae el `estado`: `ACEPTADO` si SUNAT ya la procesó, `ENVIADO_PENDIENTE_TICKET` si todavía la procesa (khipu consulta el ticket
            solo, cada vez más espaciado, durante ~24 h; también `POST /v1/guias-remision/{id}/ticket`) o `ERROR_ENVIO` (ver `ultimo_error` y
            `reintento_automatico`).

            **Remitente (09)**: `motivo` (catálogo 20), `modalidad` (catálogo 18) y, en transporte público (`01`), `transportista`. En privado (`02`) van
            `traslado.vehiculos` y `traslado.conductores` (el primero de cada lista es el principal), salvo con el indicador `vehiculo_m1_o_l`. El traslado entre
            establecimientos (`04`) va del establecimiento propio de partida al de llegada, con el propio remitente como destinatario.

            **Transportista (31)**: `remitente`, `registro_mtc` y siempre el vehículo y el conductor principales; sin motivo ni modalidad.

            **Puntos de partida y llegada**: ubigeo (catálogo 13) y dirección; un establecimiento de la propia empresa se indica solo con su código
            (`establecimiento`, de `GET /v1/empresa/establecimientos`; `0000` el domicilio fiscal) y khipu completa el resto.

            **Errores** (antes de numerar, el mensaje empieza por el código SUNAT cuando lo hay): `422 VALIDACION`, `422 TRASLADO_INVALIDO`,
            `422 TRANSPORTE_INVALIDO`, `422 DESTINATARIO_INVALIDO`, `422 REMITENTE_INVALIDO`, `422 ITEM_INVALIDO`, `422 DOCUMENTO_RELACIONADO_INVALIDO`,
            `422 SERIE_INVALIDA` / `SERIE_NO_CONFIGURADA`, `422 CREDENCIALES_API_SUNAT_NO_CARGADAS` / `CREDENCIALES_SOL_NO_CARGADAS` / `GRE_NO_DISPONIBLE` (solo si
            se envía en el acto).

            **Reintentos seguros** (`Idempotency-Key`): igual que en las facturas, la misma clave y el mismo cuerpo devuelven `200` con la misma guía, sin
            numerar otra ni volver a enviarla. Las claves de las guías son aparte de las de facturas y notas.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Guía emitida; `estado` dice si SUNAT ya la aceptó"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pedido repetido con la misma `Idempotency-Key`: la guía ya emitida, en su estado actual"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Datos inválidos o regla de SUNAT incumplida; `codigo` y `mensaje` dicen cuál")})
    public ResponseEntity<ApiResponse<GuiaRemisionResponse>> crear(HttpServletRequest req, @Valid @RequestBody GuiaRemisionRequest body,
            @Parameter(description = "Clave única por guía (un UUID), para repetir el pedido sin emitir dos veces", example = "5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
            @RequestHeader(name = ClaveDeIdempotencia.CABECERA, required = false) String clave) {
        var idempotencia = ClaveDeIdempotencia.de(clave, body, json);
        var emision = emitir.emitir(TenantActual.id(req), body.aComando(), idempotencia);
        return ResponseEntity.status(emision.repetida() ? HttpStatus.OK : HttpStatus.CREATED).body(ApiResponse.ok(GuiaRemisionResponse.de(emision.guia(), BASE)));
    }

    @PostMapping("/validar")
    @Operation(summary = "Validar una guía sin emitirla", description = """
            Recibe el mismo cuerpo que `POST /v1/guias-remision` y responde la guía que se emitiría —con los establecimientos de la empresa ya resueltos—
            **sin numerar, firmar, guardar ni enviar nada**. Aplica las mismas reglas que la emisión: lo que esta rechazaría responde aquí el mismo `422`. No
            comprueba la serie, el certificado ni las credenciales. El `id` no existe, `numero` es `null` y no trae `enlaces`.""")
    public ApiResponse<GuiaRemisionResponse> validar(HttpServletRequest req, @Valid @RequestBody GuiaRemisionRequest body) {
        return ApiResponse.ok(GuiaRemisionResponse.de(emitir.validar(TenantActual.id(req), body.aComando()), null));
    }

    @GetMapping
    @Operation(summary = "Listar guías de remisión", description = """
            Guías de la empresa, de la más reciente a la más antigua, paginadas; el total va en `X-Total-Count`. Filtros: `estado`, `tipo` (`09` o `31`),
            `serie` exacta y fecha de emisión `desde`/`hasta` (inclusive; `desde > hasta` es `400 RANGO_INVALIDO`).""")
    public ResponseEntity<ApiResponse<List<GuiaRemisionResponse>>> listar(HttpServletRequest req,
            @Parameter(description = "Estado de la guía") @RequestParam(required = false) EstadoGuia estado,
            @Parameter(description = "`09` remitente o `31` transportista", example = "09") @RequestParam(required = false) String tipo,
            @Parameter(description = "Serie exacta (T001, V001…)", example = "T001") @RequestParam(required = false) String serie,
            @Parameter(description = "Fecha de emisión mínima, `YYYY-MM-DD`") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Fecha de emisión máxima, `YYYY-MM-DD`") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(description = "Página, desde 1") @RequestParam(defaultValue = "1") int pagina,
            @Parameter(description = "Resultados por página, 1–100") @RequestParam(name = "por_pagina", defaultValue = "20") int porPagina) {
        UUID t = TenantActual.id(req);
        if (desde != null && hasta != null && desde.isAfter(hasta)) throw new DomainException("RANGO_INVALIDO", "desde (" + desde + ") es posterior a hasta (" + hasta + ")");
        TipoDocumento tipoGuia = null;
        if (tipo != null) {
            if (!tipo.equals("09") && !tipo.equals("31")) throw new DomainException("PARAMETRO_INVALIDO", "tipo es 09 (remitente) o 31 (transportista)");
            tipoGuia = TipoDocumento.porCodigo(tipo);
        }
        if (serie != null && !serie.matches("[TV][A-Z0-9]{3}")) throw new DomainException("PARAMETRO_INVALIDO", "serie de guía con formato inválido (T### o V###)");
        var filtro = new GuiaRemisionRepository.Filtro(estado, tipoGuia, serie, desde, hasta);
        List<GuiaRemisionResponse> datos = consultar.listar(t, filtro, Math.max(1, pagina), Math.min(100, Math.max(1, porPagina))).stream().map(g -> GuiaRemisionResponse.de(g, BASE)).toList();
        return ResponseEntity.ok().header(FacturaController.TOTAL_HEADER, String.valueOf(consultar.contar(t, filtro))).body(ApiResponse.ok(datos));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar una guía de remisión", description = "Estado, ticket, respuesta de SUNAT (`cdr`), datos del traslado y enlaces de descarga.")
    public ApiResponse<GuiaRemisionResponse> obtener(HttpServletRequest req, @PathVariable UUID id) {
        return ApiResponse.ok(GuiaRemisionResponse.de(consultar.obtener(TenantActual.id(req), id), BASE));
    }

    @PostMapping("/{id}/enviar")
    @Operation(summary = "Enviar (o reenviar) la guía a SUNAT", description = """
            Envía una guía `FIRMADO` (emitida con `enviar_automatico: false`) o reenvía una en `ERROR_ENVIO` —por ejemplo, después de corregir las credenciales—
            sin esperar al reintento automático. Otra en cualquier otro estado responde `409 ESTADO_NO_ENVIABLE`.""")
    public ApiResponse<GuiaRemisionResponse> enviar(HttpServletRequest req, @PathVariable UUID id) {
        return ApiResponse.ok(GuiaRemisionResponse.de(enviar.enviar(TenantActual.id(req), id), BASE));
    }

    @PostMapping("/{id}/ticket")
    @Operation(summary = "Consultar el ticket en SUNAT", description = """
            Pregunta a SUNAT por el ticket de una guía `ENVIADO_PENDIENTE_TICKET` y aplica la respuesta: el CDR (`ACEPTADO`, `ACEPTADO_CON_OBS`, `RECHAZADO`) o
            sigue en proceso. Si khipu había dejado de consultarlo solo (`reintento_automatico: false`) y SUNAT sigue procesándolo, retoma las consultas. Una
            guía que no espera un ticket responde `409 TICKET_NO_PENDIENTE`.""")
    public ApiResponse<GuiaRemisionResponse> ticket(HttpServletRequest req, @PathVariable UUID id) {
        return ApiResponse.ok(GuiaRemisionResponse.de(enviar.consultarTicket(TenantActual.id(req), id), BASE));
    }

    @GetMapping("/{id}/xml")
    @Operation(summary = "Descargar el XML firmado", description = "El `DespatchAdvice` tal como se envió a SUNAT. Nombre `RUC-09-SERIE-NUMERO.xml` (o `-31-`).")
    public ResponseEntity<byte[]> xml(HttpServletRequest req, @PathVariable UUID id) {
        UUID t = TenantActual.id(req);
        GuiaRemision g = consultar.obtener(t, id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + g.nombreArchivo() + ".xml\"").body(consultar.xml(t, id));
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Descargar la representación impresa (PDF)", description = """
            Emisor, destinatario, datos del traslado (motivo, modalidad, partida, llegada, transportista, vehículos y conductores), bienes y el valor resumen. Lleva
            el QR con la URL de consulta que SUNAT pone en el CDR cuando la trae; antes de eso, sale sin QR. Se genera la primera vez y se guarda.""")
    public ResponseEntity<byte[]> pdf(HttpServletRequest req, @PathVariable UUID id) {
        UUID t = TenantActual.id(req);
        GuiaRemision g = consultar.obtener(t, id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + g.nombreArchivo() + ".pdf\"").body(consultar.pdf(t, id));
    }

    @GetMapping("/{id}/cdr")
    @Operation(summary = "Descargar la constancia de recepción (CDR)", description = "El ZIP que devolvió SUNAT al consultar el ticket; con `formato=xml`, el `ApplicationResponse`. `404 SIN_CDR` mientras no haya.")
    public ResponseEntity<byte[]> cdr(HttpServletRequest req, @PathVariable UUID id, @Parameter(description = "`zip` (por defecto) o `xml`") @RequestParam(required = false) String formato) {
        if (formato != null && !formato.equalsIgnoreCase("zip") && !formato.equalsIgnoreCase("xml"))
            throw new DomainException("PARAMETRO_INVALIDO", "formato debe ser 'zip' (por defecto) o 'xml'");
        UUID t = TenantActual.id(req);
        GuiaRemision g = consultar.obtener(t, id);
        if ("xml".equalsIgnoreCase(formato))
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"R-" + g.nombreArchivo() + ".xml\"").body(consultar.cdrXml(t, id));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"R-" + g.nombreArchivo() + ".zip\"").body(consultar.cdr(t, id));
    }
}
