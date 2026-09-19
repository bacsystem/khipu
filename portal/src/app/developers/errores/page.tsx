import Link from "next/link";
import { Aviso, Codigo, P, PaginaGuia, Seccion, Tabla } from "@/components/developers/guia/prosa";
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
  ["FIRMADO", "XML firmado y validado contra los esquemas de SUNAT; no enviado.", "Solo persiste con enviar_automatico: false. Envíelo con POST /v1/facturas/{id}/enviar antes de 3 días."],
  ["ENVIADO", "SUNAT recibió el comprobante y aún no hay respuesta final.", "Consulte más tarde; khipu lo actualiza al recibir el CDR."],
  ["ACEPTADO", "SUNAT aceptó (CDR con código 0). Comprobante válido.", "Descargue el CDR (enlaces.cdr) y entregue la factura a su cliente."],
  ["ACEPTADO_CON_OBS", "Aceptado con observaciones (códigos 4xxx). Es válido.", "Lea cdr.observaciones y corrija la causa en las siguientes emisiones: SUNAT convierte observaciones en rechazos con el tiempo."],
  ["RECHAZADO", "SUNAT rechazó (2000–3999) o un error del emisor impidió recibirlo (1000–1999).", "Corrija lo que indica cdr.descripcion y emita de nuevo; puede reutilizar el número."],
  ["ERROR_ENVIO", "SUNAT no estuvo disponible o falló la comunicación.", "No es un rechazo. khipu reintenta solo con espera creciente (hasta 20 intentos); puede forzarlo con POST /v1/facturas/{id}/enviar."],
  ["INVALIDO", "El XML no pasó la validación local (esquema).", "Contacte soporte con el id: no debería ocurrir con datos que la API aceptó."],
  ["ANULADO", "Comunicación de baja aceptada por SUNAT.", "Terminal (en desarrollo)."],
];

const HTTP: Array<[string, string]> = [
  ["200 / 201 / 204", "Éxito. 201 al crear (factura, serie, API key); 204 sin cuerpo (certificado, credenciales, revocar)."],
  ["400", "Petición mal formada: JSON inválido (JSON_INVALIDO) o parámetro con formato incorrecto (PARAMETRO_INVALIDO)."],
  ["401", "Sin credenciales válidas: falta o es inválida la X-Api-Key (NO_AUTORIZADO), o la sesión del portal expiró."],
  ["403", "Operación reservada al portal (REQUIERE_SESION: gestión de API keys) o empresa de otra cuenta (EMPRESA_AJENA)."],
  ["404", "No existe o pertenece a otra empresa (NO_ENCONTRADO); constancia aún no disponible (SIN_CDR); ruta inexistente (RUTA_INEXISTENTE)."],
  ["409", "Conflicto: correlativo repetido (DUPLICADO) o comprobante que no admite envío (ESTADO_NO_ENVIABLE)."],
  ["422", "Datos válidos en forma pero no en fondo: validación de campos (VALIDACION, detalle en errores) o regla de negocio (el codigo dice cuál)."],
  ["500", "Error interno; el mensaje incluye un trace_id para soporte."],
];

const CODIGOS: Array<[string, string, string, string]> = [
  ["VALIDACION", "422", "Uno o más campos no cumplen formato u obligatoriedad; errores lista campo → mensajes.", "Corrija los campos indicados."],
  ["SERIE_INVALIDA", "422", "La serie no tiene el formato del tipo (factura: F + 3 alfanuméricos).", "Use una serie F###."],
  ["SERIE_NO_CONFIGURADA", "422", "La serie no está registrada en la empresa.", "Créela en POST /v1/series o en el portal."],
  ["DUPLICADO", "409", "Ya existe un comprobante con esa serie y correlativo (o la serie ya existe).", "Reintento seguro: no se emitió nada nuevo."],
  ["NUMERO_YA_ASIGNADO", "422", "Se intentó asignar número a un comprobante que ya lo tiene.", "No debería ocurrir vía API; contacte soporte."],
  ["FECHA_INVALIDA", "422", "fecha_emision futura o fecha_vencimiento anterior a la emisión.", "Use una fecha de emisión de hoy o anterior y un vencimiento igual o posterior."],
  ["MONEDA_INVALIDA", "422", "Moneda distinta de PEN/USD/EUR.", "Vea el catálogo 02."],
  ["TIPO_OPERACION_INVALIDO", "422", "tipo_operacion no existe en el catálogo 51 o no aplica a facturas (regla 3206).", "Use un código del catálogo 51 cuya columna de comprobante incluya Factura."],
  ["RECEPTOR_INVALIDO", "422", "En factura el adquirente debe tener RUC válido (tipo_doc 6, 11 dígitos).", "Corrija cliente.tipo_doc / num_doc."],
  ["SIN_ITEMS", "422", "La factura no tiene ítems.", "Envíe al menos un ítem."],
  ["FORMA_PAGO_INVALIDA", "422", "Regla de forma de pago incumplida; el mensaje empieza por el código SUNAT (3244–3267, 3319).", "Ajuste cuotas, monto pendiente o fechas según el mensaje."],
  ["DESCUENTO_INVALIDO / CARGO_INVALIDO / DETRACCION_INVALIDA / RETENCION_INVALIDA / PERCEPCION_INVALIDA", "422", "Regla SUNAT de descuentos, cargos, detracción, retención o percepción incumplida; el mensaje empieza por el código SUNAT (p. ej. 2954, 3127, 3263, 3308).", "Corrija el dato indicado; vea el caso correspondiente en la guía."],
  ["ITEM_INVALIDO / REDONDEO_INVALIDO", "422", "Código de producto SUNAT o GTIN con formato inválido (3496, 4334, 4335) o redondeo fuera de ±1.00 (3303).", "Corrija el campo indicado; vea el caso «Fecha de vencimiento, código de producto, GTIN y redondeo» en la guía."],
  ["NOMBRE_COMERCIAL_INVALIDO", "422", "El nombre comercial de la empresa supera 1500 caracteres o tiene saltos de línea (regla 4092).", "Corríjalo en `PUT /v1/empresa/datos-fiscales` o en la página Empresa."],
  ["NOTA_INVALIDA", "422", "La factura modificada no existe, no está aceptada o está anulada (2119/2120); la fecha de la nota es anterior a la de la factura (2885); el motivo no está en el catálogo 09/10 (2172); la nota de crédito supera los importes de la factura (3286/3503); NC 13 sobre una factura al contado o sin cuotas (3257/3260/3320/3321); nota total (sin items) sobre una factura con anticipos, o con descuento_global/cargos propios; forma_pago fuera de la NC 13.", "Emita la nota sobre una factura ACEPTADA con un motivo del catálogo; en notas parciales revise cantidades e importes; sobre facturas con anticipos envíe items por el neto."],
  ["BAJA_INVALIDA", "422", "El comprobante no está aceptado por SUNAT (2398), es una boleta (2308), se emitió hace más de 7 días (2957), el motivo no tiene 3–100 caracteres (2315) o ya hay una comunicación de baja en curso.", "Dé de baja solo comprobantes ACEPTADOS dentro del plazo; fuera de él emita una nota de crédito."],
  ["DOCUMENTO_RELACIONADO_INVALIDO", "422", "Orden de compra, guía de remisión u otro documento relacionado con formato inválido o repetido (reglas 4233, 4005, 4006, 4009, 4010, 2364, 2365).", "Revise el número (serie-número de guía, sin espacios en otros documentos) y el tipo del catálogo; las facturas de anticipo van en `anticipos`."],
  ["ANTICIPO_INVALIDO", "422", "La factura de anticipo no existe en la empresa, no está aceptada por SUNAT (3218), es de otro cliente o moneda (2071), se repite (3215) o el monto supera lo facturado.", "Emita y espere la aceptación de la factura de anticipo; use su serie-número y el valor sin IGV."],
  ["DOMICILIO_INVALIDO / CUENTA_DETRACCIONES_INVALIDA", "422", "Datos fiscales de la empresa fuera de formato: ubigeo que no está en el catálogo 13 (4093), dirección con saltos de línea o fuera de 3–200 caracteres (4094), establecimiento anexo que no son 4 dígitos (3030), cuenta del Banco de la Nación con caracteres no válidos.", "Corrija en PUT /v1/empresa/datos-fiscales o en la página Empresa del portal."],
  ["CREDENCIALES_SOL_NO_CARGADAS", "422", "La empresa no tiene usuario/clave SOL y se pidió enviar a SUNAT.", "Cárguelas en el portal o emita con enviar_automatico: false."],
  ["CERTIFICADO_NO_CARGADO / CERTIFICADO_VENCIDO / CERTIFICADO_INVALIDO", "422", "Sin certificado, vencido, o su OU no contiene el RUC.", "Cargue un certificado vigente de la empresa."],
  ["XSD_INVALIDO / FIRMA_FALLIDA", "422", "El XML generado no validó o no pudo firmarse.", "Contacte soporte con el id; suele ser un dato fuera de catálogo."],
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
        <Aviso tono="aviso">
          <Codigo>ERROR_ENVIO</Codigo> no significa que SUNAT rechazó nada: el comprobante ya tiene número y firma, y khipu lo entregará. Solo{" "}
          <Codigo>RECHAZADO</Codigo> exige corregir y volver a emitir.
        </Aviso>
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
            ["2000 – 3999", "Rechazo por validación del contenido (importes, catálogos, forma de pago, receptor…).", "RECHAZADO: corrija el dato indicado y vuelva a emitir; puede reutilizar el número."],
            ["4000 – 4999", "Observación: aceptado, pero un dato conviene corregirlo.", "ACEPTADO_CON_OBS: válido; corrija en próximas emisiones."],
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
