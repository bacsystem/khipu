# Backlog heredado de la Fase 1A

Hallazgos de la revisión final de la rama `feat/fase-1a` que **no bloquean el merge** y se difieren a los planes 1B/1C. Ordenados por impacto. Cada ítem indica el plan sugerido.

## Prerrequisitos de 1B (tocar antes de escribir código de 1B)

| # | Ítem | Archivo | Plan |
|---|---|---|---|
| 1 | `JdbcComprobanteRepository.guardar` fija `ticket = NULL` en cada UPDATE; 1B introduce tickets (RC/RA). Añadir `ticket` al dominio y al UPDATE. | `adapters/out-persistence/.../JdbcComprobanteRepository.java` | 1B |
| 2 | `evento_documento` no tiene `tenant_id` (spec 03 exige `tenant_id` en toda tabla de negocio). Migración V3 antes de que 1B escriba eventos. | `V1__esquema_inicial.sql` | 1B |
| 3 | Códigos SOAPFault `1000–1999` (errores de formato del contribuyente, p. ej. 1033 duplicado) se reintentan hasta 20 veces. Evaluar estado terminal `ERROR_FORMATO` para 1000–1999. | `SoapBillingGateway`, `EnviarDocumentoService` | 1B |

## Contrato REST y validación de entrada

| # | Ítem | Plan |
|---|---|---|
| 4 | `GlobalExceptionHandler`: mapear `NoResourceFoundException` (404), `HttpRequestMethodNotSupportedException` (405), `MethodArgumentTypeMismatchException` (400, p. ej. `/v1/facturas/no-uuid`, `?estado=FOO`), `MaxUploadSizeExceededException` (413), `DataIntegrityViolationException` (409). Hoy caen en 500. | 1B |
| 5 | `FacturaRequest`: `precio_unitario` debe ser `@Positive` para afectación 10 (gratuitas requieren catálogo 07 códigos 11–17 + `PriceTypeCode 02`); `@Digits(integer=12, fraction=2)` en importes; `@Size` en `razon_social`, `descripcion`, `num_doc`. | 1B |
| 6 | Freemarker `number_format="0.00"` redondea HALF_EVEN; el dominio usa HALF_UP. Con `@Digits(fraction=2)` desaparece la discrepancia; alternativamente formatear con `?string` explícito. | 1B |
| 7 | `GET /v1/facturas`: falta `meta` de paginación y filtros `serie`, `numero`, `desde`, `hasta` (spec 05). | 1B |
| 8 | `forma_pago` ausente en el request; `cac:PaymentTerms` fijo en `Contado`. Necesario para facturas a crédito (cuotas). | 1B |
| 9 | Límite de tamaño de body JSON (Tomcat `maxPostSize` no cubre JSON); configurar `server.max-http-request-header-size` y un filtro de tamaño o `spring.servlet.multipart.max-file-size` documentado. | 1C |

## SUNAT / homologación

| # | Ítem | Plan |
|---|---|---|
| 10 | Restaurar atributos `schemeName/schemeAgencyName/schemeURI` en `cac:PartyIdentification/cbc:ID` y `schemeID/schemeAgencyID` en `cac:TaxScheme/cbc:ID` de cabecera antes de la primera corrida en beta (evita observaciones 4xxx). Ajustar 2 aserciones en `FreemarkerUblGeneratorTest`. | 1C (antes de beta) |
| 11 | Añadir `cac:TaxCategory/cbc:ID` (S/E/O) por línea y cabecera, `cbc:IssueTime`, y dirección completa del emisor; comparar con el ejemplo oficial en beta. | 1C |
| 12 | `wsse:Password` sin `Type` — coincide con el ejemplo oficial; si beta lo observa, añadir `Type="…#PasswordText"`. | 1C |
| 13 | Verificar que el RUC del receptor exista en SUNAT para las pruebas beta (2xxx si no existe). | 1C |
| 14 | El CDR se guarda/sirve como ZIP (`R-….zip`); el spec 05 menciona `.cdr.xml`. Decidir y alinear spec o código. | 1B |

## Operación / NFR

| # | Ítem | Plan |
|---|---|---|
| 15 | Proxy corporativo: `HttpClient` por defecto ignora `HTTPS_PROXY` (spec 01). Usar `ProxySelector` desde variables de entorno. | 1C |
| 16 | `FileSystemDocumentStorage.guardar` no es atómico (escribir a temporal + `move`). | 1C |
| 17 | `Tenant`, `CredencialesSol`, `CertificadoDigital` son records: `toString()` expondría secretos si alguien los loguea. Sobrescribir `toString`. | 1B |
| 18 | Testcontainers `api.version=1.41` fijado en dos `build.gradle.kts` (persistence, bootstrap). Subir a Testcontainers ≥ 1.20.4 o mover a `~/.testcontainers.properties`. | 1C |
| 19 | `FacturaE2ETest` usa puerto WireMock fijo 18089; usar `WireMockExtension` con puerto dinámico + `DynamicPropertySource`. | 1C |
| 20 | `AppProperties.outbox.intervaloMs` no se lee (lo usa `@Scheduled` directo). Unificar. | 1C |
| 21 | Guard de secretos duplicado en dos beans de `AppConfig`; mover a un `ApplicationRunner`. | 1C |
| 22 | Regla ArchUnit de `application` no prohíbe `javax.sql..`/`javax.xml.crypto..`. | 1B |

## Cobertura de tests

| # | Ítem | Plan |
|---|---|---|
| 23 | `AdministrarTenantService`: tests de `obtener`, `listarSeries`, `crearApiKey`, credenciales en blanco. | 1B |
| 24 | `JdbcApiKeyRepositoryTest` inexistente. | 1B |
| 25 | Test directo de `NUMERO_YA_ASIGNADO` en `ComprobanteTest`. | 1B |
| 26 | Aserción del header `SOAPAction` en `SoapBillingGatewayTest`. | 1B |

## Documentación

| # | Ítem | Plan |
|---|---|---|
| 27 | Registrar en `docs/superpowers/specs/` las desviaciones ya decididas: XSD validado tras la firma; atributos `scheme*` removidos (temporal); regla "las subconsultas por `comprobante_id` solo reciben ids obtenidos de `documento` filtrado por tenant". | 1B |
