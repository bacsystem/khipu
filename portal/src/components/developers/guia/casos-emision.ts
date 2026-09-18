/**
 * Casos de emisión de factura documentados en la guía. Cada uno lleva el JSON exacto que acepta `POST /v1/facturas`,
 * qué calcula khipu y las reglas SUNAT que aplican. `disponible: false` marca los casos cuyo soporte está en desarrollo
 * (se muestran para que el integrador planifique, con la advertencia correspondiente).
 */
export type CasoEmision = {
  id: string;
  titulo: string;
  cuando: string;
  request: string;
  notas: string[];
  disponible: boolean;
  /** Endpoint del request; por defecto `POST /v1/facturas`. */
  endpoint?: string;
};

const CLIENTE = `"cliente": {
    "tipo_doc": "6",
    "num_doc": "20601234567",
    "razon_social": "COMERCIAL ANDINA S.A.C.",
    "direccion": "Av. Javier Prado Este 123, San Isidro, Lima"
  }`;

export const CASOS: CasoEmision[] = [
  {
    id: "gravada-contado",
    titulo: "Venta gravada al contado (el caso base)",
    cuando: "Venta de bienes o servicios con IGV, pagada al emitir. Es la factura más común.",
    request: `POST /v1/facturas
X-Api-Key: fk_TU_API_KEY
Content-Type: application/json

{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    {
      "codigo": "SRV-001",
      "descripcion": "Servicio de consultoría – setiembre 2026",
      "unidad": "ZZ",
      "cantidad": 1,
      "precio_unitario": 1180.00,
      "tipo_afectacion_igv": "10"
    },
    {
      "codigo": "LAP-15",
      "descripcion": "Laptop 15\\" 16 GB RAM",
      "unidad": "NIU",
      "cantidad": 2,
      "precio_unitario": 2360.00,
      "tipo_afectacion_igv": "10"
    }
  ]
}`,
    notas: [
      "`precio_unitario` es el precio **con IGV**: khipu calcula el valor unitario (1 000.00), el IGV (18 %) y los totales; no envíe importes sin IGV.",
      "`correlativo` omitido: khipu asigna el siguiente número de la serie F001 de forma atómica (sin huecos ni duplicados aunque emita en paralelo).",
      "`tipo_operacion` omitido = `0101` (venta interna). `forma_pago` omitida = contado.",
      "Unidades: `ZZ` para servicios, `NIU` para bienes contables; otras en el catálogo 03.",
      "Respuesta `201` con `estado_documento` `ACEPTADO` si SUNAT respondió en la misma llamada.",
    ],
    disponible: true,
  },
  {
    id: "mixta",
    titulo: "Ítems gravados, exonerados e inafectos en la misma factura",
    cuando: "Una venta que combina productos con IGV, exonerados por ley (Apéndice I: p. ej. libros, ciertos alimentos) e inafectos (fuera del ámbito del IGV).",
    request: `{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Impresora láser", "unidad": "NIU", "cantidad": 1, "precio_unitario": 590.00, "tipo_afectacion_igv": "10" },
    { "descripcion": "Libro técnico (exonerado)", "unidad": "NIU", "cantidad": 3, "precio_unitario": 80.00, "tipo_afectacion_igv": "20" },
    { "descripcion": "Tasa administrativa (inafecta)", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 25.00, "tipo_afectacion_igv": "30" }
  ]
}`,
    notas: [
      "Para `20` y `30` el `precio_unitario` **no incluye IGV** (no lo hay): valor = precio.",
      "khipu agrupa los totales por afectación (`totales.gravado`, `exonerado`, `inafecto`) y arma un `TaxSubtotal` por cada tributo (IGV 1000, EXO 9997, INA 9998) como exige SUNAT.",
      "Use `20` solo si el bien o servicio está en el Apéndice I de la Ley del IGV; `30` para operaciones fuera del ámbito (p. ej. venta de terrenos, tasas). Usar el código equivocado no se detecta localmente pero es una contingencia tributaria.",
    ],
    disponible: true,
  },
  {
    id: "dolares",
    titulo: "Factura en dólares",
    cuando: "Operaciones pactadas en moneda extranjera. Todos los importes del comprobante van en esa moneda.",
    request: `{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "USD",
  ${CLIENTE},
  "items": [
    { "descripcion": "Licencia anual de software", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 1180.00, "tipo_afectacion_igv": "10" }
  ]
}`,
    notas: [
      "Monedas aceptadas: `PEN`, `USD`, `EUR` (catálogo 02). No se mezclan monedas en un comprobante (SUNAT 2071).",
      "El monto en letras del XML se genera en la moneda indicada (\"… CON 00/100 DÓLARES AMERICANOS\").",
      "El tipo de cambio no forma parte de la factura electrónica; llévelo en su sistema contable.",
    ],
    disponible: true,
  },
  {
    id: "credito",
    titulo: "Al crédito con cuotas",
    cuando: "El cliente paga después de la emisión, en una o varias cuotas. Obligatorio declararlo desde 2022 (RS 193-2020); habilita el factoring de la factura.",
    request: `{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Implementación ERP – fase 1", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 11800.00, "tipo_afectacion_igv": "10" }
  ],
  "forma_pago": {
    "tipo": "credito",
    "monto_pendiente": 11800.00,
    "cuotas": [
      { "monto": 5900.00, "vencimiento": "2026-10-17" },
      { "monto": 5900.00, "vencimiento": "2026-11-17" }
    ]
  }
}`,
    notas: [
      "`monto_pendiente` es lo que queda por pagar al emitir (puede ser menor que el total si hubo un adelanto) y **debe ser igual a la suma de las cuotas** (SUNAT 3319).",
      "Cada cuota vence **después** de la fecha de emisión (3267). khipu numera las cuotas `Cuota001`, `Cuota002`… en el orden enviado.",
      "Si envía `\"tipo\": \"contado\"` con cuotas o `monto_pendiente`, la API responde `422 FORMA_PAGO_INVALIDA` con `3252 - …`.",
      "La respuesta devuelve `forma_pago` con los identificadores SUNAT de cada cuota.",
    ],
    disponible: true,
  },
  {
    id: "sin-enviar",
    titulo: "Emitir sin enviar y enviar después",
    cuando: "Emisión en lote fuera de horario, o cuando quiere revisar el XML firmado antes de que llegue a SUNAT.",
    request: `{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Mantenimiento mensual", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 354.00, "tipo_afectacion_igv": "10" }
  ],
  "enviar_automatico": false
}

// más tarde:
POST /v1/facturas/{id}/enviar
X-Api-Key: fk_TU_API_KEY`,
    notas: [
      "El comprobante queda `FIRMADO` con su número asignado y su XML descargable (`GET /v1/facturas/{id}/xml`).",
      "Recuerde el plazo: SUNAT debe recibirlo dentro de los **3 días calendario** siguientes a `fecha_emision`.",
      "`POST /v1/facturas/{id}/enviar` también sirve para reintentar un `ERROR_ENVIO` sin esperar al reintento automático.",
    ],
    disponible: true,
  },
  {
    id: "correlativo",
    titulo: "Con número correlativo propio (idempotencia)",
    cuando: "Su sistema ya lleva la numeración y quiere que khipu la respete, o necesita reintentar una llamada sin riesgo de emitir dos veces.",
    request: `{
  "serie": "F001",
  "correlativo": 125,
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Servicio de transporte", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 236.00, "tipo_afectacion_igv": "10" }
  ]
}`,
    notas: [
      "Si `F001-125` ya existe la API responde `409 DUPLICADO` y no emite nada: puede reintentar la misma llamada con seguridad tras un timeout.",
      "Sin `correlativo`, khipu usa el siguiente de la serie; no mezcle ambos modos en la misma serie o dejará huecos.",
    ],
    disponible: true,
  },
  {
    id: "descuentos",
    titulo: "Descuentos por ítem y globales",
    cuando: "Promociones o negociaciones que rebajan una línea o el total sin manipular el precio unitario. Cada descuento es un porcentaje o un monto (sobre el valor sin IGV) y decide si afecta la base del IGV.",
    request: `{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Monitor 27\\"", "unidad": "NIU", "cantidad": 2, "precio_unitario": 1180.00, "tipo_afectacion_igv": "10",
      "descuento": { "porcentaje": 10 } },
    { "descripcion": "Cable HDMI", "unidad": "NIU", "cantidad": 1, "precio_unitario": 59.00, "tipo_afectacion_igv": "10",
      "descuento": { "monto": 5.00, "afecta_base_igv": false } }
  ],
  "descuento_global": { "porcentaje": 2 }
}`,
    notas: [
      "`descuento` por ítem: `porcentaje` **o** `monto` (nunca ambos), sobre el valor de venta sin IGV de la línea. `afecta_base_igv` (por defecto `true`) → código **00**: el IGV se calcula sobre la base rebajada; `false` → código **01**: descuento financiero, el IGV no cambia y solo baja lo que se paga.",
      "`descuento_global`: mismo formato. `afecta_base_igv: true` → código **02**, se aplica sobre la base gravada (requiere ítems gravados); `false` → **03**, se resta del importe a pagar.",
      "La respuesta devuelve por ítem `valor_venta`, `igv` y `descuento.monto`, y en `totales`: `total_valor_venta`, `total_precio_venta`, `total_descuentos` (los que no afectan la base) y `total` (a pagar).",
      "En el XML: `cac:AllowanceCharge` con factor, monto y base (reglas 3052, 2955, 3290 por línea; 3025, 2968, 3016 global) y `AllowanceTotalAmount` en los totales (3300).",
    ],
    disponible: true,
  },
  {
    id: "cargos",
    titulo: "Cargos por ítem y globales",
    cuando: "Conceptos que se cobran además del precio: flete, embalaje, gastos administrativos, recargo al consumo o propinas. Cada cargo es un porcentaje o un monto (sobre el valor sin IGV) y su código del catálogo 53 decide si paga IGV.",
    request: `{
  "serie": "F001",
  "fecha_emision": "2026-09-18",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Refrigeradora 300 L", "unidad": "NIU", "cantidad": 1, "precio_unitario": 1770.00, "tipo_afectacion_igv": "10",
      "cargos": [ { "monto": 50.00 }, { "monto": 20.00, "afecta_base_igv": false } ] }
  ],
  "cargos": [ { "porcentaje": 10, "motivo": "recargo_consumo" } ]
}`,
    notas: [
      "`items[].cargos[]`: mismo formato que `descuento` — `porcentaje` **o** `monto` (nunca ambos) sobre el valor de venta sin IGV de la línea. `afecta_base_igv` (por defecto `true`) → código **47**: se suma al valor de venta y paga IGV (flete gravado); `false` → **48**: se cobra sin IGV (reembolso de gastos). No se admiten en líneas gratuitas.",
      "`cargos[]` globales: `afecta_base_igv: true` → **49**, se suma a la base gravada y al IGV (requiere ítems gravados); `false` → **50**, sin IGV; `motivo: \"recargo_consumo\"` → **46**, recargo al consumo y/o propinas, que por la Ley 25988 nunca afecta la base (no admite `afecta_base_igv: true`). El porcentaje se aplica sobre la base gravada (49) o sobre toda la base onerosa (46/50).",
      "La respuesta devuelve por ítem `cargos[]` con `monto`, `afecta_base_igv`, `motivo` y el `codigo` SUNAT derivado, y en `totales`: `cargos[]` globales, `total_cargos` (los que no afectan la base: 48 + 46/50) y `total` = precio de venta + `total_cargos` − descuentos − anticipos. En el ejemplo: valor de venta 1 550.00, IGV 279.00, cargos sin IGV 20.00 + 155.00 → total 2 004.00.",
      "En el XML: `cac:AllowanceCharge` con `ChargeIndicator true`, factor, monto y base (reglas 3114, 3052, 2955, 3290 por línea; 3025, 2968, 3016, 3307 global), la base del IGV incluye 47/49 (3277, 3291) y `ChargeTotalAmount` en los totales (3301, 3280). FISE (45) no está soportado.",
    ],
    disponible: true,
  },
  {
    id: "documentos-relacionados",
    titulo: "Orden de compra, guías de remisión y otros documentos",
    cuando: "La factura acompaña un traslado con guía de remisión, responde a una orden de compra del cliente o sustenta un trámite (código SCOP, ticket ENAPU, declaración de importación…). Son datos informativos: no cambian importes.",
    request: `{
  "serie": "F001",
  "fecha_emision": "2026-09-18",
  "moneda": "PEN",
  ${CLIENTE},
  "orden_compra": "OC-2026-0457",
  "guias": [ { "tipo": "09", "numero": "T001-123" } ],
  "documentos_relacionados": [ { "tipo": "05", "numero": "SCOP-8841203" } ],
  "items": [
    { "descripcion": "Combustible diésel B5", "unidad": "GLL", "cantidad": 500, "precio_unitario": 15.90, "tipo_afectacion_igv": "10" }
  ]
}`,
    notas: [
      "`orden_compra`: 1–20 caracteres (admite espacios, no saltos de línea) → `cac:OrderReference` (regla 4233).",
      "`guias[]`: `tipo` **09** (guía de remisión remitente) o **31** (transportista), `numero` serie-número con el formato que exige SUNAT: electrónica `T001-123` / `V001-45`, física `0001-123`, `EG01-45`… (reglas 4005, 4006); no se admiten repetidas (2364) → `cac:DespatchDocumentReference`.",
      "`documentos_relacionados[]`: `tipo` del catálogo 12 —**04** ticket ENAPU, **05** código SCOP, **06** factura electrónica remitente, **07** guía remitente, **08** salida de depósito franco, **09** declaración simplificada de importación, **99** otros— y `numero` de hasta 30 caracteres sin espacios (reglas 4009, 4010, 2365) → `cac:AdditionalDocumentReference`. Las facturas de anticipo (02) van en `anticipos`, no aquí.",
      "La respuesta devuelve el bloque `referencias { orden_compra, guias[], documentos_relacionados[] }` solo cuando se envió alguno; el detalle del portal lo muestra como \"Documentos relacionados\". Error `DOCUMENTO_RELACIONADO_INVALIDO` (422) con la regla SUNAT en el mensaje.",
    ],
    disponible: true,
  },
  {
    id: "campos-opcionales",
    titulo: "Fecha de vencimiento, código de producto, GTIN y redondeo",
    cuando: "Datos que el cliente o SUNAT pueden esperar sin cambiar el cálculo: la fecha límite de pago, el código de producto SUNAT (obligatorio para los emisores del padrón: mineras, combustibles, explosivos…), el GTIN del producto y el redondeo para cobrar en efectivo sin céntimos.",
    request: `{
  "serie": "F001",
  "fecha_emision": "2026-09-18",
  "fecha_vencimiento": "2026-10-18",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Combustible diésel B5", "unidad": "GLL", "cantidad": 7, "precio_unitario": 16.91, "tipo_afectacion_igv": "10",
      "codigo_sunat": "15101505", "gtin": { "tipo": "GTIN-13", "codigo": "7750182000123" } }
  ],
  "redondeo": -0.37
}`,
    notas: [
      "`fecha_vencimiento` → `cbc:DueDate`; no puede ser anterior a `fecha_emision` (`FECHA_INVALIDA`). Es informativa: al crédito las cuotas de `forma_pago` siguen mandando.",
      "`items[].codigo_sunat`: 8 dígitos UNSPSC (catálogo 25) → `cac:CommodityClassification`. khipu valida el formato (regla 3496); SUNAT observa —no rechaza— los que no están en su listado (4332) o no llegan al tercer nivel (terminados en 0000, 4337). `GET /v1/catalogos/25` trae los listados 25.1 (padrón obligado), 25.2 (detracciones) y 25.3 (percepciones); el catálogo completo lo publica SUNAT.",
      "`items[].gtin { tipo, codigo }`: `GTIN-8`, `GTIN-12`, `GTIN-13` o `GTIN-14` con la longitud que corresponde (reglas 4333–4335) → `cac:StandardItemIdentification/cbc:ID@schemeID`.",
      "`redondeo`: entre −1.00 y 1.00, se suma al total a pagar (`PayableRoundingAmount`, regla 3303; `total` = precio de venta + cargos − descuentos − anticipos + redondeo, regla 3280); el monto en letras usa el total redondeado. En el ejemplo: 7 × 16.91 = 118.37 → total 118.00. Error `REDONDEO_INVALIDO`.",
      "El nombre comercial del emisor no va en cada factura: se configura una vez en `PUT /v1/empresa/datos-fiscales` (`nombre_comercial`) o en la página Empresa del portal y khipu lo escribe en `cac:PartyName` (regla 4092).",
    ],
    disponible: true,
  },
  {
    id: "gratuitas",
    titulo: "Bonificaciones y muestras (operaciones gratuitas)",
    cuando: "Entrega bienes o servicios sin cobrar: bonificación por volumen, muestras, publicidad, retiro para trabajadores. SUNAT exige informarlas con su valor referencial y, si son gravadas, con el IGV que habrían generado.",
    request: `{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Caja de 12 unidades", "unidad": "NIU", "cantidad": 10, "precio_unitario": 59.00, "tipo_afectacion_igv": "10" },
    { "descripcion": "Caja de 12 unidades – bonificación 10+1", "unidad": "NIU", "cantidad": 1, "precio_unitario": 50.00, "tipo_afectacion_igv": "15" },
    { "descripcion": "Muestra médica", "unidad": "NIU", "cantidad": 3, "precio_unitario": 8.00, "tipo_afectacion_igv": "33" }
  ]
}`,
    notas: [
      "Afectaciones gratuitas del catálogo 07: `11`–`16` (gravadas: retiro por premio, donación, retiro, publicidad, bonificación, entrega a trabajadores), `21` (exonerada) y `31`–`37` (inafectas). En estas líneas `precio_unitario` es el **valor referencial sin IGV**, no un precio de venta.",
      "La línea no suma al importe a pagar: la respuesta trae `gratuita: true`, `precio_venta: 0.00` y, en gravadas, el `igv` informativo. En `totales`, `gratuito` e `igv_gratuitas` van aparte de `total`.",
      "En el XML: `PriceTypeCode 02` (valor referencial), `Price/PriceAmount 0`, tributo `9996` (GRA) por línea y en un subtotal global propio, y la leyenda `1002` obligatoria. Reglas 2640, 3110, 3111, 3224, 3234, 3276, 3302.",
      "Una factura solo con gratuitas tiene `total` 0.00. No se soportan `17` (IVAP, arroz pilado) ni `40` (exportación).",
    ],
    disponible: true,
  },
  {
    id: "detraccion",
    titulo: "Operación sujeta a detracción (SPOT)",
    cuando: "Servicios y bienes del anexo de detracciones (transporte de carga, construcción, servicios empresariales, arrendamiento…) por más de S/ 700: el cliente deposita el porcentaje en su cuenta del Banco de la Nación y le paga el resto.",
    request: `{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  "tipo_operacion": "1001",
  ${CLIENTE},
  "items": [
    { "descripcion": "Servicio de consultoría empresarial", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 11800.00, "tipo_afectacion_igv": "10" }
  ],
  "detraccion": {
    "codigo_bien_servicio": "022",
    "porcentaje": 12,
    "cuenta_banco_nacion": "00-000-123456"
  }
}`,
    notas: [
      "`tipo_operacion` **1001** (sujeta a detracción; 1002 recursos hidrobiológicos, 1003 transporte de pasajeros, 1004 transporte de carga, que fijan el código 004/028/027). Con 1001–1004 la detracción es obligatoria (3127) y con cualquier otro tipo está prohibida (3128).",
      "`codigo_bien_servicio` del catálogo 54 y `porcentaje` según la tabla vigente del SPOT para ese bien/servicio (khipu no la impone: consulte la RS 183-2004 y sus modificatorias).",
      "`monto` siempre en **soles**: en facturas en PEN puede omitirlo y khipu lo calcula (total × %, redondeado al sol, como exige el SPOT); en USD/EUR debe enviarlo convertido al tipo de cambio del día.",
      "`cuenta_banco_nacion` es la cuenta de detracciones del emisor; puede omitirse si la empresa la tiene configurada (`PUT /v1/empresa/datos-fiscales` o página Empresa del portal), si no `422 DETRACCION_INVALIDA` (3034). `medio_pago` del catálogo 59 (por defecto `001` depósito en cuenta).",
      "La detracción no cambia los totales: la respuesta trae `detraccion` con la descripción del catálogo y el monto; el XML lleva `PaymentMeans`/`PaymentTerms` con indicador `Detraccion` y la leyenda 2006.",
    ],
    disponible: true,
  },
  {
    id: "retencion",
    titulo: "Con retención del IGV (cliente agente de retención)",
    cuando: "Su cliente es agente de retención designado por SUNAT: le retiene el 3 % del importe total y lo entera a SUNAT por usted. Se informa en la factura; no cambia los totales.",
    request: `{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Servicio de consultoría", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 11800.00, "tipo_afectacion_igv": "10" }
  ],
  "retencion_igv": {}
}`,
    notas: [
      "`retencion_igv: {}` basta: khipu aplica la tasa legal del 3 % sobre el importe total (`porcentaje` y `monto` son opcionales; si envía `monto`, debe coincidir ±1 con base × %, regla 3263).",
      "La respuesta trae `retencion_igv.monto` y `neto_cobrar` (total − retención). Si la factura es al crédito, ponga `monto_pendiente` = neto a cobrar.",
      "XML: `cac:AllowanceCharge` global con `ChargeIndicator false`, código **62**, factor 0.03, monto y base = importe total (reglas 3114, 3262–3264). SUNAT verifica además que el cliente esté en el padrón de agentes de retención y usted no (3262/3269): eso no se puede validar localmente.",
    ],
    disponible: true,
  },
  {
    id: "percepcion",
    titulo: "Con percepción del IGV (usted es agente de percepción)",
    cuando: "Su empresa está designada agente de percepción (venta interna de bienes del anexo, combustibles) y cobra la percepción además del importe de la factura.",
    request: `{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  "tipo_operacion": "2001",
  ${CLIENTE},
  "items": [
    { "descripcion": "Bebidas gaseosas (caja x 12)", "unidad": "NIU", "cantidad": 100, "precio_unitario": 35.40, "tipo_afectacion_igv": "10" }
  ],
  "percepcion": { "regimen": "51" }
}`,
    notas: [
      "Exige `tipo_operacion` **2001**, forma de pago al **contado** y moneda **PEN** (reglas 3308, 3330, 2788); con 2001 al contado la percepción es obligatoria (3093).",
      "`regimen` del catálogo 53: `51` venta interna (2 %), `52` combustible (1 %), `53` agente con tasa especial (0,5 %). La tasa la fija el régimen (catálogo 22); `base` (por defecto el importe total) y `monto` son opcionales y se validan con tolerancia ±1 (2797, 2798).",
      "El importe a pagar del comprobante (`totales.total`) **no** incluye la percepción; la respuesta trae `percepcion.total_con_percepcion`, que es lo que cobra al cliente, y el XML lleva el `AllowanceCharge` 51/52/53 (`ChargeIndicator true`), un `PaymentTerms` con indicador `Percepcion` y la leyenda 2000.",
    ],
    disponible: true,
  },
  {
    id: "anticipos",
    titulo: "Anticipos (factura de adelanto y factura final)",
    cuando: "El cliente paga por adelantado (total o parcialmente) antes de la entrega: primero se emite una factura por el anticipo y, al entregar, la factura final por la operación completa descontando lo ya pagado.",
    request: `1) Factura de anticipo: una factura normal cuyo ítem describe el adelanto
{
  "serie": "F001",
  "fecha_emision": "2026-09-01",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Anticipo 30 % – fabricación de mobiliario (contrato 2026-045)", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 3540.00, "tipo_afectacion_igv": "10" }
  ]
}
→ F001-120: valor 3000.00 + IGV 540.00 = 3540.00 (debe quedar ACEPTADO)

2) Factura final: los ítems describen la operación completa y anticipos[] lo ya facturado
{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Fabricación de mobiliario (contrato 2026-045)", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 11800.00, "tipo_afectacion_igv": "10" }
  ],
  "anticipos": [
    { "serie": "F001", "numero": 120, "monto": 3000.00, "fecha_pago": "2026-09-01" }
  ]
}`,
    notas: [
      "`anticipos[].serie` y `numero` identifican la factura de anticipo: debe ser de esta misma empresa, al mismo cliente, en la misma moneda y estar `ACEPTADO` o `ACEPTADO_CON_OBS` (SUNAT la busca en su registro: regla 3218). Si no cumple, `422 ANTICIPO_INVALIDO`.",
      "`monto` es el **valor sin IGV** que se regulariza (en el ejemplo 3000.00). khipu calcula el importe pagado con IGV (3540.00), no puede superar lo facturado en el anticipo ni lo facturado en esta factura para la misma afectación.",
      "`afectacion` (opcional): `gravado` (código 04, por defecto), `exonerado` (05) o `inafecto` (06). Decide de qué base se descuenta: un anticipo gravado reduce la base del IGV (reglas 3277, 3291); uno exonerado/inafecto reduce esa base sin IGV.",
      "Cálculo del ejemplo: operación 10000.00 + IGV 1800.00 = 11800.00; anticipo 3000.00 → base gravada 7000.00, IGV 1260.00; `total_anticipos` 3540.00; `total` a pagar 11800.00 − 3540.00 = 8260.00. `total_valor_venta` y `total_precio_venta` siguen siendo los brutos (10000.00 y 11800.00, reglas 3278/3279).",
      "En el XML cada anticipo va como `AdditionalDocumentReference` (tipo 02, RUC del emisor, identificador de pago), `PrepaidPayment` (importe con IGV) y `AllowanceCharge` 04/05/06; los totales llevan `PrepaidAmount` (reglas 2503, 2509, 3211–3220, 3282, 3287).",
      "Un anticipo por el 100 % deja base, IGV y total en 0.00: la factura final sigue siendo obligatoria para documentar la entrega. Con forma de pago al crédito, `monto_pendiente` y las cuotas se validan contra el saldo tras anticipos.",
      "La respuesta devuelve `anticipos[]` con `comprobante`, `monto`, `importe_pagado`, `afectacion`, `codigo_sunat` y `fecha_pago`, y en `totales` el campo `total_anticipos`.",
    ],
    disponible: true,
  },
  {
    id: "isc-icbper",
    titulo: "ISC e ICBPER",
    cuando: "Bienes afectos al Impuesto Selectivo al Consumo (bebidas alcohólicas, gaseosas, combustibles, vehículos, cigarrillos…) o bolsas de plástico (ICBPER, monto fijo por bolsa según el año).",
    request: `{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Cerveza 620 ml (caja x 12)", "unidad": "NIU", "cantidad": 10, "precio_unitario": 159.30, "tipo_afectacion_igv": "10",
      "isc": { "sistema": "01", "tasa": 35 } },
    { "descripcion": "Pisco 750 ml", "unidad": "NIU", "cantidad": 6, "precio_unitario": 8.555, "tipo_afectacion_igv": "10",
      "isc": { "sistema": "02", "monto_unitario": 2.25 } },
    { "descripcion": "Bolsa plástica", "unidad": "NIU", "cantidad": 3, "precio_unitario": 0.618, "tipo_afectacion_igv": "10", "icbper": true }
  ]
}`,
    notas: [
      "`isc.sistema` del catálogo 08: `01` al valor lleva `tasa` (%) sobre el valor de venta; `02` monto fijo lleva `monto_unitario`. El `precio_unitario` incluye ISC e IGV: khipu separa valor, ISC e IGV (ejemplo: 159.30 = 100 × 1.35 × 1.18). `03` (precio de venta al público) **no está soportado**: su base es el PVP sugerido, que la API aún no recibe; se responde `422`.",
      "El ISC forma parte de la base del IGV (regla 204) y se informa en un `TaxSubtotal` 2000 por línea (con `TierRange` = sistema) y global (reglas 3108, 2373, 3210).",
      "`icbper: true` marca bolsas de plástico: una bolsa por unidad (`unidad` NIU), monto fijo vigente por año (S/ 0.50 desde 2023, Ley 30884) incluido en el precio; se informa como tributo 7152 sin base ni tasa (reglas 3236–3238).",
      "La respuesta trae por ítem `isc {sistema, tasa, monto}` e `icbper`, y en `totales` `isc` e `icbper`; `total_precio_venta` los incluye (regla 55).",
    ],
    disponible: true,
  },
  {
    id: "notas",
    titulo: "Notas de crédito y débito",
    endpoint: "/v1/notas",
    cuando: "Corregir o anular una factura ya aceptada: devoluciones (total o por ítem), descuentos posteriores, anulación por error, reprogramación de cuotas (nota de crédito); intereses por mora, penalidades o aumentos de valor (nota de débito).",
    request: `{
  "tipo": "07",
  "serie": "FC01",
  "fecha_emision": "2026-09-18",
  "documento_afectado": { "serie": "F001", "numero": 125 },
  "motivo": "01",
  "descripcion": "Anulación de la operación por error en el pedido"
}`,
    notas: [
      "`tipo` **07** nota de crédito / **08** nota de débito; `serie` registrada con ese tipo y que empiece por `F` (p. ej. `FC01`, `FD01`; regla 1001). `documento_afectado` debe ser una factura de la empresa **aceptada** por SUNAT y no anulada (reglas 2119, 2120); la nota toma su cliente, moneda y tipo de operación, y su fecha no puede ser anterior (2885).",
      "`motivo` del catálogo **09** (NC: `01` anulación, `02` error en el RUC, `04`/`05` descuentos, `06`/`07` devoluciones, `09` disminución, `13` corrección de cuotas…) o **10** (ND: `01` intereses por mora, `02` aumento en el valor, `03` penalidades…); `descripcion` es el sustento (1–500 caracteres, regla 2135).",
      "**Nota total**: sin `items`, khipu copia ítems, descuento global y cargos de la factura. **Nota parcial**: envíe `items` con el mismo formato que en la factura (p. ej. una laptop de las dos facturadas). Una nota de crédito nunca supera los importes de la factura, ni en total (3286) ni por tributo (3503); una nota de débito no tiene tope.",
      "**NC 13** (reprogramar cuotas de una factura al crédito): envíe `forma_pago` al crédito con las cuotas corregidas; la nota sale con importe 0 (regla 3315; `items` se ignora) y las cuotas se validan contra la factura (3320, 3321).",
      "La respuesta es el mismo comprobante que en facturas más el bloque `nota { tipo_afectado, documento_afectado, motivo, motivo_descripcion, descripcion }`; consulta, XML, CDR y reenvío van por `GET /v1/facturas/{id}`…, y la factura lista sus notas en `notas[]`. En el XML: `CreditNote`/`DebitNote` con `cac:DiscrepancyResponse` y `cac:BillingReference`. Error `NOTA_INVALIDA` (422) con la regla SUNAT en el mensaje. Homologado en e-beta: NC total, parcial, 13 y ND.",
    ],
    disponible: true,
  },
];

export const RESPUESTA_EJEMPLO = `{
  "estado": "exito",
  "datos": {
    "id": "5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f",
    "tipo": "01",
    "serie": "F001",
    "numero": 125,
    "fecha_emision": "2026-09-17",
    "moneda": "PEN",
    "tipo_operacion": "0101",
    "receptor": { "tipo_doc": "6", "num_doc": "20601234567", "razon_social": "COMERCIAL ANDINA S.A.C.", "direccion": "Av. Javier Prado Este 123, San Isidro, Lima" },
    "items": [
      { "codigo": "SRV-001", "descripcion": "Servicio de consultoría – setiembre 2026", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 1180.00, "tipo_afectacion_igv": "10" }
    ],
    "estado_documento": "ACEPTADO",
    "hash": "y4M8+jW8Xp278K1aM02q19KjvO3k=",
    "nombre_archivo": "20123456789-01-F001-00000125",
    "intentos": 1,
    "ultimo_error": null,
    "cdr": { "codigo": "0", "descripcion": "La Factura numero F001-125, ha sido aceptada", "observaciones": [] },
    "totales": { "gravado": 1000.00, "exonerado": 0.00, "inafecto": 0.00, "igv": 180.00, "total": 1180.00 },
    "forma_pago": { "tipo": "contado", "monto_pendiente": null, "cuotas": [] },
    "enlaces": { "xml": "/v1/facturas/5f2c…/xml", "cdr": "/v1/facturas/5f2c…/cdr" }
  }
}`;

export const ERROR_EJEMPLO = `HTTP/1.1 422 Unprocessable Entity

{
  "estado": "error",
  "codigo": "FORMA_PAGO_INVALIDA",
  "mensaje": "3319 - La suma de las cuotas (5000.00) debe ser igual al monto neto pendiente de pago (11800.00)",
  "datos": null,
  "errores": null
}`;
