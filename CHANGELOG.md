# Changelog — khipu

Formato basado en [Keep a Changelog](https://keepachangelog.com/es/1.1.0/); versionado [SemVer](https://semver.org/lang/es/) (pre-1.0: cambios rompientes suben el minor, el resto el patch).

## [0.1.16] - 2026-09-18

### Added
- Suite de homologación contra e-beta (#32): `./gradlew :bootstrap:homologacion` emite 17 escenarios de factura (gravada, exonerada, inafecta, mixta, gratuita, descuentos, cargos, USD, crédito, detracción, retención, percepción, ISC/ICBPER, referencias y campos opcionales, 50 ítems, anticipo y su regularización) con el flujo completo y exige CDR `0` sin observaciones; guarda XML y CDR por escenario y un `RESUMEN.md`. Workflow nocturno `homologacion.yml` con la evidencia como artifact. Primera ejecución: **17/17 aceptados sin observaciones**.

### Fixed
- XML: `cac:TaxScheme/cbc:ID` lleva los atributos del catálogo 05 (`Codigo de tributos`, `PE:SUNAT`, `catalogo05`) en lugar de los de UN/ECE 5153; e-beta observaba (4255/4256) todas las facturas.
- SUNAT: el gateway usa una conexión nueva por envío y reintenta hasta 3 veces el `HTTP 401` intermitente del balanceador de SUNAT (una de cada dos peticiones, y toda conexión keep-alive reutilizada); antes el comprobante quedaba en `ERROR_ENVIO` hasta el reintento del outbox.

## [0.1.15] - 2026-09-18

### Added
- API: campos opcionales de la factura (#64): `fecha_vencimiento` (→ `cbc:DueDate`, no anterior a la emisión), `items[].codigo_sunat` (8 dígitos UNSPSC, catálogo 25 → `cac:CommodityClassification`, regla 3496), `items[].gtin { tipo GTIN-8|12|13|14, codigo }` (→ `cac:StandardItemIdentification`, reglas 4333–4335) y `redondeo` (→ `PayableRoundingAmount`, entre −1.00 y 1.00, regla 3303; el total y el monto en letras lo incluyen, regla 3280). Respuesta con `fecha_vencimiento`, `codigo_sunat`, `gtin` y `totales.redondeo`. Nuevos errores `ITEM_INVALIDO`, `REDONDEO_INVALIDO`.
- API: nombre comercial de la empresa en `PUT /v1/empresa/datos-fiscales { nombre_comercial }` (→ `cac:PartyName` del emisor, regla 4092; error `NOMBRE_COMERCIAL_INVALIDO`); `GET /v1/empresa` lo devuelve.
- API: catálogo 25 (`GET /v1/catalogos/25`) con los listados 25.1 (padrón obligado a código de producto), 25.2 (detracciones) y 25.3 (percepciones) y sus partidas arancelarias; el UNSPSC completo lo publica SUNAT y no se valida contra él.
- Persistencia: migración V13 (`tenant.nombre_comercial`, `comprobante.fecha_vencimiento/redondeo`, `comprobante_item.codigo_sunat/gtin_tipo/gtin`).
- Portal: nombre comercial en el formulario de datos fiscales; vencimiento, códigos de producto y redondeo en el detalle; caso en la guía; errores y catálogo 25 documentados.

## [0.1.14] - 2026-09-18

### Added
- API: documentos relacionados en la factura (#63): `orden_compra` (1–20 caracteres → `cac:OrderReference`, regla 4233), `guias[] { tipo 09|31, numero }` (→ `cac:DespatchDocumentReference` con formato serie-número de SUNAT, reglas 4005, 4006, 2364) y `documentos_relacionados[] { tipo 04–09|99, numero }` (→ `cac:AdditionalDocumentReference`, catálogo 12, reglas 4009, 4010, 2365). Respuesta con el bloque `referencias` cuando hay alguno. Nuevo error `DOCUMENTO_RELACIONADO_INVALIDO`. Migración V12 (`comprobante.orden_compra`, tabla `comprobante_documento_relacionado`).
- Portal: sección "Documentos relacionados" en el detalle; caso en la guía, código en errores y uso de los catálogos 01 y 12.

## [0.1.13] - 2026-09-18

### Added
- API: cargos por ítem y globales (#62): `items[].cargos[] { porcentaje | monto, afecta_base_igv }` y `cargos[] { porcentaje | monto, afecta_base_igv, motivo? }`, con el mismo formato que los descuentos; khipu deriva el código del catálogo 53 (línea 47/48, global 49/50, `motivo: recargo_consumo` → 46) y lo devuelve en la respuesta. Los 47/49 se suman a la base del IGV (reglas 38, 3277, 3278, 3291); 48, 46 (recargo al consumo y propinas) y 50 se cobran sin IGV y van a `ChargeTotalAmount` (3301) sumando al importe a pagar (3280); el precio de venta unitario los incluye (3270). En el XML: `cac:AllowanceCharge` con `ChargeIndicator true`, factor (solo si reproduce el monto, 3290/3307), monto y base. Respuesta con `cargos[]` por ítem y en totales, y `totales.total_cargos`. Nuevo error `CARGO_INVALIDO` (2954, 2955, 3052, 4268, 4291). FISE (45) y los cargos de línea 07/54 quedan fuera. Migración V11.
- Portal: columna de cargos por línea, "Cargo global (49)" y "Otros cargos sin IGV" en la liquidación; la guía documenta el caso y la página de errores el código.

## [0.1.12] - 2026-09-17

### Added
- API: datos fiscales de la empresa (#29b): `PUT /v1/empresa/datos-fiscales { domicilio { ubigeo (catálogo 13), direccion, urbanizacion, distrito, provincia, departamento, codigo_establecimiento }, cuenta_detracciones }`; `GET /v1/empresa` los devuelve y `GET /v1/empresas` trae `tiene_domicilio`. El domicilio va en el XML como `RegistrationAddress` completa del emisor (ubigeo, establecimiento anexo, urbanización, provincia, departamento, distrito, dirección y país; reglas 4093–4098, 4041, 3030). Nuevos errores `DOMICILIO_INVALIDO` y `CUENTA_DETRACCIONES_INVALIDA`.
- API: catálogo 13 (ubigeo INEI, 1892 distritos con departamento/provincia/distrito) en `GET /v1/catalogos/13`.
- API: `detraccion.cuenta_banco_nacion` es opcional si la empresa tiene cuenta de detracciones configurada (regla 3034 si no hay ninguna).
- XML: `cbc:IssueTime` con la hora local de emisión (se guarda en `documento.hora_emision`). Migración V10.
- Portal: formulario de domicilio fiscal (departamento → provincia → distrito en cascada) y cuenta de detracciones en la página Empresa; la guía y la página de errores lo documentan.

## [0.1.11] - 2026-09-17

### Added
- API: anticipos regularizados en la factura final (#51): `anticipos[] { serie, numero, monto (sin IGV), afectacion gravado|exonerado|inafecto, fecha_pago }`. La factura de anticipo debe ser de la misma empresa, al mismo cliente, en la misma moneda y estar aceptada por SUNAT (3218); el monto no puede superar lo facturado en ella ni lo facturado en la final para esa afectación. En el XML: `AdditionalDocumentReference` (02, RUC emisor, identificador de pago), `PrepaidPayment` (importe con IGV) y `AllowanceCharge` 04/05/06 que reduce la base del tributo (3277, 3291); `PrepaidAmount` restado del importe a pagar (2503, 2509, 3211–3220, 3280, 3282, 3287); total valor/precio de venta siguen brutos (3278, 3279). Respuesta con `anticipos[]` (`comprobante`, `monto`, `importe_pagado`, `codigo_sunat`…) y `totales.total_anticipos`. Nuevo error `ANTICIPO_INVALIDO`. Migración V9.
- Portal: bloque de anticipos regularizados y la línea "Anticipos ya pagados" en la liquidación; la guía documenta el flujo completo (factura de anticipo → factura final) y la página de errores los códigos `*_INVALIDO`.

## [0.1.10] - 2026-09-17

### Added
- API: ISC por ítem (#52): `items[].isc { sistema (catálogo 08), tasa | monto_unitario }`; el `precio_unitario` incluye ISC e IGV y khipu los separa; el ISC entra en la base del IGV (regla 204) y se informa como tributo 2000 por línea (con `TierRange`) y global (reglas 3108, 2373, 3210). Respuesta con `isc` por ítem y en totales.
- API: ICBPER por ítem (#53): `items[].icbper: true` (una bolsa por unidad, monto vigente por año según la Ley 30884 — S/ 0.50 desde 2023) como tributo 7152 con `BaseUnitMeasure` y `PerUnitAmount`, sin base ni tasa (reglas 3236–3238, 4318). Respuesta con `icbper` por ítem y en totales; `total_precio_venta` los incluye (regla 55). Migración V8.
- Portal: insignias ISC/ICBPER por línea y totales en la liquidación; la guía documenta el caso.

## [0.1.9] - 2026-09-17

### Added
- API: retención del IGV informada en la factura (#47): `retencion_igv { porcentaje (3 % por defecto), monto (opcional, calculado sobre el importe total, tolerancia ±1) }` → `cac:AllowanceCharge` 62 (reglas 3114, 3262–3264); respuesta con `neto_cobrar`.
- API: percepción del IGV (#48): `percepcion { regimen 51/52/53, porcentaje (lo fija el catálogo 22), base (por defecto el total), monto (opcional) }`, solo con `tipo_operacion` 2001, al contado y en PEN (2788, 2797, 2798, 3093, 3308, 3330) → `cac:AllowanceCharge` 51/52/53 con `ChargeIndicator true`, `PaymentTerms` `Percepcion` con el total más percepción (3309/3310) y leyenda 2000; respuesta con `total_con_percepcion`. Migración V7.
- Portal: bloques de retención y percepción en la liquidación; la guía documenta ambos casos.

## [0.1.8] - 2026-09-17

### Added
- API: detracción (SPOT) en la factura (#46). `detraccion { codigo_bien_servicio (catálogo 54), porcentaje, monto (PEN, opcional en facturas en soles: se calcula redondeado al sol), cuenta_banco_nacion, medio_pago (catálogo 59, por defecto 001) }`, obligatoria con `tipo_operacion` 1001–1004 y prohibida en los demás (reglas 3033, 3034, 3037, 3127–3129, 3174, 3208). XML con `cac:PaymentMeans` (cuenta BN) y `cac:PaymentTerms` `Detraccion` (bien/servicio, %, monto en PEN) y leyenda 2006. Respuesta con `detraccion` y la descripción del catálogo. Migración V6.
- Portal: bloque "Detracción (SPOT)" en la liquidación del comprobante; la guía documenta el caso.

## [0.1.7] - 2026-09-17

### Added
- API: operaciones gratuitas en la factura (#50). `tipo_afectacion_igv` acepta `11`–`16` (gravadas gratuitas), `21` (exonerada gratuita) y `31`–`37` (inafectas gratuitas): el `precio_unitario` es el valor referencial sin IGV, la línea no suma al importe a pagar y su IGV solo se informa (tributo 9996, categoría Z). Respuesta con `gratuita`, `precio_venta` por ítem y `gratuito` / `igv_gratuitas` en totales. XML con `PriceTypeCode 02`, `Price 0`, subtotal 9996 y leyenda 1002 (reglas 2640, 3110, 3111, 3224, 3234, 3276, 3302, 54). Los subtotales globales se agrupan por tributo (`Tributo`: IGV, EXO, INA, GRA).
- Portal: etiquetas del catálogo 07 completo, líneas gratuitas marcadas y bloque "operaciones gratuitas" en la liquidación; la guía documenta el caso.

## [0.1.6] - 2026-09-17

### Added
- API: descuentos en la factura (#49). Por ítem `descuento: { porcentaje | monto, afecta_base_igv }` (catálogo 53: `00` afecta la base del IGV, `01` no) y global `descuento_global` (`02` sobre la base gravada, `03` sobre el importe a pagar). khipu calcula monto, factor y bases; la respuesta devuelve por ítem `valor_venta`, `igv` y `descuento`, y en `totales` `total_valor_venta`, `total_precio_venta`, `total_descuentos` y `descuento_global`. XML con `cac:AllowanceCharge` por línea y global y `AllowanceTotalAmount` (reglas 33, 38, 46/47, 51, 53/54, 3052, 3290, 3300). Migración V5.
- Portal: el detalle muestra el descuento por línea y el descuento global / descuentos que no afectan el IGV en la liquidación; la guía documenta el caso.

## [0.1.5] - 2026-09-17

### Added
- API: `GET /v1/catalogos` y `GET /v1/catalogos/{id}` (públicos, sin credenciales) con los catálogos oficiales de SUNAT (Anexo 8 de las reglas de validación 2026-08-26: 01, 02, 03, 05, 06, 07, 08, 09, 10, 12, 16, 22, 23, 51, 52, 53, 54, 59, 60) cargados desde recursos del dominio (`CatalogoSunat`), base para validar códigos antes de firmar y para documentarlos.
- OpenAPI: introducción de la API (autenticación, entornos, sobre de respuesta, estados del comprobante, códigos HTTP, plazos), etiquetas y descripción de cada endpoint, y descripción de cada campo con su catálogo y su significado. Se ve en `/developers` y en `/openapi.json`.
- Developer portal: nuevas secciones **Guía de emisión** (inicio rápido, anatomía de la factura, casos de emisión con JSON completo —contado, mixta, dólares, crédito con cuotas, emitir sin enviar, correlativo propio— y los casos en desarrollo con su contrato previsto, lectura de la respuesta, buenas prácticas), **Catálogos SUNAT** (todos los catálogos con buscador, leídos de la API) y **Errores y estados** (estados, códigos HTTP, códigos de khipu con qué hacer, rangos de SUNAT).

## [0.1.4] - 2026-09-17

### Added
- API: forma de pago de la factura (RS 193-2020, #30). `POST /v1/facturas` acepta `forma_pago` (`contado` por defecto, o `credito` con `monto_pendiente` y `cuotas[]` de `monto` + `vencimiento`); `ComprobanteResponse.forma_pago` la devuelve con los identificadores SUNAT (`Cuota001…`). El XML lleva un `cac:PaymentTerms` por indicador (`Credito` con el neto pendiente y uno por cuota con `PaymentDueDate`). Las reglas de la hoja Factura2_0 se validan antes de consumir numeración y responden `422 FORMA_PAGO_INVALIDA` con el código SUNAT en el mensaje (3244, 3249–3253, 3256, 3265, 3267, 3319). Migración V4 (`comprobante.forma_pago`, `monto_pendiente`, tabla `comprobante_cuota`).
- Portal: el detalle del comprobante muestra la forma de pago y, al crédito, el neto pendiente y el calendario de cuotas.

## [0.1.3] - 2026-09-17

### Changed
- UBL factura: `cac:TaxCategory/cbc:ID` (catálogo 05: S/E/O, UN/ECE 5305) en cada línea y en los subtotales globales, y atributos `schemeName`/`schemeAgencyName`/`schemeURI` del catálogo 06 en el RUC del emisor y el documento del receptor, con los valores exactos que exige la hoja `Factura2_0` de las reglas de validación 2026-08-26 (#29, parte a). Nuevo test de contrato `AtributosSunatFacturaTest` que fija por XPath cada atributo que SUNAT observa si difiere (4251–4259).

## [0.1.2] - 2026-09-17

### Fixed
- API: los faults SUNAT 1000–1999 (errores del contenido o del emisor: 1001 formato de serie, 1033 registrado previamente con otros datos, 1034–1036 nombre de archivo ≠ XML, 1059 sin firma, 1078 emisor no autorizado) dejan el comprobante en `RECHAZADO` con el código y la descripción del fault, en un solo intento. Antes se trataban como transitorios y el outbox los reintentaba 20 veces con backoff hasta 6 h, ocultando el error real (#33). Solo 0100–0999 (servicio no disponible, timeout, credenciales) siguen siendo reintentables.
- API: `enlaces.cdr` en `ComprobanteResponse` solo aparece cuando existe la constancia; un rechazo por fault trae `cdr.codigo`/`descripcion` pero no archivo. El portal usa ese enlace para ofrecer la descarga y rotula el código como "Código SUNAT" cuando no hay CDR.

## [0.1.1] - 2026-09-17

### Changed
- El proyecto pasa a llamarse **khipu** (repo `bacsystem/khipu`, `spring.application.name`, remitente por defecto, `khipu-portal`). Paquete Java, base de datos y cookies conservan `factura` como identificador técnico.

### Added
- API: `GET /v1/empresa/api-keys` (lista con prefijo, estado y fechas) y `DELETE /v1/empresa/api-keys/{id}` (revocación idempotente, 404 si es de otro tenant). Crear, listar y revocar keys exige sesión del portal (JWT): con `X-Api-Key` responde 403 `REQUIERE_SESION`, para que una key filtrada no pueda crear otras ni revocar las del tenant.
- API: `X-Total-Count` en `GET /v1/facturas`, receptor e ítems en `ComprobanteResponse`, y `GET /v1/facturas/{id}/cdr?formato=xml`.
- Portal: rediseño "khipu" (estilo Linear) de comprobantes, series, empresa y API keys: franja de métricas, tablas con filtros y paginación, diálogos de creación y de referencia técnica, vista previa de XML/CDR.
- Portal: página de API keys con listado, creación (secreto de un solo uso) y revocación en línea; diálogo "Prueba de emisión" con ejemplos cURL/Node/Python.
- Portal: menú de usuario en el sidebar (cuenta, tema claro/oscuro/sistema, cambio de contraseña, cierre de sesión); componente `ui/menu`.
- Portal: acceso desde otro equipo de la red local (`PORTAL_DEV_ORIGINS`), documentado en `portal/README.md`.
- Portal: `API_PUBLIC_URL` (opcional) para la URL de la API que se muestra al usuario en los ejemplos de integración y en "Try it" de `/developers`, cuando difiere de la interna `API_BASE_URL` (Docker, red privada).
- Portal: componentes compartidos `ui/metrica`, `ui/pie-tabla` (contador + filas por página + paginador), `ui/boton-copiar` y `ui/referencia-tecnica` (modal de referencia con secciones, tabla de campos y reglas), usados por comprobantes, series, empresa y API keys.

### Fixed
- API: rutas inexistentes responden 404 `RUTA_INEXISTENTE` y parámetros con tipo inválido 400 `PARAMETRO_INVALIDO` (antes 500). `GET /v1/facturas/{id}/cdr?formato=` distinto de `zip`/`xml` responde 400; un CDR almacenado ilegible responde 500 `CDR_CORRUPTO` (antes 422) conservando la causa en el log.
- Build: Testcontainers alineado entre `bootstrap` (BOM de Spring Boot) y `out-persistence`; evitaba mezclar dos versiones de docker-java al correr todos los tests desde IntelliJ.
- Portal: los mocks de MSW siguen `API_BASE_URL` y Playwright la fija en localhost, para que un `.env.local` no desvíe los e2e al backend real; e2e de login/comprobantes actualizados al rediseño.

## [0.1.0] - 2026-09-14

- Fase 1A (backend de emisión SUNAT) y etapa 1 del portal.
