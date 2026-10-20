import type { DocumentoLegal } from "./tipos";
import { RESPONSABLE, VERSION_PRIVACIDAD } from "./version";

/**
 * Política de privacidad (#342), redactada siguiendo la Ley N.° 29733 y su reglamento. BORRADOR escrito sin asesoría legal: lo revisa y
 * reemplaza el dueño del producto con su abogado (#283). Para publicar un cambio, ver las instrucciones de `version.ts`.
 */
export const PRIVACIDAD: DocumentoLegal = {
  titulo: "Política de privacidad",
  version: VERSION_PRIVACIDAD,
  resumen:
    "Cómo tratamos los datos personales que nos confías al usar khipu, conforme a la Ley N.° 29733, Ley de Protección de Datos Personales, y su reglamento: qué datos, para qué, con quién los compartimos, cuánto tiempo los guardamos y cómo ejercer tus derechos.",
  secciones: [
    {
      titulo: "1. Responsable del tratamiento",
      parrafos: [
        `El responsable de los datos personales de tu cuenta es ${RESPONSABLE.razonSocial} (RUC ${RESPONSABLE.ruc}), con domicilio en ${RESPONSABLE.domicilio}. Los datos se almacenan en el banco de datos de clientes de khipu, inscrito en el Registro Nacional de Protección de Datos Personales con el código ${RESPONSABLE.inscripcionBancoDeDatos}.`,
        `Para cualquier asunto de privacidad puedes escribirnos a ${RESPONSABLE.correo}.`,
      ],
    },
    {
      titulo: "2. Qué datos tratamos",
      parrafos: ["Tratamos solo los datos necesarios para prestar el servicio:"],
      lista: [
        "Datos de tu cuenta: nombre de la cuenta, correo electrónico, celular de contacto y tu contraseña, que guardamos solo como un hash irreversible.",
        "Datos de tus empresas: RUC, razón social, domicilio fiscal, establecimientos y series.",
        "Credenciales para emitir: tu certificado digital con su contraseña y tu usuario y clave SOL, que guardamos cifrados.",
        "Comprobantes: los datos que incluyes en cada comprobante, entre ellos los de tus clientes (nombre o razón social, tipo y número de documento de identidad, dirección y correo cuando los registras).",
        "Datos técnicos: dirección IP, fecha y hora de los accesos, intentos de inicio de sesión y el registro de las acciones relevantes sobre tu cuenta, que usamos para la seguridad del servicio.",
      ],
      cierre: [
        "No tratamos datos sensibles y no pedimos más datos de los que el servicio necesita.",
      ],
    },
    {
      titulo: "3. Para qué los usamos",
      parrafos: ["Usamos tus datos para:"],
      lista: [
        "Crear y administrar tu cuenta, verificar tu correo y permitirte recuperar el acceso.",
        "Generar, firmar, validar y enviar tus comprobantes a SUNAT, consultar su estado y conservarlos.",
        "Proteger el servicio: detectar accesos indebidos, limitar intentos fallidos y llevar un registro de auditoría.",
        "Enviarte comunicaciones necesarias del servicio: avisos de rechazos, mantenimientos, vencimiento de tu plan o de tu certificado.",
        "Gestionar tu plan y sus pagos, y cumplir nuestras obligaciones legales y tributarias.",
      ],
      cierre: [
        "Estos tratamientos son necesarios para ejecutar la relación contractual que aceptas al crear tu cuenta, o los exige la ley, por lo que no requieren un consentimiento adicional (artículo 14 de la Ley N.° 29733). No usamos tus datos para publicidad de terceros, no los vendemos y no te enviaremos comunicaciones comerciales sin tu consentimiento previo y separado.",
      ],
    },
    {
      titulo: "4. Los datos de tus clientes",
      parrafos: [
        "Respecto de los datos personales de tus clientes que incluyes en los comprobantes, el responsable eres tú y khipu actúa como encargado del tratamiento: los tratamos solo para emitir, enviar, conservar y entregarte esos comprobantes, siguiendo tus instrucciones, y no los usamos para nada más.",
        "Te corresponde contar con una base legal para tratar esos datos. Emitir un comprobante es una obligación tributaria, lo que normalmente constituye esa base.",
      ],
    },
    {
      titulo: "5. Con quién los compartimos",
      parrafos: ["Compartimos datos solo con quienes los necesitan para prestar el servicio:"],
      lista: [
        "SUNAT, que recibe tus comprobantes y los datos que contienen, por mandato de la normativa tributaria.",
        "Nuestro proveedor de infraestructura en la nube, donde funcionan la aplicación y la base de datos.",
        "Nuestro proveedor de almacenamiento de archivos, donde se guardan los XML, CDR y PDF, y las copias de respaldo cifradas.",
        "Nuestro proveedor de correo electrónico, que entrega los correos del servicio (verificación, recuperación de contraseña y avisos).",
        "Autoridades judiciales o administrativas, cuando una norma o una orden lo exija.",
      ],
      cierre: [
        "Los proveedores actúan como encargados del tratamiento: solo pueden usar los datos para prestarnos su servicio y deben protegerlos con medidas de seguridad adecuadas. Te informaremos de la identidad de cada uno si nos lo pides.",
      ],
    },
    {
      titulo: "6. Transferencia internacional",
      parrafos: [
        "Algunos de nuestros proveedores de infraestructura, almacenamiento y correo tienen sus servidores fuera del Perú, por lo que tus datos pueden almacenarse en otros países. Este flujo transfronterizo es necesario para prestar el servicio y lo hacemos con proveedores que ofrecen un nivel de protección adecuado, conforme al artículo 15 de la Ley N.° 29733.",
      ],
    },
    {
      titulo: "7. Cuánto tiempo los conservamos",
      lista: [
        "Datos de tu cuenta y de tus empresas: mientras tu cuenta esté vigente.",
        "Certificado digital y credenciales SOL: mientras los mantengas cargados; al reemplazarlos o al dar de baja la cuenta se eliminan.",
        "Comprobantes (XML, CDR y PDF): durante el plazo de conservación de tu plan y, como mínimo, mientras no haya prescrito la obligación tributaria vinculada a ellos, que en general es de cinco años contados desde el 1 de enero del año siguiente a su emisión.",
        "Registros técnicos y de seguridad: hasta doce meses, salvo que hagan falta para investigar un incidente.",
        "Después de la baja de la cuenta: eliminamos o anonimizamos los datos en un plazo de noventa días, salvo los que debamos conservar por obligación legal, que se guardan bloqueados hasta que venza ese plazo.",
      ],
      parrafos: ["Conservamos cada dato solo el tiempo necesario:"],
    },
    {
      titulo: "8. Cómo los protegemos",
      parrafos: ["Aplicamos medidas técnicas y organizativas proporcionales al riesgo, entre ellas:"],
      lista: [
        "Toda la comunicación con el portal y la API viaja cifrada (HTTPS).",
        "El certificado digital, su contraseña y las credenciales SOL se guardan cifrados con AES-256-GCM y una clave maestra que no está en la base de datos.",
        "Las contraseñas y las API keys se guardan solo como hashes irreversibles.",
        "El acceso administrativo exige un segundo factor de autenticación, y cada acción queda registrada.",
        "El personal de soporte solo puede ver tu cuenta en modo de solo lectura, por un tiempo limitado y quedando registrado; no puede emitir, ni cambiar tus credenciales o tu contraseña.",
        "Copias de respaldo cifradas, guardadas en un lugar distinto al de la base de datos.",
      ],
      cierre: [
        "Si ocurriera un incidente de seguridad que afecte tus datos, te lo comunicaremos y lo notificaremos a la Autoridad Nacional de Protección de Datos Personales en los términos que exige la normativa.",
      ],
    },
    {
      titulo: "9. Tus derechos",
      parrafos: [
        "Como titular de tus datos personales puedes ejercer en cualquier momento tus derechos de información, acceso, rectificación, cancelación y oposición (derechos ARCO), así como revocar un consentimiento que nos hayas dado.",
        `Para ejercerlos escríbenos a ${RESPONSABLE.correo} indicando tu nombre, el correo de tu cuenta, el derecho que quieres ejercer y, si corresponde, los documentos que lo sustenten. Responderemos dentro de los plazos que establece el reglamento de la Ley N.° 29733. Muchos datos de tu cuenta puedes corregirlos tú mismo desde el portal.`,
        "La cancelación de datos que debemos conservar por obligación legal (por ejemplo, los comprobantes ya enviados a SUNAT) se hará efectiva cuando venza el plazo de esa obligación.",
        "Si consideras que no atendimos tu solicitud, puedes presentar una reclamación ante la Autoridad Nacional de Protección de Datos Personales del Ministerio de Justicia y Derechos Humanos.",
      ],
    },
    {
      titulo: "10. Cookies",
      parrafos: [
        "El portal usa solo las cookies técnicas necesarias para mantener tu sesión iniciada y recordar la empresa que estás usando. No usamos cookies de publicidad ni de analítica de terceros.",
      ],
    },
    {
      titulo: "11. Menores de edad",
      parrafos: ["khipu es un servicio para empresas y profesionales. No está dirigido a menores de edad y no recopilamos a sabiendas sus datos."],
    },
    {
      titulo: "12. Cambios a esta política",
      parrafos: [
        "Si cambiamos esta política publicaremos la nueva versión con su fecha y, si el cambio afecta la forma en que tratamos tus datos, te pediremos aceptarla la próxima vez que entres al portal.",
      ],
    },
  ],
};
