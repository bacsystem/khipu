import type { Metadata } from "next";
import { useMessages } from "next-intl";
import { getMessages } from "next-intl/server";
import { entornos, indiceEntornos, limites, reintentos } from "@/components/developers/entornos-datos";
import { NotaProsa, P, PaginaGuia, Seccion, Tabla } from "@/components/developers/guia/prosa";
import { Rico } from "@/components/developers/guia/rico";
import { interpolar } from "@/i18n/mensajes";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: interpolar(messages.developers.meta.entornos, { app: messages.app.nombre }) };
}

export default function EntornosPage() {
  const messages = useMessages();
  const t = messages.developers.entornos;
  const indice = indiceEntornos(messages);
  const titulo = (id: string) => indice.find((s) => s.id === id)?.label ?? id;
  return (
    <PaginaGuia titulo={t.titulo} resumen={t.resumen} indice={indice}>
      <Seccion id="entornos" titulo={titulo("entornos")}>
        <P>{t.intro}</P>
        <Tabla cabeceras={["", "BETA", "PRODUCCION"]} filas={entornos(messages).map(([a, b, p]) => [a, b, p])} />
        <NotaProsa>
          <Rico texto={t.notaBeta} />
        </NotaProsa>
      </Seccion>

      <Seccion id="limites" titulo={titulo("limites")}>
        <Tabla cabeceras={t.cabeceras.limites} filas={limites(messages).map(([q, l, c]) => [q, l, c])} />
        <P>
          <Rico texto={t.emisionSimultanea} negrita="text-foreground" />
        </P>
      </Seccion>

      <Seccion id="reintentos" titulo={titulo("reintentos")}>
        <Tabla cabeceras={t.cabeceras.reintentos} filas={reintentos(messages).map(([q, c]) => [q, c])} />
      </Seccion>

      <Seccion id="pendiente" titulo={titulo("pendiente")}>
        <P>{t.pendienteIntro}</P>
        <ul className="list-disc space-y-2 pl-5 text-control leading-relaxed text-muted-foreground">
          {t.pendientes.map((texto) => (
            <li key={texto}>
              <Rico texto={texto} negrita="text-foreground" />
            </li>
          ))}
        </ul>
      </Seccion>
    </PaginaGuia>
  );
}
