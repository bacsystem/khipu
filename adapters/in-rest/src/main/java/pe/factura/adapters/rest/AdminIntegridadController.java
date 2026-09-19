package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.application.port.in.VerificarIntegridadUseCase;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma")
@RequiredArgsConstructor
public class AdminIntegridadController {
    private final VerificarIntegridadUseCase integridad;

    @PostMapping("/integridad")
    @Operation(summary = "Verificar la integridad del storage en un rango de fechas", description = """
            Recorre los comprobantes firmados de todas las empresas emitidos entre `desde` y `hasta` (inclusive) y comprueba que su XML
            exista en el storage con el DigestValue con el que se firmó, y que el CDR exista cuando SUNAT lo emitió. Solo lee. El mismo
            barrido corre a diario sobre los últimos días (`app.integridad`). Devuelve los problemas encontrados: `XML_FALTANTE`,
            `XML_CORRUPTO`, `CDR_FALTANTE`, `STORAGE_INACCESIBLE`.""")
    public ApiResponse<VerificarIntegridadUseCase.Informe> verificar(
            @Parameter(example = "2026-09-01") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(example = "2026-09-30") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ApiResponse.ok(integridad.verificar(desde, hasta));
    }
}
