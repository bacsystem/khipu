package pe.factura.adapters.rest;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.ComprobanteResponse;
import pe.factura.adapters.rest.dto.FacturaRequest;
import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/facturas")
@RequiredArgsConstructor
public class FacturaController {
    private static final String BASE = "/v1/facturas";
    public static final String TOTAL_HEADER = "X-Total-Count";
    private final EmitirComprobanteUseCase emitir;
    private final EnviarDocumentoUseCase enviar;
    private final ConsultarComprobanteUseCase consultar;


    @PostMapping
    public ResponseEntity<ApiResponse<ComprobanteResponse>> crear(HttpServletRequest req, @Valid @RequestBody FacturaRequest body) {
        Comprobante c = emitir.emitirFactura(TenantActual.id(req), body.aComando());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ComprobanteResponse.de(c, BASE)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ComprobanteResponse>>> listar(HttpServletRequest req, @RequestParam(required = false) EstadoDocumento estado,
                                                                        @RequestParam(defaultValue = "1") int pagina, @RequestParam(name = "por_pagina", defaultValue = "20") int porPagina) {
        UUID t = TenantActual.id(req);
        List<ComprobanteResponse> datos = consultar.listar(t, estado, Math.max(1, pagina), Math.min(100, Math.max(1, porPagina)))
                .stream().map(c -> ComprobanteResponse.de(c, BASE)).toList();
        return ResponseEntity.ok().header(TOTAL_HEADER, String.valueOf(consultar.contar(t, estado))).body(ApiResponse.ok(datos));
    }

    @GetMapping("/{id}")
    public ApiResponse<ComprobanteResponse> obtener(HttpServletRequest req, @PathVariable UUID id) {
        return ApiResponse.ok(ComprobanteResponse.de(consultar.obtener(TenantActual.id(req), id), BASE));
    }

    @PostMapping("/{id}/enviar")
    public ApiResponse<ComprobanteResponse> enviar(HttpServletRequest req, @PathVariable UUID id) {
        return ApiResponse.ok(ComprobanteResponse.de(enviar.enviar(TenantActual.id(req), id), BASE));
    }

    @GetMapping("/{id}/xml")
    public ResponseEntity<byte[]> xml(HttpServletRequest req, @PathVariable UUID id) {
        UUID t = TenantActual.id(req);
        Comprobante c = consultar.obtener(t, id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + c.nombreArchivo() + ".xml\"")
                .body(consultar.xml(t, id));
    }

    @GetMapping("/{id}/cdr")
    public ResponseEntity<byte[]> cdr(HttpServletRequest req, @PathVariable UUID id,
                                      @RequestParam(required = false) String formato) {
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
}
