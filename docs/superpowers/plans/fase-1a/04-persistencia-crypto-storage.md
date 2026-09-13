# 04 · Persistencia, cifrado y storage

Índice del plan: [README](README.md). Módulos `adapters/out-crypto`, `adapters/out-storage`, `adapters/out-persistence`.

### Task 9: `AesGcmSecretCipher`

**Files:**
- Create: `adapters/out-crypto/src/main/java/pe/factura/adapters/crypto/AesGcmSecretCipher.java`
- Test: `adapters/out-crypto/src/test/java/pe/factura/adapters/crypto/AesGcmSecretCipherTest.java`

**Interfaces:**
- Produces: `new AesGcmSecretCipher(String masterKeyBase64)` implementa `SecretCipher`. Formato del cifrado: `[12 bytes IV][ciphertext+tag]`.

- [ ] **Step 1: Test**

```java
package pe.factura.adapters.crypto;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class AesGcmSecretCipherTest {
    String key = Base64.getEncoder().encodeToString(new byte[32]);

    @Test void cifraYDescifra() {
        AesGcmSecretCipher c = new AesGcmSecretCipher(key);
        byte[] cifrado = c.cifrar("moddatos".getBytes());
        assertThat(cifrado).hasSizeGreaterThan(12 + 8);
        assertThat(new String(c.descifrar(cifrado))).isEqualTo("moddatos");
    }
    @Test void ivAleatorioProduceCifradosDistintos() {
        AesGcmSecretCipher c = new AesGcmSecretCipher(key);
        assertThat(c.cifrar("x".getBytes())).isNotEqualTo(c.cifrar("x".getBytes()));
    }
    @Test void claveIncorrectaFalla() {
        byte[] otra = new byte[32]; otra[0] = 1;
        byte[] cifrado = new AesGcmSecretCipher(key).cifrar("x".getBytes());
        assertThatThrownBy(() -> new AesGcmSecretCipher(Base64.getEncoder().encodeToString(otra)).descifrar(cifrado))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void claveDebeTener32Bytes() {
        assertThatThrownBy(() -> new AesGcmSecretCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Ver fallar** — `./gradlew :adapters:out-crypto:test` → FAIL de compilación.

- [ ] **Step 3: Implementación**

```java
package pe.factura.adapters.crypto;

import pe.factura.application.port.out.SecretCipher;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class AesGcmSecretCipher implements SecretCipher {
    private static final int IV = 12, TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public AesGcmSecretCipher(String masterKeyBase64) {
        byte[] k = Base64.getDecoder().decode(masterKeyBase64);
        if (k.length != 32) throw new IllegalArgumentException("MASTER_KEY debe ser 32 bytes en base64");
        this.key = new SecretKeySpec(k, "AES");
    }

    @Override public byte[] cifrar(byte[] plano) {
        try {
            byte[] iv = new byte[IV]; RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plano);
            byte[] out = new byte[IV + ct.length];
            System.arraycopy(iv, 0, out, 0, IV); System.arraycopy(ct, 0, out, IV, ct.length);
            return out;
        } catch (Exception e) { throw new IllegalStateException("Error cifrando", e); }
    }

    @Override public byte[] descifrar(byte[] cifrado) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(cifrado, 0, IV)));
            return c.doFinal(Arrays.copyOfRange(cifrado, IV, cifrado.length));
        } catch (Exception e) { throw new IllegalStateException("Error descifrando (clave incorrecta o datos alterados)", e); }
    }
}
```

- [ ] **Step 4: Ver pasar** — `./gradlew :adapters:out-crypto:test` → PASS.
- [ ] **Step 5: Commit** — `git add adapters/out-crypto && git commit -m "feat(crypto): cifrado AES-256-GCM de secretos"`

---

### Task 10: `FileSystemDocumentStorage`

**Files:**
- Create: `adapters/out-storage/src/main/java/pe/factura/adapters/storage/FileSystemDocumentStorage.java`
- Test: `adapters/out-storage/src/test/java/pe/factura/adapters/storage/FileSystemDocumentStorageTest.java`

**Interfaces:**
- Produces: `new FileSystemDocumentStorage(Path raiz)` implementa `DocumentStorage`. Rechaza claves con `..` o absolutas.

- [ ] **Step 1: Test**

```java
package pe.factura.adapters.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class FileSystemDocumentStorageTest {
    @TempDir Path dir;

    @Test void guardaYLeeCreandoDirectorios() {
        var s = new FileSystemDocumentStorage(dir);
        s.guardar("t1/2026/09/20100066603-01-F001-1.xml", "<x/>".getBytes());
        assertThat(Files.exists(dir.resolve("t1/2026/09/20100066603-01-F001-1.xml"))).isTrue();
        assertThat(new String(s.leer("t1/2026/09/20100066603-01-F001-1.xml"))).isEqualTo("<x/>");
    }
    @Test void leerInexistenteLanza() {
        assertThatThrownBy(() -> new FileSystemDocumentStorage(dir).leer("no/existe")).isInstanceOf(IllegalStateException.class);
    }
    @Test void rechazaPathTraversal() {
        var s = new FileSystemDocumentStorage(dir);
        assertThatThrownBy(() -> s.guardar("../fuera.xml", new byte[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.guardar("/etc/passwd", new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Ver fallar** — `./gradlew :adapters:out-storage:test` → FAIL de compilación.

- [ ] **Step 3: Implementación**

```java
package pe.factura.adapters.storage;

import pe.factura.application.port.out.DocumentStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileSystemDocumentStorage implements DocumentStorage {
    private final Path raiz;
    public FileSystemDocumentStorage(Path raiz) { this.raiz = raiz.toAbsolutePath().normalize(); }

    @Override public void guardar(String key, byte[] contenido) {
        Path p = resolver(key);
        try { Files.createDirectories(p.getParent()); Files.write(p, contenido); }
        catch (IOException e) { throw new IllegalStateException("No se pudo guardar " + key, e); }
    }
    @Override public byte[] leer(String key) {
        Path p = resolver(key);
        if (!Files.exists(p)) throw new IllegalStateException("No existe " + key);
        try { return Files.readAllBytes(p); } catch (IOException e) { throw new IllegalStateException("No se pudo leer " + key, e); }
    }
    private Path resolver(String key) {
        if (key == null || key.startsWith("/") || key.contains("..")) throw new IllegalArgumentException("Clave inválida: " + key);
        Path p = raiz.resolve(key).normalize();
        if (!p.startsWith(raiz)) throw new IllegalArgumentException("Clave fuera de la raíz: " + key);
        return p;
    }
}
```

- [ ] **Step 4: Ver pasar** — `./gradlew :adapters:out-storage:test` → PASS.
- [ ] **Step 5: Commit** — `git add adapters/out-storage && git commit -m "feat(storage): almacenamiento de documentos en disco"`

---

### Task 11: Esquema Flyway y repositorios JDBC

**Files:**
- Create: `adapters/out-persistence/src/main/resources/db/migration/V1__esquema_inicial.sql`
- Create en `adapters/out-persistence/src/main/java/pe/factura/adapters/persistence/`: `JdbcUnitOfWork.java`, `JdbcTenantRepository.java`, `JdbcApiKeyRepository.java`, `JdbcSerieRepository.java`, `JdbcComprobanteRepository.java`, `JdbcOutboxRepository.java`
- Test: `adapters/out-persistence/src/test/java/pe/factura/adapters/persistence/PersistenciaTestBase.java`, `JdbcSerieRepositoryTest.java`, `JdbcComprobanteRepositoryTest.java`, `JdbcTenantRepositoryTest.java`, `JdbcOutboxRepositoryTest.java`

**Interfaces:**
- Consumes: puertos (Task 5), `SecretCipher` (Task 9), `Comprobante.rehidratar` (Task 4).
- Produces: constructores `new JdbcXRepository(JdbcTemplate)`; `JdbcTenantRepository(JdbcTemplate, SecretCipher)`; `JdbcUnitOfWork(PlatformTransactionManager)`. Requiere Docker para Testcontainers.

- [ ] **Step 1: Migración**

`V1__esquema_inicial.sql`:
```sql
CREATE TABLE tenant (
    id                  UUID PRIMARY KEY,
    ruc                 VARCHAR(11) NOT NULL UNIQUE,
    razon_social        VARCHAR(250) NOT NULL,
    entorno             VARCHAR(12) NOT NULL,
    sol_usuario_enc     BYTEA,
    sol_clave_enc       BYTEA,
    cert_pkcs12_enc     BYTEA,
    cert_clave_enc      BYTEA,
    cert_vigencia_hasta DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE api_key (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL REFERENCES tenant(id),
    key_hash   VARCHAR(64) NOT NULL UNIQUE,
    prefijo    VARCHAR(10) NOT NULL,
    activa     BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);

CREATE TABLE serie (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenant(id),
    tipo          VARCHAR(2) NOT NULL,
    codigo        VARCHAR(4) NOT NULL,
    ultimo_numero BIGINT NOT NULL DEFAULT 0,
    activa        BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (tenant_id, tipo, codigo)
);

CREATE TABLE documento (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL REFERENCES tenant(id),
    tipo             VARCHAR(2) NOT NULL,
    serie            VARCHAR(4) NOT NULL,
    numero           BIGINT NOT NULL,
    fecha_emision    DATE NOT NULL,
    estado           VARCHAR(25) NOT NULL,
    hash             VARCHAR(100),
    nombre_archivo   VARCHAR(40) NOT NULL,
    ticket           VARCHAR(40),
    intentos         INT NOT NULL DEFAULT 0,
    ultimo_error     TEXT,
    cdr_codigo       VARCHAR(4),
    cdr_descripcion  TEXT,
    cdr_observaciones JSONB,
    xml_key          VARCHAR(200),
    cdr_key          VARCHAR(200),
    pdf_key          VARCHAR(200),
    idempotency_key  VARCHAR(100),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, tipo, serie, numero)
);
CREATE INDEX ix_documento_tenant_estado ON documento (tenant_id, estado);
CREATE INDEX ix_documento_tenant_fecha  ON documento (tenant_id, fecha_emision);
CREATE UNIQUE INDEX ux_documento_idempotency ON documento (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE comprobante (
    documento_id       UUID PRIMARY KEY REFERENCES documento(id),
    tipo_operacion     VARCHAR(4) NOT NULL,
    moneda             VARCHAR(3) NOT NULL,
    receptor_tipo_doc  VARCHAR(1) NOT NULL,
    receptor_num_doc   VARCHAR(15) NOT NULL,
    receptor_nombre    VARCHAR(250) NOT NULL,
    receptor_direccion VARCHAR(250),
    total_gravado      NUMERIC(14,2) NOT NULL,
    total_exonerado    NUMERIC(14,2) NOT NULL,
    total_inafecto     NUMERIC(14,2) NOT NULL,
    total_igv          NUMERIC(14,2) NOT NULL,
    total              NUMERIC(14,2) NOT NULL
);

CREATE TABLE comprobante_item (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    comprobante_id       UUID NOT NULL REFERENCES comprobante(documento_id),
    orden                INT NOT NULL,
    codigo               VARCHAR(30),
    descripcion          TEXT NOT NULL,
    unidad               VARCHAR(3) NOT NULL,
    cantidad             NUMERIC(14,4) NOT NULL,
    precio_unitario      NUMERIC(14,4) NOT NULL,
    tipo_afectacion_igv  VARCHAR(2) NOT NULL
);

CREATE TABLE outbox (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    agregado          VARCHAR(12) NOT NULL DEFAULT 'DOCUMENTO',
    agregado_id       UUID NOT NULL,
    accion            VARCHAR(20) NOT NULL,
    intentos          INT NOT NULL DEFAULT 0,
    siguiente_intento TIMESTAMPTZ NOT NULL,
    locked_until      TIMESTAMPTZ,
    ultimo_error      TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_outbox_pendientes ON outbox (siguiente_intento) WHERE locked_until IS NULL;

CREATE TABLE evento_documento (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    documento_id    UUID NOT NULL,
    estado_anterior VARCHAR(25),
    estado_nuevo    VARCHAR(25) NOT NULL,
    detalle         TEXT,
    ocurrido_en     TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

- [ ] **Step 2: Base de tests con Testcontainers**

`PersistenciaTestBase.java`:
```java
package pe.factura.adapters.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;

@Testcontainers
abstract class PersistenciaTestBase {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static DataSource ds; static JdbcTemplate jdbc; static JdbcUnitOfWork uow;

    @BeforeAll static void migrar() {
        DriverManagerDataSource d = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        ds = d; jdbc = new JdbcTemplate(d); uow = new JdbcUnitOfWork(new DataSourceTransactionManager(d));
        Flyway.configure().dataSource(d).locations("classpath:db/migration").load().migrate();
    }

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant CASCADE");
    }

    static UUID tenantDePrueba() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno) VALUES (?, ?, 'EMPRESA SAC', 'BETA')", id, "20" + String.format("%09d", Math.abs(id.hashCode()) % 1_000_000_000));
        return id;
    }
}
```

- [ ] **Step 3: Tests de series y comprobantes**

`JdbcSerieRepositoryTest.java`:
```java
package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Serie;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

class JdbcSerieRepositoryTest extends PersistenciaTestBase {
    JdbcSerieRepository repo = new JdbcSerieRepository(jdbc);

    @Test void siguienteNumeroEsCorrelativo() {
        UUID t = tenantDePrueba();
        repo.crear(new Serie(t, TipoDocumento.FACTURA, "F001", 10, true));
        assertThat(uow.ejecutar(() -> repo.siguienteNumero(t, TipoDocumento.FACTURA, "F001"))).isEqualTo(11);
        assertThat(uow.ejecutar(() -> repo.siguienteNumero(t, TipoDocumento.FACTURA, "F001"))).isEqualTo(12);
    }

    @Test void serieNoConfigurada() {
        UUID t = tenantDePrueba();
        assertThatThrownBy(() -> uow.ejecutar(() -> repo.siguienteNumero(t, TipoDocumento.FACTURA, "F009")))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("SERIE_NO_CONFIGURADA");
    }

    @Test void concurrenciaNoDuplicaNumeros() throws Exception {
        UUID t = tenantDePrueba();
        repo.crear(new Serie(t, TipoDocumento.FACTURA, "F001", 0, true));
        ExecutorService ex = Executors.newFixedThreadPool(8);
        List<Future<Long>> futuros = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) futuros.add(ex.submit(() -> uow.ejecutar(() -> repo.siguienteNumero(t, TipoDocumento.FACTURA, "F001"))));
        java.util.Set<Long> numeros = new java.util.HashSet<>();
        for (Future<Long> f : futuros) numeros.add(f.get());
        ex.shutdown();
        assertThat(numeros).hasSize(40).contains(1L, 40L);
    }
}
```

`JdbcComprobanteRepositoryTest.java`:
```java
package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JdbcComprobanteRepositoryTest extends PersistenciaTestBase {
    JdbcComprobanteRepository repo = new JdbcComprobanteRepository(jdbc);
    Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    private Comprobante factura(UUID t, long numero) {
        Comprobante c = Comprobante.crearFactura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)), clock);
        c.asignarNumero(numero, "20100066603");
        return c;
    }

    @Test void guardaYRehidrataCompleto() {
        UUID t = tenantDePrueba();
        Comprobante c = factura(t, 1);
        c.firmar("HASH==", t + "/2026/09/20100066603-01-F001-1.xml");
        repo.guardar(c);
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "aceptada", List.of("4252 - obs")), "k/cdr.zip");
        repo.guardar(c);

        Comprobante r = repo.buscar(t, c.id()).orElseThrow();
        assertThat(r.estado()).isEqualTo(EstadoDocumento.ACEPTADO_CON_OBS);
        assertThat(r.numero()).isEqualTo(1L);
        assertThat(r.hash()).isEqualTo("HASH==");
        assertThat(r.items()).hasSize(2);
        assertThat(r.items().get(1).afectacion()).isEqualTo(TipoAfectacionIgv.EXONERADO);
        assertThat(r.totales().total()).isEqualByComparingTo("286.00");
        assertThat(r.cdr().observaciones()).containsExactly("4252 - obs");
        assertThat(r.cdrKey()).isEqualTo("k/cdr.zip");
        assertThat(r.receptor().razonSocial()).isEqualTo("CLIENTE SAC");
    }

    @Test void existeYUnicidad() {
        UUID t = tenantDePrueba();
        Comprobante c = factura(t, 5); c.firmar("h", "k"); repo.guardar(c);
        assertThat(repo.existe(t, TipoDocumento.FACTURA, "F001", 5)).isTrue();
        assertThat(repo.existe(t, TipoDocumento.FACTURA, "F001", 6)).isFalse();
        Comprobante dup = factura(t, 5); dup.firmar("h", "k");
        assertThatThrownBy(() -> repo.guardar(dup)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test void aislamientoPorTenant() {
        UUID t1 = tenantDePrueba(), t2 = tenantDePrueba();
        Comprobante c = factura(t1, 1); c.firmar("h", "k"); repo.guardar(c);
        assertThat(repo.buscar(t2, c.id())).isEmpty();
        assertThat(repo.listar(t1, null, 1, 10)).hasSize(1);
        assertThat(repo.listar(t2, null, 1, 10)).isEmpty();
    }
}
```

- [ ] **Step 4: Tests de tenant y outbox**

`JdbcTenantRepositoryTest.java`:
```java
package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SecretCipher;
import pe.factura.domain.tenant.*;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcTenantRepositoryTest extends PersistenciaTestBase {
    SecretCipher cipher = new SecretCipher() {   // reversible y detectable en la BD
        public byte[] cifrar(byte[] p) { byte[] r = p.clone(); for (int i = 0; i < r.length; i++) r[i] ^= 0x5A; return r; }
        public byte[] descifrar(byte[] c) { return cifrar(c); }
    };
    JdbcTenantRepository repo = new JdbcTenantRepository(jdbc, cipher);

    @Test void guardaSecretosCifradosYLosRecupera() {
        Tenant t = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA SAC", Entorno.BETA,
                new CredencialesSol("MODDATOS", "moddatos"), new CertificadoDigital(new byte[]{1, 2, 3}, "clave", LocalDate.of(2030, 1, 1)));
        repo.guardar(t);
        byte[] enBd = jdbc.queryForObject("SELECT sol_clave_enc FROM tenant WHERE id = ?", byte[].class, t.id());
        assertThat(new String(enBd)).isNotEqualTo("moddatos");
        Tenant r = repo.buscar(t.id()).orElseThrow();
        assertThat(r.sol().clave()).isEqualTo("moddatos");
        assertThat(r.certificado().pkcs12()).containsExactly(1, 2, 3);
        assertThat(r.certificado().vigenciaHasta()).isEqualTo(LocalDate.of(2030, 1, 1));
        assertThat(repo.buscarPorRuc("20100066603")).isPresent();
    }

    @Test void actualizaYPermiteNulos() {
        Tenant t = new Tenant(UUID.randomUUID(), "20100066603", "A", Entorno.BETA, null, null);
        repo.guardar(t);
        repo.guardar(t.conCredencialesSol(new CredencialesSol("U", "C")));
        Tenant r = repo.buscar(t.id()).orElseThrow();
        assertThat(r.sol().usuario()).isEqualTo("U");
        assertThat(r.certificado()).isNull();
    }
}
```

`JdbcOutboxRepositoryTest.java`:
```java
package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.OutboxItem;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcOutboxRepositoryTest extends PersistenciaTestBase {
    JdbcOutboxRepository repo = new JdbcOutboxRepository(jdbc);

    @Test void tomaSoloVencidasYLasBloquea() {
        UUID t = tenantDePrueba(), doc = UUID.randomUUID();
        repo.programar(t, "ENVIAR", doc, Instant.now().minusSeconds(5));
        repo.programar(t, "ENVIAR", UUID.randomUUID(), Instant.now().plusSeconds(3600));
        List<OutboxItem> tomadas = uow.ejecutar(() -> repo.tomarVencidas(10, Duration.ofMinutes(2)));
        assertThat(tomadas).hasSize(1);
        assertThat(tomadas.get(0).agregadoId()).isEqualTo(doc);
        assertThat(uow.ejecutar(() -> repo.tomarVencidas(10, Duration.ofMinutes(2)))).isEmpty();   // bloqueada
    }

    @Test void reprogramarYCompletar() {
        UUID t = tenantDePrueba();
        repo.programar(t, "ENVIAR", UUID.randomUUID(), Instant.now().minusSeconds(5));
        OutboxItem item = uow.ejecutar(() -> repo.tomarVencidas(10, Duration.ofMinutes(2))).get(0);
        repo.reprogramar(item.id(), Instant.now().minusSeconds(1), "timeout");
        OutboxItem otraVez = uow.ejecutar(() -> repo.tomarVencidas(10, Duration.ofMinutes(2))).get(0);
        assertThat(otraVez.intentos()).isEqualTo(1);
        repo.completar(otraVez.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox", Integer.class)).isZero();
    }
}
```

- [ ] **Step 5: Ver fallar** — `./gradlew :adapters:out-persistence:test` → FAIL de compilación.

- [ ] **Step 6: Implementación**

`JdbcUnitOfWork.java`:
```java
package pe.factura.adapters.persistence;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.factura.application.port.out.UnitOfWork;
import java.util.function.Supplier;

public class JdbcUnitOfWork implements UnitOfWork {
    private final TransactionTemplate tx;
    public JdbcUnitOfWork(PlatformTransactionManager tm) { this.tx = new TransactionTemplate(tm); }
    @Override public <T> T ejecutar(Supplier<T> trabajo) { return tx.execute(s -> trabajo.get()); }
    @Override public void ejecutar(Runnable trabajo) { tx.executeWithoutResult(s -> trabajo.run()); }
}
```

`JdbcSerieRepository.java`:
```java
package pe.factura.adapters.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.SerieRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Serie;

import java.util.List;
import java.util.UUID;

public class JdbcSerieRepository implements SerieRepository {
    private final JdbcTemplate jdbc;
    public JdbcSerieRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public long siguienteNumero(UUID tenantId, TipoDocumento tipo, String serie) {
        List<Long> actual = jdbc.queryForList(
                "SELECT ultimo_numero FROM serie WHERE tenant_id = ? AND tipo = ? AND codigo = ? AND activa FOR UPDATE",
                Long.class, tenantId, tipo.codigo(), serie);
        if (actual.isEmpty()) throw new DomainException("SERIE_NO_CONFIGURADA", "Serie no configurada o inactiva: " + serie);
        long siguiente = actual.get(0) + 1;
        jdbc.update("UPDATE serie SET ultimo_numero = ? WHERE tenant_id = ? AND tipo = ? AND codigo = ?", siguiente, tenantId, tipo.codigo(), serie);
        return siguiente;
    }
    @Override public void crear(Serie s) {
        jdbc.update("INSERT INTO serie (tenant_id, tipo, codigo, ultimo_numero, activa) VALUES (?, ?, ?, ?, ?)",
                s.tenantId(), s.tipo().codigo(), s.codigo(), s.ultimoNumero(), s.activa());
    }
    @Override public List<Serie> listar(UUID tenantId) {
        return jdbc.query("SELECT tenant_id, tipo, codigo, ultimo_numero, activa FROM serie WHERE tenant_id = ? ORDER BY tipo, codigo",
                (rs, i) -> new Serie(rs.getObject("tenant_id", UUID.class), TipoDocumento.porCodigo(rs.getString("tipo")),
                        rs.getString("codigo"), rs.getLong("ultimo_numero"), rs.getBoolean("activa")), tenantId);
    }
}
```

`JdbcTenantRepository.java`:
```java
package pe.factura.adapters.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.SecretCipher;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.tenant.*;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class JdbcTenantRepository implements TenantRepository {
    private static final String COLS = "id, ruc, razon_social, entorno, sol_usuario_enc, sol_clave_enc, cert_pkcs12_enc, cert_clave_enc, cert_vigencia_hasta";
    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;
    public JdbcTenantRepository(JdbcTemplate jdbc, SecretCipher cipher) { this.jdbc = jdbc; this.cipher = cipher; }

    @Override public void guardar(Tenant t) {
        byte[] su = t.sol() == null ? null : cipher.cifrar(t.sol().usuario().getBytes(StandardCharsets.UTF_8));
        byte[] sc = t.sol() == null ? null : cipher.cifrar(t.sol().clave().getBytes(StandardCharsets.UTF_8));
        byte[] cp = t.certificado() == null ? null : cipher.cifrar(t.certificado().pkcs12());
        byte[] cc = t.certificado() == null ? null : cipher.cifrar(t.certificado().clave().getBytes(StandardCharsets.UTF_8));
        Date cv = t.certificado() == null || t.certificado().vigenciaHasta() == null ? null : Date.valueOf(t.certificado().vigenciaHasta());
        jdbc.update("""
            INSERT INTO tenant (id, ruc, razon_social, entorno, sol_usuario_enc, sol_clave_enc, cert_pkcs12_enc, cert_clave_enc, cert_vigencia_hasta)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET razon_social = EXCLUDED.razon_social, entorno = EXCLUDED.entorno,
              sol_usuario_enc = EXCLUDED.sol_usuario_enc, sol_clave_enc = EXCLUDED.sol_clave_enc,
              cert_pkcs12_enc = EXCLUDED.cert_pkcs12_enc, cert_clave_enc = EXCLUDED.cert_clave_enc,
              cert_vigencia_hasta = EXCLUDED.cert_vigencia_hasta, updated_at = now()
            """, t.id(), t.ruc(), t.razonSocial(), t.entorno().name(), su, sc, cp, cc, cv);
    }
    @Override public Optional<Tenant> buscar(UUID id) {
        return jdbc.query("SELECT " + COLS + " FROM tenant WHERE id = ?", this::mapear, id).stream().findFirst();
    }
    @Override public Optional<Tenant> buscarPorRuc(String ruc) {
        return jdbc.query("SELECT " + COLS + " FROM tenant WHERE ruc = ?", this::mapear, ruc).stream().findFirst();
    }
    private Tenant mapear(ResultSet rs, int i) throws SQLException {
        CredencialesSol sol = rs.getBytes("sol_usuario_enc") == null ? null
                : new CredencialesSol(txt(rs.getBytes("sol_usuario_enc")), txt(rs.getBytes("sol_clave_enc")));
        Date cv = rs.getDate("cert_vigencia_hasta");
        CertificadoDigital cert = rs.getBytes("cert_pkcs12_enc") == null ? null
                : new CertificadoDigital(cipher.descifrar(rs.getBytes("cert_pkcs12_enc")), txt(rs.getBytes("cert_clave_enc")), cv == null ? null : cv.toLocalDate());
        return new Tenant(rs.getObject("id", UUID.class), rs.getString("ruc"), rs.getString("razon_social"),
                Entorno.valueOf(rs.getString("entorno")), sol, cert);
    }
    private String txt(byte[] enc) { return new String(cipher.descifrar(enc), StandardCharsets.UTF_8); }
}
```

`JdbcApiKeyRepository.java`:
```java
package pe.factura.adapters.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.ApiKeyRepository;
import pe.factura.domain.tenant.ApiKey;
import java.util.Optional;
import java.util.UUID;

public class JdbcApiKeyRepository implements ApiKeyRepository {
    private final JdbcTemplate jdbc;
    public JdbcApiKeyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void guardar(ApiKey k) {
        jdbc.update("INSERT INTO api_key (id, tenant_id, key_hash, prefijo, activa) VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET activa = EXCLUDED.activa, revoked_at = CASE WHEN EXCLUDED.activa THEN NULL ELSE now() END",
                k.id(), k.tenantId(), k.hash(), k.prefijo(), k.activa());
    }
    @Override public Optional<ApiKey> buscarPorHash(String hash) {
        return jdbc.query("SELECT id, tenant_id, key_hash, prefijo, activa FROM api_key WHERE key_hash = ?",
                (rs, i) -> new ApiKey(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("key_hash"), rs.getString("prefijo"), rs.getBoolean("activa")), hash)
                .stream().findFirst();
    }
}
```

`JdbcComprobanteRepository.java`:
```java
package pe.factura.adapters.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.ComprobanteRepository;
import pe.factura.domain.documento.*;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class JdbcComprobanteRepository implements ComprobanteRepository {
    private final JdbcTemplate jdbc;
    public JdbcComprobanteRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void guardar(Comprobante c) {
        int filas = jdbc.update("""
            UPDATE documento SET estado = ?, hash = ?, ticket = NULL, intentos = ?, ultimo_error = ?, cdr_codigo = ?, cdr_descripcion = ?,
              cdr_observaciones = ?::jsonb, xml_key = ?, cdr_key = ?, updated_at = now() WHERE id = ? AND tenant_id = ?
            """, c.estado().name(), c.hash(), c.intentos(), c.ultimoError(),
                c.cdr() == null ? null : c.cdr().codigo(), c.cdr() == null ? null : c.cdr().descripcion(),
                c.cdr() == null ? null : aJson(c.cdr().observaciones()), c.xmlKey(), c.cdrKey(), c.id(), c.tenantId());
        if (filas > 0) return;
        jdbc.update("""
            INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, hash, nombre_archivo, intentos, ultimo_error, xml_key, cdr_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, c.id(), c.tenantId(), c.tipo().codigo(), c.serie(), c.numero(), Date.valueOf(c.fechaEmision()), c.estado().name(),
                c.hash(), c.nombreArchivo(), c.intentos(), c.ultimoError(), c.xmlKey(), c.cdrKey());
        Totales t = c.totales();
        jdbc.update("""
            INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre, receptor_direccion,
              total_gravado, total_exonerado, total_inafecto, total_igv, total) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, c.id(), c.tipoOperacion(), c.moneda(), c.receptor().tipoDoc(), c.receptor().numDoc(), c.receptor().razonSocial(),
                c.receptor().direccion(), t.gravado(), t.exonerado(), t.inafecto(), t.igv(), t.total());
        int orden = 1;
        for (Item i : c.items()) {
            jdbc.update("INSERT INTO comprobante_item (comprobante_id, orden, codigo, descripcion, unidad, cantidad, precio_unitario, tipo_afectacion_igv) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    c.id(), orden++, i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.precioUnitario(), i.afectacion().codigo());
        }
    }

    @Override public Optional<Comprobante> buscar(UUID tenantId, UUID id) {
        return jdbc.query(SELECT + " WHERE d.id = ? AND d.tenant_id = ?", this::mapear, id, tenantId).stream().findFirst();
    }
    @Override public boolean existe(UUID tenantId, TipoDocumento tipo, String serie, long numero) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM documento WHERE tenant_id = ? AND tipo = ? AND serie = ? AND numero = ?", Integer.class, tenantId, tipo.codigo(), serie, numero);
        return n != null && n > 0;
    }
    @Override public List<Comprobante> listar(UUID tenantId, EstadoDocumento estado, int pagina, int porPagina) {
        String sql = SELECT + " WHERE d.tenant_id = ?" + (estado == null ? "" : " AND d.estado = ?") + " ORDER BY d.created_at DESC LIMIT ? OFFSET ?";
        Object[] args = estado == null ? new Object[]{tenantId, porPagina, (pagina - 1) * porPagina} : new Object[]{tenantId, estado.name(), porPagina, (pagina - 1) * porPagina};
        return jdbc.query(sql, this::mapear, args);
    }

    private static final String SELECT = """
        SELECT d.id, d.tenant_id, d.tipo, d.serie, d.numero, d.fecha_emision, d.estado, d.hash, d.nombre_archivo, d.intentos, d.ultimo_error,
               d.cdr_codigo, d.cdr_descripcion, d.cdr_observaciones::text AS cdr_obs, d.xml_key, d.cdr_key,
               c.tipo_operacion, c.moneda, c.receptor_tipo_doc, c.receptor_num_doc, c.receptor_nombre, c.receptor_direccion
        FROM documento d JOIN comprobante c ON c.documento_id = d.id
        """;

    private Comprobante mapear(ResultSet rs, int i) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<Item> items = jdbc.query("SELECT codigo, descripcion, unidad, cantidad, precio_unitario, tipo_afectacion_igv FROM comprobante_item WHERE comprobante_id = ? ORDER BY orden",
                (r, k) -> new Item(r.getString("codigo"), r.getString("descripcion"), r.getString("unidad"), r.getBigDecimal("cantidad"),
                        r.getBigDecimal("precio_unitario"), TipoAfectacionIgv.porCodigo(r.getString("tipo_afectacion_igv"))), id);
        Cdr cdr = rs.getString("cdr_codigo") == null ? null : new Cdr(rs.getString("cdr_codigo"), rs.getString("cdr_descripcion"), deJson(rs.getString("cdr_obs")));
        return Comprobante.rehidratar(id, rs.getObject("tenant_id", UUID.class), TipoDocumento.porCodigo(rs.getString("tipo")), rs.getString("serie"),
                rs.getLong("numero"), rs.getDate("fecha_emision").toLocalDate(), rs.getString("moneda"), rs.getString("tipo_operacion"),
                new Receptor(rs.getString("receptor_tipo_doc"), rs.getString("receptor_num_doc"), rs.getString("receptor_nombre"), rs.getString("receptor_direccion")),
                items, EstadoDocumento.valueOf(rs.getString("estado")), rs.getString("hash"), rs.getString("nombre_archivo"),
                rs.getString("xml_key"), rs.getString("cdr_key"), cdr, rs.getInt("intentos"), rs.getString("ultimo_error"));
    }

    /** JSON mínimo para una lista de strings (sin dependencia de Jackson en este módulo). */
    static String aJson(List<String> l) {
        StringBuilder sb = new StringBuilder("[");
        for (int k = 0; k < l.size(); k++) { if (k > 0) sb.append(','); sb.append('"').append(l.get(k).replace("\\", "\\\\").replace("\"", "\\\"")).append('"'); }
        return sb.append(']').toString();
    }
    static List<String> deJson(String json) {
        if (json == null || json.length() <= 2) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : json.substring(1, json.length() - 1).split("\",\"")) out.add(s.replaceAll("^\"|\"$", "").replace("\\\"", "\"").replace("\\\\", "\\"));
        return out;
    }
}
```

`JdbcOutboxRepository.java`:
```java
package pe.factura.adapters.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.OutboxItem;
import pe.factura.application.port.out.OutboxRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class JdbcOutboxRepository implements OutboxRepository {
    private final JdbcTemplate jdbc;
    public JdbcOutboxRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void programar(UUID tenantId, String accion, UUID agregadoId, Instant cuando) {
        jdbc.update("INSERT INTO outbox (tenant_id, agregado_id, accion, siguiente_intento) VALUES (?, ?, ?, ?)", tenantId, agregadoId, accion, Timestamp.from(cuando));
    }
    /** Debe ejecutarse dentro de una transacción (UnitOfWork) para que FOR UPDATE SKIP LOCKED tenga efecto. */
    @Override public List<OutboxItem> tomarVencidas(int limite, Duration lock) {
        List<OutboxItem> items = jdbc.query("""
            SELECT id, tenant_id, agregado_id, accion, intentos FROM outbox
            WHERE siguiente_intento <= now() AND (locked_until IS NULL OR locked_until < now())
            ORDER BY siguiente_intento FOR UPDATE SKIP LOCKED LIMIT ?
            """, (rs, i) -> new OutboxItem(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("agregado_id", UUID.class), rs.getString("accion"), rs.getInt("intentos")), limite);
        for (OutboxItem it : items)
            jdbc.update("UPDATE outbox SET locked_until = ? WHERE id = ?", Timestamp.from(Instant.now().plus(lock)), it.id());
        return items;
    }
    @Override public void reprogramar(UUID id, Instant cuando, String error) {
        jdbc.update("UPDATE outbox SET intentos = intentos + 1, siguiente_intento = ?, locked_until = NULL, ultimo_error = ? WHERE id = ?", Timestamp.from(cuando), error, id);
    }
    @Override public void completar(UUID id) { jdbc.update("DELETE FROM outbox WHERE id = ?", id); }
}
```

- [ ] **Step 7: Ver pasar** — `./gradlew :adapters:out-persistence:test` → PASS (requiere Docker en marcha).

- [ ] **Step 8: Commit**

```bash
git add adapters/out-persistence
git commit -m "feat(persistence): esquema Flyway inicial y repositorios JDBC con bloqueo de serie y outbox"
```
