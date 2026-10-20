import type { DocumentoLegal } from "./tipos";
import { RESPONSABLE, VERSION_TERMINOS } from "./version";

/**
 * Términos del servicio (#342). BORRADOR escrito sin asesoría legal: lo revisa y reemplaza el dueño del producto con su abogado (#283).
 * Para publicar un cambio, ver las instrucciones de `version.ts`.
 */
export const TERMINOS: DocumentoLegal = {
  titulo: "Términos del servicio",
  version: VERSION_TERMINOS,
  resumen:
    "Estas condiciones regulan el uso de khipu, el servicio de facturación electrónica para emitir comprobantes ante SUNAT desde el portal o la API. Al crear tu cuenta, o al aceptarlas cuando cambien, declaras que las leíste y las aceptas.",
  secciones: [
    {
      titulo: "1. Quiénes somos",
      parrafos: [
        `khipu es un servicio de ${RESPONSABLE.razonSocial} (RUC ${RESPONSABLE.ruc}), con domicilio en ${RESPONSABLE.domicilio} («khipu», «nosotros»). «Tú» o «el cliente» es la persona natural o jurídica que crea una cuenta y la usa, y quien la registra en nombre de una empresa declara tener facultades para obligarla.`,
      ],
    },
    {
      titulo: "2. El servicio",
      parrafos: [
        "khipu genera, firma digitalmente, valida y envía a SUNAT comprobantes de pago electrónicos (facturas, boletas, notas de crédito y de débito, comunicaciones de baja y resúmenes diarios) en formato UBL 2.1, y conserva el XML y la constancia de recepción (CDR) que devuelve SUNAT.",
        "El servicio opera bajo el Sistema de Emisión Electrónica desde los sistemas del contribuyente (SEE - Del Contribuyente): cada comprobante se firma con el certificado digital de tu empresa y se envía con tu usuario y clave SOL. khipu no es un Operador de Servicios Electrónicos (OSE) ni un Proveedor de Servicios Electrónicos (PSE) inscrito ante SUNAT, y no emite comprobantes en nombre propio.",
        "Puedes usar khipu desde el portal web o integrarlo a tus sistemas mediante la API, con las claves (API keys) que generes desde el portal.",
      ],
    },
    {
      titulo: "3. Tu cuenta",
      lista: [
        "Los datos que registras (nombre, correo, celular, RUC y datos de tus empresas) deben ser veraces y estar actualizados.",
        "Eres responsable de guardar en reserva tu contraseña y tus API keys, y de todo lo que se haga con ellas. Si sospechas que alguien más las conoce, cámbialas o revócalas de inmediato y avísanos.",
        "Debes verificar tu correo electrónico para crear empresas y emitir.",
        "Una API key da acceso a la empresa para la que se generó: entrégala solo a quien deba integrarse en tu nombre.",
      ],
      parrafos: ["Al crear la cuenta te comprometes a lo siguiente:"],
    },
    {
      titulo: "4. Lo que te corresponde a ti",
      parrafos: ["La obligación tributaria de emitir, y el contenido de cada comprobante, son tuyos. En particular:"],
      lista: [
        "La exactitud de los datos de cada comprobante: comprador, montos, tributos, descripciones y el tipo de operación que elijas.",
        "Mantener vigente tu certificado digital y correctas tus credenciales SOL. Con un certificado vencido o una clave SOL errada, SUNAT rechaza los envíos.",
        "Revisar el estado de tus comprobantes y atender los rechazos u observaciones de SUNAT dentro de los plazos que fija la normativa.",
        "Conservar tus comprobantes y constancias por el plazo que exige la normativa tributaria, aunque khipu también los conserve según tu plan (ver sección 8).",
        "Usar el servicio de forma lícita: no emitir comprobantes falsos o por operaciones inexistentes, no intentar acceder a cuentas o empresas ajenas, no interferir con el funcionamiento del servicio ni someterlo a cargas desproporcionadas.",
      ],
    },
    {
      titulo: "5. Lo que nos corresponde",
      lista: [
        "Prestar el servicio con diligencia: validar los comprobantes contra el estándar de SUNAT antes de enviarlos, reintentar los envíos cuando SUNAT no responde y avisarte de los rechazos.",
        "Proteger tu información con las medidas descritas en la política de privacidad, incluido el cifrado de tu certificado digital y de tus credenciales SOL.",
        "Usar tus credenciales y tu certificado solo para lo que nos pidas: firmar y enviar tus comprobantes y consultar su estado ante SUNAT.",
        "Avisarte con anticipación de los mantenimientos programados y de los cambios relevantes del servicio.",
      ],
      parrafos: ["Por nuestra parte nos comprometemos a:"],
    },
    {
      titulo: "6. Disponibilidad",
      parrafos: [
        "Hacemos lo razonable para que khipu esté disponible de forma continua, pero no podemos garantizar que funcione sin interrupciones. Puede haber mantenimientos, fallas de nuestros proveedores de infraestructura o caídas de los servicios de SUNAT, que no dependen de nosotros.",
        "Cuando SUNAT no responde, los comprobantes quedan en cola y se reenvían automáticamente. Si una interrupción pudiera hacerte incumplir un plazo, te corresponde usar los mecanismos de contingencia que prevé la normativa de SUNAT.",
      ],
    },
    {
      titulo: "7. Planes y pagos",
      parrafos: [
        "Cada plan fija un número de comprobantes por mes, un tiempo de conservación y un precio, publicados en el portal. Al llegar al tope del mes, el servicio no emite más comprobantes hasta el mes siguiente o hasta que cambies de plan.",
        "Si el pago de tu plan vence y pasa el periodo de gracia, puedes seguir entrando y consultando tus comprobantes, pero no emitir nuevos hasta regularizarlo. Los precios pueden cambiar; te avisaremos antes de que un cambio te afecte.",
      ],
    },
    {
      titulo: "8. Conservación de tus comprobantes",
      parrafos: [
        "khipu conserva el XML firmado, el CDR y la representación impresa (PDF) de tus comprobantes durante el tiempo que indique tu plan, y puedes descargarlos en cualquier momento mientras tu cuenta esté activa.",
        "La conservación que ofrece khipu es un servicio adicional y no reemplaza tu obligación de conservar los comprobantes por el plazo que exige la normativa tributaria. Te recomendamos descargar periódicamente tus archivos.",
      ],
    },
    {
      titulo: "9. Suspensión y baja de la cuenta",
      parrafos: [
        "Puedes pedir la baja de tu cuenta en cualquier momento escribiéndonos. Antes de la baja te daremos la oportunidad de descargar tus comprobantes.",
        "Podemos suspender una cuenta, con aviso previo salvo urgencia, si se usa en contra de estos términos o de la ley, si pone en riesgo la seguridad del servicio o de otros clientes, o por falta de pago. Suspender no borra nada: al reactivarla todo vuelve a estar disponible.",
        "Tras la baja, conservamos los datos que la ley nos obligue a conservar y eliminamos o anonimizamos el resto, como se describe en la política de privacidad.",
      ],
    },
    {
      titulo: "10. Propiedad intelectual",
      parrafos: [
        "El software, la marca y la documentación de khipu nos pertenecen o los usamos con licencia. Te damos un derecho personal, no exclusivo e intransferible a usarlos mientras tu cuenta esté vigente. Los datos y comprobantes que cargas son tuyos.",
      ],
    },
    {
      titulo: "11. Límites de responsabilidad",
      parrafos: [
        "No respondemos por los rechazos, multas o contingencias tributarias que se deriven de los datos que tú ingresas, de un certificado vencido o de credenciales SOL incorrectas, ni por las interrupciones de SUNAT o de terceros ajenos a nuestro control.",
        "En la medida en que la ley lo permita, nuestra responsabilidad total frente a ti por cualquier reclamo vinculado al servicio se limita al monto que nos hayas pagado en los doce meses anteriores al hecho que lo origina. Esta limitación no aplica en caso de dolo o culpa inexcusable.",
      ],
    },
    {
      titulo: "12. Cambios a estos términos",
      parrafos: [
        "Podemos modificar estos términos. Cuando lo hagamos publicaremos la nueva versión con su fecha y, si el cambio afecta lo que aceptaste, te pediremos aceptarla la próxima vez que entres al portal. Mientras no la aceptes no podrás usar el panel; si no estás de acuerdo, puedes pedir la baja de tu cuenta.",
      ],
    },
    {
      titulo: "13. Ley aplicable",
      parrafos: [
        "Estos términos se rigen por las leyes de la República del Perú. Cualquier controversia se someterá a los jueces y tribunales de Lima Cercado, salvo que la ley disponga otra cosa. Si contratas como consumidor, conservas los derechos que te reconoce el Código de Protección y Defensa del Consumidor.",
      ],
    },
    {
      titulo: "14. Contacto",
      parrafos: [`Para cualquier consulta sobre estos términos escríbenos a ${RESPONSABLE.correo}.`],
    },
  ],
};
