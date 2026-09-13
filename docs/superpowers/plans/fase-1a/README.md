# Fase 1A — Núcleo Factura: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Emitir una factura electrónica (tipo 01) de extremo a extremo: JSON → validación → numeración → UBL 2.1 → XSD → firma XML-DSig → `sendBill` a SUNAT → CDR → estado, con reintentos por outbox y descarga de XML/CDR, sobre una base hexagonal multi-tenant.

**Architecture:** Monolito modular Gradle con módulos `domain` (puro), `application` (casos de uso + puertos) y adaptadores `in-rest`, `in-scheduler`, `out-ubl`, `out-signing`, `out-sunat-soap`, `out-storage`, `out-persistence`, `out-crypto`, cableados en `bootstrap`. El documento se persiste firmado y numerado **antes** de enviar; si SUNAT falla transitoriamente queda `ERROR_ENVIO` y el `OutboxWorker` reintenta con backoff.

**Tech Stack:** Java 21, Gradle 8 (Kotlin DSL), Spring Boot 3.3.4, PostgreSQL 16 + Flyway, JdbcTemplate, Freemarker 2.3.33 (plantillas UBL), `javax.xml.crypto` (firma), `java.net.http.HttpClient` (SOAP), JUnit 5, AssertJ, Mockito, Testcontainers 1.20.1, WireMock 3.9.1, ArchUnit 1.3.0.

**Spec:** `docs/superpowers/specs/README.md` (índice) — en particular `02-arquitectura.md`, `03-dominio-y-datos.md`, `04-flujos-sunat.md` §1.1 y §1.5, `05-contrato-rest.md`, `06-seguridad-y-errores.md`. Fuente SUNAT: `docs/sunat/notes.md`.

## Global Constraints

- Regla de dependencia: `adapters/* → application → domain`; `bootstrap` ve todo; los adaptadores no se conocen entre sí. `domain` y `application` no dependen de Spring, JDBC, Freemarker ni XML-DSig (verificado con ArchUnit).
- Paquete raíz `pe.factura`. Nombres de clases y campos en español, sin tildes en identificadores.
- Toda consulta a BD recibe `tenantId`; ninguna tabla de negocio sin `tenant_id`.
- Numeración correlativa por `(tenant, tipo, serie)` bajo `SELECT … FOR UPDATE`; nunca reutilizable.
- `UNIQUE (tenant_id, tipo, serie, numero)` en `documento`.
- Nombre de archivo SUNAT: `{RUC}-{TIPO}-{SERIE}-{NUMERO}` (p. ej. `20100066603-01-F001-1`), ZIP y XML con el mismo nombre.
- UBL: `UBLVersionID=2.1`, `CustomizationID=2.0`; firma en `ext:UBLExtensions/ext:UBLExtension/ext:ExtensionContent`, enveloped, sobre todo el documento.
- Códigos SUNAT: SOAPFault `0100–1999` → transitorio (`ERROR_ENVIO`, reintentable con el mismo número); SOAPFault o CDR `2000–3999` → `RECHAZADO` (numeración consumida); CDR `0` con notas `4000+` → `ACEPTADO_CON_OBS`.
- Backoff del outbox: `min(2^intentos minutos, 6 h)`, máximo 20 intentos.
- IGV 18 %. `precio_unitario` de entrada **incluye IGV** para ítems gravados (afectación `10`); `20` exonerado y `30` inafecto no llevan IGV.
- Secretos (clave SOL, PKCS#12 y su clave) cifrados con AES-256-GCM (`MASTER_KEY` base64 de 32 bytes). API keys almacenadas como SHA-256(key + `API_KEY_PEPPER`).
- Respuestas REST con sobre `{estado, datos, mensaje?, errores?, codigo?}`; JSON `snake_case`.
- Zona horaria de negocio `America/Lima` (inyectada vía `java.time.Clock`).

## Desviaciones respecto al spec (simplificaciones deliberadas de Fase 1A)

| Spec | Plan 1A | Motivo |
|---|---|---|
| JAXB para UBL | Plantillas **Freemarker** + validación XSD | Menos código generado, salida determinista y fácil de comparar con las guías SUNAT |
| Apache CXF para SOAP | Sobre SOAP construido a mano + `HttpClient` | El servicio tiene 5 métodos; evita generar stubs desde WSDL y es trivial de simular con WireMock |
| Spring Data JPA | **JdbcTemplate** + SQL explícito | Control total sobre `FOR UPDATE SKIP LOCKED` y sobre el filtro por `tenant_id`; menos mapeo |

Si se prefiere volver a lo indicado en el spec, el cambio queda confinado al adaptador correspondiente.

## Fuera de este plan (planes 1B y 1C)

Notas 07/08, boletas + Resumen Diario, Comunicación de Baja, PDF, modo `single` + CLI, paquete `dist/`, k6, suite de homologación beta.

---

## Estructura de archivos

```
factura/
├── settings.gradle.kts
├── build.gradle.kts                      # plugins/versions comunes (java 21, tests)
├── gradle/libs.versions.toml             # catálogo de versiones
├── docker-compose.yml                    # postgres para desarrollo
├── domain/
│   └── src/main/java/pe/factura/domain/
│       ├── DomainException.java
│       ├── documento/ TipoDocumento, EstadoDocumento, TipoAfectacionIgv, Receptor, Item, ItemCalculado,
│       │              Totales, Cdr, Comprobante, NombreArchivo, MontoEnLetras
│       └── tenant/    Entorno, CredencialesSol, CertificadoDigital, Tenant, Serie, ApiKey
├── application/
│   └── src/main/java/pe/factura/application/
│       ├── port/in/   EmitirComprobanteUseCase, EnviarDocumentoUseCase, ConsultarComprobanteUseCase, AdministrarTenantUseCase
│       ├── port/out/  UblGenerator, XsdValidator, XmlSigner, SunatBillingGateway, CdrParser, DocumentStorage,
│       │              SecretCipher, ComprobanteRepository, SerieRepository, TenantRepository, ApiKeyRepository,
│       │              OutboxRepository, UnitOfWork, SunatTransientException, SunatRechazoException
│       └── service/   EmitirComprobanteService, EnviarDocumentoService, ConsultarComprobanteService,
│                      AdministrarTenantService, Backoff
├── adapters/
│   ├── out-crypto/       AesGcmSecretCipher
│   ├── out-storage/      FileSystemDocumentStorage
│   ├── out-persistence/  db/migration/V1__esquema_inicial.sql, Jdbc*Repository, JdbcUnitOfWork
│   ├── out-ubl/          FreemarkerUblGenerator, templates/invoice.ftl, JaxpXsdValidator, resources/xsd/2.1/**
│   ├── out-signing/      XmlDsigSigner
│   ├── out-sunat-soap/   SoapBillingGateway, SoapEnvelope, ZipUtil, XmlCdrParser, SunatUrls
│   ├── in-rest/          ApiKeyFilter, ApiResponse, GlobalExceptionHandler, FacturaController, EmpresaController,
│   │                     AdminTenantController, dto/*
│   └── in-scheduler/     OutboxWorker
└── bootstrap/            FacturaApplication, AppConfig, application.yml, ArchitectureTest, FacturaE2ETest
```

Cada módulo tiene `build.gradle.kts` y `src/{main,test}/java`.

---

## Archivos del plan (ejecutar en orden)

| # | Archivo | Tareas |
|---|---|---|
| 01 | [01-scaffold.md](01-scaffold.md) | Repositorio, Gradle multi-módulo, bootstrap arranca, ArchUnit |
| 02 | [02-dominio.md](02-dominio.md) | Tipos, estados, ítems y totales, `Comprobante`, tenant/serie/api key, nombre de archivo, monto en letras |
| 03 | [03-application.md](03-application.md) | Puertos de salida, casos de uso `EmitirComprobante`, `EnviarDocumento`, `ConsultarComprobante`, `AdministrarTenant`, backoff |
| 04 | [04-persistencia-crypto-storage.md](04-persistencia-crypto-storage.md) | Flyway V1, repositorios JDBC, `UnitOfWork`, AES-GCM, storage en disco |
| 05 | [05-ubl-xsd-firma.md](05-ubl-xsd-firma.md) | Plantilla `Invoice` Freemarker, validador XSD, firmador XML-DSig + hash |
| 06 | [06-sunat-soap.md](06-sunat-soap.md) | ZIP, sobre SOAP `sendBill`, parseo de CDR, faults, WireMock |
| 07 | [07-rest.md](07-rest.md) | Filtro API key, sobre de respuesta, `POST/GET /v1/facturas`, `/enviar`, `/xml`, `/cdr` |
| 08 | [08-outbox-admin.md](08-outbox-admin.md) | `OutboxWorker`, endpoints de tenant/certificado/credenciales/series/api-keys |
| 09 | [09-e2e.md](09-e2e.md) | Prueba extremo a extremo (Testcontainers + WireMock), `docker-compose`, README del proyecto |
