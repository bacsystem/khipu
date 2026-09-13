# 03 · Application: puertos y casos de uso

Índice del plan: [README](README.md). Módulo `application` — depende solo de `domain`.

### Task 5: Puertos de salida y excepciones SUNAT

**Files:**
- Create en `application/src/main/java/pe/factura/application/port/out/`: `UblGenerator.java`, `XsdValidator.java`, `XmlSigner.java`, `FirmaResultado.java`, `SunatBillingGateway.java`, `SunatTransientException.java`, `SunatRechazoException.java`, `CdrParser.java`, `DocumentStorage.java`, `SecretCipher.java`, `ComprobanteRepository.java`, `SerieRepository.java`, `TenantRepository.java`, `ApiKeyRepository.java`, `OutboxRepository.java`, `OutboxItem.java`, `UnitOfWork.java`

**Interfaces (Produces — todas las tareas siguientes dependen de estas firmas exactas):**

```java
package pe.factura.application.port.out;

public interface UblGenerator { String generar(Comprobante c, Tenant t); }             // XML sin firmar, UTF-8
public interface XsdValidator { void validar(String xml, TipoDocumento tipo); }         // lanza DomainException("XSD_INVALIDO", …)
public record FirmaResultado(String xmlFirmado, String hash) {}                          // hash = DigestValue base64
public interface XmlSigner { FirmaResultado firmar(String xml, CertificadoDigital cert); }
public interface SunatBillingGateway {
    /** Comprime el XML en {nombreArchivo}.zip y llama a sendBill. Devuelve el ZIP del CDR. */
    byte[] sendBill(Tenant tenant, String nombreArchivo, byte[] xmlFirmado);            // lanza SunatTransientException | SunatRechazoException
}
public class SunatTransientException extends RuntimeException { String codigo(); }      // fault 0100–1999, timeout, red, 5xx
public class SunatRechazoException extends RuntimeException { String codigo(); String descripcion(); } // fault ≥ 2000
public interface CdrParser { Cdr parsear(byte[] cdrZip); }
public interface DocumentStorage { void guardar(String key, byte[] contenido); byte[] leer(String key); }
public interface SecretCipher { byte[] cifrar(byte[] plano); byte[] descifrar(byte[] cifrado); }
public interface ComprobanteRepository {
    void guardar(Comprobante c);                                                        // insert o update por id
    Optional<Comprobante> buscar(UUID tenantId, UUID id);
    boolean existe(UUID tenantId, TipoDocumento tipo, String serie, long numero);
    List<Comprobante> listar(UUID tenantId, EstadoDocumento estado, int pagina, int porPagina);
}
public interface SerieRepository {
    long siguienteNumero(UUID tenantId, TipoDocumento tipo, String serie);              // bloqueo de fila; DomainException("SERIE_NO_CONFIGURADA")
    void crear(Serie s);  List<Serie> listar(UUID tenantId);
}
public interface TenantRepository { void guardar(Tenant t); Optional<Tenant> buscar(UUID id); Optional<Tenant> buscarPorRuc(String ruc); }
public interface ApiKeyRepository { void guardar(ApiKey k); Optional<ApiKey> buscarPorHash(String hash); }
public record OutboxItem(UUID id, UUID tenantId, UUID agregadoId, String accion, int intentos) {}
public interface OutboxRepository {
    void programar(UUID tenantId, String accion, UUID agregadoId, Instant cuando);
    List<OutboxItem> tomarVencidas(int limite, Duration lock);                          // FOR UPDATE SKIP LOCKED + marca locked_until
    void reprogramar(UUID id, Instant cuando, String error);
    void completar(UUID id);
}
public interface UnitOfWork { <T> T ejecutar(Supplier<T> trabajo); void ejecutar(Runnable trabajo); }
```

- [ ] **Step 1: Crear los archivos con exactamente esas firmas** (un archivo por tipo, con los `import` de `pe.factura.domain.documento.*`, `pe.factura.domain.tenant.*`, `java.util.*`, `java.time.*`, `java.util.function.Supplier`). Las dos excepciones:

```java
package pe.factura.application.port.out;

public class SunatTransientException extends RuntimeException {
    private final String codigo;
    public SunatTransientException(String codigo, String mensaje, Throwable causa) { super(mensaje, causa); this.codigo = codigo; }
    public SunatTransientException(String codigo, String mensaje) { this(codigo, mensaje, null); }
    public String codigo() { return codigo; }
}
```
```java
package pe.factura.application.port.out;

public class SunatRechazoException extends RuntimeException {
    private final String codigo, descripcion;
    public SunatRechazoException(String codigo, String descripcion) { super(codigo + " - " + descripcion); this.codigo = codigo; this.descripcion = descripcion; }
    public String codigo() { return codigo; }
    public String descripcion() { return descripcion; }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew :application:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add application
git commit -m "feat(application): puertos de salida y excepciones SUNAT"
```

---

### Task 6: `Backoff` y caso de uso `EnviarDocumento`

**Files:**
- Create: `application/src/main/java/pe/factura/application/service/Backoff.java`
- Create: `application/src/main/java/pe/factura/application/port/in/EnviarDocumentoUseCase.java`
- Create: `application/src/main/java/pe/factura/application/service/EnviarDocumentoService.java`
- Test: `application/src/test/java/pe/factura/application/service/BackoffTest.java`, `EnviarDocumentoServiceTest.java`
- Create (soporte de tests, reutilizado en Task 7): `application/src/test/java/pe/factura/application/service/Fakes.java`

**Interfaces:**
- Produces: `Backoff.siguiente(int intentos, Instant ahora) -> Instant` = `ahora + min(2^intentos min, 6 h)`; `EnviarDocumentoUseCase { Comprobante enviar(UUID tenantId, UUID comprobanteId); }` — idempotente: si el estado no es enviable lanza `DomainException("ESTADO_NO_ENVIABLE")`.
- Consumes: puertos de Task 5, `Comprobante` de Task 4.

- [ ] **Step 1: Fakes en memoria (soporte de tests)**

`Fakes.java`:
```java
package pe.factura.application.service;

import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.*;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;

final class Fakes {
    static final class Comprobantes implements ComprobanteRepository {
        final Map<UUID, Comprobante> datos = new HashMap<>();
        public void guardar(Comprobante c) { datos.put(c.id(), c); }
        public Optional<Comprobante> buscar(UUID t, UUID id) { return Optional.ofNullable(datos.get(id)).filter(c -> c.tenantId().equals(t)); }
        public boolean existe(UUID t, TipoDocumento tipo, String serie, long numero) {
            return datos.values().stream().anyMatch(c -> c.tenantId().equals(t) && c.tipo() == tipo && c.serie().equals(serie) && Long.valueOf(numero).equals(c.numero()));
        }
        public List<Comprobante> listar(UUID t, EstadoDocumento e, int p, int pp) { return datos.values().stream().filter(c -> c.tenantId().equals(t)).toList(); }
    }
    static final class Series implements SerieRepository {
        final Map<String, Long> ultimo = new HashMap<>();
        public long siguienteNumero(UUID t, TipoDocumento tipo, String serie) {
            String k = t + tipo.codigo() + serie;
            if (!ultimo.containsKey(k)) throw new DomainException("SERIE_NO_CONFIGURADA", "Serie no configurada: " + serie);
            return ultimo.merge(k, 1L, Long::sum);
        }
        public void crear(Serie s) { ultimo.put(s.tenantId() + s.tipo().codigo() + s.codigo(), s.ultimoNumero()); }
        public List<Serie> listar(UUID t) { return List.of(); }
    }
    static final class Tenants implements TenantRepository {
        final Map<UUID, Tenant> datos = new HashMap<>();
        public void guardar(Tenant t) { datos.put(t.id(), t); }
        public Optional<Tenant> buscar(UUID id) { return Optional.ofNullable(datos.get(id)); }
        public Optional<Tenant> buscarPorRuc(String ruc) { return datos.values().stream().filter(t -> t.ruc().equals(ruc)).findFirst(); }
    }
    static final class Storage implements DocumentStorage {
        final Map<String, byte[]> datos = new HashMap<>();
        public void guardar(String k, byte[] c) { datos.put(k, c); }
        public byte[] leer(String k) { byte[] b = datos.get(k); if (b == null) throw new IllegalStateException("no existe " + k); return b; }
    }
    static final class Outbox implements OutboxRepository {
        record Fila(UUID tenantId, String accion, UUID agregadoId, Instant cuando) {}
        final List<Fila> filas = new ArrayList<>();
        public void programar(UUID t, String accion, UUID id, Instant cuando) { filas.add(new Fila(t, accion, id, cuando)); }
        public List<OutboxItem> tomarVencidas(int l, Duration d) { return List.of(); }
        public void reprogramar(UUID id, Instant c, String e) {}
        public void completar(UUID id) {}
    }
    static final class Gateway implements SunatBillingGateway {
        RuntimeException falla; byte[] respuesta = "cdr".getBytes(); String ultimoNombre;
        public byte[] sendBill(Tenant t, String nombre, byte[] xml) { ultimoNombre = nombre; if (falla != null) throw falla; return respuesta; }
    }
    static final class Cdrs implements CdrParser {
        Cdr cdr = new Cdr("0", "aceptada", List.of());
        public Cdr parsear(byte[] zip) { return cdr; }
    }
    static final UnitOfWork UOW = new UnitOfWork() {
        public <T> T ejecutar(Supplier<T> w) { return w.get(); }
        public void ejecutar(Runnable w) { w.run(); }
    };
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    static Tenant tenantListo(UUID id) {
        return new Tenant(id, "20100066603", "EMPRESA SAC", Entorno.BETA, new CredencialesSol("MODDATOS", "moddatos"),
                new CertificadoDigital(new byte[]{1}, "clave", LocalDate.of(2030, 1, 1)));
    }
    static Comprobante facturaFirmada(UUID tenantId, Storage storage) {
        Comprobante c = Comprobante.crearFactura(tenantId, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", java.math.BigDecimal.ONE, new java.math.BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)), CLOCK);
        c.asignarNumero(1, "20100066603");
        String key = "k/" + c.nombreArchivo() + ".xml";
        storage.guardar(key, "<xml/>".getBytes());
        c.firmar("hash", key);
        return c;
    }
}
```

- [ ] **Step 2: Tests**

`BackoffTest.java`:
```java
package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class BackoffTest {
    private final Instant ahora = Instant.parse("2026-09-13T15:00:00Z");
    @Test void exponencialConTope() {
        assertThat(Backoff.siguiente(1, ahora)).isEqualTo(ahora.plus(Duration.ofMinutes(2)));
        assertThat(Backoff.siguiente(3, ahora)).isEqualTo(ahora.plus(Duration.ofMinutes(8)));
        assertThat(Backoff.siguiente(8, ahora)).isEqualTo(ahora.plus(Duration.ofMinutes(256)));
        assertThat(Backoff.siguiente(9, ahora)).isEqualTo(ahora.plus(Duration.ofHours(6)));
        assertThat(Backoff.siguiente(20, ahora)).isEqualTo(ahora.plus(Duration.ofHours(6)));
    }
}
```

`EnviarDocumentoServiceTest.java`:
```java
package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EnviarDocumentoServiceTest {
    UUID tenantId = UUID.randomUUID();
    Fakes.Comprobantes comprobantes = new Fakes.Comprobantes();
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Storage storage = new Fakes.Storage();
    Fakes.Gateway gateway = new Fakes.Gateway();
    Fakes.Cdrs cdrs = new Fakes.Cdrs();
    EnviarDocumentoService service;
    Comprobante c;

    @BeforeEach void setUp() {
        tenants.guardar(Fakes.tenantListo(tenantId));
        c = Fakes.facturaFirmada(tenantId, storage);
        comprobantes.guardar(c);
        service = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, Fakes.UOW);
    }

    @Test void aceptadoGuardaCdrYEstado() {
        Comprobante r = service.enviar(tenantId, c.id());
        assertThat(r.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(gateway.ultimoNombre).isEqualTo("20100066603-01-F001-1");
        assertThat(storage.datos).containsKey("k/R-20100066603-01-F001-1.zip");
        assertThat(r.cdrKey()).isEqualTo("k/R-20100066603-01-F001-1.zip");
    }

    @Test void cdrConObservaciones() {
        cdrs.cdr = new Cdr("0", "ok", List.of("4252 - obs"));
        assertThat(service.enviar(tenantId, c.id()).estado()).isEqualTo(EstadoDocumento.ACEPTADO_CON_OBS);
    }

    @Test void cdrRechazo() {
        cdrs.cdr = new Cdr("2324", "registrado previamente", List.of());
        assertThat(service.enviar(tenantId, c.id()).estado()).isEqualTo(EstadoDocumento.RECHAZADO);
    }

    @Test void faultTransitorioDejaErrorEnvio() {
        gateway.falla = new SunatTransientException("0109", "timeout");
        Comprobante r = service.enviar(tenantId, c.id());
        assertThat(r.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(r.intentos()).isEqualTo(1);
        assertThat(r.ultimoError()).contains("0109");
    }

    @Test void faultDefinitivoRechaza() {
        gateway.falla = new SunatRechazoException("2324", "registrado previamente");
        Comprobante r = service.enviar(tenantId, c.id());
        assertThat(r.estado()).isEqualTo(EstadoDocumento.RECHAZADO);
        assertThat(r.cdr().codigo()).isEqualTo("2324");
    }

    @Test void reintentoDesdeErrorEnvioFunciona() {
        gateway.falla = new SunatTransientException("0109", "timeout");
        service.enviar(tenantId, c.id());
        gateway.falla = null;
        assertThat(service.enviar(tenantId, c.id()).estado()).isEqualTo(EstadoDocumento.ACEPTADO);
    }

    @Test void estadoNoEnviableLanza() {
        service.enviar(tenantId, c.id());
        assertThatThrownBy(() -> service.enviar(tenantId, c.id()))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("ESTADO_NO_ENVIABLE");
    }

    @Test void otroTenantNoVeElDocumento() {
        assertThatThrownBy(() -> service.enviar(UUID.randomUUID(), c.id()))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }
}
```

- [ ] **Step 3: Ejecutar y ver fallar**

Run: `./gradlew :application:test`
Expected: FAIL de compilación.

- [ ] **Step 4: Implementación**

`Backoff.java`:
```java
package pe.factura.application.service;

import java.time.Duration;
import java.time.Instant;

public final class Backoff {
    private static final Duration TOPE = Duration.ofHours(6);
    private Backoff() {}
    public static Instant siguiente(int intentos, Instant ahora) {
        int exp = Math.min(intentos, 20);
        Duration d = Duration.ofMinutes(1L << exp);
        return ahora.plus(d.compareTo(TOPE) > 0 ? TOPE : d);
    }
}
```

`port/in/EnviarDocumentoUseCase.java`:
```java
package pe.factura.application.port.in;

import pe.factura.domain.documento.Comprobante;
import java.util.UUID;

public interface EnviarDocumentoUseCase {
    /** Envía a SUNAT un comprobante FIRMADO o en ERROR_ENVIO y persiste el resultado. Nunca lanza por errores de SUNAT. */
    Comprobante enviar(UUID tenantId, UUID comprobanteId);
}
```

`service/EnviarDocumentoService.java`:
```java
package pe.factura.application.service;

import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.tenant.Tenant;

import java.util.UUID;

public class EnviarDocumentoService implements EnviarDocumentoUseCase {
    private final ComprobanteRepository comprobantes;
    private final TenantRepository tenants;
    private final DocumentStorage storage;
    private final SunatBillingGateway gateway;
    private final CdrParser cdrParser;
    private final UnitOfWork uow;

    public EnviarDocumentoService(ComprobanteRepository comprobantes, TenantRepository tenants, DocumentStorage storage,
                                  SunatBillingGateway gateway, CdrParser cdrParser, UnitOfWork uow) {
        this.comprobantes = comprobantes; this.tenants = tenants; this.storage = storage;
        this.gateway = gateway; this.cdrParser = cdrParser; this.uow = uow;
    }

    @Override
    public Comprobante enviar(UUID tenantId, UUID comprobanteId) {
        Comprobante c = comprobantes.buscar(tenantId, comprobanteId)
                .orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
        if (!c.estado().esEnviable())
            throw new DomainException("ESTADO_NO_ENVIABLE", "El comprobante está en estado " + c.estado());
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        tenant.exigirCredencialesSol();

        byte[] xml = storage.leer(c.xmlKey());
        try {
            byte[] cdrZip = gateway.sendBill(tenant, c.nombreArchivo(), xml);
            Cdr cdr = cdrParser.parsear(cdrZip);
            String cdrKey = c.xmlKey().substring(0, c.xmlKey().lastIndexOf('/') + 1) + "R-" + c.nombreArchivo() + ".zip";
            storage.guardar(cdrKey, cdrZip);
            c.marcarEnviado();
            c.aplicarCdr(cdr, cdrKey);
        } catch (SunatTransientException e) {
            c.marcarErrorEnvio(e.codigo() + " - " + e.getMessage());
        } catch (SunatRechazoException e) {
            c.rechazarPorFault(e.codigo(), e.descripcion());
        }
        uow.ejecutar(() -> comprobantes.guardar(c));
        return c;
    }
}
```

- [ ] **Step 5: Ejecutar y ver pasar**

Run: `./gradlew :application:test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add application
git commit -m "feat(application): caso de uso EnviarDocumento con manejo de CDR y faults SUNAT"
```

---

### Task 7: Caso de uso `EmitirComprobante`

**Files:**
- Create: `application/src/main/java/pe/factura/application/port/in/EmitirComprobanteUseCase.java`, `EmitirFacturaCommand.java`
- Create: `application/src/main/java/pe/factura/application/service/EmitirComprobanteService.java`
- Test: `application/src/test/java/pe/factura/application/service/EmitirComprobanteServiceTest.java`

**Interfaces:**
- Produces:
  - `EmitirFacturaCommand(String serie, Long correlativo, LocalDate fechaEmision, String moneda, String tipoOperacion, Receptor receptor, List<Item> items, boolean enviarAutomatico)`
  - `EmitirComprobanteUseCase { Comprobante emitirFactura(UUID tenantId, EmitirFacturaCommand cmd); }`
  - Clave de storage del XML: `{tenantId}/{yyyy}/{MM}/{nombreArchivo}.xml`.
- Consumes: `EnviarDocumentoUseCase` (Task 6), `Backoff`, puertos (Task 5).

- [ ] **Step 1: Test**

`EmitirComprobanteServiceTest.java`:
```java
package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.Serie;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EmitirComprobanteServiceTest {
    UUID tenantId = UUID.randomUUID();
    Fakes.Comprobantes comprobantes = new Fakes.Comprobantes();
    Fakes.Series series = new Fakes.Series();
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Storage storage = new Fakes.Storage();
    Fakes.Outbox outbox = new Fakes.Outbox();
    Fakes.Gateway gateway = new Fakes.Gateway();
    Fakes.Cdrs cdrs = new Fakes.Cdrs();
    UblGenerator ubl = (c, t) -> "<Invoice>" + c.nombreArchivo() + "</Invoice>";
    XsdValidator xsd = (xml, tipo) -> {};
    XmlSigner signer = (xml, cert) -> new FirmaResultado(xml.replace("<Invoice>", "<Invoice><ds:Signature/>"), "HASH" + xml.length());
    EmitirComprobanteService service;

    @BeforeEach void setUp() {
        tenants.guardar(Fakes.tenantListo(tenantId));
        series.crear(new Serie(tenantId, TipoDocumento.FACTURA, "F001", 0, true));
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, Fakes.UOW);
        service = new EmitirComprobanteService(comprobantes, series, tenants, storage, outbox, ubl, xsd, signer, enviar, Fakes.UOW, Fakes.CLOCK);
    }

    private EmitirFacturaCommand cmd(Long correlativo, boolean enviar) {
        return new EmitirFacturaCommand("F001", correlativo, LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)), enviar);
    }

    @Test void asignaNumeroFirmaGuardaYEnvia() {
        Comprobante c = service.emitirFactura(tenantId, cmd(null, true));
        assertThat(c.numero()).isEqualTo(1L);
        assertThat(c.hash()).startsWith("HASH");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(c.xmlKey()).isEqualTo(tenantId + "/2026/09/20100066603-01-F001-1.xml");
        assertThat(new String(storage.leer(c.xmlKey()))).contains("<ds:Signature/>");
        assertThat(comprobantes.datos).containsKey(c.id());
        assertThat(outbox.filas).isEmpty();
    }

    @Test void numeracionCorrelativa() {
        service.emitirFactura(tenantId, cmd(null, false));
        Comprobante segundo = service.emitirFactura(tenantId, cmd(null, false));
        assertThat(segundo.numero()).isEqualTo(2L);
        assertThat(segundo.estado()).isEqualTo(EstadoDocumento.FIRMADO);
    }

    @Test void correlativoExplicitoSeRespetaYNoSeDuplica() {
        Comprobante c = service.emitirFactura(tenantId, cmd(50L, false));
        assertThat(c.numero()).isEqualTo(50L);
        assertThatThrownBy(() -> service.emitirFactura(tenantId, cmd(50L, false)))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("DUPLICADO");
    }

    @Test void sinEnvioAutomaticoQuedaFirmado() {
        Comprobante c = service.emitirFactura(tenantId, cmd(null, false));
        assertThat(c.estado()).isEqualTo(EstadoDocumento.FIRMADO);
        assertThat(gateway.ultimoNombre).isNull();
    }

    @Test void falloTransitorioProgramaOutbox() {
        gateway.falla = new SunatTransientException("0109", "timeout");
        Comprobante c = service.emitirFactura(tenantId, cmd(null, true));
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(outbox.filas).hasSize(1);
        assertThat(outbox.filas.get(0).accion()).isEqualTo("ENVIAR");
        assertThat(outbox.filas.get(0).cuando()).isEqualTo(Backoff.siguiente(1, Fakes.CLOCK.instant()));
    }

    @Test void xsdInvalidoNoConsumeNumeroNiGuarda() {
        XsdValidator malo = (xml, tipo) -> { throw new DomainException("XSD_INVALIDO", "línea 3"); };
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, Fakes.UOW);
        EmitirComprobanteService s = new EmitirComprobanteService(comprobantes, series, tenants, storage, outbox, ubl, malo, signer, enviar, Fakes.UOW, Fakes.CLOCK);
        assertThatThrownBy(() -> s.emitirFactura(tenantId, cmd(null, true))).extracting("codigo").isEqualTo("XSD_INVALIDO");
        assertThat(comprobantes.datos).isEmpty();
    }

    @Test void tenantSinCertificadoFalla() {
        Tenant sinCert = new Tenant(tenantId, "20100066603", "EMPRESA SAC", pe.factura.domain.tenant.Entorno.BETA, null, null);
        tenants.guardar(sinCert);
        assertThatThrownBy(() -> service.emitirFactura(tenantId, cmd(null, false))).extracting("codigo").isEqualTo("CERTIFICADO_NO_CARGADO");
    }

    @Test void serieNoConfiguradaFalla() {
        assertThatThrownBy(() -> service.emitirFactura(tenantId, new EmitirFacturaCommand("F999", null, LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)), false)))
                .extracting("codigo").isEqualTo("SERIE_NO_CONFIGURADA");
    }
}
```

- [ ] **Step 2: Ejecutar y ver fallar**

Run: `./gradlew :application:test --tests '*EmitirComprobanteServiceTest*'`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementación**

`port/in/EmitirFacturaCommand.java`:
```java
package pe.factura.application.port.in;

import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.Receptor;
import java.time.LocalDate;
import java.util.List;

public record EmitirFacturaCommand(String serie, Long correlativo, LocalDate fechaEmision, String moneda, String tipoOperacion,
                                   Receptor receptor, List<Item> items, boolean enviarAutomatico) {}
```

`port/in/EmitirComprobanteUseCase.java`:
```java
package pe.factura.application.port.in;

import pe.factura.domain.documento.Comprobante;
import java.util.UUID;

public interface EmitirComprobanteUseCase {
    Comprobante emitirFactura(UUID tenantId, EmitirFacturaCommand cmd);
}
```

`service/EmitirComprobanteService.java`:
```java
package pe.factura.application.service;

import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Tenant;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

public class EmitirComprobanteService implements EmitirComprobanteUseCase {
    public static final String ACCION_ENVIAR = "ENVIAR";

    private final ComprobanteRepository comprobantes;
    private final SerieRepository series;
    private final TenantRepository tenants;
    private final DocumentStorage storage;
    private final OutboxRepository outbox;
    private final UblGenerator ubl;
    private final XsdValidator xsd;
    private final XmlSigner signer;
    private final EnviarDocumentoUseCase enviar;
    private final UnitOfWork uow;
    private final Clock clock;

    public EmitirComprobanteService(ComprobanteRepository comprobantes, SerieRepository series, TenantRepository tenants,
                                    DocumentStorage storage, OutboxRepository outbox, UblGenerator ubl, XsdValidator xsd,
                                    XmlSigner signer, EnviarDocumentoUseCase enviar, UnitOfWork uow, Clock clock) {
        this.comprobantes = comprobantes; this.series = series; this.tenants = tenants; this.storage = storage;
        this.outbox = outbox; this.ubl = ubl; this.xsd = xsd; this.signer = signer; this.enviar = enviar;
        this.uow = uow; this.clock = clock;
    }

    @Override
    public Comprobante emitirFactura(UUID tenantId, EmitirFacturaCommand cmd) {
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        tenant.exigirListoParaEmitir(LocalDate.now(clock));

        Comprobante c = Comprobante.crearFactura(tenantId, cmd.serie(), cmd.fechaEmision(), cmd.moneda(),
                cmd.tipoOperacion(), cmd.receptor(), cmd.items(), clock);

        Comprobante firmado = uow.ejecutar(() -> {
            long numero;
            if (cmd.correlativo() != null) {
                if (comprobantes.existe(tenantId, TipoDocumento.FACTURA, cmd.serie(), cmd.correlativo()))
                    throw new DomainException("DUPLICADO", "Ya existe " + cmd.serie() + "-" + cmd.correlativo());
                numero = cmd.correlativo();
            } else {
                numero = series.siguienteNumero(tenantId, TipoDocumento.FACTURA, cmd.serie());
            }
            c.asignarNumero(numero, tenant.ruc());

            String xml = ubl.generar(c, tenant);
            xsd.validar(xml, TipoDocumento.FACTURA);
            FirmaResultado firma = signer.firmar(xml, tenant.certificado());

            String key = tenantId + "/" + c.fechaEmision().getYear() + "/" + String.format("%02d", c.fechaEmision().getMonthValue())
                    + "/" + c.nombreArchivo() + ".xml";
            storage.guardar(key, firma.xmlFirmado().getBytes(StandardCharsets.UTF_8));
            c.firmar(firma.hash(), key);
            comprobantes.guardar(c);
            return c;
        });

        if (!cmd.enviarAutomatico()) return firmado;

        Comprobante enviado = enviar.enviar(tenantId, firmado.id());
        if (enviado.estado() == EstadoDocumento.ERROR_ENVIO) {
            uow.ejecutar(() -> outbox.programar(tenantId, ACCION_ENVIAR, enviado.id(), Backoff.siguiente(enviado.intentos(), clock.instant())));
        }
        return enviado;
    }
}
```

- [ ] **Step 4: Ejecutar y ver pasar**

Run: `./gradlew :application:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add application
git commit -m "feat(application): caso de uso EmitirComprobante con numeración, firma y envío híbrido"
```

---

### Task 8: Casos de uso `ConsultarComprobante` y `AdministrarTenant`

**Files:**
- Create: `application/src/main/java/pe/factura/application/port/in/ConsultarComprobanteUseCase.java`, `AdministrarTenantUseCase.java`
- Create: `application/src/main/java/pe/factura/application/service/ConsultarComprobanteService.java`, `AdministrarTenantService.java`, `ApiKeyGenerator.java`
- Test: `application/src/test/java/pe/factura/application/service/AdministrarTenantServiceTest.java`

**Interfaces:**
- Produces:
  ```java
  public interface ConsultarComprobanteUseCase {
      Comprobante obtener(UUID tenantId, UUID id);                       // DomainException("NO_ENCONTRADO")
      List<Comprobante> listar(UUID tenantId, EstadoDocumento estado, int pagina, int porPagina);
      byte[] xml(UUID tenantId, UUID id);                                // bytes del XML firmado
      byte[] cdr(UUID tenantId, UUID id);                                // ZIP del CDR; DomainException("SIN_CDR")
  }
  public interface AdministrarTenantUseCase {
      record TenantCreado(Tenant tenant, String apiKeyEnClaro) {}
      TenantCreado crearTenant(String ruc, String razonSocial, Entorno entorno);
      Tenant obtener(UUID tenantId);
      void cargarCertificado(UUID tenantId, byte[] pkcs12, String clave);   // valida abriendo el KeyStore (RUC en OU y vigencia)
      void cargarCredencialesSol(UUID tenantId, String usuario, String clave);
      void crearSerie(UUID tenantId, TipoDocumento tipo, String codigo, long correlativoInicial);
      List<Serie> listarSeries(UUID tenantId);
      String crearApiKey(UUID tenantId);                                   // devuelve la key en claro una sola vez
  }
  public final class ApiKeyGenerator { public static String generar(); public static String hash(String key, String pepper); public static String prefijo(String key); }
  ```
  Formato de API key: `fk_` + 40 caracteres base64url; `prefijo` = primeros 10 caracteres; `hash` = hex(SHA-256(key + pepper)).
- Consumes: puertos Task 5.

- [ ] **Step 1: Test**

`AdministrarTenantServiceTest.java`:
```java
package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.AdministrarTenantUseCase.TenantCreado;
import pe.factura.application.port.out.ApiKeyRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.ApiKey;
import pe.factura.domain.tenant.Entorno;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class AdministrarTenantServiceTest {
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Series series = new Fakes.Series();
    Map<String, ApiKey> keys = new HashMap<>();
    ApiKeyRepository apiKeys = new ApiKeyRepository() {
        public void guardar(ApiKey k) { keys.put(k.hash(), k); }
        public Optional<ApiKey> buscarPorHash(String h) { return Optional.ofNullable(keys.get(h)); }
    };
    AdministrarTenantService service = new AdministrarTenantService(tenants, series, apiKeys, Fakes.UOW, "pepper", Fakes.CLOCK);

    @Test void crearTenantDevuelveApiKeyUnaVez() {
        TenantCreado r = service.crearTenant("20100066603", "EMPRESA SAC", Entorno.BETA);
        assertThat(r.apiKeyEnClaro()).startsWith("fk_").hasSize(43);
        assertThat(keys).containsKey(ApiKeyGenerator.hash(r.apiKeyEnClaro(), "pepper"));
        assertThat(tenants.buscarPorRuc("20100066603")).isPresent();
    }

    @Test void rucDuplicadoFalla() {
        service.crearTenant("20100066603", "A", Entorno.BETA);
        assertThatThrownBy(() -> service.crearTenant("20100066603", "B", Entorno.BETA)).extracting("codigo").isEqualTo("DUPLICADO");
    }

    @Test void credencialesYSerie() {
        UUID id = service.crearTenant("20100066603", "A", Entorno.BETA).tenant().id();
        service.cargarCredencialesSol(id, "MODDATOS", "moddatos");
        assertThat(tenants.buscar(id).get().sol().usernameToken("20100066603")).isEqualTo("20100066603MODDATOS");
        service.crearSerie(id, TipoDocumento.FACTURA, "F001", 10);
        assertThat(series.siguienteNumero(id, TipoDocumento.FACTURA, "F001")).isEqualTo(11);
        assertThatThrownBy(() -> service.crearSerie(id, TipoDocumento.FACTURA, "B001", 0)).extracting("codigo").isEqualTo("SERIE_INVALIDA");
    }

    @Test void certificadoInvalidoFalla() {
        UUID id = service.crearTenant("20100066603", "A", Entorno.BETA).tenant().id();
        assertThatThrownBy(() -> service.cargarCertificado(id, new byte[]{1, 2, 3}, "x"))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("CERTIFICADO_INVALIDO");
    }
}
```

- [ ] **Step 2: Ejecutar y ver fallar**

Run: `./gradlew :application:test --tests '*AdministrarTenantServiceTest*'`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementación**

`ApiKeyGenerator.java`:
```java
package pe.factura.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class ApiKeyGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private ApiKeyGenerator() {}
    public static String generar() {
        byte[] b = new byte[30]; RANDOM.nextBytes(b);
        return "fk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
    public static String hash(String key, String pepper) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((key + pepper).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String prefijo(String key) { return key.substring(0, Math.min(10, key.length())); }
}
```

`port/in/ConsultarComprobanteUseCase.java` y `port/in/AdministrarTenantUseCase.java`: exactamente las firmas del bloque **Interfaces**.

`service/ConsultarComprobanteService.java`:
```java
package pe.factura.application.service;

import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.out.ComprobanteRepository;
import pe.factura.application.port.out.DocumentStorage;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;

import java.util.List;
import java.util.UUID;

public class ConsultarComprobanteService implements ConsultarComprobanteUseCase {
    private final ComprobanteRepository comprobantes;
    private final DocumentStorage storage;
    public ConsultarComprobanteService(ComprobanteRepository comprobantes, DocumentStorage storage) { this.comprobantes = comprobantes; this.storage = storage; }

    public Comprobante obtener(UUID tenantId, UUID id) {
        return comprobantes.buscar(tenantId, id).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
    }
    public List<Comprobante> listar(UUID tenantId, EstadoDocumento estado, int pagina, int porPagina) { return comprobantes.listar(tenantId, estado, pagina, porPagina); }
    public byte[] xml(UUID tenantId, UUID id) { return storage.leer(obtener(tenantId, id).xmlKey()); }
    public byte[] cdr(UUID tenantId, UUID id) {
        Comprobante c = obtener(tenantId, id);
        if (c.cdrKey() == null) throw new DomainException("SIN_CDR", "El comprobante aún no tiene CDR");
        return storage.leer(c.cdrKey());
    }
}
```

`service/AdministrarTenantService.java`:
```java
package pe.factura.application.service;

import pe.factura.application.port.in.AdministrarTenantUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.*;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public class AdministrarTenantService implements AdministrarTenantUseCase {
    private final TenantRepository tenants;
    private final SerieRepository series;
    private final ApiKeyRepository apiKeys;
    private final UnitOfWork uow;
    private final String pepper;
    private final Clock clock;

    public AdministrarTenantService(TenantRepository tenants, SerieRepository series, ApiKeyRepository apiKeys, UnitOfWork uow, String pepper, Clock clock) {
        this.tenants = tenants; this.series = series; this.apiKeys = apiKeys; this.uow = uow; this.pepper = pepper; this.clock = clock;
    }

    public TenantCreado crearTenant(String ruc, String razonSocial, Entorno entorno) {
        if (tenants.buscarPorRuc(ruc).isPresent()) throw new DomainException("DUPLICADO", "Ya existe un tenant con RUC " + ruc);
        Tenant t = new Tenant(UUID.randomUUID(), ruc, razonSocial, entorno, null, null);
        String key = ApiKeyGenerator.generar();
        uow.ejecutar(() -> {
            tenants.guardar(t);
            apiKeys.guardar(new ApiKey(UUID.randomUUID(), t.id(), ApiKeyGenerator.hash(key, pepper), ApiKeyGenerator.prefijo(key), true));
        });
        return new TenantCreado(t, key);
    }

    public Tenant obtener(UUID tenantId) { return tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado")); }

    public void cargarCertificado(UUID tenantId, byte[] pkcs12, String clave) {
        Tenant t = obtener(tenantId);
        LocalDate vigencia;
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(pkcs12), clave.toCharArray());
            String alias = ks.aliases().nextElement();
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
            if (ks.getKey(alias, clave.toCharArray()) == null) throw new IllegalStateException("sin clave privada");
            String subject = cert.getSubjectX500Principal().getName();
            if (!subject.contains("OU=" + t.ruc())) throw new DomainException("CERTIFICADO_INVALIDO", "El RUC " + t.ruc() + " no figura en el campo OU del certificado");
            vigencia = cert.getNotAfter().toInstant().atZone(ZoneId.of("America/Lima")).toLocalDate();
        } catch (DomainException e) { throw e;
        } catch (Exception e) { throw new DomainException("CERTIFICADO_INVALIDO", "No se pudo abrir el PKCS#12: " + e.getMessage()); }
        if (vigencia.isBefore(LocalDate.now(clock))) throw new DomainException("CERTIFICADO_VENCIDO", "El certificado venció el " + vigencia);
        uow.ejecutar(() -> tenants.guardar(t.conCertificado(new CertificadoDigital(pkcs12, clave, vigencia))));
    }

    public void cargarCredencialesSol(UUID tenantId, String usuario, String clave) {
        Tenant t = obtener(tenantId);
        if (usuario == null || usuario.isBlank() || clave == null || clave.isBlank()) throw new DomainException("CREDENCIALES_INVALIDAS", "Usuario y clave SOL son obligatorios");
        uow.ejecutar(() -> tenants.guardar(t.conCredencialesSol(new CredencialesSol(usuario, clave))));
    }

    public void crearSerie(UUID tenantId, TipoDocumento tipo, String codigo, long correlativoInicial) {
        obtener(tenantId);
        if (!tipo.serieValida(codigo)) throw new DomainException("SERIE_INVALIDA", "Serie " + codigo + " no válida para " + tipo);
        uow.ejecutar(() -> series.crear(new Serie(tenantId, tipo, codigo, correlativoInicial, true)));
    }

    public List<Serie> listarSeries(UUID tenantId) { return series.listar(tenantId); }

    public String crearApiKey(UUID tenantId) {
        obtener(tenantId);
        String key = ApiKeyGenerator.generar();
        uow.ejecutar(() -> apiKeys.guardar(new ApiKey(UUID.randomUUID(), tenantId, ApiKeyGenerator.hash(key, pepper), ApiKeyGenerator.prefijo(key), true)));
        return key;
    }
}
```

- [ ] **Step 4: Ejecutar y ver pasar**

Run: `./gradlew :application:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add application
git commit -m "feat(application): consulta de comprobantes y administración de tenant, series y API keys"
```
