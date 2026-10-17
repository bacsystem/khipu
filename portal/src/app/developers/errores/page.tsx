import Link from "next/link";
import { Codigo, NotaProsa, P, PaginaGuia, Seccion, Tabla } from "@/components/developers/guia/prosa";
import { messages } from "@/lib/messages";

export const metadata = { title: `Errores y estados · ${messages.app.nombre}` };

const INDICE = [
  { id: "estados", label: "Estados del comprobante" },
  { id: "http", label: "Códigos HTTP" },
  { id: "codigos", label: "Códigos de error de khipu" },
  { id: "sunat", label: "Códigos de SUNAT" },
];

/** Estados del comprobante con su significado y la acción esperada del integrador. */
const ESTADOS: Array<[string, string, string]> = [
  ["RECIBIDO", "Datos validados y número asignado; el XML aún no está firmado.", "Transitorio, dura milisegundos."],
  ["FIRMADO", "XML firmado y validado contra los esquemas de SUNAT; no enviado.", "Solo persiste con enviar_automatico: false. Envíelo con POST /v1/facturas/{id}/enviar antes de fecha_limite_envio: factura y sus notas, 3 días; boleta y nota de boleta, como la boleta, sola hasta el 5.º día y por resumen diario hasta el 7.º (la NC 10 y la ND 03 de boleta no van en el resumen: hasta el 5.º). Pasado el 7.º día una boleta no vence: se informa en un resumen diario fuera de plazo; SUNAT puede observarla con la 4443 (ACEPTADO_CON_OBS), y en e-beta la aceptó sin observaciones."],
  ["ENVIADO", "SUNAT recibió el comprobante y aún no hay respuesta final.", "Consulte más tarde; khipu lo actualiza al recibir el CDR."],
  ["ACEPTADO", "SUNAT aceptó (CDR con código 0). Comprobante válido.", "Descargue el CDR (enlaces.cdr) y entregue el comprobante a su cliente."],
  ["ACEPTADO_CON_OBS", "Aceptado con observaciones (códigos 4xxx). Es válido.", "Lea cdr.observaciones y corrija la causa en las siguientes emisiones: SUNAT convierte observaciones en rechazos con el tiempo. Una boleta enviada pasado el 7.º día puede quedar así con la 4443 (informada fuera de plazo; baja.fuera_de_plazo: true): envíe las boletas a tiempo."],
  ["RECHAZADO", "SUNAT rechazó (2000–3999) o un error del emisor impidió recibirlo (1000–1999).", "Terminal. Corrija lo que indica cdr.descripcion y emita un comprobante nuevo, con otro número: el rechazado conserva el suyo (en un rechazo 2000–3999 SUNAT lo da por utilizado) y khipu nunca reasigna un número; un correlativo repetido responde 409 DUPLICADO."],
  ["ERROR_ENVIO", "SUNAT no estuvo disponible o falló la comunicación.", "No es un rechazo. khipu reintenta solo con espera creciente (hasta 20 intentos); puede forzarlo con POST /v1/facturas/{id}/enviar."],
  ["INVALIDO", "El XML no pasó la validación local (esquema).", "Contacte soporte con el id: no debería ocurrir con datos que la API aceptó."],
  ["ANULADO", "Baja aceptada por SUNAT: en una comunicación de baja (factura y sus notas) o en el resumen diario (boleta y sus notas).", "Terminal: el número queda consumido."],
  ["FUERA_DE_PLAZO", "No llegó a SUNAT dentro del plazo de envío y ya no le queda canal (fecha_limite_envio, días calendario desde la emisión: 3 para factura y sus notas —2108—; 5 para la NC 10 y la ND 03 de boleta, que no van en el resumen diario —1079—). Se marca al intentar enviarlo o en el barrido horario. Una boleta, o una nota de boleta que va en el resumen, ya no llega a este estado: pasado el 7.º día se informa en un resumen diario fuera de plazo: SUNAT no la rechaza y puede observarla con la 4443.", "Terminal; el número queda consumido: emita un comprobante nuevo con fecha vigente. Un envío manual responde 409 FUERA_DE_PLAZO. Una boleta que ya estaba en este estado antes del cambio no se reabre: contacte a soporte."],
  ["DESCARTADO", "El soporte de khipu dejó de intentar enviarlo: estaba en ERROR_ENVIO y no se recuperaba.", "Terminal: emita un comprobante nuevo; el número queda consumido. Un envío manual responde 409 ESTADO_NO_ENVIABLE."],
];

const HTTP: Array<[string, string]> = [
  ["200 / 201 / 204", "Éxito. 201 al crear (factura, nota, serie, API key); 200 al repetir un pedido de emisión con la misma Idempotency-Key (el comprobante ya emitido); 204 sin cuerpo (certificado, credenciales, revocar)."],
  ["400", "Petición mal formada: JSON inválido (JSON_INVALIDO) o parámetro con formato incorrecto (PARAMETRO_INVALIDO)."],
  ["401", "Sin credenciales válidas: falta o es inválida la X-Api-Key (NO_AUTORIZADO), o la sesión del portal expiró."],
  ["403", "Operación reservada al portal (REQUIERE_SESION: gestión de API keys) o empresa de otra cuenta (EMPRESA_AJENA)."],
  ["404", "No existe o pertenece a otra empresa (NO_ENCONTRADO); constancia aún no disponible (SIN_CDR); ruta inexistente (RUTA_INEXISTENTE)."],
  ["409", "Conflicto: correlativo repetido (DUPLICADO), comprobante que no admite envío (ESTADO_NO_ENVIABLE), fuera del plazo de envío (FUERA_DE_PLAZO), aún no aceptado para enviarlo por correo (NO_ACEPTADO) o establecimiento con series activas (ESTABLECIMIENTO_EN_USO)."],
  ["422", "Datos válidos en forma pero no en fondo: validación de campos (VALIDACION, detalle en errores) o regla de negocio (el codigo dice cuál)."],
  ["500", "Error interno; el mensaje incluye un trace_id para soporte."],
  ["502 / 503", "Falla del correo saliente al enviar un comprobante: el servidor de correo lo rechazó (502 CORREO_NO_ENVIADO) o no está habilitado (503 CORREO_NO_CONFIGURADO)."],
];

const CODIGOS: Array<[string, string, string, string]> = [
  ["VALIDACION", "422", "Uno o más campos no cumplen formato u obligatoriedad; errores lista campo → mensajes.", "Corrija los campos indicados."],
  ["SERIE_INVALIDA", "422", "La serie no tiene el formato de su tipo: factura (01) F + 3 alfanuméricos; boleta (03) B + 3; nota (07/08) F o B + 3 (FC01, BC01). Al registrarla en POST /v1/series, el tipo no coincide con la letra. En una nota, la serie no empieza con la letra del documento que modifica (F sobre factura, B sobre boleta: 2399/2400).", "Use F### para facturas y sus notas, B### para boletas y sus notas, registradas con su tipo."],
  ["SERIE_NO_CONFIGURADA", "422", "La serie no está registrada en la empresa.", "Créela en POST /v1/series o en el portal."],
  ["DUPLICADO", "409", "Ya existe un comprobante con esa serie y correlativo (o la serie ya existe).", "Reintento seguro: no se emitió nada nuevo."],
  ["IDEMPOTENCIA_INVALIDA", "422", "La Idempotency-Key de POST /v1/facturas o de POST /v1/notas ya se usó con otro contenido en las últimas 24 horas, o no tiene el formato (8 a 100 letras, dígitos, - o _). Las claves de facturas y de notas son independientes.", "Use una clave nueva (un UUID) por factura o nota; repita la misma clave solo para reintentar el mismo pedido, que responde 200 con el mismo comprobante."],
  ["NUMERO_YA_ASIGNADO", "422", "Se intentó asignar número a un comprobante que ya lo tiene.", "No debería ocurrir vía API; contacte soporte."],
  ["FECHA_INVALIDA", "422", "fecha_emision futura, con el plazo de envío ya vencido (factura y sus notas: más de 3 días calendario atrás, 2108; boleta y sus notas: más de 7, 1079; NC 10 y ND 03 de boleta: más de 5, 1079), o fecha_vencimiento anterior a la emisión. Solo al emitir: una boleta ya emitida que se envía pasado el 7.º día no da este error, se informa fuera de plazo (4443).", "Use una fecha de emisión de hoy o de los días anteriores dentro del plazo (3 para factura y sus notas, 7 para boleta y sus notas, 5 para la NC 10 y la ND 03 de boleta) y un vencimiento igual o posterior."],
  ["MONEDA_INVALIDA", "422", "Moneda distinta de PEN/USD/EUR.", "Vea el catálogo 02."],
  ["TIPO_OPERACION_INVALIDO", "422", "tipo_operacion no existe en el catálogo 51 o no aplica al tipo de comprobante (regla 3206); en boleta, khipu emite solo 0101, 0113 (NRUS), 1001–1004 (detracción) y 2001 (percepción); o es 0202/0205 (hospedaje y paquete turístico a no domiciliados), que exigen los datos del huésped por línea y aún no se soportan.", "Use un código del catálogo 51 cuya columna de comprobante incluya el tipo que emite (Factura o Boleta); una exportación va en factura."],
  ["RECEPTOR_INVALIDO", "422", "El comprador no cumple las reglas de su comprobante. Factura de venta interna: tipo_doc 6 y un RUC válido (11 dígitos que empiezan por 10/15/16/17/20 y dígito verificador correcto, 2017). Exportación (0200–0208): documento del catálogo 06 con su formato (2800–2802), `pais` obligatorio (ISO 3166-1) y sin RUC en 0200/0201/0204 (2800). Boleta: cualquier documento del catálogo 06 —DNI de 8 dígitos (4207), RUC con dígito verificador (2017), los demás hasta 15 caracteres sin espacios (4208)— o tipo_doc y num_doc «-» sin documento, solo en PEN y con un importe total de hasta S/ 700.00. Una nota sigue la regla del documento que modifica (toma su comprador). En todos, razón social o nombre de 3 a 1500 caracteres sin contar los espacios de los extremos y sin saltos de línea (2022; en boleta, error en SUNAT desde 2027-01-01). Un RUC que no figura en el padrón de SUNAT (1083, en factura, boleta con RUC y notas), mientras el padrón esté al día: SUNAT rechazaría el comprobante. Un RUC de baja (4013) o no habido (4014) no es error: se emite y la respuesta lleva `avisos`.", "Corrija cliente.tipo_doc / num_doc / razon_social. Sin documento, use «CLIENTES VARIOS» como nombre; por encima de S/ 700 o en otra moneda, identifique al comprador. Con 1083, confirme el RUC en SUNAT; si es de una empresa recién inscrita, reintente al día siguiente, cuando el padrón la incluya. Se detecta antes de consumir número."],
  ["RUC_INVALIDO", "422", "El RUC de la empresa no tiene 11 dígitos con prefijo 10/15/16/17/20 o su dígito verificador no cuadra.", "Copie el RUC tal como figura en la ficha RUC."],
  ["SIN_ITEMS", "422", "Comprobante sin ítems. Por la API no se ve: en factura y boleta un items vacío responde VALIDACION, y una nota sin items es total (copia los del documento).", "Envíe al menos un ítem."],
  ["FORMA_PAGO_INVALIDA", "422", "Regla de forma de pago incumplida; el mensaje empieza por el código SUNAT (3244–3267, 3319). Una boleta al crédito: la boleta se emite al contado.", "Ajuste cuotas, monto pendiente o fechas según el mensaje; una venta al crédito va en factura."],
  ["DESCUENTO_INVALIDO / CARGO_INVALIDO / DETRACCION_INVALIDA / RETENCION_INVALIDA / PERCEPCION_INVALIDA", "422", "Regla SUNAT de descuentos, cargos, detracción, retención o percepción incumplida; el mensaje empieza por el código SUNAT (p. ej. 2954, 3127, 3263, 3308). RETENCION_INVALIDA también rechaza una retención en una boleta (la aplica un agente con RUC: va en factura). Incluye los datos sectoriales de la detracción: 1002 exige `hidrobiologico` en cada ítem (3063, 3130–3135) y 1004 `transporte` (3116–3126); fuera de esas operaciones no se admiten.", "Corrija el dato indicado; vea el caso correspondiente en la guía (detracción, hidrobiológicos, transporte de carga)."],
  ["ITEM_INVALIDO / REDONDEO_INVALIDO", "422", "Ítem sin descripción o con más de 500 caracteres (2026/2027), unidad de medida que no existe en el catálogo 03 (2936; en blanco responde VALIDACION), cantidad cero o con más de 12 enteros / 10 decimales (2024/2025), precio negativo o en cero (2369; el valor referencial de una gratuita, 2367) o línea cuyo valor de venta queda en 0.00 (2370), bolsas de ICBPER con cantidad no entera o de más de 5 dígitos (2892) o con unidad distinta de NIU en un comprobante nuevo (SUNAT solo observa la 4320; khipu la rechaza porque el ICBPER es por bolsa), ICBPER en una boleta NRUS (tipo de operación 0113, 3240), más de 999 líneas (2023; 2137 en las notas), código interno de más de 30 caracteres, código de producto SUNAT o GTIN con formato inválido (3496, 4334, 4335), o redondeo fuera de ±1.00 (3303).", "Corrija el campo indicado; vea el caso «Fecha de vencimiento, código de producto, GTIN y redondeo» en la guía."],
  ["AFECTACION_INVALIDA", "422", "Comprobante afecto al IVAP (afectación 17, arroz pilado) con ítems de otra afectación (10/20/30 o gratuitas), o una línea IVAP con ISC o ICBPER (2650, 3223). Exportación (tipo de operación 0200–0208) con ítems que no son `40` (2642), ítem `40` en una venta interna (3107), o línea `40` con ISC/ICBPER (3223).", "Emita el comprobante IVAP solo con ítems `17` y la exportación solo con ítems `40`, sin `isc` ni `icbper`; las demás ventas van en otro comprobante."],
  ["EXPORTACION_INVALIDA", "422", "`exportacion` en una venta interna; Incoterm fuera de los Incoterms 2020; `pais_uso` ausente en 0201/0208 (3098), igual a PE o fuera del catálogo 04 (3099), o presente en un tipo que no lo admite (4041).", "Envíe `exportacion` solo con `tipo_operacion` 0200–0208, con un Incoterm válido y `pais_uso` únicamente en 0201/0208."],
  ["LEYENDA_INVALIDA", "422", "Leyenda que no existe en el catálogo 52 (3027), que khipu genera sola (1000, 1002, 2000, 2006, 2007), o 2001/2002/2003/2008 en una factura sin total exonerado (3283–3285, 3289).", "Envíe en `leyendas` solo códigos de zona o régimen del catálogo 52 (`GET /v1/catalogos/52`) y, para las de Amazonía o Tacna, ítems exonerados."],
  ["NOMBRE_COMERCIAL_INVALIDO", "422", "El nombre comercial de la empresa supera 1500 caracteres o tiene saltos de línea (regla 4092).", "Corríjalo en `PUT /v1/empresa/datos-fiscales` o en la página Empresa."],
  ["NOTA_INVALIDA", "422", "La factura o boleta modificada no existe, no está aceptada o está anulada (2119/2120); la fecha de la nota es anterior a la del documento (2885); el motivo no está en el catálogo 09/10 (2172); NC 13 sobre una boleta (3259); NC 04, 05 u 08 sobre una boleta (4367: el descuento o la bonificación a un consumidor final va en la misma boleta, RCP art. 10 num. 1.4); en una nota de crédito sobre boleta el total admite S/ 1 de más; la nota de crédito supera los importes de la factura, o el saldo que le queda tras las notas de crédito anteriores (3286/3503 — SUNAT compara nota por nota; khipu además acumula, para que dos notas totales no acrediten dos veces la misma factura); NC 13 sobre una factura al contado o sin cuotas (3257/3260/3320/3321); nota total (sin items) sobre una factura con anticipos, o con descuento_global/cargos propios; forma_pago fuera de la NC 13.", "Emita la nota sobre una factura ACEPTADA con un motivo del catálogo; en notas parciales revise cantidades e importes; sobre facturas con anticipos envíe items por el neto."],
  ["BAJA_INVALIDA", "422", "El comprobante no está aceptado por SUNAT (2398; en boleta y sus notas, 2663), pasaron más de 7 días calendario desde su emisión, en hora de Lima (2957; no se cuentan desde la aceptación de SUNAT), el motivo no tiene 3–100 caracteres (2315) o ya hay una baja en curso.", "Dé de baja solo comprobantes ACEPTADOS dentro del plazo; fuera de él emita una nota de crédito. Una boleta informada fuera de plazo ya pasó los 7 días: no se anula, se corrige con una nota de crédito."],
  ["DOCUMENTO_RELACIONADO_INVALIDO", "422", "Orden de compra, guía de remisión u otro documento relacionado con formato inválido o repetido (reglas 4233, 4005, 4006, 4009, 4010, 2364, 2365). En una boleta, un documento relacionado de tipo 06 o 07 (4009: la boleta solo admite 04, 05, 08, 09 y 99).", "Revise el número (serie-número de guía, sin espacios en otros documentos) y el tipo del catálogo; las facturas de anticipo van en `anticipos`. Quite el documento 06 o 07 de la boleta (en factura sí se admiten)."],
  ["ANTICIPO_INVALIDO", "422", "La factura de anticipo no existe en la empresa, no está aceptada por SUNAT (3218), es de otro cliente o moneda (2071), se repite (3215) o el monto supera lo facturado; o se enviaron anticipos en una boleta, que aún no los descuenta.", "Emita y espere la aceptación de la factura de anticipo; use su serie-número y el valor sin IGV."],
  ["FUERA_DE_PLAZO", "409", "Se pidió enviar un comprobante cuyo plazo venció sin canal para llegar a SUNAT: factura o nota de factura pasado el 3.er día (2108), NC 10 o ND 03 de boleta pasado el 5.º (1079). El comprobante queda en el estado FUERA_DE_PLAZO. Una boleta, o una nota de boleta que va en el resumen diario, nunca: pasado el 5.º día se informa en un resumen diario (hasta el 7.º en plazo, después fuera de plazo, que SUNAT puede observar con la 4443).", "Emita un comprobante nuevo con fecha vigente; el número del vencido queda consumido."],
  ["CDR_YA_DISPONIBLE", "409", "Se pidió recuperar de SUNAT el CDR de un comprobante que ya lo tiene.", "Descárguelo con GET /v1/facturas/{id}/cdr."],
  ["CDR_NO_CONSULTABLE", "409", "Se pidió recuperar el CDR de una boleta o de una nota de boleta (serie B) con POST /v1/facturas/{id}/cdr/recuperar. Según el manual del programador de SUNAT, getStatusCdr cubre solo factura y notas de crédito y débito (01, 07, 08) de serie F; la boleta y sus notas quedan fuera. El barrido horario de recuperación tampoco las consulta. Qué respondería SUNAT a esa consulta no está documentado, y en e-beta no se puede comprobar porque no publica el servicio de consulta.", "Su constancia es la del envío individual o la del resumen diario que la informó: si se informó en un resumen, véalo en GET /v1/facturas/{id}/bajas y consulte su ticket con GET /v1/bajas/{id}; si se envió sola y quedó en ERROR_ENVIO, reintente el envío con POST /v1/facturas/{id}/enviar. Si ya tiene su CDR, descárguelo con GET /v1/facturas/{id}/cdr (CDR_YA_DISPONIBLE)."],
  ["NO_DISPONIBLE_EN_BETA", "422", "Consulta de CDR o de validez con una empresa en entorno BETA: SUNAT no publica billConsultService ni billValidService en e-beta.", "Use una empresa en PRODUCCION (o configure SUNAT_CONSULTA_BETA_URL / SUNAT_VALIDEZ_BETA_URL si dispone de un entorno de pruebas)."],
  ["ESTABLECIMIENTO_INVALIDO", "422", "Código de establecimiento que no son 4 dígitos (3030), `0000` enviado como anexo (es el domicilio fiscal), anexo inexistente o dado de baja al crear una serie o emitir, o código de la ruta distinto del cuerpo.", "Registre el anexo en `POST /v1/empresa/establecimientos` con el código de la ficha RUC y asígnelo a la serie; el 0000 se edita en datos fiscales."],
  ["ESTABLECIMIENTO_EN_USO", "409", "Se intentó dar de baja un establecimiento que todavía tiene series activas.", "Cree las series de reemplazo en otro establecimiento antes de darlo de baja."],
  ["DOMICILIO_INVALIDO / CUENTA_DETRACCIONES_INVALIDA", "422", "Datos fiscales de la empresa fuera de formato: ubigeo que no está en el catálogo 13 (4093), dirección con saltos de línea o fuera de 3–200 caracteres (4094), establecimiento anexo que no son 4 dígitos (3030), cuenta del Banco de la Nación con caracteres no válidos.", "Corrija en PUT /v1/empresa/datos-fiscales o en la página Empresa del portal."],
  ["CREDENCIALES_SOL_NO_CARGADAS", "422", "La empresa no tiene usuario/clave SOL y se pidió enviar a SUNAT.", "Cárguelas en el portal o emita con enviar_automatico: false."],
  ["CERTIFICADO_NO_CARGADO / CERTIFICADO_VENCIDO / CERTIFICADO_INVALIDO", "422", "Sin certificado, vencido, o su OU no contiene el RUC.", "Cargue un certificado vigente de la empresa."],
  ["LIMITE_PLAN", "429", "La cuenta llegó al tope de documentos del mes de su plan (facturas, boletas y notas de todas sus empresas). No se emite ni se gasta número.", "Vuelva a emitir el mes siguiente o pida un cambio de plan; la comunicación de baja no consume."],
  ["PLAN_VENCIDO", "402", "El plan de la cuenta venció y pasaron sus días de gracia (en gracia se sigue emitiendo). Aplica a facturas, boletas y notas; no se emite ni se gasta número.", "Renueve el plan para volver a emitir; consultar lo ya emitido y la comunicación de baja siguen funcionando."],
  ["XSD_INVALIDO / FIRMA_FALLIDA", "422", "El XML generado no validó o no pudo firmarse.", "Contacte soporte con el id; suele ser un dato fuera de catálogo."],
  ["NO_ACEPTADO", "409", "Se pidió enviar por correo un comprobante que SUNAT aún no aceptó (FIRMADO, ERROR_ENVIO, RECHAZADO o ANULADO).", "Espere la aceptación; el PDF sí se puede descargar desde FIRMADO."],
  ["CORREO_NO_ENVIADO", "502", "El servidor de correo rechazó o no aceptó el envío al adquirente (SMTP caído, buzón inválido).", "Reintente más tarde; el comprobante no cambia de estado."],
  ["CORREO_NO_CONFIGURADO", "503", "El servidor no tiene el envío de correos habilitado: no se envió nada al adquirente.", "Reintentar no sirve hasta que se habilite; entregue el PDF por su cuenta o contacte a soporte."],
  ["PERSONALIZACION_INVALIDA / LOGO_INVALIDO / OBSERVACIONES_INVALIDAS", "422", "Diseño del PDF fuera de formato (plantilla desconocida, color no hexadecimal, pie de más de 300 caracteres o con saltos de línea, observaciones de más de 1000), logo que no es PNG/JPEG o pesa más de 200 KB, u observaciones del comprobante con caracteres de control.", "Corrija el valor indicado en el mensaje; los textos del PDF no afectan al XML ni a SUNAT."],
  ["SIN_FIRMA", "422", "Se pidió el PDF de un comprobante que aún no está numerado y firmado.", "Solo ocurre con comprobantes INVALIDO; corrija y vuelva a emitir."],
  ["ESTADO_NO_ENVIABLE", "409", "Se intentó enviar un comprobante ACEPTADO, RECHAZADO o ANULADO.", "Solo FIRMADO y ERROR_ENVIO se envían."],
  ["ESTADO_CONFLICTO", "409", "Dos operaciones cambiaron el estado a la vez.", "Vuelva a consultar el comprobante."],
  ["SIN_CDR", "404", "SUNAT aún no emitió la constancia.", "Espere a ACEPTADO/RECHAZADO."],
  ["CDR_CORRUPTO", "500", "La constancia almacenada no se puede leer.", "Contacte soporte."],
  ["NO_ENCONTRADO", "404", "El recurso no existe o es de otra empresa.", "Revise el id."],
  ["NO_AUTORIZADO", "401", "X-Api-Key ausente, inválida o revocada.", "Genere una llave nueva en el portal."],
  ["REQUIERE_SESION", "403", "Crear, listar o revocar API keys solo desde el portal.", "Hágalo desde el portal con su sesión."],
  ["PARAMETRO_INVALIDO", "400", "Un parámetro de ruta o query no tiene el formato esperado (p. ej. id que no es UUID, formato del CDR).", "Corrija la URL."],
  ["JSON_INVALIDO", "400", "El cuerpo no es JSON válido.", "Revise comillas, comas y codificación UTF-8."],
  ["RUTA_INEXISTENTE", "404", "La ruta no existe en esta versión de la API.", "Revise el path y la versión."],
  ["INTERNO", "500", "Error no controlado.", "Reintente; si persiste, envíe el trace_id a soporte."],
];

export default function ErroresPage() {
  return (
    <PaginaGuia
      titulo="Errores y estados"
      resumen="Qué significa cada estado de un comprobante, cada código HTTP y cada código de error de la API, y qué hacer en cada caso. Cuando la regla viene de SUNAT, el mensaje lleva su código oficial."
      indice={INDICE}
    >
      <Seccion id="estados" titulo="Estados del comprobante">
        <P>
          El ciclo normal es <Codigo>RECIBIDO</Codigo> → <Codigo>FIRMADO</Codigo> → <Codigo>ENVIADO</Codigo> → <Codigo>ACEPTADO</Codigo>. Los demás
          estados son desvíos con una acción concreta:
        </P>
        <Tabla cabeceras={["Estado", "Significado", "Qué hacer"]} filas={ESTADOS.map(([e, s, q]) => [e, s, q])} />
        <NotaProsa tono="aviso">
          <Codigo>ERROR_ENVIO</Codigo> no significa que SUNAT rechazó nada: el comprobante ya tiene número y firma, y khipu lo entregará. Solo{" "}
          <Codigo>RECHAZADO</Codigo> exige corregir y volver a emitir.
        </NotaProsa>
      </Seccion>

      <Seccion id="http" titulo="Códigos HTTP">
        <Tabla cabeceras={["HTTP", "Cuándo"]} filas={HTTP.map(([h, c]) => [h, c])} />
      </Seccion>

      <Seccion id="codigos" titulo="Códigos de error de khipu">
        <P>
          Van en <Codigo>codigo</Codigo> del sobre de respuesta y son estables: programe contra ellos, no contra el texto de <Codigo>mensaje</Codigo>.
        </P>
        <Tabla cabeceras={["Código", "HTTP", "Significado", "Qué hacer"]} filas={CODIGOS.map(([c, h, s, q]) => [c, h, s, q])} />
      </Seccion>

      <Seccion id="sunat" titulo="Códigos de SUNAT">
        <P>
          SUNAT responde con un código numérico que khipu conserva en <Codigo>cdr.codigo</Codigo> (respuesta de SUNAT) o al inicio de{" "}
          <Codigo>mensaje</Codigo> (cuando khipu aplica la misma regla antes de enviar). Los rangos:
        </P>
        <Tabla
          cabeceras={["Rango", "Tipo", "Qué hacer"]}
          filas={[
            ["0", "Aceptado.", "Nada: comprobante válido."],
            ["0100 – 0999", "Fallo del servicio de SUNAT o de autenticación (0100 sistema no disponible, 0102 usuario o clave SOL incorrectos, 0111 sin perfil para emitir).", "khipu reintenta solo (ERROR_ENVIO). Si es 010x de credenciales, corrija usuario/clave SOL en el portal."],
            ["1000 – 1999", "Error del emisor en el envío: nombre de archivo, XML vacío, comprobante ya registrado con otros datos (1033), emisor no autorizado (1078).", "El comprobante queda RECHAZADO sin reintentos; revise el mensaje y emita de nuevo."],
            ["2000 – 3999", "Rechazo por validación del contenido (importes, catálogos, forma de pago, receptor…).", "RECHAZADO: el número queda utilizado. Corrija el dato indicado y emita un comprobante nuevo, con otro número."],
            ["4000 – 4999", "Observación: aceptado, pero un dato conviene corregirlo. Por ejemplo la 4443, que SUNAT puede dar a una boleta informada en el resumen diario pasado el 7.º día (en e-beta no la dio).", "ACEPTADO_CON_OBS: válido; corrija en próximas emisiones (en la 4443, envíe las boletas a tiempo)."],
          ]}
        />
        <P>
          Ejemplos de reglas que khipu comprueba localmente con el mismo código: <Codigo>3244</Codigo> falta la forma de pago, <Codigo>3249</Codigo>{" "}
          crédito sin cuotas, <Codigo>3319</Codigo> las cuotas no suman el pendiente, <Codigo>3267</Codigo> cuota que vence antes de la emisión. La tabla
          completa de códigos de retorno la publica SUNAT en sus reglas de validación; los catálogos de códigos están en{" "}
          <Link href="/developers/catalogos" className="text-primary hover:underline">
            Catálogos SUNAT
          </Link>
          .
        </P>
      </Seccion>
    </PaginaGuia>
  );
}
