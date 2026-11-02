import type { Metadata } from "next";
import { useMessages } from "next-intl";
import { getMessages } from "next-intl/server";
import { NotaProsa, P, PaginaGuia, Seccion, Tabla } from "@/components/developers/guia/prosa";
import { Rico } from "@/components/developers/guia/rico";
import { codigosDeError, codigosHttp, estados } from "@/components/developers/errores-datos";
import { indiceErrores } from "@/components/developers/indices";
import { interpolar } from "@/i18n/mensajes";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: interpolar(messages.developers.meta.errores, { app: messages.app.nombre }) };
}

export default function ErroresPage() {
  const messages = useMessages();
  const t = messages.developers.errores;
  return (
    <PaginaGuia titulo={t.titulo} resumen={t.resumen} indice={indiceErrores(messages)}>
      <Seccion id="estados" titulo={t.indice.estados}>
        <P>
          <Rico texto={t.ciclo} />
        </P>
        <Tabla cabeceras={t.cabeceras.estados} filas={estados(messages).map(([e, s, q]) => [e, s, q])} />
        <NotaProsa tono="aviso">
          <Rico texto={t.notaErrorEnvio} />
        </NotaProsa>
      </Seccion>

      <Seccion id="http" titulo={t.indice.http}>
        <Tabla cabeceras={t.cabeceras.http} filas={codigosHttp(messages).map(([h, c]) => [h, c])} />
      </Seccion>

      <Seccion id="codigos" titulo={t.indice.codigos}>
        <P>
          <Rico texto={t.codigosIntro} />
        </P>
        <Tabla cabeceras={t.cabeceras.codigos} filas={codigosDeError(messages).map(([c, h, s, q]) => [c, h, s, q])} />
      </Seccion>

      <Seccion id="sunat" titulo={t.indice.sunat}>
        <P>
          <Rico texto={t.sunatIntro} />
        </P>
        <Tabla cabeceras={t.cabeceras.sunat} filas={t.sunatFilas} />
        <P>
          <Rico texto={t.sunatEjemplos} />
        </P>
      </Seccion>
    </PaginaGuia>
  );
}
