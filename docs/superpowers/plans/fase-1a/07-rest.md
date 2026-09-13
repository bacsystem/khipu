# 07 · Adaptador REST

Índice del plan: [README](README.md). Módulo `adapters/in-rest`. Contrato: `docs/superpowers/specs/05-contrato-rest.md`.

### Task 16: Autenticación por API key, sobre de respuesta y manejo de errores

**Files:**
- Create en `adapters/in-rest/src/main/java/pe/factura/adapters/rest/`: `ApiResponse.java`, `ApiKeyFilter.java`, `TenantActual.java`, `GlobalExceptionHandler.java`
- Test: `adapters/in-rest/src/test/java/pe/factura/adapters/rest/ApiKeyFilterTest.java`

**Interfaces:**
- Produces:
  - `record ApiResponse<T>(String estado, T datos, String mensaje, String codigo, Map<String, List<String>> errores)` con `static ok(T)`, `static error(codigo, mensaje)`, `static validacion(errores)`.
  - `ApiKeyFilter(ApiKeyRepository, String pepper)`: `OncePerRequestFilter` que para rutas `/v1/**` (excepto `/v1/admin/**` y `/health`) lee `X-Api-Key`, calcula `ApiKeyGenerator.hash`, busca la key activa y guarda el `tenantId` en el atributo de request `TENANT_ID`; si falta o es inválida responde `401` con `ApiResponse.error("NO_AUTORIZADO", …)`.
  - `TenantActual.id(HttpServletRequest) -> UUID`.
  - `GlobalExceptionHandler`: `DomainException` → `422` (o `404` si código `NO_ENCONTRADO`, `409` si `DUPLICADO` / `ESTADO_NO_ENVIABLE`); `MethodArgumentNotValidException` → `422` con mapa campo→mensajes; `HttpMessageNotReadableException` → `400`; resto → `500` con `codigo=INTERNO` (sin detalle).

- [ ] **Step 1: Test del filtro**

```java
package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.factura.application.port.out.ApiKeyRepository;
import pe.factura.application.service.ApiKeyGenerator;
import pe.factura.domain.tenant.ApiKey;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyFilterTest {
    UUID tenant = UUID.randomUUID();
    String key = "fk_valida";
    ApiKeyRepository repo = new ApiKeyRepository() {
        public void guardar(ApiKey k) {}
        public Optional<ApiKey> buscarPorHash(String h) {
            return h.equals(ApiKeyGenerator.hash(key, "pep")) ? Optional.of(new ApiKey(UUID.randomUUID(), tenant, h, "fk_valida", true)) : Optional.empty();
        }
    };
    ApiKeyFilter filter = new ApiKeyFilter(repo, "pep");

    @Test void keyValidaDejaPasarYExponeTenant() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/facturas");
        req.addHeader("X-Api-Key", key);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(TenantActual.id(req)).isEqualTo(tenant);
    }

    @Test void sinKeyResponde401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/facturas");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("\"estado\":\"error\"").contains("NO_AUTORIZADO");
    }

    @Test void keyInvalidaResponde401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/facturas");
        req.addHeader("X-Api-Key", "fk_otra");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test void rutasAdminYHealthNoRequierenApiKey() throws Exception {
        for (String uri : new String[]{"/v1/admin/tenants", "/health"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(req, res, chain);
            assertThat(chain.getRequest()).as(uri).isNotNull();
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }
}
```

- [ ] **Step 2: Ver fallar** — `./gradlew :adapters:in-rest:test` → FAIL de compilación.

- [ ] **Step 3: Implementación**

`ApiResponse.java`:
```java
package pe.factura.adapters.rest;

import java.util.List;
import java.util.Map;

public record ApiResponse<T>(String estado, T datos, String mensaje, String codigo, Map<String, List<String>> errores) {
    public static <T> ApiResponse<T> ok(T datos) { return new ApiResponse<>("exito", datos, null, null, null); }
    public static ApiResponse<Void> error(String codigo, String mensaje) { return new ApiResponse<>("error", null, mensaje, codigo, null); }
    public static ApiResponse<Void> validacion(Map<String, List<String>> errores) { return new ApiResponse<>("error", null, "Validación fallida", "VALIDACION", errores); }
}
```

`TenantActual.java`:
```java
package pe.factura.adapters.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public final class TenantActual {
    public static final String ATRIBUTO = "TENANT_ID";
    private TenantActual() {}
    public static UUID id(HttpServletRequest req) {
        Object v = req.getAttribute(ATRIBUTO);
        if (v == null) throw new IllegalStateException("Petición sin tenant autenticado");
        return (UUID) v;
    }
}
```

`ApiKeyFilter.java`:
```java
package pe.factura.adapters.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.factura.application.port.out.ApiKeyRepository;
import pe.factura.application.service.ApiKeyGenerator;
import pe.factura.domain.tenant.ApiKey;

import java.io.IOException;
import java.util.Optional;

public class ApiKeyFilter extends OncePerRequestFilter {
    private final ApiKeyRepository apiKeys;
    private final String pepper;
    public ApiKeyFilter(ApiKeyRepository apiKeys, String pepper) { this.apiKeys = apiKeys; this.pepper = pepper; }

    @Override protected boolean shouldNotFilter(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return !uri.startsWith("/v1/") || uri.startsWith("/v1/admin/");
    }

    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String key = req.getHeader("X-Api-Key");
        Optional<ApiKey> k = key == null || key.isBlank() ? Optional.empty() : apiKeys.buscarPorHash(ApiKeyGenerator.hash(key.trim(), pepper));
        if (k.isEmpty() || !k.get().activa()) {
            res.setStatus(401);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"estado\":\"error\",\"codigo\":\"NO_AUTORIZADO\",\"mensaje\":\"API key ausente o inválida\"}");
            return;
        }
        req.setAttribute(TenantActual.ATRIBUTO, k.get().tenantId());
        chain.doFilter(req, res);
    }
}
```

`GlobalExceptionHandler.java`:
```java
package pe.factura.adapters.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.factura.domain.DomainException;

import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> dominio(DomainException e) {
        HttpStatus st = switch (e.codigo()) {
            case "NO_ENCONTRADO", "SIN_CDR" -> HttpStatus.NOT_FOUND;
            case "DUPLICADO", "ESTADO_NO_ENVIABLE" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(st).body(ApiResponse.error(e.codigo(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validacion(MethodArgumentNotValidException e) {
        Map<String, List<String>> errores = new TreeMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errores.computeIfAbsent(f.getField(), k -> new ArrayList<>()).add(f.getDefaultMessage()));
        return ResponseEntity.unprocessableEntity().body(ApiResponse.validacion(errores));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> jsonInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error("JSON_INVALIDO", "El cuerpo de la petición no es JSON válido"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> interno(Exception e) {
        String traceId = UUID.randomUUID().toString();
        log.error("Error interno trace_id={}", traceId, e);
        return ResponseEntity.internalServerError().body(ApiResponse.error("INTERNO", "Error interno. trace_id=" + traceId));
    }
}
```

- [ ] **Step 4: Ver pasar** — `./gradlew :adapters:in-rest:test` → PASS.
- [ ] **Step 5: Commit** — `git add adapters/in-rest && git commit -m "feat(rest): filtro de API key, sobre de respuesta y manejo global de errores"`

---

### Task 17: `FacturaController`

**Files:**
- Create en `adapters/in-rest/src/main/java/pe/factura/adapters/rest/`: `dto/FacturaRequest.java`, `dto/ComprobanteResponse.java`, `FacturaController.java`
- Test: `adapters/in-rest/src/test/java/pe/factura/adapters/rest/FacturaControllerTest.java`

**Interfaces:**
- Consumes: `EmitirComprobanteUseCase`, `EnviarDocumentoUseCase`, `ConsultarComprobanteUseCase` (Task 7/6/8), `TenantActual`, `ApiResponse`.
- Produces endpoints:
  - `POST /v1/facturas` → `201` `ApiResponse<ComprobanteResponse>`
  - `GET /v1/facturas?estado=&pagina=1&por_pagina=20` → `200` lista
  - `GET /v1/facturas/{id}` → `200`
  - `POST /v1/facturas/{id}/enviar` → `200` con el comprobante actualizado
  - `GET /v1/facturas/{id}/xml` → `application/xml`, `Content-Disposition: attachment; filename="{nombre}.xml"`
  - `GET /v1/facturas/{id}/cdr` → `application/zip`, `filename="R-{nombre}.zip"`

- [ ] **Step 1: Test (WebMvcTest con mocks)**

```java
package pe.factura.adapters.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FacturaController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class FacturaControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean EmitirComprobanteUseCase emitir;
    @MockBean EnviarDocumentoUseCase enviar;
    @MockBean ConsultarComprobanteUseCase consultar;

    UUID tenant = UUID.randomUUID();

    static Comprobante aceptado(UUID tenant) {
        Comprobante c = Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima")));
        c.asignarNumero(601, "20100066603"); c.firmar("HASH", "k.xml"); c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "k.zip");
        return c;
    }

    String cuerpo = """
        {"serie":"F001","fecha_emision":"2026-09-13","tipo_operacion":"0101","moneda":"PEN",
         "cliente":{"tipo_doc":"6","num_doc":"20601234567","razon_social":"CLIENTE SAC","direccion":"AV 1"},
         "items":[{"codigo":"P1","descripcion":"Prod","unidad":"NIU","cantidad":1,"precio_unitario":118.00,"tipo_afectacion_igv":"10"}]}
        """;

    @Test void crearFacturaDevuelve201ConSobre() throws Exception {
        when(emitir.emitirFactura(eq(tenant), any())).thenReturn(aceptado(tenant));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("exito"))
                .andExpect(jsonPath("$.datos.serie").value("F001"))
                .andExpect(jsonPath("$.datos.numero").value(601))
                .andExpect(jsonPath("$.datos.estado_documento").value("ACEPTADO"))
                .andExpect(jsonPath("$.datos.hash").value("HASH"))
                .andExpect(jsonPath("$.datos.cdr.codigo").value("0"))
                .andExpect(jsonPath("$.datos.totales.total").value(118.00))
                .andExpect(jsonPath("$.datos.enlaces.xml").exists());
        ArgumentCaptor<EmitirFacturaCommand> cap = ArgumentCaptor.forClass(EmitirFacturaCommand.class);
        org.mockito.Mockito.verify(emitir).emitirFactura(eq(tenant), cap.capture());
        assertThat(cap.getValue().enviarAutomatico()).isTrue();
        assertThat(cap.getValue().items().get(0).afectacion()).isEqualTo(TipoAfectacionIgv.GRAVADO);
    }

    @Test void validacionDeDtoDevuelve422() throws Exception {
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content("{\"serie\":\"F001\",\"items\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores.cliente").exists())
                .andExpect(jsonPath("$.errores.items").exists());
    }

    @Test void errorDeDominioSeMapea() throws Exception {
        when(emitir.emitirFactura(eq(tenant), any())).thenThrow(new DomainException("DUPLICADO", "Ya existe F001-1"));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(cuerpo))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DUPLICADO"));
    }

    @Test void descargaXml() throws Exception {
        Comprobante c = aceptado(tenant);
        when(consultar.obtener(tenant, c.id())).thenReturn(c);
        when(consultar.xml(tenant, c.id())).thenReturn("<Invoice/>".getBytes());
        mvc.perform(get("/v1/facturas/{id}/xml", c.id()).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"20100066603-01-F001-601.xml\""))
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string("<Invoice/>"));
    }

    @Test void enviarManual() throws Exception {
        Comprobante c = aceptado(tenant);
        when(enviar.enviar(tenant, c.id())).thenReturn(c);
        mvc.perform(post("/v1/facturas/{id}/enviar", c.id()).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos.estado_documento").value("ACEPTADO"));
    }
}
```

- [ ] **Step 2: Ver fallar** — `./gradlew :adapters:in-rest:test --tests '*FacturaControllerTest*'` → FAIL de compilación.

- [ ] **Step 3: DTOs**

`dto/FacturaRequest.java`:
```java
package pe.factura.adapters.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.TipoAfectacionIgv;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FacturaRequest(
        @NotBlank @Pattern(regexp = "F[A-Z0-9]{3}", message = "serie de factura inválida") String serie,
        @Positive Long correlativo,
        @NotNull LocalDate fechaEmision,
        @Pattern(regexp = "\\d{4}") String tipoOperacion,
        @NotBlank @Pattern(regexp = "PEN|USD|EUR") String moneda,
        @NotNull @Valid ClienteDto cliente,
        @NotEmpty @Valid List<ItemDto> items,
        Boolean enviarAutomatico) {

    public record ClienteDto(@NotBlank String tipoDoc, @NotBlank String numDoc, @NotBlank String razonSocial, String direccion) {}

    public record ItemDto(String codigo, @NotBlank String descripcion, @NotBlank String unidad,
                          @NotNull @Positive BigDecimal cantidad, @NotNull @PositiveOrZero BigDecimal precioUnitario,
                          @NotBlank @Pattern(regexp = "10|20|30") String tipoAfectacionIgv) {}

    public EmitirFacturaCommand aComando() {
        return new EmitirFacturaCommand(serie, correlativo, fechaEmision, moneda, tipoOperacion,
                new Receptor(cliente.tipoDoc(), cliente.numDoc(), cliente.razonSocial(), cliente.direccion()),
                items.stream().map(i -> new Item(i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.precioUnitario(), TipoAfectacionIgv.porCodigo(i.tipoAfectacionIgv()))).toList(),
                enviarAutomatico == null || enviarAutomatico);
    }
}
```

`dto/ComprobanteResponse.java`:
```java
package pe.factura.adapters.rest.dto;

import pe.factura.domain.documento.Comprobante;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ComprobanteResponse(UUID id, String tipo, String serie, Long numero, LocalDate fechaEmision, String moneda,
                                  String estadoDocumento, String hash, Integer intentos, String ultimoError,
                                  CdrDto cdr, TotalesDto totales, Map<String, String> enlaces) {
    public record CdrDto(String codigo, String descripcion, List<String> observaciones) {}
    public record TotalesDto(BigDecimal gravado, BigDecimal exonerado, BigDecimal inafecto, BigDecimal igv, BigDecimal total) {}

    public static ComprobanteResponse de(Comprobante c, String base) {
        String p = base + "/" + c.id();
        return new ComprobanteResponse(c.id(), c.tipo().codigo(), c.serie(), c.numero(), c.fechaEmision(), c.moneda(),
                c.estado().name(), c.hash(), c.intentos(), c.ultimoError(),
                c.cdr() == null ? null : new CdrDto(c.cdr().codigo(), c.cdr().descripcion(), c.cdr().observaciones()),
                new TotalesDto(c.totales().gravado(), c.totales().exonerado(), c.totales().inafecto(), c.totales().igv(), c.totales().total()),
                Map.of("xml", p + "/xml", "cdr", p + "/cdr"));
    }
}
```

- [ ] **Step 4: Controller**

`FacturaController.java`:
```java
package pe.factura.adapters.rest;

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
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/facturas")
public class FacturaController {
    private static final String BASE = "/v1/facturas";
    private final EmitirComprobanteUseCase emitir;
    private final EnviarDocumentoUseCase enviar;
    private final ConsultarComprobanteUseCase consultar;

    public FacturaController(EmitirComprobanteUseCase emitir, EnviarDocumentoUseCase enviar, ConsultarComprobanteUseCase consultar) {
        this.emitir = emitir; this.enviar = enviar; this.consultar = consultar;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ComprobanteResponse>> crear(HttpServletRequest req, @Valid @RequestBody FacturaRequest body) {
        Comprobante c = emitir.emitirFactura(TenantActual.id(req), body.aComando());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ComprobanteResponse.de(c, BASE)));
    }

    @GetMapping
    public ApiResponse<List<ComprobanteResponse>> listar(HttpServletRequest req, @RequestParam(required = false) EstadoDocumento estado,
                                                        @RequestParam(defaultValue = "1") int pagina, @RequestParam(name = "por_pagina", defaultValue = "20") int porPagina) {
        return ApiResponse.ok(consultar.listar(TenantActual.id(req), estado, Math.max(1, pagina), Math.min(100, Math.max(1, porPagina)))
                .stream().map(c -> ComprobanteResponse.de(c, BASE)).toList());
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
    public ResponseEntity<byte[]> cdr(HttpServletRequest req, @PathVariable UUID id) {
        UUID t = TenantActual.id(req);
        Comprobante c = consultar.obtener(t, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"R-" + c.nombreArchivo() + ".zip\"")
                .body(consultar.cdr(t, id));
    }
}
```

Nota: `@WebMvcTest` necesita `spring.jackson.property-naming-strategy=SNAKE_CASE`; añadir `adapters/in-rest/src/test/resources/application.properties` con esa línea.

- [ ] **Step 5: Ver pasar** — `./gradlew :adapters:in-rest:test` → PASS.
- [ ] **Step 6: Commit** — `git add adapters/in-rest && git commit -m "feat(rest): endpoints de facturas (crear, listar, obtener, enviar, xml, cdr)"`
