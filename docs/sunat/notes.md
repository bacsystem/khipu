# Notas técnicas SUNAT — SEE desde los Sistemas del Contribuyente

Fuente: documentación oficial en https://cpe.sunat.gob.pe/guias-y-manuales (descargada el 2026-09-13).
Copias locales en `docs/sunat/ref/`.

| Documento | Archivo local | Para qué sirve |
|---|---|---|
| Manual del Programador v2.1 (2021) | `ref/manual_programador_see_contribuyente_v2.1.pdf` | Nombres de archivo, SOAP, métodos, CDR, firma, errores |
| Servicios Web disponibles (2026-05) | `ref/servicios_web_disponibles_2026-05.pdf` | URLs WSDL vigentes beta/producción |
| Manual de Servicios GRE (REST) | `ref/manual_servicios_gre_rest.pdf` | OAuth2 + API REST de guía de remisión |
| Manual URL GRE (REST) | `ref/manual_url_gre_rest.xlsx` | Endpoints, payload y códigos de error GRE |
| Guía XML Factura UBL 2.1 | `ref/guia_xml_factura_ubl2.1.pdf` | Estructura del `Invoice` |
| Guía Resumen Diario v2.0 | `ref/guia_resumen_diario_boletas_v2.0.pdf` | Estructura `SummaryDocuments` |
| Reglas de validación (2026-08-26) | `ref/reglas_validacion_2026-08-26.xlsx` | Todas las validaciones, códigos de retorno y catálogos |
| Manual técnico OSE v5.2 | `ref/manual_tecnico_operatividad_ose_v5.2.docx` | Cómo se comporta un OSE (para el adaptador OSE futuro) |

Otros documentos disponibles en el portal (no descargados): guías XML de boleta, nota de crédito, nota de débito; XSD/XSL UBL 2.1; guía de resumen de contingencia.

---

## 1. Dos protocolos distintos

| Protocolo | Comprobantes | Autenticación |
|---|---|---|
| **SOAP + WS-Security** (`billService`) | Factura 01, Boleta 03, NC 07, ND 08, Resumen Diario (RC), Comunicación de Baja (RA), Retención 20, Percepción 40, Resumen de Reversión (RR) | `UsernameToken`: `Username = RUC + usuarioSOL`, `Password = claveSOL` |
| **REST + OAuth2** (`api-cpe`) | Guía de Remisión Remitente 09, Guía de Remisión Transportista 31 | Bearer token obtenido con `client_id`/`client_secret` (generados en menú SOL) + credenciales SOL |

> El manual del programador v2.1 aún lista un WSDL SOAP para guías (`e-guiaremision.sunat.gob.pe`). Ese canal es el antiguo; la GRE vigente (RS 000123-2022) se envía por REST. Diseñar el adaptador de GRE solo sobre REST.

---

## 2. Endpoints

### SOAP — Producción
| Servicio | WSDL |
|---|---|
| Factura, notas, boleta (vía resumen), Resumen Diario, Comunicación de Baja, lotes | `https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService?wsdl` |
| Retención, Percepción, Resumen de Reversión | `https://e-factura.sunat.gob.pe/ol-ti-itemision-otroscpe-gem/billService?wsdl` |
| Consulta validez de CPE | `https://e-factura.sunat.gob.pe/ol-it-wsconsvalidcpe/billValidService?wsdl` |
| Consulta CDR y estado de envío | `https://e-factura.sunat.gob.pe/ol-it-wsconscpegem/billConsultService?wsdl` |

### SOAP — Beta (pruebas)
| Servicio | WSDL |
|---|---|
| Factura y afines | `https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService?wsdl` |
| Retención/Percepción | `https://e-beta.sunat.gob.pe/ol-ti-itemision-otroscpe-gem-beta/billService?wsdl` |

Credenciales beta: `Username = <RUC>MODDATOS`, `Password = moddatos`.
Namespace SOAP: `http://service.sunat.gob.pe`. El ZIP viaja como adjunto MTOM/`cid:` (`contentFile`) o base64.

### REST GRE
| Operación | Método | URL |
|---|---|---|
| Token | POST | `https://api-seguridad.sunat.gob.pe/v1/clientessol/{client_id}/oauth2/token/` |
| Enviar GRE | POST | `https://api-cpe.sunat.gob.pe/v1/contribuyente/gem/comprobantes/{ruc}-{codCpe}-{serie}-{numero}` |
| Consultar ticket | GET | `https://api-cpe.sunat.gob.pe/v1/contribuyente/gem/comprobantes/envios/{numTicket}` |

Beta GRE: los manuales oficiales no publican un entorno de pruebas REST. Pendiente de confirmar (ver §10).

---

## 3. Métodos SOAP (`billService`)

| Método | Entrada | Salida | Uso |
|---|---|---|---|
| `sendBill(fileName, contentFile)` | nombre ZIP + bytes ZIP (1 XML) | bytes ZIP con CDR (`R-<nombre>.xml`) | **Síncrono**. Factura, NC/ND de factura, Retención, Percepción |
| `sendSummary(fileName, contentFile)` | nombre ZIP + bytes ZIP (1 XML) | `String ticket` | **Asíncrono**. Resumen Diario (RC), Comunicación de Baja (RA), Resumen de Reversión (RR) |
| `sendPack(fileName, contentFile)` | nombre ZIP + bytes ZIP (varios XML) | `String ticket` | Lote de facturas/notas (LT). Opcional |
| `getStatus(ticket)` | ticket | `{statusCode, content}` | `0` = OK, `98` = en proceso, `99` = con errores. `content` = ZIP con CDR (si 0 o 99) |
| `getStatusCdr(ruc, tipo, serie, numero)` | datos del CPE | `{statusCode, content, statusMessage}` | Recuperar CDR de factura/nota ya enviada (solo producción) |

**Excepciones SOAP**: cuando el error es de tipo *excepción* (códigos 0100–1999) SUNAT responde con `SOAPFault` cuyo `faultcode` es el código y `faultstring` la descripción — no hay CDR. El documento se considera **no informado** y puede reenviarse.

---

## 4. Nomenclatura de archivos

Formato general: `{RUC}-{TIPO}-{SERIE}-{NUMERO}` (ZIP y XML con el mismo nombre; dentro del ZIP puede ir una carpeta `dummy` vacía).

| Documento | Tipo | Serie | Ejemplo |
|---|---|---|---|
| Factura | `01` | `F###` | `20100066603-01-F001-1.zip` |
| Boleta | `03` | `B###` | `20100066603-03-B001-1.zip` (solo dentro del RC) |
| Nota de crédito | `07` | `F###` / `B###` (según afecte) | `20100066603-07-F001-1.zip` |
| Nota de débito | `08` | `F###` / `B###` | `20100066603-08-F001-1.zip` |
| Comunicación de Baja | `RA` | fecha `YYYYMMDD` + correlativo | `20100066603-RA-20110522-1.zip` |
| Resumen Diario | `RC` | fecha `YYYYMMDD` + correlativo | `20100066603-RC-20110522-1.zip` |
| Retención | `20` | `R###` | `20100066603-20-R001-1.zip` |
| Percepción | `40` | `P###` | `20100066603-40-P001-1.zip` |
| Resumen de Reversión | `RR` | fecha + correlativo | `20100066603-RR-20150522-1.zip` |
| GRE Remitente | `09` | `T###` | `20100066603-09-T001-1.zip` |
| GRE Transportista | `31` | `V###` | `20100066603-31-V001-1.zip` |
| Lote | `LT` | fecha + correlativo | `20100066603-LT-20160504-1.zip` |

Número: 1 a 8 dígitos, sin ceros a la izquierda obligatorios.

**Resumen Diario**: bloques de máximo **500 líneas**; si hay más boletas, varios RC el mismo día con correlativo distinto. El RC también sirve para informar boletas *anuladas* (condición 3 en `SummaryDocumentsLine`) — las boletas no se dan de baja con RA.

---

## 5. Estructura UBL

| Documento | Raíz UBL | `UBLVersionID` | `CustomizationID` |
|---|---|---|---|
| Factura / Boleta | `Invoice` | `2.1` | `2.0` |
| Nota de crédito | `CreditNote` | `2.1` | `2.0` |
| Nota de débito | `DebitNote` | `2.1` | `2.0` |
| Resumen Diario | `SummaryDocuments` | `2.0` | `1.1` |
| Comunicación de Baja | `VoidedDocuments` | `2.0` | `1.0` |
| Retención | `Retention` | `2.0` | `1.0` |
| Percepción | `Perception` | `2.0` | `1.0` |
| GRE | `DespatchAdvice` | `2.1` | `2.0` |
| CDR (respuesta SUNAT) | `ApplicationResponse` | `2.0` | — |

XSD/XSL oficiales UBL 2.1 en el portal (`Archivos XSD (1).zip`, `XSL Actualizado al 06092022`). Validar contra XSD antes de firmar (el manual lo recomienda explícitamente).

Los ID de resumen/baja son `RC-YYYYMMDD-N` y `RA-YYYYMMDD-N` (fecha de **generación**, no de emisión de las boletas; el RC lleva además `ReferenceDate` con la fecha de emisión).

---

## 6. Firma digital (XML-DSig)

- Certificado **X.509 v3**, clave privada ≥ 1024 bits (en la práctica 2048), RUC en el campo `OU` del Subject.
- El certificado debe registrarse previamente en SOL (*Actualización de certificado digital*). SUNAT valida vigencia y no revocación.
- La firma va en `ext:UBLExtensions/ext:UBLExtension/ext:ExtensionContent` — **un único** `UBLExtension` para la firma (vacío antes de firmar).
- Se firma **todo el documento** (raíz `Invoice`, `CreditNote`, `DebitNote`, `SummaryDocuments`, `VoidedDocuments`, `DespatchAdvice`…). Enveloped signature.
- Antes de firmar el XML debe estar completo, incluido `cac:Signature`.
- Tras firmar no se puede alterar nada.
- La codificación de la firma debe coincidir con la del XML (usar UTF-8 siempre).
- Algoritmos usados en los ejemplos oficiales: `rsa-sha1` / `sha1` (los ejemplos), pero SUNAT acepta `rsa-sha256`. Configurable.
- El `DigestValue` de la firma es el **hash** que va en la representación impresa y en el QR.

---

## 7. CDR y códigos de respuesta

- Nombre: `R-<nombre enviado sin extensión>.xml`, dentro de un ZIP. Firmado por SUNAT.
- Formato: `ApplicationResponse` UBL 2.0. Campo clave: `cac:DocumentResponse/cac:Response/cbc:ResponseCode` + `cbc:Description` + `cac:Status/cbc:StatusCode` (notas/observaciones).

| Rango | Tipo | Efecto |
|---|---|---|
| `0` | Aceptado | Comprobante válido |
| `0100`–`0999` | Excepción SUNAT | SOAPFault, no informado, reenviar |
| `1000`–`1999` | Excepción del contribuyente (formato/estructura) | SOAPFault, no informado, corregir y reenviar |
| `2000`–`3999` | **Rechazo** | CDR con rechazo. En facturas/notas la **numeración queda consumida** (emitir nuevo número). En RC/RA/retención/percepción/GRE se rechaza todo el documento sin consumir numeración; reenviar con el mismo nombre |
| `4000+` | **Observación** | CDR aceptado con advertencias; el comprobante es válido |

Catálogo completo de códigos: hoja `CódigosRetorno` de `ref/reglas_validacion_2026-08-26.xlsx`. Nota: SUNAT publica periódicamente "observaciones que migran a error" — los 4xxx pueden convertirse en 2xxx/3xxx con el tiempo.

Estados de `getStatus`: `0` procesado OK, `98` en proceso, `99` con errores (puede traer CDR de rechazo).

---

## 8. GRE (API REST) — detalle

**Token**: `POST https://api-seguridad.sunat.gob.pe/v1/clientessol/{client_id}/oauth2/token/`
Body `x-www-form-urlencoded`:
```
grant_type=password
scope=https://api-cpe.sunat.gob.pe
client_id=<client_id>
client_secret=<client_secret>
username=<RUC><usuarioSOL>
password=<claveSOL>
```
Respuesta: `{access_token, token_type, expires_in}` — vigencia ~1 hora. Reutilizar dentro del periodo.

**Envío**: `POST /v1/contribuyente/gem/comprobantes/{ruc}-{codCpe}-{serie}-{numero}` con `Authorization: Bearer <token>`:
```json
{
  "archivo": {
    "nomArchivo": "20480072872-31-V001-407.zip",
    "arcGreZip": "<base64 del zip>",
    "hashZip": "<SHA-256 del zip>"
  }
}
```
Respuesta `{numTicket: <UUID>, fecRecepcion: "yyyy-MM-ddTHH:mm:ss"}`.

**Consulta**: `GET /v1/contribuyente/gem/comprobantes/envios/{numTicket}`
```json
{ "codRespuesta": "98" }                                       // en proceso
{ "codRespuesta": "0",  "arcCdr": "<base64>", "indCdrGenerado": "1" }   // OK
{ "codRespuesta": "99", "error": {"numError":"2345","desError":"..."}, "arcCdr": "...", "indCdrGenerado": "1" }
{ "codRespuesta": "99", "error": {...}, "indCdrGenerado": "0" }
```
Errores HTTP: `422` con `errors[]` (`codError`/`desError`) para validaciones; `401` token inválido; `500`/`503`.
Errores específicos: 501–507 (payload), 155–161 (ZIP/XML), 0508/0509 (ticket).

Un ZIP contiene exactamente **un** XML `{ruc}-{09|31}-{serie}-{numero}.xml`.

---

## 9. Consecuencias para el diseño de la API

1. **Dos adaptadores de salida**: `SunatSoapBillingAdapter` (WS-Security, MTOM) y `SunatRestGreAdapter` (OAuth2 con caché de token por tenant). Ambos implementan el puerto `SunatGateway`, pero con operaciones distintas; el caso de uso elige por tipo de comprobante.
2. **Por tenant** se guardan: RUC, usuario SOL, clave SOL, certificado + clave privada, y para GRE `client_id` + `client_secret`. Todo cifrado en reposo.
3. **Tres modos de envío**:
   - Síncrono (`sendBill`): 01, 07/08 de factura, 20, 40.
   - Asíncrono con ticket SOAP (`sendSummary` + `getStatus`): RC, RA, RR.
   - Asíncrono con ticket REST: 09, 31.
4. **Boletas nunca van solas**: se emiten, se firman, se guardan y se agrupan en un RC (máx. 500 líneas) que un scheduler envía. Sus NC/ND (serie B) también van dentro del RC. Anulación de boletas = línea con condición 3 en un RC posterior.
5. **Bajas (RA) solo para facturas/notas de factura** (serie F) aceptadas. Retención/percepción se revierten con RR.
6. **Rechazo de factura consume numeración**: el caso de uso no debe reusar serie-número; el cliente debe emitir uno nuevo.
7. **SOAPFault ≠ CDR rechazado**: fault (0100–1999) → estado `ERROR_ENVIO` reintentable sin cambiar número; CDR 2xxx–3xxx → `RECHAZADO` definitivo.
8. **Hash** = `DigestValue` de la firma; obligatorio en PDF/QR.
9. Validar contra **XSD** y contra las **reglas de validación** locales antes de enviar (evita quemar numeración).
10. Entornos por tenant: `BETA` / `PRODUCCION` → cambia WSDL y credenciales.

---

## 10. Pendientes de confirmar

- URL beta para GRE REST (los manuales oficiales no la publican).
- Estructura XML detallada de `DespatchAdvice` para GRE 2022 (descargar la guía específica cuando se llegue a esa fase).
- Si se soportará `sendPack` (lotes). Propuesta: no en v1.
