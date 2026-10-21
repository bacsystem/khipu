import type { Mensajes } from "@/i18n/mensajes";
import { tecnico } from "@/lib/developers/tecnico";
import es from "@/messages/es.json";

/**
 * Casos de emisión de factura, boleta, liquidación de compra, retención y percepción documentados en la guía. Cada uno lleva el JSON exacto que acepta su endpoint (`POST /v1/facturas` si no dice otro),
 * qué calcula khipu y las reglas SUNAT que aplican. `disponible: false` marca los casos cuyo soporte está en desarrollo
 * (se muestran para que el integrador planifique, con la advertencia correspondiente). El request es técnico y vive aquí; el título, cuándo usarlo y las notas
 * están en `messages.developers.casos.<id>` (#25).
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

type IdDeCaso = keyof Mensajes["developers"]["casos"];
type BaseDeCaso = Omit<CasoEmision, "id" | "titulo" | "cuando" | "notas"> & { id: IdDeCaso };

const CLIENTE = tecnico`"cliente": {
    "tipo_doc": "6",
    "num_doc": "20601234565",
    "razon_social": "COMERCIAL ANDINA S.A.C.",
    "direccion": "Av. Javier Prado Este 123, San Isidro, Lima"
  }`;

const CASOS: BaseDeCaso[] = [
  {
    id: "gravada-contado",
    request: tecnico`POST /v1/facturas
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
    disponible: true,
  },
  {
    id: "mixta",
    request: tecnico`{
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
    disponible: true,
  },
  {
    id: "dolares",
    request: tecnico`{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "USD",
  "tipo_cambio": 3.756,
  ${CLIENTE},
  "items": [
    { "descripcion": "Licencia anual de software", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 1180.00, "tipo_afectacion_igv": "10" }
  ]
}`,
    disponible: true,
  },
  {
    id: "credito",
    request: tecnico`{
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
    disponible: true,
  },
  {
    id: "calcular",
    request: tecnico`POST /v1/facturas/calcular

{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Servicio de consultoría", "unidad": "ZZ", "cantidad": 2, "precio_unitario": 590.00, "tipo_afectacion_igv": "10",
      "descuento": { "porcentaje": 10, "afecta_base_igv": true } }
  ],
  "descuento_global": { "monto": 20.00, "afecta_base_igv": true },
  "redondeo": 0.05
}`,
    disponible: true,
  },
  {
    id: "sin-enviar",
    request: tecnico`{
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
    disponible: true,
  },
  {
    id: "correlativo",
    request: tecnico`{
  "serie": "F001",
  "correlativo": 125,
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Servicio de transporte", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 236.00, "tipo_afectacion_igv": "10" }
  ]
}`,
    disponible: true,
  },
  {
    id: "idempotency-key",
    endpoint: "/v1/notas",
    request: tecnico`POST /v1/notas
X-Api-Key: fk_TU_API_KEY
Content-Type: application/json
Idempotency-Key: 5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f

{
  "tipo": "07",
  "serie": "FC01",
  "fecha_emision": "2026-09-18",
  "documento_afectado": { "serie": "F001", "numero": 125 },
  "motivo": "01",
  "descripcion": "Anulación de la operación por error en el pedido"
}

// 1.ª llamada: 201 Created, nota FC01-1 (si la respuesta se pierde, repita la MISMA llamada con la MISMA clave)
// reintento:   200 OK, la misma nota FC01-1: mismo id y número, sin emitir otra ni consumir número ni volver a enviarla a SUNAT
// misma clave con otro cuerpo: 422 IDEMPOTENCIA_INVALIDA`,
    disponible: true,
  },
  {
    id: "descuentos",
    request: tecnico`{
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
    disponible: true,
  },
  {
    id: "cargos",
    request: tecnico`{
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
    disponible: true,
  },
  {
    id: "documentos-relacionados",
    request: tecnico`{
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
    disponible: true,
  },
  {
    id: "campos-opcionales",
    request: tecnico`{
  "serie": "F001",
  "fecha_emision": "2026-09-18",
  "fecha_vencimiento": "2026-10-18",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Combustible diésel B5", "unidad": "GLL", "cantidad": 7, "precio_unitario": 16.91, "tipo_afectacion_igv": "10",
      "codigo_sunat": "15101505", "gtin": { "tipo": "GTIN-13", "codigo": "7750182000123" } }
  ],
  "redondeo": -0.37,
  "observaciones": "Entrega en almacén central. Horario: 9 a 18 h."
}`,
    disponible: true,
  },
  {
    id: "gratuitas",
    request: tecnico`{
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
    disponible: true,
  },
  {
    id: "detraccion",
    request: tecnico`{
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
    disponible: true,
  },
  {
    id: "hidrobiologicos",
    request: tecnico`{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  "tipo_operacion": "1002",
  ${CLIENTE},
  "items": [
    { "descripcion": "Anchoveta fresca", "unidad": "TNE", "cantidad": 12.5, "precio_unitario": 1180.00, "tipo_afectacion_igv": "10",
      "hidrobiologico": {
        "matricula": "CO-12345-PM", "nombre_embarcacion": "DON JOSÉ II", "especie": "Anchoveta (Engraulis ringens)",
        "lugar_descarga": "Muelle de Chimbote", "fecha_descarga": "2026-09-15", "cantidad": 12.5
      } }
  ],
  "detraccion": { "codigo_bien_servicio": "004", "porcentaje": 4, "cuenta_banco_nacion": "00-000-123456" }
}`,
    disponible: true,
  },
  {
    id: "transporte-carga",
    request: tecnico`{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  "tipo_operacion": "1004",
  ${CLIENTE},
  "items": [
    { "descripcion": "Flete Chimbote – Lima", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 2950.00, "tipo_afectacion_igv": "10",
      "transporte": {
        "origen":  { "ubigeo": "021801", "direccion": "Av. Los Pescadores 450, Chimbote" },
        "destino": { "ubigeo": "150101", "direccion": "Jr. de la Unión 100, Lima" },
        "detalle_viaje": "Traslado de 20 t de harina de pescado en camión furgón",
        "valor_referencial": { "servicio": 2500.00, "carga_efectiva": 2400.00, "carga_util_nominal": 2600.00 },
        "tramos": [
          { "origen_ubigeo": "021801", "destino_ubigeo": "150101", "descripcion": "Chimbote – Lima por Panamericana Norte", "valor_carga_efectiva": 2400.00,
            "vehiculos": [ { "configuracion": "T3S3", "carga_util_tm": 30, "carga_efectiva_tm": 20 } ] }
        ]
      } }
  ],
  "detraccion": { "codigo_bien_servicio": "027", "porcentaje": 4, "cuenta_banco_nacion": "00-000-123456" }
}`,
    disponible: true,
  },
  {
    id: "retencion",
    request: tecnico`{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Servicio de consultoría", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 11800.00, "tipo_afectacion_igv": "10" }
  ],
  "retencion_igv": {}
}`,
    disponible: true,
  },
  {
    id: "percepcion",
    request: tecnico`{
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
    disponible: true,
  },
  {
    id: "anticipos",
    request: tecnico`1) Factura de anticipo: una factura normal cuyo ítem describe el adelanto
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
    disponible: true,
  },
  {
    id: "isc-icbper",
    request: tecnico`{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Cerveza 620 ml (caja x 12)", "unidad": "NIU", "cantidad": 10, "precio_unitario": 159.30, "tipo_afectacion_igv": "10",
      "isc": { "sistema": "01", "tasa": 35 } },
    { "descripcion": "Pisco 750 ml", "unidad": "NIU", "cantidad": 6, "precio_unitario": 8.555, "tipo_afectacion_igv": "10",
      "isc": { "sistema": "02", "monto_unitario": 2.25 } },
    { "descripcion": "Cerveza 620 ml (botella)", "unidad": "NIU", "cantidad": 10, "precio_unitario": 3.599, "tipo_afectacion_igv": "10",
      "isc": { "sistema": "03", "tasa": 30, "base_pvp": 3.50 } },
    { "descripcion": "Bolsa plástica", "unidad": "NIU", "cantidad": 3, "precio_unitario": 0.618, "tipo_afectacion_igv": "10", "icbper": true }
  ]
}`,
    disponible: true,
  },
  {
    id: "otros-tributos-placa",
    request: tecnico`{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Diésel B5", "unidad": "GLL", "cantidad": 10, "precio_unitario": 17.70, "tipo_afectacion_igv": "10",
      "otros_tributos": { "tasa": 3.5 }, "placa": "ABC-123" }
  ]
}`,
    disponible: true,
  },
  {
    id: "ivap",
    request: tecnico`{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  ${CLIENTE},
  "items": [
    { "descripcion": "Arroz pilado superior", "unidad": "KGM", "cantidad": 500, "precio_unitario": 3.12, "tipo_afectacion_igv": "17" }
  ]
}`,
    disponible: true,
  },
  {
    id: "exportacion",
    request: tecnico`{
  "serie": "F001",
  "fecha_emision": "2026-09-17",
  "tipo_operacion": "0200",
  "moneda": "USD",
  "cliente": { "tipo_doc": "0", "num_doc": "US123456789", "razon_social": "ACME IMPORTS LLC", "direccion": "1200 Main St, Miami FL", "pais": "US" },
  "items": [
    { "descripcion": "Café verde en grano", "unidad": "KGM", "cantidad": 1000, "precio_unitario": 4.50, "tipo_afectacion_igv": "40" }
  ],
  "exportacion": { "incoterm": "FOB" }
}`,
    disponible: true,
  },
  {
    id: "boleta-dni",
    request: tecnico`{
  "serie": "B001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  "cliente": { "tipo_doc": "1", "num_doc": "45678912", "razon_social": "MARÍA LÓPEZ QUISPE" },
  "items": [
    { "codigo": "P001", "descripcion": "Arroz extra 5 kg", "unidad": "NIU", "cantidad": 2, "precio_unitario": 23.60, "tipo_afectacion_igv": "10" },
    { "codigo": "P002", "descripcion": "Leche evaporada 400 g", "unidad": "NIU", "cantidad": 6, "precio_unitario": 4.72, "tipo_afectacion_igv": "10" }
  ]
}`,
    disponible: true,
  },
  {
    id: "boleta-sin-documento",
    request: tecnico`{
  "serie": "B001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  "cliente": { "tipo_doc": "-", "num_doc": "-", "razon_social": "CLIENTES VARIOS" },
  "items": [
    { "descripcion": "Menú ejecutivo", "unidad": "NIU", "cantidad": 2, "precio_unitario": 18.00, "tipo_afectacion_igv": "10" },
    { "descripcion": "Chicha morada 1 L", "unidad": "NIU", "cantidad": 1, "precio_unitario": 9.50, "tipo_afectacion_igv": "10" }
  ]
}`,
    disponible: true,
  },
  {
    id: "boleta-alta-tardia",
    endpoint: "/v1/facturas/{id}/enviar",
    request: tecnico`// B001-88, emitida el 2026-09-17 con "enviar_automatico": false
POST /v1/facturas/{id}/enviar
X-Api-Key: fk_TU_API_KEY

// respuesta (extracto), el 2026-09-23 (6.º día)
{
  "estado": "exito",
  "datos": {
    "serie": "B001",
    "numero": 88,
    "estado_documento": "ACEPTADO",
    "fecha_limite_envio": "2026-09-24",
    "baja": {
      "identificador": "RC-20260923-1",
      "comprobante": "B001-88",
      "tipo_comprobante": "03",
      "fecha_referencia": "2026-09-17",
      "condicion": "ALTA",
      "fuera_de_plazo": false,
      "estado": "ACEPTADA"
    }
  }
}`,
    disponible: true,
  },
  {
    id: "notas",
    endpoint: "/v1/notas",
    request: tecnico`{
  "tipo": "07",
  "serie": "FC01",
  "fecha_emision": "2026-09-18",
  "documento_afectado": { "serie": "F001", "numero": 125 },
  "motivo": "01",
  "descripcion": "Anulación de la operación por error en el pedido"
}`,
    disponible: true,
  },
  {
    id: "notas-boleta",
    endpoint: "/v1/notas",
    request: tecnico`{
  "tipo": "07",
  "serie": "BC01",
  "fecha_emision": "2026-09-18",
  "documento_afectado": { "serie": "B001", "numero": 87 },
  "motivo": "07",
  "descripcion": "Devolución de un producto",
  "items": [
    {
      "codigo": "P002",
      "descripcion": "Leche evaporada 400 g",
      "unidad": "NIU",
      "cantidad": 1,
      "precio_unitario": 4.72,
      "tipo_afectacion_igv": "10"
    }
  ]
}`,
    disponible: true,
  },
  {
    id: "liquidacion-compra",
    endpoint: "/v1/liquidaciones-compra",
    request: tecnico`{
  "serie": "L001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  "vendedor": {
    "tipo_doc": "1",
    "num_doc": "45678912",
    "nombre": "JUAN QUISPE MAMANI",
    "direccion": "Caserío Santa Rosa s/n",
    "ubigeo": "060101",
    "condicion": "02"
  },
  "lugar_operacion": {
    "direccion": "Fundo Santa Rosa, km 12 carretera a Bambamarca",
    "ubigeo": "060101",
    "condicion": "02"
  },
  "items": [
    {
      "codigo": "CAF-01",
      "descripcion": "Café pergamino seco",
      "unidad": "KGM",
      "cantidad": 100,
      "precio_unitario": 11.80,
      "tipo_afectacion_igv": "10",
      "codigo_sunat": "50201706"
    }
  ]
}

// respuesta 201 (extracto)
{
  "estado": "exito",
  "datos": {
    "tipo": "04",
    "serie": "L001",
    "tipo_operacion": "0501",
    "fecha_limite_envio": "2026-09-18",
    "receptor": { "tipo_doc": "1", "num_doc": "45678912", "razon_social": "JUAN QUISPE MAMANI" },
    "totales": { "gravado": 1000.00, "igv": 180.00, "total": 985.00 },
    "liquidacion": {
      "subtotal": 1180.00,
      "igv_retenido": 180.00,
      "retencion_renta": { "tasa": 1.5, "base": 1000.00, "monto": 15.00 },
      "neto_pagar": 985.00
    }
  }
}`,
    disponible: true,
  },
  {
    id: "comprobante-retencion",
    endpoint: "/v1/retenciones",
    request: tecnico`{
  "serie": "R001",
  "fecha_emision": "2026-09-17",
  "proveedor": {
    "ruc": "20131312955",
    "razon_social": "PROVEEDOR DEL NORTE S.A.C.",
    "direccion": "Av. Grau 123, Piura"
  },
  "regimen": "01",
  "documentos": [
    {
      "tipo": "01",
      "numero": "F001-123",
      "fecha_emision": "2026-09-10",
      "importe_total": 1180.00,
      "moneda": "PEN",
      "pago": { "numero": 1, "fecha": "2026-09-17", "importe": 1180.00 }
    }
  ]
}

// respuesta 201 (extracto)
{
  "estado": "exito",
  "datos": {
    "tipo": "20",
    "serie": "R001",
    "fecha_limite_envio": "2026-09-24",
    "receptor": { "tipo_doc": "6", "num_doc": "20131312955", "razon_social": "PROVEEDOR DEL NORTE S.A.C." },
    "totales": { "total": 35.40 },
    "agente": {
      "regimen": "01",
      "descripcion_regimen": "Tasa 3%",
      "tasa": 3,
      "importe_total": 35.40,
      "importe_neto": 1144.60
    }
  }
}`,
    disponible: true,
  },
  {
    id: "comprobante-percepcion",
    endpoint: "/v1/percepciones",
    request: tecnico`{
  "serie": "P001",
  "fecha_emision": "2026-09-17",
  "cliente": {
    "tipo_doc": "6",
    "num_doc": "20601234565",
    "nombre": "CLIENTE MAYORISTA S.A.C."
  },
  "regimen": "01",
  "documentos": [
    {
      "tipo": "01",
      "numero": "F001-9",
      "fecha_emision": "2026-09-10",
      "importe_total": 1000.00,
      "pago": { "fecha": "2026-09-17", "importe": 1000.00 }
    }
  ]
}

// respuesta 201 (extracto)
{
  "estado": "exito",
  "datos": {
    "tipo": "40",
    "serie": "P001",
    "receptor": { "tipo_doc": "6", "num_doc": "20601234565" },
    "totales": { "total": 20.00 },
    "agente": {
      "regimen": "01",
      "tasa": 2,
      "importe_total": 20.00,
      "importe_neto": 1020.00
    }
  }
}`,
    disponible: true,
  },
  {
    id: "baja",
    endpoint: "/v1/facturas/{id}/baja",
    request: tecnico`{
  "motivo": "Error en el RUC del cliente"
}`,
    disponible: true,
  },
  {
    id: "boleta-baja",
    endpoint: "/v1/facturas/{id}/baja",
    request: tecnico`{
  "motivo": "Venta anulada: error en el importe"
}

// respuesta 201 (extracto)
{
  "estado": "exito",
  "datos": {
    "identificador": "RC-20260918-1",
    "comprobante": "B001-87",
    "tipo_comprobante": "03",
    "fecha_referencia": "2026-09-17",
    "condicion": "BAJA",
    "estado": "ACEPTADA"
  }
}`,
    disponible: true,
  },
  {
    id: "boleta-alcance",
    request: tecnico`// Boleta sin documento por más de S/ 700.00: se rechaza antes de consumir número
{
  "serie": "B001",
  "fecha_emision": "2026-09-17",
  "moneda": "PEN",
  "cliente": { "tipo_doc": "-", "num_doc": "-", "razon_social": "CLIENTES VARIOS" },
  "items": [
    { "descripcion": "Televisor 43 pulgadas", "unidad": "NIU", "cantidad": 1, "precio_unitario": 1500.00, "tipo_afectacion_igv": "10" }
  ]
}

// respuesta 422
{
  "estado": "error",
  "codigo": "RECEPTOR_INVALIDO",
  "mensaje": "Una boleta de más de S/ 700.00 (o en otra moneda sin tipo_cambio) identifica al comprador con su documento; esta es de PEN 1500.00",
  "datos": null,
  "errores": null
}`,
    disponible: true,
  },
  {
    id: "guia-remision-remitente",
    endpoint: "/v1/guias-remision",
    request: tecnico`{
  "serie": "T001",
  "fecha_emision": "2026-10-20",
  "destinatario": { "tipo_doc": "6", "num_doc": "20601234565", "nombre": "COMERCIAL ANDINA S.A.C." },
  "motivo": "01",
  "modalidad": "02",
  "traslado": {
    "fecha_inicio": "2026-10-21",
    "peso_bruto": 25.5,
    "unidad_peso": "KGM",
    "partida": { "establecimiento": "0001" },
    "llegada": { "ubigeo": "040101", "direccion": "Calle Mercaderes 200, Arequipa" },
    "vehiculos": [ { "placa": "ABC123" } ],
    "conductores": [ { "tipo_doc": "1", "num_doc": "45678912", "nombres": "JUAN", "apellidos": "QUISPE MAMANI", "licencia": "Q45678912" } ]
  },
  "items": [
    { "codigo": "LAP-15", "descripcion": "Laptop 15 pulgadas", "unidad": "NIU", "cantidad": 2 }
  ],
  "documentos_relacionados": [ { "tipo": "01", "numero": "F001-123", "ruc_emisor": "20100066603" } ]
}`,
    disponible: true,
  },
  {
    id: "guia-remision-transportista",
    endpoint: "/v1/guias-remision",
    request: tecnico`{
  "serie": "V001",
  "destinatario": { "tipo_doc": "6", "num_doc": "20601234565", "nombre": "COMERCIAL ANDINA S.A.C." },
  "remitente": { "tipo_doc": "6", "num_doc": "20555555556", "nombre": "DISTRIBUIDORA NORTE S.A." },
  "registro_mtc": "1512345CNG",
  "traslado": {
    "fecha_inicio": "2026-10-21",
    "peso_bruto": 1.25,
    "unidad_peso": "TNE",
    "partida": { "ubigeo": "150101", "direccion": "Av. Argentina 1234, Lima" },
    "llegada": { "ubigeo": "040101", "direccion": "Calle Mercaderes 200, Arequipa" },
    "vehiculos": [ { "placa": "ABC123" } ],
    "conductores": [ { "tipo_doc": "1", "num_doc": "45678912", "nombres": "JUAN", "apellidos": "QUISPE MAMANI", "licencia": "Q45678912" } ]
  },
  "items": [
    { "descripcion": "Cajas de laptops", "unidad": "BX", "cantidad": 10 }
  ],
  "documentos_relacionados": [ { "tipo": "09", "numero": "T001-15", "ruc_emisor": "20555555556" } ]
}`,
    disponible: true,
  },
];

/** Los casos con sus textos en el idioma de `messages`. */
export function casos(messages: Mensajes = es): CasoEmision[] {
  return CASOS.map((c) => ({ ...c, ...messages.developers.casos[c.id] }));
}

export const RESPUESTA_EJEMPLO = tecnico`{
  "estado": "exito",
  "datos": {
    "id": "5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f",
    "tipo": "01",
    "serie": "F001",
    "numero": 125,
    "fecha_emision": "2026-09-17",
    "moneda": "PEN",
    "tipo_operacion": "0101",
    "receptor": { "tipo_doc": "6", "num_doc": "20601234565", "razon_social": "COMERCIAL ANDINA S.A.C.", "direccion": "Av. Javier Prado Este 123, San Isidro, Lima" },
    "items": [
      { "codigo": "SRV-001", "descripcion": "Servicio de consultoría – setiembre 2026", "unidad": "ZZ", "cantidad": 1, "precio_unitario": 1180.00, "tipo_afectacion_igv": "10" }
    ],
    "estado_documento": "ACEPTADO",
    "hash": "y4M8+jW8Xp278K1aM02q19KjvO3k=",
    "nombre_archivo": "20123456786-01-F001-00000125",
    "intentos": 1,
    "ultimo_error": null,
    "cdr": { "codigo": "0", "descripcion": "La Factura numero F001-125, ha sido aceptada", "observaciones": [] },
    "totales": { "gravado": 1000.00, "exonerado": 0.00, "inafecto": 0.00, "igv": 180.00, "total": 1180.00 },
    "forma_pago": { "tipo": "contado", "monto_pendiente": null, "cuotas": [] },
    "enlaces": { "xml": "/v1/facturas/5f2c…/xml", "pdf": "/v1/facturas/5f2c…/pdf", "cdr": "/v1/facturas/5f2c…/cdr" }
  }
}`;

export const ERROR_EJEMPLO = tecnico`HTTP/1.1 422 Unprocessable Entity

{
  "estado": "error",
  "codigo": "FORMA_PAGO_INVALIDA",
  "mensaje": "3319 - La suma de las cuotas (5000.00) debe ser igual al monto neto pendiente de pago (11800.00)",
  "datos": null,
  "errores": null
}`;
