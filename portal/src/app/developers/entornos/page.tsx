import Link from "next/link";
import { ENTORNOS, INDICE_ENTORNOS, LIMITES, REINTENTOS } from "@/components/developers/entornos-datos";
import { Codigo, NotaProsa, P, PaginaGuia, Seccion, Tabla } from "@/components/developers/guia/prosa";
import { messages } from "@/lib/messages";

export const metadata = { title: `Entornos y límites · ${messages.app.nombre}` };

export default function EntornosPage() {
  return (
    <PaginaGuia
      titulo="Entornos y límites"
      resumen="En qué se diferencian BETA y producción, qué topes tiene la API y cuánto esperar antes de volver a intentar. Todo lo de esta página es lo que hoy hace khipu; lo que todavía no se publica está al final."
      indice={INDICE_ENTORNOS}
    >
      <Seccion id="entornos" titulo="BETA y producción">
        <P>
          Cada empresa tiene su entorno, que se fija al registrarla. La URL de la API y la API key son las mismas en los dos: lo que cambia es adónde envía khipu el comprobante y qué
          validez tiene. El paso de BETA a producción lo hace la plataforma, a pedido de soporte, cuando SUNAT haya aprobado la homologación: no hay un control para hacerlo en el
          portal ni en la API, y se rechaza (409) mientras la empresa tenga envíos pendientes.
        </P>
        <Tabla cabeceras={["", "BETA", "PRODUCCION"]} filas={ENTORNOS.map(([a, b, p]) => [a, b, p])} />
        <NotaProsa>
          Integre y pruebe todo en <Codigo>BETA</Codigo>: lo que SUNAT acepta allí lo acepta en producción con las mismas reglas de contenido. Los casos que khipu verificó contra
          e-beta están en la{" "}
          <Link href="/developers/guia" className="text-primary hover:underline">
            guía de emisión
          </Link>
          .
        </NotaProsa>
      </Seccion>

      <Seccion id="limites" titulo="Límites y cuotas">
        <Tabla cabeceras={["Qué", "Límite", "Cuando se pasa"]} filas={LIMITES.map(([q, l, c]) => [q, l, c])} />
        <P>
          La emisión no tiene un tope de peticiones por segundo: lo que la acota es el tope mensual del plan. Las emisiones simultáneas sobre{" "}
          <strong className="text-foreground">una misma serie</strong> se atienden una tras otra, porque SUNAT exige numeración consecutiva sin huecos; repartir la carga en varias series
          las paraleliza.
        </P>
      </Seccion>

      <Seccion id="reintentos" titulo="Reintentos y vigencias">
        <Tabla cabeceras={["Qué", "Cuánto"]} filas={REINTENTOS.map(([q, c]) => [q, c])} />
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
            <strong className="text-foreground">SLA</strong> de disponibilidad y tiempos de respuesta. Sí está medido el costo de preparar un comprobante sin SUNAT (alrededor de 30 ms
            al percentil 95 con 50 documentos por segundo en la prueba de carga, medido en desarrollo: no es un compromiso); el tiempo total depende de SUNAT.
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
