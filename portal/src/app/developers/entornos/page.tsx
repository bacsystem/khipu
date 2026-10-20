import Link from "next/link";
import { Codigo, NotaProsa, P, PaginaGuia, Seccion, Tabla } from "@/components/developers/guia/prosa";
import { messages } from "@/lib/messages";

export const metadata = { title: `Entornos y límites · ${messages.app.nombre}` };

const INDICE = [
  { id: "entornos", label: "BETA y producción" },
  { id: "limites", label: "Límites y cuotas" },
  { id: "reintentos", label: "Reintentos y vigencias" },
  { id: "pendiente", label: "Lo que aún no está publicado" },
];

export default function EntornosPage() {
  return (
    <PaginaGuia
      titulo="Entornos y límites"
      resumen="En qué se diferencian BETA y producción, qué topes tiene la API y cuánto esperar antes de volver a intentar. Todo lo de esta página es lo que hoy hace khipu; lo que todavía no se publica está al final."
      indice={INDICE}
    >
      <Seccion id="entornos" titulo="BETA y producción">
        <P>
          Cada empresa tiene su entorno. La URL de la API y la API key son las mismas en los dos: lo que cambia es adónde envía khipu el comprobante y qué validez tiene. Se
          cambia desde el portal, en <em>Empresa</em>, cuando SUNAT haya aprobado la homologación.
        </P>
        <Tabla
          cabeceras={["", "BETA", "PRODUCCION"]}
          filas={[
            ["Destino", "El ambiente de pruebas de SUNAT (e-beta).", "SUNAT en producción."],
            ["Validez tributaria", "Ninguna: son comprobantes de prueba.", "Plena: el CDR aceptado es la constancia legal."],
            ["Credenciales SOL", "Las de prueba de SUNAT (usuario MODDATOS).", "El usuario secundario de la empresa."],
            [
              "Consultar el CDR o la validez de un comprobante",
              "No disponible: SUNAT no publica esos servicios en e-beta. Responde 422 NO_DISPONIBLE_EN_BETA.",
              "Disponible (POST /v1/facturas/{id}/cdr/recuperar y GET /v1/consultas/validez).",
            ],
            [
              "Reglas de validación",
              "Las mismas. e-beta no cruza el padrón de contribuyentes: por ejemplo, aceptó una factura con la tasa reducida del IGV sin la observación 4439 que SUNAT sí puede dar en producción.",
              "Las mismas, más el cruce con el padrón.",
            ],
          ]}
        />
        <NotaProsa>
          Integre y pruebe todo en <Codigo>BETA</Codigo>: lo que SUNAT acepta allí lo acepta en producción con las mismas reglas de contenido. Los casos que khipu verificó
          contra e-beta están en la{" "}
          <Link href="/developers/guia" className="text-primary hover:underline">
            guía de emisión
          </Link>
          .
        </NotaProsa>
      </Seccion>

      <Seccion id="limites" titulo="Límites y cuotas">
        <Tabla
          cabeceras={["Qué", "Límite", "Cuando se pasa"]}
          filas={[
            ["Ítems por comprobante", "999 líneas (SUNAT 2023; 2137 en las notas).", "422 ITEM_INVALIDO."],
            ["Descripción de un ítem", "De 1 a 500 caracteres (2026, 2027).", "422 ITEM_INVALIDO."],
            ["Nombre o razón social del comprador", "De 3 a 1500 caracteres sin contar los espacios de los extremos (2022).", "422 RECEPTOR_INVALIDO."],
            ["Boleta sin documento del comprador", "Hasta S/ 700.00, solo en PEN.", "422 RECEPTOR_INVALIDO."],
            ["Documentos por mes", "El tope del plan de la cuenta, sumando facturas, boletas y notas de todas sus empresas.", "429 LIMITE_PLAN; 402 PLAN_VENCIDO cuando el plan venció y pasaron sus días de gracia."],
            ["Listados", "De 1 a 100 resultados por página (20 por defecto). El total viene en la cabecera X-Total-Count.", "Se ajusta al rango, no es un error."],
            ["Consulta de un RUC (GET /v1/consultas/ruc/{ruc})", "60 por minuto y 5 000 por día, por empresa.", "429 DEMASIADAS_CONSULTAS."],
            ["Certificado digital", "Un archivo .p12 o .pfx de hasta 1 MB.", "El archivo se rechaza."],
            ["Inicio de sesión del portal", "Los intentos fallidos se limitan por correo y por IP durante 15 minutos.", "429 DEMASIADOS_INTENTOS_LOGIN."],
          ]}
        />
        <P>
          La emisión no tiene un tope de peticiones por segundo: lo que la acota es el tope mensual del plan. Las emisiones simultáneas sobre <strong className="text-foreground">una
          misma serie</strong> se atienden una tras otra, porque SUNAT exige numeración consecutiva sin huecos; repartir la carga en varias series las paraleliza.
        </P>
      </Seccion>

      <Seccion id="reintentos" titulo="Reintentos y vigencias">
        <Tabla
          cabeceras={["Qué", "Cuánto"]}
          filas={[
            ["Idempotency-Key", "24 horas: repetir la misma clave con el mismo pedido devuelve 200 con el mismo comprobante; con otro contenido responde 422 IDEMPOTENCIA_INVALIDA."],
            [
              "Reenvío de un comprobante en ERROR_ENVIO",
              "khipu reintenta solo con espera creciente, hasta 20 intentos (unas 74 horas). Una boleta no se abandona mientras siga en plazo del resumen diario (hasta el 7.º día).",
            ],
            ["Consulta del ticket de una baja o un resumen diario", "Cada 30 segundos al principio, con intervalo creciente hasta 5 minutos, sin límite de intentos mientras SUNAT no responda."],
            ["Entrega de un webhook", "Si su servidor no responde 2xx, khipu reintenta a 1, 5 y 15 minutos y a 1, 3, 6 y 12 horas. Son 8 intentos en total (unas 22 horas); tras 10 fallos seguidos el webhook se apaga y se avisa por correo a la cuenta."],
            ["Log de entregas de webhooks", "30 días (GET /v1/webhooks/{id}/entregas)."],
            ["Plazo de envío a SUNAT", "Factura y sus notas: 3 días calendario desde la emisión. Boleta y sus notas: sola hasta el 5.º día y por resumen diario hasta el 7.º; pasado el 7.º se informa fuera de plazo."],
            ["Anular (baja)", "Hasta 7 días calendario desde la emisión, en hora de Lima (regla 2957)."],
          ]}
        />
      </Seccion>

      <Seccion id="pendiente" titulo="Lo que aún no está publicado">
        <P>Esto no se ha definido todavía y por eso no figura arriba con un número; cuando exista se publica aquí:</P>
        <ul className="list-disc space-y-2 pl-5 text-[13px] leading-relaxed text-muted-foreground">
          <li>
            <strong className="text-foreground">IPs de salida</strong> de khipu hacia su servidor (para quien filtra por IP los webhooks). Mientras tanto, verifique cada entrega con la
            firma <Codigo>X-Khipu-Signature</Codigo>, que no depende de la IP: vea{" "}
            <Link href="/developers/guia#webhooks" className="text-primary hover:underline">
              Webhooks
            </Link>
            .
          </li>
          <li>
            <strong className="text-foreground">SLA</strong> de disponibilidad y tiempos de respuesta. Sí está medido el costo de preparar un comprobante sin SUNAT (alrededor de 30 ms al
            percentil 95 en la prueba de carga, con 50 documentos por segundo); el tiempo total depende de SUNAT.
          </li>
          <li>
            <strong className="text-foreground">SDKs y conectores</strong> oficiales: no existen todavía. La API es REST con JSON y los snippets de la{" "}
            <Link href="/developers/guia#inicio-rapido" className="text-primary hover:underline">
              guía de inicio rápido
            </Link>{" "}
            son el punto de partida.
          </li>
        </ul>
      </Seccion>
    </PaginaGuia>
  );
}
