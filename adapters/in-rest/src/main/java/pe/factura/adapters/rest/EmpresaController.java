package pe.factura.adapters.rest;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.factura.adapters.rest.dto.ApiKeyResponse;
import pe.factura.adapters.rest.dto.ApiKeyResumenResponse;
import pe.factura.adapters.rest.dto.CredencialesSolRequest;
import pe.factura.adapters.rest.dto.EmpresaResponse;
import pe.factura.adapters.rest.dto.SerieRequest;
import pe.factura.adapters.rest.dto.SerieResponse;
import pe.factura.application.port.in.AdministrarTenantUseCase;
import pe.factura.domain.documento.TipoDocumento;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class EmpresaController {
    private final AdministrarTenantUseCase admin;

    @GetMapping("/empresa")
    public ApiResponse<EmpresaResponse> ver(HttpServletRequest req) {
        return ApiResponse.ok(EmpresaResponse.de(admin.obtener(TenantActual.id(req))));
    }

    @PostMapping(value = "/empresa/certificado", consumes = "multipart/form-data")
    public ResponseEntity<Void> certificado(HttpServletRequest req, @RequestParam("archivo") MultipartFile archivo, @RequestParam("clave") String clave) throws IOException {
        admin.cargarCertificado(TenantActual.id(req), archivo.getBytes(), clave);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/empresa/credenciales-sol")
    public ResponseEntity<Void> credencialesSol(HttpServletRequest req, @Valid @RequestBody CredencialesSolRequest body) {
        admin.cargarCredencialesSol(TenantActual.id(req), body.usuario(), body.clave());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/empresa/api-keys")
    public ResponseEntity<ApiResponse<ApiKeyResponse>> apiKey(HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(new ApiKeyResponse(admin.crearApiKey(TenantActual.id(req)))));
    }

    @GetMapping("/empresa/api-keys")
    public ApiResponse<List<ApiKeyResumenResponse>> apiKeys(HttpServletRequest req) {
        return ApiResponse.ok(admin.listarApiKeys(TenantActual.id(req)).stream().map(ApiKeyResumenResponse::de).toList());
    }

    @DeleteMapping("/empresa/api-keys/{id}")
    public ResponseEntity<Void> revocarApiKey(HttpServletRequest req, @PathVariable UUID id) {
        admin.revocarApiKey(TenantActual.id(req), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/series")
    public ResponseEntity<Void> crearSerie(HttpServletRequest req, @Valid @RequestBody SerieRequest body) {
        admin.crearSerie(TenantActual.id(req), TipoDocumento.porCodigo(body.tipo()), body.serie(), body.correlativoInicial() == null ? 0 : body.correlativoInicial());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/series")
    public ApiResponse<List<SerieResponse>> series(HttpServletRequest req) {
        return ApiResponse.ok(admin.listarSeries(TenantActual.id(req)).stream()
                .map(s -> new SerieResponse(s.tipo().codigo(), s.codigo(), s.ultimoNumero(), s.activa()))
                .toList());
    }
}
