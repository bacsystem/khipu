/**
 * Los datos de la página «Entornos y límites» (#26), aparte de la página para que el buscador de la documentación indexe lo mismo que ella muestra: cada fila de las tablas es un
 * resultado de búsqueda. Una página de App Router no puede exportar nada más que su componente.
 */

export const INDICE_ENTORNOS = [
  { id: "entornos", label: "BETA y producción" },
  { id: "limites", label: "Límites y cuotas" },
  { id: "reintentos", label: "Reintentos y vigencias" },
  { id: "pendiente", label: "Lo que aún no está publicado" },
];

/** Aspecto, cómo es en BETA y cómo en producción. */
export const ENTORNOS: Array<[string, string, string]> = [
  ["Destino", "El ambiente de pruebas de SUNAT (e-beta).", "SUNAT en producción."],
  ["Validez tributaria", "Ninguna: son comprobantes de prueba.", "Plena: el CDR aceptado es la constancia legal."],
  ["Credenciales SOL", "Las de prueba de SUNAT (usuario MODDATOS).", "El usuario secundario de la empresa."],
  [
    "Consultar el CDR o la validez de un comprobante",
    "El CDR no: SUNAT no publica ese servicio en e-beta (422 NO_DISPONIBLE_EN_BETA). La validez sí, si la empresa cargó sus credenciales API SUNAT (consulta por la API REST de SUNAT).",
    "Disponible (POST /v1/facturas/{id}/cdr/recuperar y GET /v1/consultas/validez, por la API REST con credenciales API SUNAT o con la clave SOL).",
  ],
  [
    "Reglas de validación",
    "Las mismas. e-beta no cruza el padrón de contribuyentes: por ejemplo, aceptó una factura con la tasa reducida del IGV sin la observación 4439 que SUNAT sí puede dar en producción.",
    "Las mismas, más el cruce con el padrón.",
  ],
];

/** Qué, el límite y qué pasa al pasarlo. */
export const LIMITES: Array<[string, string, string]> = [
  ["Ítems por comprobante", "999 líneas (SUNAT 2023; 2137 en las notas).", "422 ITEM_INVALIDO."],
  ["Descripción de un ítem", "De 1 a 500 caracteres (2026, 2027).", "422 ITEM_INVALIDO."],
  ["Nombre o razón social del comprador", "De 3 a 1500 caracteres sin contar los espacios de los extremos (2022).", "422 RECEPTOR_INVALIDO."],
  ["Boleta sin documento del comprador", "Hasta S/ 700.00 de importe total: en PEN, o en otra moneda con tipo_cambio si el total en soles no pasa de S/ 700.00.", "422 RECEPTOR_INVALIDO."],
  ["Documentos por mes", "El tope del plan de la cuenta, sumando facturas, boletas y notas de todas sus empresas.", "429 LIMITE_PLAN; 402 PLAN_VENCIDO cuando el plan venció y pasaron sus días de gracia."],
  [
    "Listados",
    "De 1 a 100 resultados por página (20 por defecto); el total viene en la cabecera X-Total-Count.",
    "El listado de comprobantes ajusta el valor al rango; los de clientes, productos y entregas de webhooks responden 422 PAGINACION_INVALIDA.",
  ],
  ["Consulta de un RUC (GET /v1/consultas/ruc/{ruc})", "60 por minuto y 5 000 por día, por empresa.", "429 DEMASIADAS_CONSULTAS."],
  ["Certificado digital", "Un archivo .p12 o .pfx de hasta 1 MB.", "422 ARCHIVO_DEMASIADO_GRANDE."],
  ["Inicio de sesión del portal", "Los intentos fallidos se limitan por correo y por IP durante 15 minutos.", "429 DEMASIADOS_INTENTOS_LOGIN."],
];

/** Qué y cuánto. */
export const REINTENTOS: Array<[string, string]> = [
  ["Idempotency-Key", "24 horas: repetir la misma clave con el mismo pedido devuelve 200 con el mismo comprobante; con otro contenido responde 422 IDEMPOTENCIA_INVALIDA."],
  [
    "Reenvío de un comprobante en ERROR_ENVIO",
    "khipu reintenta solo con espera creciente, hasta 20 intentos (unas 74 horas). Una boleta no se abandona mientras siga en plazo del resumen diario (hasta el 7.º día).",
  ],
  ["Consulta del ticket de una baja o un resumen diario", "Cada 30 segundos al principio, con intervalo creciente hasta 5 minutos, sin límite de intentos mientras SUNAT no responda."],
  ["Entrega de un webhook", "Si su servidor no responde 2xx, khipu reintenta a 1, 5 y 15 minutos y a 1, 3, 6 y 12 horas. Son 8 intentos en total (unas 22 horas); tras 10 fallos seguidos el webhook se apaga y se avisa por correo a la cuenta."],
  ["Log de entregas de webhooks", "30 días (GET /v1/webhooks/{id}/entregas)."],
  [
    "Plazo de envío a SUNAT",
    "Factura y sus notas: 3 días calendario desde la emisión. Boleta y sus notas: sola hasta el 5.º día y por resumen diario hasta el 7.º; pasado el 7.º se informa fuera de plazo. La NC 10 y la ND 03 de boleta no van en el resumen: vencen el 5.º y quedan en FUERA_DE_PLAZO.",
  ],
  ["Anular (baja)", "Hasta 7 días calendario desde la emisión, en hora de Lima (regla 2957)."],
];
