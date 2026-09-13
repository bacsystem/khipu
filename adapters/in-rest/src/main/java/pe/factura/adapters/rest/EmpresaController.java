package pe.factura.adapters.rest;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.factura.adapters.rest.dto.CredencialesSolRequest;
import pe.factura.adapters.rest.dto.SerieRequest;
import pe.factura.application.port.in.AdministrarTenantUseCase;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Tenant;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class EmpresaController {
    private final AdministrarTenantUseCase admin;

    @GetMapping("/empresa")
    public ApiResponse<Map<String, Object>> ver(HttpServletRequest req) {
        Tenant t = admin.obtener(TenantActual.id(req));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.id()); m.put("ruc", t.ruc()); m.put("razon_social", t.razonSocial()); m.put("entorno", t.entorno());
        m.put("tiene_credenciales_sol", t.sol() != null);
        m.put("certificado_vigencia_hasta", t.certificado() == null ? null : t.certificado().vigenciaHasta());
        return ApiResponse.ok(m);
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
    public ResponseEntity<ApiResponse<Map<String, String>>> apiKey(HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(Map.of("api_key", admin.crearApiKey(TenantActual.id(req)))));
    }

    @PostMapping("/series")
    public ResponseEntity<Void> crearSerie(HttpServletRequest req, @Valid @RequestBody SerieRequest body) {
        admin.crearSerie(TenantActual.id(req), TipoDocumento.porCodigo(body.tipo()), body.serie(), body.correlativoInicial() == null ? 0 : body.correlativoInicial());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/series")
    public ApiResponse<List<Map<String, Object>>> series(HttpServletRequest req) {
        return ApiResponse.ok(admin.listarSeries(TenantActual.id(req)).stream().<Map<String, Object>>map(s -> Map.of(
                "tipo", s.tipo().codigo(), "serie", s.codigo(), "ultimo_numero", s.ultimoNumero(), "activa", s.activa())).toList());
    }
}
