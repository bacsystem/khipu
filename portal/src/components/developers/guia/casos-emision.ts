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
    cuando: "Promociones o negociaciones que rebajan una línea o el total sin manipular el precio unitario.",
    request: `{
  "...": "campos habituales",
  "items": [
    { "descripcion": "Monitor 27\\"", "unidad": "NIU", "cantidad": 2, "precio_unitario": 1180.00, "tipo_afectacion_igv": "10",
      "descuento": { "porcentaje": 10 } }
  ],
  "descuento_global": { "monto": 100.00 }
}`,
    notas: ["Códigos del catálogo 53: `00` descuento por ítem que afecta la base del IGV, `02` descuento global que afecta la base, `03` que no la afecta."],
    disponible: false,
  },
  {
    id: "gratuitas",
    titulo: "Bonificaciones y muestras (operaciones gratuitas)",
    cuando: "Entrega bienes o servicios sin cobrar: bonificación por volumen, muestras, publicidad, retiro para trabajadores.",
    request: `{
  "...": "campos habituales",
  "items": [
    { "descripcion": "Producto bonificado", "unidad": "NIU", "cantidad": 5, "precio_unitario": 59.00, "tipo_afectacion_igv": "15" }
  ]
}`,
    notas: [
      "Afectaciones gratuitas del catálogo 07: `11`–`17` (gravadas), `21` (exonerada), `31`–`37` (inafectas). El `precio_unitario` es el **valor referencial** (`PriceTypeCode 02`); no suma al total a pagar.",
      "khipu añade la leyenda 1002 obligatoria y el IGV de gratuitas (tributo 9996) fuera del total.",
    ],
    disponible: false,
  },
  {
    id: "detraccion",
    titulo: "Operación sujeta a detracción (SPOT)",
    cuando: "Servicios y bienes del anexo de detracciones (transporte de carga, construcción, servicios empresariales…) por más de S/ 700: el cliente deposita el porcentaje en su cuenta del Banco de la Nación.",
    request: `{
  "...": "campos habituales",
  "tipo_operacion": "1001",
  "detraccion": {
    "codigo_bien_servicio": "022",
    "porcentaje": 12,
    "monto": 1416.00,
    "cuenta_banco_nacion": "00-000-123456",
    "medio_pago": "001"
  }
}`,
    notas: ["Códigos del catálogo 54 (bien o servicio) y 59 (medio de pago). El monto de la detracción siempre va en soles. khipu añade la leyenda 2006."],
    disponible: false,
  },
  {
    id: "retencion",
    titulo: "Con retención del IGV (cliente agente de retención)",
    cuando: "Su cliente es agente de retención designado por SUNAT y le retendrá el 3 % del total.",
    request: `{
  "...": "campos habituales",
  "retencion_igv": { "porcentaje": 3, "monto": 354.00 }
}`,
    notas: ["Se declara como cargo/descuento `62` (catálogo 53); el neto pendiente de pago de la forma de pago debe descontarla."],
    disponible: false,
  },
  {
    id: "percepcion",
    titulo: "Con percepción del IGV (usted es agente de percepción)",
    cuando: "Su empresa está designada agente de percepción (venta interna 2 %, combustibles 1 %, agente 0,5 %).",
    request: `{
  "...": "campos habituales",
  "percepcion": { "regimen": "51", "porcentaje": 2, "monto": 236.00 }
}`,
    notas: ["Códigos 51/52/53 del catálogo 53; la percepción se suma al importe a pagar (`PayableAmount`) y lleva la leyenda 2000."],
    disponible: false,
  },
  {
    id: "anticipos",
    titulo: "Anticipos (factura de adelanto y factura final)",
    cuando: "Cobra un adelanto con una factura y luego emite la factura final descontándolo.",
    request: `{
  "...": "campos habituales de la factura final",
  "anticipos": [
    { "serie": "F001", "numero": 120, "monto": 5900.00 }
  ]
}`,
    notas: ["La factura de anticipo debe existir y estar aceptada; el total de anticipos se descuenta con el código `04` y `PrepaidAmount`."],
    disponible: false,
  },
  {
    id: "isc-icbper",
    titulo: "ISC e ICBPER",
    cuando: "Bienes afectos al Impuesto Selectivo al Consumo (bebidas alcohólicas, combustibles…) o bolsas de plástico (ICBPER, monto fijo por bolsa).",
    request: `{
  "...": "campos habituales",
  "items": [
    { "descripcion": "Cerveza 620 ml", "unidad": "NIU", "cantidad": 24, "precio_unitario": 6.50, "tipo_afectacion_igv": "10",
      "isc": { "sistema": "01", "porcentaje": 35 } },
    { "descripcion": "Bolsa plástica", "unidad": "NIU", "cantidad": 3, "precio_unitario": 0.10, "tipo_afectacion_igv": "10", "icbper": true }
  ]
}`,
    notas: ["Sistemas de ISC del catálogo 08 (`01` al valor, `02` específico, `03` precio de venta al público). El ISC forma parte de la base del IGV."],
    disponible: false,
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
