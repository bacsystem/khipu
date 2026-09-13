# 08 · OutboxWorker y endpoints de administración

Índice del plan: [README](README.md). Módulos `adapters/in-scheduler` y `adapters/in-rest`.

### Task 18: `OutboxWorker`

**Files:**
- Create: `adapters/in-scheduler/src/main/java/pe/factura/adapters/scheduler/OutboxWorker.java`
- Test: `adapters/in-scheduler/src/test/java/pe/factura/adapters/scheduler/OutboxWorkerTest.java`

**Interfaces:**
- Consumes: `OutboxRepository`, `UnitOfWork`, `EnviarDocumentoUseCase`, `Backoff`, `Clock`.
- Produces: `new OutboxWorker(OutboxRepository, UnitOfWork, EnviarDocumentoUseCase, Clock, int maxIntentos)` con método público `int procesar()` (devuelve filas procesadas) y `@Scheduled(fixedDelayString = "${app.outbox.intervalo-ms:10000}") void tick()`.
- Semántica por fila con acción `ENVIAR`:
  - resultado `ACEPTADO`/`ACEPTADO_CON_OBS`/`RECHAZADO` → `completar`.
  - resultado `ERROR_ENVIO` → si `intentos+1 >= maxIntentos` → `completar` (queda `ERROR_ENVIO` definitivo, se registra en log); si no → `reprogramar(id, Backoff.siguiente(intentos+1, ahora), ultimoError)`.
  - `DomainException` `ESTADO_NO_ENVIABLE` o `NO_ENCONTRADO` → `completar` (ya fue enviado por otra vía).
  - cualquier otra excepción → `reprogramar` con backoff.

- [ ] **Step 1: Test**

```java
package pe.factura.adapters.scheduler;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.out.OutboxItem;
import pe.factura.application.port.out.OutboxRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxWorkerTest {
    Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    UnitOfWork uow = new UnitOfWork() {
        public <T> T ejecutar(Supplier<T> w) { return w.get(); }
        public void ejecutar(Runnable w) { w.run(); }
    };
    OutboxRepository outbox = mock(OutboxRepository.class);
    EnviarDocumentoUseCase enviar = mock(EnviarDocumentoUseCase.class);
    OutboxWorker worker = new OutboxWorker(outbox, uow, enviar, clock, 20);
    UUID tenant = UUID.randomUUID(), doc = UUID.randomUUID(), fila = UUID.randomUUID();

    private Comprobante conEstado(EstadoDocumento e, int intentos) {
        return Comprobante.rehidratar(doc, tenant, TipoDocumento.FACTURA, "F001", 1L, LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "X", null), List.of(new Item("P", "d", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO)),
                e, "h", "20100066603-01-F001-1", "k", null, null, intentos, e == EstadoDocumento.ERROR_ENVIO ? "timeout" : null);
    }

    @Test void aceptadoCompleta() {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 0)));
        when(enviar.enviar(tenant, doc)).thenReturn(conEstado(EstadoDocumento.ACEPTADO, 1));
        assertThat(worker.procesar()).isEqualTo(1);
        verify(outbox).completar(fila);
    }

    @Test void errorEnvioReprogramaConBackoff() {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 2)));
        when(enviar.enviar(tenant, doc)).thenReturn(conEstado(EstadoDocumento.ERROR_ENVIO, 3));
        worker.procesar();
        verify(outbox).reprogramar(fila, clock.instant().plus(Duration.ofMinutes(8)), "timeout");
    }

    @Test void superaMaximoYCompleta() {
        OutboxWorker w = new OutboxWorker(outbox, uow, enviar, clock, 3);
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 2)));
        when(enviar.enviar(tenant, doc)).thenReturn(conEstado(EstadoDocumento.ERROR_ENVIO, 3));
        w.procesar();
        verify(outbox).completar(fila);
        verify(outbox, never()).reprogramar(any(), any(), any());
    }

    @Test void estadoNoEnviableCompleta() {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 0)));
        when(enviar.enviar(tenant, doc)).thenThrow(new DomainException("ESTADO_NO_ENVIABLE", "ya aceptado"));
        worker.procesar();
        verify(outbox).completar(fila);
    }

    @Test void excepcionInesperadaReprograma() {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 0)));
        when(enviar.enviar(tenant, doc)).thenThrow(new IllegalStateException("storage caído"));
        worker.procesar();
        verify(outbox).reprogramar(eq(fila), eq(clock.instant().plus(Duration.ofMinutes(2))), contains("storage caído"));
    }

    @Test void sinFilasNoHaceNada() {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of());
        assertThat(worker.procesar()).isZero();
        verifyNoInteractions(enviar);
    }
}
```

- [ ] **Step 2: Ver fallar** — `./gradlew :adapters:in-scheduler:test` → FAIL de compilación.

- [ ] **Step 3: Implementación**

```java
package pe.factura.adapters.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.out.OutboxItem;
import pe.factura.application.port.out.OutboxRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.application.service.Backoff;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

public class OutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private static final int LOTE = 50;
    private static final Duration LOCK = Duration.ofMinutes(2);

    private final OutboxRepository outbox;
    private final UnitOfWork uow;
    private final EnviarDocumentoUseCase enviar;
    private final Clock clock;
    private final int maxIntentos;

    public OutboxWorker(OutboxRepository outbox, UnitOfWork uow, EnviarDocumentoUseCase enviar, Clock clock, int maxIntentos) {
        this.outbox = outbox; this.uow = uow; this.enviar = enviar; this.clock = clock; this.maxIntentos = maxIntentos;
    }

    @Scheduled(fixedDelayString = "${app.outbox.intervalo-ms:10000}")
    public void tick() {
        try { procesar(); } catch (Exception e) { log.error("Fallo del ciclo de outbox", e); }
    }

    public int procesar() {
        List<OutboxItem> filas = uow.ejecutar(() -> outbox.tomarVencidas(LOTE, LOCK));
        for (OutboxItem fila : filas) procesarFila(fila);
        return filas.size();
    }

    private void procesarFila(OutboxItem fila) {
        try {
            if (!"ENVIAR".equals(fila.accion())) { log.warn("Acción desconocida {} en outbox {}", fila.accion(), fila.id()); outbox.completar(fila.id()); return; }
            Comprobante c = enviar.enviar(fila.tenantId(), fila.agregadoId());
            if (c.estado() == EstadoDocumento.ERROR_ENVIO) {
                int intentos = fila.intentos() + 1;
                if (intentos >= maxIntentos) {
                    log.error("Documento {} agotó {} intentos de envío: {}", c.nombreArchivo(), maxIntentos, c.ultimoError());
                    outbox.completar(fila.id());
                } else {
                    outbox.reprogramar(fila.id(), Backoff.siguiente(intentos, clock.instant()), c.ultimoError());
                }
            } else {
                outbox.completar(fila.id());
            }
        } catch (DomainException e) {
            log.info("Outbox {} descartada: {} {}", fila.id(), e.codigo(), e.getMessage());
            outbox.completar(fila.id());
        } catch (Exception e) {
            log.warn("Outbox {} falló, se reprograma: {}", fila.id(), e.toString());
            outbox.reprogramar(fila.id(), Backoff.siguiente(fila.intentos() + 1, clock.instant()), e.toString());
        }
    }
}
```

- [ ] **Step 4: Ver pasar** — `./gradlew :adapters:in-scheduler:test` → PASS.
- [ ] **Step 5: Commit** — `git add adapters/in-scheduler && git commit -m "feat(scheduler): OutboxWorker con reintentos exponenciales y tope de intentos"`

---

### Task 19: Endpoints de administración

**Files:**
- Create: `adapters/in-rest/src/main/java/pe/factura/adapters/rest/AdminTenantController.java`, `EmpresaController.java`, `PlatformKeyFilter.java`, `dto/CrearTenantRequest.java`, `dto/SerieRequest.java`, `dto/CredencialesSolRequest.java`
- Test: `adapters/in-rest/src/test/java/pe/factura/adapters/rest/EmpresaControllerTest.java`, `PlatformKeyFilterTest.java`

**Interfaces:**
- Consumes: `AdministrarTenantUseCase` (Task 8).
- Produces:
  - `PlatformKeyFilter(String platformKey)`: para `/v1/admin/**` exige header `X-Platform-Key` igual a la configurada (comparación en tiempo constante); si la clave configurada está vacía responde `404` (modo `single` o sin configurar).
  - `POST /v1/admin/tenants {ruc, razon_social, entorno}` → `201 {tenant_id, ruc, api_key}`.
  - `GET /v1/empresa` → datos del tenant (sin secretos; incluye `certificado_vigencia_hasta`, `tiene_credenciales_sol`).
  - `POST /v1/empresa/certificado` multipart `archivo` (PFX) + `clave` → `204`.
  - `PUT /v1/empresa/credenciales-sol {usuario, clave}` → `204`.
  - `POST /v1/empresa/api-keys` → `201 {api_key}`.
  - `POST /v1/series {tipo, serie, correlativo_inicial}` → `201`; `GET /v1/series` → lista.

- [ ] **Step 1: Tests**

`PlatformKeyFilterTest.java`:
```java
package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.assertThat;

class PlatformKeyFilterTest {
    @Test void claveCorrectaPasa() throws Exception {
        var req = new MockHttpServletRequest("POST", "/v1/admin/tenants"); req.addHeader("X-Platform-Key", "secreta");
        var chain = new MockFilterChain();
        new PlatformKeyFilter("secreta").doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }
    @Test void claveIncorrecta401() throws Exception {
        var req = new MockHttpServletRequest("POST", "/v1/admin/tenants"); req.addHeader("X-Platform-Key", "otra");
        var res = new MockHttpServletResponse();
        new PlatformKeyFilter("secreta").doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(401);
    }
    @Test void sinClaveConfigurada404() throws Exception {
        var req = new MockHttpServletRequest("POST", "/v1/admin/tenants"); req.addHeader("X-Platform-Key", "x");
        var res = new MockHttpServletResponse();
        new PlatformKeyFilter("").doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(404);
    }
    @Test void otrasRutasNoSeFiltran() throws Exception {
        var req = new MockHttpServletRequest("GET", "/v1/facturas");
        var chain = new MockFilterChain();
        new PlatformKeyFilter("secreta").doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }
}
```

`EmpresaControllerTest.java`:
```java
package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.AdministrarTenantUseCase;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {EmpresaController.class, AdminTenantController.class}, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class EmpresaControllerTest {
    @Autowired MockMvc mvc;
    @MockBean AdministrarTenantUseCase admin;
    UUID tenant = UUID.randomUUID();

    @Test void verEmpresaSinSecretos() throws Exception {
        when(admin.obtener(tenant)).thenReturn(new Tenant(tenant, "20100066603", "EMPRESA SAC", Entorno.BETA,
                new CredencialesSol("MODDATOS", "moddatos"), new CertificadoDigital(new byte[]{1}, "clave", LocalDate.of(2030, 1, 1))));
        mvc.perform(get("/v1/empresa").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.ruc").value("20100066603"))
                .andExpect(jsonPath("$.datos.tiene_credenciales_sol").value(true))
                .andExpect(jsonPath("$.datos.certificado_vigencia_hasta").value("2030-01-01"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("moddatos"))));
    }

    @Test void subirCertificado() throws Exception {
        mvc.perform(multipart("/v1/empresa/certificado").file(new MockMultipartFile("archivo", "c.pfx", "application/x-pkcs12", new byte[]{1, 2}))
                        .param("clave", "test1234").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isNoContent());
        verify(admin).cargarCertificado(eq(tenant), eq(new byte[]{1, 2}), eq("test1234"));
    }

    @Test void credencialesSol() throws Exception {
        mvc.perform(put("/v1/empresa/credenciales-sol").contentType("application/json").content("{\"usuario\":\"MODDATOS\",\"clave\":\"moddatos\"}").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isNoContent());
        verify(admin).cargarCredencialesSol(tenant, "MODDATOS", "moddatos");
    }

    @Test void crearSerieYListar() throws Exception {
        when(admin.listarSeries(tenant)).thenReturn(List.of(new Serie(tenant, TipoDocumento.FACTURA, "F001", 0, true)));
        mvc.perform(post("/v1/series").contentType("application/json").content("{\"tipo\":\"01\",\"serie\":\"F001\",\"correlativo_inicial\":0}").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isCreated());
        verify(admin).crearSerie(tenant, TipoDocumento.FACTURA, "F001", 0);
        mvc.perform(get("/v1/series").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos[0].serie").value("F001")).andExpect(jsonPath("$.datos[0].tipo").value("01"));
    }

    @Test void crearApiKey() throws Exception {
        when(admin.crearApiKey(tenant)).thenReturn("fk_nueva");
        mvc.perform(post("/v1/empresa/api-keys").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.datos.api_key").value("fk_nueva"));
    }

    @Test void adminCreaTenant() throws Exception {
        Tenant t = new Tenant(tenant, "20100066603", "EMPRESA SAC", Entorno.BETA, null, null);
        when(admin.crearTenant("20100066603", "EMPRESA SAC", Entorno.BETA)).thenReturn(new AdministrarTenantUseCase.TenantCreado(t, "fk_primera"));
        mvc.perform(post("/v1/admin/tenants").contentType("application/json").content("{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA SAC\",\"entorno\":\"BETA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.tenant_id").value(tenant.toString()))
                .andExpect(jsonPath("$.datos.api_key").value("fk_primera"));
    }
}
```

- [ ] **Step 2: Ver fallar** — `./gradlew :adapters:in-rest:test` → FAIL de compilación.

- [ ] **Step 3: Implementación**

`PlatformKeyFilter.java`:
```java
package pe.factura.adapters.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class PlatformKeyFilter extends OncePerRequestFilter {
    private final String platformKey;
    public PlatformKeyFilter(String platformKey) { this.platformKey = platformKey == null ? "" : platformKey; }

    @Override protected boolean shouldNotFilter(HttpServletRequest req) { return !req.getRequestURI().startsWith("/v1/admin/"); }

    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        if (platformKey.isBlank()) { res.setStatus(404); return; }
        String dada = req.getHeader("X-Platform-Key");
        boolean ok = dada != null && MessageDigest.isEqual(dada.getBytes(StandardCharsets.UTF_8), platformKey.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"estado\":\"error\",\"codigo\":\"NO_AUTORIZADO\",\"mensaje\":\"Clave de plataforma inválida\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
```

DTOs:
```java
package pe.factura.adapters.rest.dto;
import jakarta.validation.constraints.*;
import pe.factura.domain.tenant.Entorno;
public record CrearTenantRequest(@NotBlank @Pattern(regexp = "\\d{11}") String ruc, @NotBlank String razonSocial, @NotNull Entorno entorno) {}
```
```java
package pe.factura.adapters.rest.dto;
import jakarta.validation.constraints.*;
public record SerieRequest(@NotBlank @Pattern(regexp = "01|03|07|08") String tipo, @NotBlank String serie, @PositiveOrZero Long correlativoInicial) {}
```
```java
package pe.factura.adapters.rest.dto;
import jakarta.validation.constraints.NotBlank;
public record CredencialesSolRequest(@NotBlank String usuario, @NotBlank String clave) {}
```

`AdminTenantController.java`:
```java
package pe.factura.adapters.rest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.CrearTenantRequest;
import pe.factura.application.port.in.AdministrarTenantUseCase;

import java.util.Map;

@RestController
@RequestMapping("/v1/admin")
public class AdminTenantController {
    private final AdministrarTenantUseCase admin;
    public AdminTenantController(AdministrarTenantUseCase admin) { this.admin = admin; }

    @PostMapping("/tenants")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crear(@Valid @RequestBody CrearTenantRequest body) {
        var r = admin.crearTenant(body.ruc(), body.razonSocial(), body.entorno());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(Map.of(
                "tenant_id", r.tenant().id(), "ruc", r.tenant().ruc(), "api_key", r.apiKeyEnClaro())));
    }
}
```

`EmpresaController.java`:
```java
package pe.factura.adapters.rest;

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
public class EmpresaController {
    private final AdministrarTenantUseCase admin;
    public EmpresaController(AdministrarTenantUseCase admin) { this.admin = admin; }

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
```

- [ ] **Step 4: Ver pasar** — `./gradlew :adapters:in-rest:test` → PASS.
- [ ] **Step 5: Commit** — `git add adapters/in-rest && git commit -m "feat(rest): administración de tenants, certificado, credenciales SOL, series y API keys"`
