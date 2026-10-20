package pe.factura.adapters.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import pe.factura.adapters.rest.dto.ComprobanteResponse;
import pe.factura.adapters.rest.dto.CorreoRequest;
import pe.factura.adapters.rest.dto.FacturaRequest;
import pe.factura.application.port.in.CompartirComprobanteUseCase;
import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.in.DarDeBajaUseCase;
import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.application.port.in.EnviarAlClienteUseCase;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.in.RecuperarCdrUseCase;
import pe.factura.application.port.in.ValidarCompradorUseCase;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/facturas")
@RequiredArgsConstructor
@Tag(name = "Facturas", description = """
        Emisión y consulta de facturas (tipo 01) y boletas de venta (tipo 03) electrónicas: la letra de la serie decide cuál
        (`F###` factura, `B###` boleta). Las dos se emiten igual, en una sola llamada y una por una: khipu asigna el
        número correlativo de la serie, genera el XML UBL 2.1, lo firma con el certificado de la empresa, lo valida y lo
        envía a SUNAT; la respuesta trae el estado y, si SUNAT respondió, el CDR. Si SUNAT no está disponible, el
        comprobante queda en `ERROR_ENVIO` y khipu lo reintenta solo (ver *Estados del comprobante* en la introducción).

        Autenticación: `X-Api-Key` de la empresa (integradores) o sesión del portal. Todos los importes van con hasta
        2 decimales y en la moneda del comprobante; las fechas en `YYYY-MM-DD`.""")
public class FacturaController {
    private static final String BASE = "/v1/facturas";
    public static final String TOTAL_HEADER = "X-Total-Count";
    /** #362 B10: en la descarga del CDR de una boleta informada por resumen diario, el identificador del resumen cuyo CDR es. */
    public static final String CABECERA_CDR_RESUMEN = "X-Cdr-Resumen";
    private final EmitirComprobanteUseCase emitir;
    private final EnviarDocumentoUseCase enviar;
    private final ConsultarComprobanteUseCase consultar;
    private final DarDeBajaUseCase bajas;
    private final CompartirComprobanteUseCase compartir;
    private final RecuperarCdrUseCase cdrs;
    private final ObjectMapper json;
    private final ValidarCompradorUseCase comprador;
    private final EnviarAlClienteUseCase envioAlCliente;

    @PostMapping
    @Operation(summary = "Emitir una factura o una boleta", description = """
            Crea la factura (o la boleta, con una serie `B###`), la numera, la firma y la envía a SUNAT en la misma llamada
            (salvo `enviar_automatico: false`, que la deja `FIRMADO` para enviarla después con `POST /v1/facturas/{id}/enviar`).

            **Qué validar antes de llamar**: la serie debe existir (ver *Series*); el receptor de una factura siempre lleva
            RUC (`cliente.tipo_doc = "6"`); `tipo_afectacion_igv` de cada ítem viene del catálogo 07; `unidad` del catálogo 03;
            `tipo_operacion` del catálogo 51 (`0101` venta interna por defecto) y que khipu emita en ese comprobante (`0113` es solo de boletas; la lista de factura está en `tipo_operacion` del request): si no, `422 TIPO_OPERACION_INVALIDO` antes de consumir número.

            **Boleta** (serie `B###`): el comprador se identifica con cualquier documento del catálogo 06 (DNI de 8 dígitos,
            carné de extranjería, pasaporte, RUC…) o, si la boleta es en soles y de hasta S/ 700.00, con `tipo_doc` y `num_doc`
            `-` (sin documento). Se emite al contado; el crédito y la retención todavía van en factura
            (`422` con el motivo). Admite anticipos de otra boleta (serie `B###`) o de una factura (serie `F###`) del mismo comprador, y la exportación y el `0401` con un comprador sin RUC. Una boleta aceptada se anula con `POST /v1/facturas/{id}/baja`, que la informa en un resumen diario.

            **Precios**: `precio_unitario` es el precio de venta unitario **con IGV incluido** para ítems gravados; khipu
            calcula el valor unitario, el IGV y los totales (tolerancias SUNAT ±1).

            **Forma de pago** (obligatoria desde 2022): al contado por defecto; al crédito indique `monto_pendiente` y las
            `cuotas` con fecha posterior a la emisión.

            **RUC del comprador** (factura y boleta con RUC): se valida contra el padrón de SUNAT. Si no figura, `422 RECEPTOR_INVALIDO`
            (1083) antes de numerar, porque SUNAT rechazaría el comprobante. Si figura pero está de baja (4013) o no habido (4014), SUNAT
            lo acepta con una observación: se emite igual y la respuesta trae `avisos`. Mientras el padrón no esté cargado o tenga más
            de 3 días, no se rechaza nada.

            **Errores frecuentes**: `422 VALIDACION` (campos con formato inválido, detalle en `errores`),
            `422 FORMA_PAGO_INVALIDA` / `SERIE_INVALIDA` / `RECEPTOR_INVALIDO` (regla de negocio, el mensaje lleva el código
            SUNAT cuando aplica), `409 DUPLICADO` (ya existe ese `correlativo` en la serie), `422 CREDENCIALES_SOL_FALTAN`
            (la empresa aún no cargó usuario/clave SOL), `429 LIMITE_PLAN` (la cuenta llegó al tope de documentos del mes de su
            plan, sumando todas sus empresas: se vuelve a emitir el mes siguiente o con otro plan; no gasta número), `402 PLAN_VENCIDO`
            (el plan de la cuenta venció y pasó su gracia: se vuelve a emitir al renovarlo; no gasta número, y la consulta y la baja siguen).

            **Reintentos seguros** (`Idempotency-Key`): si la red se corta después de enviar el pedido, no se sabe si la factura
            se emitió. Mande una clave única por factura (un UUID) en la cabecera `Idempotency-Key` y repita el mismo pedido con la
            misma clave: si ya se había emitido, responde `200` con **el mismo comprobante** (mismo id y número), sin emitir otro ni
            consumir número. La misma clave con otro contenido responde `422 IDEMPOTENCIA_INVALIDA`. La clave vale para la empresa
            durante 24 horas. Sin la cabecera, cada pedido es una factura nueva.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Factura creada; `estado_documento` indica si SUNAT ya la aceptó"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pedido repetido con la misma `Idempotency-Key`: el comprobante ya emitido, en su estado actual"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Correlativo repetido en la serie (DUPLICADO)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Datos inválidos o regla de negocio incumplida; `codigo` y `mensaje` explican cuál")})
    public ResponseEntity<ApiResponse<ComprobanteResponse>> crear(HttpServletRequest req, @Valid @RequestBody FacturaRequest body,
            @Parameter(description = "Clave única por factura (un UUID), para repetir el pedido sin emitir dos veces", example = "5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
            @RequestHeader(name = ClaveDeIdempotencia.CABECERA, required = false) String clave) {
        var idempotencia = ClaveDeIdempotencia.de(clave, body, json);
        if (idempotencia == null) {
            Comprobante c = emitir.emitirFactura(TenantActual.id(req), body.aComando());
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ComprobanteResponse.de(c, BASE), avisos(c)));
        }
        var emision = emitir.emitirFactura(TenantActual.id(req), body.aComando(), idempotencia);
        return ResponseEntity.status(emision.repetida() ? HttpStatus.OK : HttpStatus.CREATED).body(ApiResponse.ok(ComprobanteResponse.de(emision.comprobante(), BASE), avisos(emision.comprobante())));
    }

    @PostMapping("/calcular")
    @Operation(summary = "Calcular los totales sin emitir", description = """
            Recibe el mismo cuerpo que `POST /v1/facturas` y responde los importes que khipu calcularía —`items` y `totales`, con descuentos, cargos, ISC, ICBPER, otros
            tributos, anticipos, redondeo, percepción, retención y tipo de cambio— **sin numerar, firmar, guardar ni enviar nada** y sin consumir cupo del plan. Arma el
            comprobante con las mismas reglas de dominio que la emisión: lo que devuelve es lo que se emitiría, y lo que la emisión rechazaría por esas reglas responde aquí el
            mismo `422` con su `codigo` y `mensaje`. No comprueba lo que depende de datos guardados (que la factura de anticipo exista y esté aceptada, el padrón de RUC, el
            plan, que haya serie y certificado): eso lo valida la emisión. El `id` de la respuesta no existe y no trae enlaces.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Importes calculados; no se guardó nada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Datos inválidos o regla de dominio incumplida, igual que en la emisión")})
    public ResponseEntity<ApiResponse<ComprobanteResponse>> calcular(HttpServletRequest req, @Valid @RequestBody FacturaRequest body) {
        Comprobante c = emitir.calcularFactura(TenantActual.id(req), body.aComando());
        return ResponseEntity.ok(ApiResponse.ok(ComprobanteResponse.de(c, null)));
    }

    /** #378: lo que SUNAT observará del comprador (4013, 4014); la factura se emitió igual, SUNAT la acepta con la observación. */
    private List<ApiResponse.Aviso> avisos(Comprobante c) {
        return ApiResponse.Aviso.de(comprador.avisosDe(c.receptor()));
    }

    @GetMapping
    @Operation(summary = "Listar facturas", description = """
            Facturas de la empresa, de la más reciente a la más antigua, paginadas. El total de resultados va en la cabecera
            `X-Total-Count` y refleja los filtros. Filtre por `estado` para trabajar una cola (p. ej. `ERROR_ENVIO` para reintentar,
            `RECHAZADO` para corregir y reemitir) y por fecha de emisión con `desde`/`hasta` (inclusive, rango abierto si falta uno;
            `desde > hasta` responde `400 RANGO_INVALIDO`, una fecha mal formada `400 PARAMETRO_INVALIDO`) y por `serie` exacta
            (F001, FC01…; con formato inválido `400 PARAMETRO_INVALIDO`, inexistente → lista vacía), por `tipo` (`01` factura, `03` boleta, `04` liquidación de compra, `07` nota de crédito, `08` nota de débito, `20` retención, `40` percepción),
            por `numero` (sin la serie) y por `cliente`: el documento (empieza por lo escrito) o parte de la razón social, sin distinguir mayúsculas. Los filtros se combinan.""")
    public ResponseEntity<ApiResponse<List<ComprobanteResponse>>> listar(HttpServletRequest req,
                                                                        @Parameter(description = "Estado del comprobante (ver *Estados del comprobante*)") @RequestParam(required = false) EstadoDocumento estado,
                                                                        @Parameter(description = "Fecha de emisión mínima, `YYYY-MM-DD` (inclusive)", example = "2026-09-01") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                                        @Parameter(description = "Fecha de emisión máxima, `YYYY-MM-DD` (inclusive)", example = "2026-09-30") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                                                        @Parameter(description = "Serie exacta del comprobante (4 caracteres: F001, FC01, FD01…)", example = "F001") @RequestParam(required = false) String serie,
                                                                        @Parameter(description = "Tipo de comprobante: `01` factura, `03` boleta, `04` liquidación de compra, `07` nota de crédito, `08` nota de débito, `20` retención, `40` percepción", example = "01") @RequestParam(required = false) String tipo,
                                                                        @Parameter(description = "Cliente: el documento (empieza por lo escrito, si son solo dígitos) o parte de la razón social, sin distinguir mayúsculas; hasta 100 caracteres", example = "andina") @RequestParam(required = false) String cliente,
                                                                        @Parameter(description = "Número del comprobante (sin la serie)", example = "125") @RequestParam(required = false) Long numero,
                                                                        @Parameter(description = "Página, desde 1") @RequestParam(defaultValue = "1") int pagina,
                                                                        @Parameter(description = "Resultados por página, 1–100") @RequestParam(name = "por_pagina", defaultValue = "20") int porPagina) {
        UUID t = TenantActual.id(req);
        var filtro = new ConsultarComprobanteUseCase.Filtro(estado, desde, hasta, serie, tipo, cliente, numero);
        List<ComprobanteResponse> datos = consultar.listar(t, filtro, Math.max(1, pagina), Math.min(100, Math.max(1, porPagina)))
                .stream().map(c -> ComprobanteResponse.de(c, BASE)).toList();
        return ResponseEntity.ok().header(TOTAL_HEADER, String.valueOf(consultar.contar(t, filtro))).body(ApiResponse.ok(datos));
    }

    @GetMapping("/descarga")
    @Operation(summary = "Descargar comprobantes en un ZIP", description = """
            Los archivos de los comprobantes que cumplen los filtros —los mismos del listado— en un ZIP que se va escribiendo mientras se leen: `<RUC>-<tipo>-<serie>-<número>.xml` (XML firmado),
            `.pdf` (representación impresa) y `R-<RUC>-….zip` (CDR, solo los que ya lo tienen). `tipos` elige cuáles (`xml,pdf,cdr`, por defecto los tres). Un comprobante todavía sin firmar no tiene archivos y se
            omite; si un archivo no se puede leer, el ZIP lo anota en `LEEME.txt` en vez de fallar entero. **Tope: 500 comprobantes por descarga**; con más responde `422 DESCARGA_EXCEDE_EL_TOPE`
            con cuántos hay (acote las fechas y descargue por tramos), y sin resultados `404 NO_ENCONTRADO`. Un `tipos` con otro valor es `400 PARAMETRO_INVALIDO`.""")
    public ResponseEntity<StreamingResponseBody> descargar(HttpServletRequest req,
                                                          @Parameter(description = "Estado del comprobante") @RequestParam(required = false) EstadoDocumento estado,
                                                          @Parameter(description = "Fecha de emisión mínima, `YYYY-MM-DD` (inclusive)", example = "2026-09-01") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                          @Parameter(description = "Fecha de emisión máxima, `YYYY-MM-DD` (inclusive)", example = "2026-09-30") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                                          @Parameter(description = "Serie exacta", example = "F001") @RequestParam(required = false) String serie,
                                                          @Parameter(description = "Tipo: `01`, `03`, `04`, `07`, `08`, `20` o `40`", example = "01") @RequestParam(required = false) String tipo,
                                                          @Parameter(description = "Cliente: documento (prefijo) o parte de la razón social", example = "andina") @RequestParam(required = false) String cliente,
                                                          @Parameter(description = "Número del comprobante (sin la serie)", example = "125") @RequestParam(required = false) Long numero,
                                                          @Parameter(description = "Qué archivos incluir: `xml`, `pdf`, `cdr` separados por coma", example = "xml,pdf") @RequestParam(required = false) String tipos) {
        UUID t = TenantActual.id(req);
        var filtro = new ConsultarComprobanteUseCase.Filtro(estado, desde, hasta, serie, tipo, cliente, numero);
        var contenido = DescargaEnZip.Contenido.de(tipos);
        long total = DescargaEnZip.verificar(consultar, t, filtro);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"comprobantes-" + LocalDate.now(java.time.ZoneId.of("America/Lima")) + ".zip\"")
                .body(salida -> DescargaEnZip.escribir(salida, consultar, t, filtro, contenido, total));
    }

    @GetMapping("/exportacion")
    @Operation(operationId = "exportarComprobantes", summary = "Exportar comprobantes a CSV o Excel", description = """
            Una fila por comprobante que cumple los filtros —los mismos del listado—, lo más reciente primero, para el registro de ventas o el contador. Columnas (en
            `snake_case`, para programar contra ellas): `tipo` (catálogo 01), `serie`, `numero`, `fecha_emision`, `cliente_tipo_doc`, `cliente_num_doc`, `cliente`, `moneda`,
            `tipo_cambio` (soles por unidad de `moneda` si se informó; vacío si no), las bases `gravado`, `exonerado`, `inafecto`, `gratuito` y `exportacion`, `igv`, `icbper`,
            `isc`, `ivap`, `otros_tributos`, `total_descuentos`, `total_cargos`, `total_anticipos`, `redondeo`, `total`, `estado` (un comprobante dado de baja sale
            `ANULADO`) y `documento_modificado` (solo en notas: tipo y serie-número, `01 F001-125`).

            **Importes**: con 2 decimales y en la moneda del comprobante. Una **nota de crédito (07) sale en negativo** —todos sus importes— para que la columna sume la
            venta neta; la de débito (08), en positivo. Cuadran así: `total` = `gravado` + `exonerado` + `inafecto` + `exportacion` + `igv` + `ivap` + `isc` + `icbper` +
            `otros_tributos` + `total_cargos` − `total_descuentos` + `redondeo`; `gratuito` no se cobra, y las bases y el IGV ya vienen netos de anticipos
            (`total_anticipos` es informativo).

            **Qué entra**: sin el filtro `estado` quedan fuera los que no son ventas (`RECHAZADO`, `INVALIDO`, `DESCARTADO`); con `estado` sale exactamente ese estado. Un
            comprobante emitido después de empezar la exportación no entra; uno que cambia de estado mientras tanto sale con el estado de ese momento.

            `formato=csv` (por defecto) es UTF-8 con BOM, separado por comas —el de Excel con la configuración regional de Perú— o por punto y coma con
            `separador=punto_y_coma`, con punto decimal; los textos que empiezan por `= + - @` llevan delante una comilla simple para que una hoja de cálculo no los ejecute
            como fórmula. `formato=xlsx` es un libro de Excel con los importes como números (recomendado para abrirlo en Excel). El archivo se escribe mientras se lee: si
            la conexión se corta a la mitad (o pasa el tiempo máximo de la respuesta, 2 minutos), el archivo llega incompleto y se reconoce por el corte —el CSV termina sin
            su última fila y el XLSX no abre—: vuelva a pedirlo. **Tope: 5000 comprobantes por exportación**; con más responde `422 EXPORTACION_EXCEDE_EL_TOPE` con cuántos
            hay (acote las fechas y exporte por tramos). Sin resultados sale el archivo con la cabecera sola. Un `formato` o `separador` con otro valor es
            `400 PARAMETRO_INVALIDO`.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "El archivo, como adjunto (`Content-Disposition`)", content = {
                    @io.swagger.v3.oas.annotations.media.Content(mediaType = "text/csv", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
                    @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary"))}),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`formato`, `separador` o un filtro con formato inválido (`PARAMETRO_INVALIDO`, `RANGO_INVALIDO`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Más de 5000 comprobantes con esos filtros (`EXPORTACION_EXCEDE_EL_TOPE`)")})
    public ResponseEntity<StreamingResponseBody> exportarComprobantes(HttpServletRequest req,
                                                         @Parameter(description = "Estado del comprobante") @RequestParam(required = false) EstadoDocumento estado,
                                                         @Parameter(description = "Fecha de emisión mínima, `YYYY-MM-DD` (inclusive)", example = "2026-09-01") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                         @Parameter(description = "Fecha de emisión máxima, `YYYY-MM-DD` (inclusive)", example = "2026-09-30") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                                         @Parameter(description = "Serie exacta", example = "F001") @RequestParam(required = false) String serie,
                                                         @Parameter(description = "Tipo: `01`, `03`, `07` u `08`", example = "01") @RequestParam(required = false) String tipo,
                                                         @Parameter(description = "Cliente: documento (prefijo) o parte de la razón social", example = "andina") @RequestParam(required = false) String cliente,
                                                         @Parameter(description = "Número del comprobante (sin la serie)", example = "125") @RequestParam(required = false) Long numero,
                                                         @Parameter(description = "`csv` (por defecto) o `xlsx`", example = "xlsx") @RequestParam(required = false) String formato,
                                                         @Parameter(description = "Solo en CSV: `coma` (por defecto) o `punto_y_coma`", example = "coma") @RequestParam(required = false) String separador) {
        UUID t = TenantActual.id(req);
        var filtro = new ConsultarComprobanteUseCase.Filtro(estado, desde, hasta, serie, tipo, cliente, numero);
        var f = ExportacionDeComprobantes.Formato.de(formato);
        char sep = ExportacionDeComprobantes.separador(separador);
        ExportacionDeComprobantes.verificar(consultar, t, filtro);
        String archivo = ExportacionDeComprobantes.nombreDeArchivo(filtro, f, LocalDate.now(java.time.ZoneId.of("America/Lima")));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.tipoDeContenido))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(salida -> ExportacionDeComprobantes.escribir(salida, consultar, t, filtro, f, sep));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar un comprobante", description = """
            Estado actual, respuesta de SUNAT (`cdr`), totales, forma de pago y enlaces de descarga. Úselo para hacer seguimiento
            de un comprobante que quedó en `ERROR_ENVIO` o `ENVIADO`. Sirve también para las notas de crédito/débito emitidas con
            `POST /v1/notas` (traen el bloque `nota`), las liquidaciones de compra de `POST /v1/liquidaciones-compra` (traen el bloque `liquidacion`) y las retenciones y percepciones de `POST /v1/retenciones` y `POST /v1/percepciones` (traen el bloque `agente`); una factura incluye en `notas` las notas emitidas sobre ella y en `eventos` el
            historial de intentos y cambios de estado (fecha, estado resultante y motivo), del más antiguo al más reciente. Si la empresa
            tiene activado el envío automático al cliente, `envio_al_cliente` dice si el correo con el PDF, el XML y el CDR salió, sigue pendiente o falló.""")
    public ApiResponse<ComprobanteResponse> obtener(HttpServletRequest req, @PathVariable UUID id) {
        UUID t = TenantActual.id(req);
        Comprobante c = consultar.obtener(t, id);
        return ApiResponse.ok(ComprobanteResponse.de(c, BASE, consultar.notasDe(t, c), bajas.deComprobante(t, id).stream().findFirst().orElse(null), consultar.eventos(t, c),
                envioAlCliente.envioDe(t, id).orElse(null)));
    }

    @PostMapping("/{id}/enviar")
    @Operation(summary = "Enviar (o reintentar) a SUNAT", description = """
            Envía un comprobante `FIRMADO` (emitido con `enviar_automatico: false`) o reintenta uno en `ERROR_ENVIO` sin
            esperar al reintento automático. Un comprobante `ACEPTADO`, `RECHAZADO` o `ANULADO` responde `409 ESTADO_NO_ENVIABLE`.
            Una boleta (o nota sobre boleta) que pasó el envío individual se informa en un resumen diario: la respuesta lo trae en `baja`
            (`condicion: ALTA`). Pasado el 7.º día va igual, con `baja.fuera_de_plazo: true`: SUNAT no la rechaza, pero puede observarla con la
            4443 (entonces `ACEPTADO_CON_OBS`; en e-beta la aceptó sin observaciones, `ACEPTADO`). Una factura, una nota de factura o una nota de crédito 10 / de débito 03 de boleta (que no van en el resumen)
            fuera de plazo responde `409 FUERA_DE_PLAZO`.

            **Efecto sobre otras boletas**: las demás boletas (y notas de boleta) de la misma fecha de emisión que siguen `FIRMADO` o en `ERROR_ENVIO` y también solo van en el resumen diario
            se informan en el mismo resumen (hasta 500 líneas, con un solo ticket y un solo CDR), también las emitidas con `enviar_automatico: false`. Cada una trae su `linea` y las `lineas` del resumen.""")
    public ApiResponse<ComprobanteResponse> enviar(HttpServletRequest req, @PathVariable UUID id) {
        UUID t = TenantActual.id(req);
        Comprobante c = enviar.enviar(t, id);
        // #331: el resumen en que se informó (si fue por uno) viaja con el envío, con su `fuera_de_plazo`: es lo que explica un ACEPTADO_CON_OBS por 4443 si SUNAT la observa.
        return ApiResponse.ok(ComprobanteResponse.de(c, BASE, null, bajas.deComprobante(t, id).stream().findFirst().orElse(null)));
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
            en cualquier otro estado. Si el servidor de correo rechaza el envío, `502 CORREO_NO_ENVIADO` (reintente más tarde). Si
            el servidor no tiene correo configurado, `503 CORREO_NO_CONFIGURADO`: no se envió nada y reintentar no sirve.""")
    public ApiResponse<Void> correo(HttpServletRequest req, @PathVariable UUID id, @Valid @RequestBody CorreoRequest body) {
        compartir.enviarPorCorreo(TenantActual.id(req), id, body.email(), body.mensaje());
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/cdr")
    @Operation(summary = "Descargar la constancia de recepción (CDR)", description = """
            El CDR es la prueba de que SUNAT recibió y aceptó (o rechazó) el comprobante. Por defecto se entrega el ZIP tal
            como lo devolvió SUNAT; con `formato=xml` se extrae el `ApplicationResponse`. Responde `404 SIN_CDR` mientras
            SUNAT no haya respondido.

            **Boleta informada en un resumen diario**: SUNAT no emite un CDR por boleta en el resumen; el que se entrega es el del
            resumen (`El Resumen diario RC-…, ha sido aceptado`). Para no cambiar el contrato, el nombre de archivo sigue siendo
            `R-<RUC>-03-<serie>-<número>`; lo que lo distingue es la cabecera `X-Cdr-Resumen` con el identificador del resumen
            (`RC-aaaammdd-n`), que también trae `cdr.resumen` en la consulta del comprobante. Sin la cabecera, el CDR es el del propio comprobante.""")
    public ResponseEntity<byte[]> cdr(HttpServletRequest req, @PathVariable UUID id,
                                      @Parameter(description = "`zip` (por defecto) o `xml`") @RequestParam(required = false) String formato) {
        UUID t = TenantActual.id(req);
        if (formato != null && !formato.equalsIgnoreCase("zip") && !formato.equalsIgnoreCase("xml")) {
            throw new DomainException("PARAMETRO_INVALIDO", "formato debe ser 'zip' (por defecto) o 'xml'");
        }
        Comprobante c = consultar.obtener(t, id);
        // #362 B10: el CDR de una boleta informada por resumen diario es el del resumen. El nombre del archivo no cambia (contrato con los integradores);
        // la cabecera dice de qué resumen es. Se lee del comprobante, que ya lo sabe por la clave con que guardó el CDR.
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.ok();
        c.resumenDelCdr().ifPresent(rc -> respuesta.header(CABECERA_CDR_RESUMEN, rc));
        if ("xml".equalsIgnoreCase(formato)) {
            return respuesta.contentType(MediaType.APPLICATION_XML)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"R-" + c.nombreArchivo() + ".xml\"")
                    .body(consultar.cdrXml(t, id));
        }
        return respuesta.contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"R-" + c.nombreArchivo() + ".zip\"")
                .body(consultar.cdr(t, id));
    }

    @PostMapping("/{id}/cdr/recuperar")
    @Operation(summary = "Recuperar el CDR desde SUNAT", description = """
            `getStatusCdr` de `billConsultService`: pide a SUNAT la constancia de un comprobante propio que quedó `ENVIADO` o en
            `ERROR_ENVIO` (por ejemplo, la conexión se cortó después de que SUNAT lo aceptara) o que ya está resuelto pero perdió
            su CDR en el storage. Si SUNAT lo tiene, lo guarda y aplica el resultado (`ACEPTADO`/`RECHAZADO`) sin reenviar;
            si no, devuelve el comprobante sin cambios. Un barrido horario hace lo mismo para todas las empresas en producción.
            Solo facturas y sus notas (serie F): SUNAT no expone `getStatusCdr` para boletas ni notas de boleta (serie B), cuya
            constancia es la del envío individual o la del resumen diario que las informa.
            Errores: `404 NO_ENCONTRADO`, `422 SIN_FIRMA`, `409 CDR_YA_DISPONIBLE`, `409 CDR_NO_CONSULTABLE`, `422 NO_DISPONIBLE_EN_BETA`, `422 CREDENCIALES_SOL_NO_CARGADAS`.""")
    public ApiResponse<ComprobanteResponse> recuperarCdr(HttpServletRequest req, @PathVariable UUID id) {
        return ApiResponse.ok(ComprobanteResponse.de(cdrs.recuperar(TenantActual.id(req), id), BASE));
    }
}
