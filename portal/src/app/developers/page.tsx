import type { Metadata } from "next";
import { getMessages } from "next-intl/server";
import { DevelopersView } from "@/components/developers/developers-view";
import { Rico } from "@/components/developers/guia/rico";
import { interpolar } from "@/i18n/mensajes";
import { apiBaseUrl, apiPublicUrl } from "@/lib/api/client";
import { getServerSession } from "@/lib/session-server";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: interpolar(messages.developers.meta.referencia, { app: messages.app.nombre }) };
}

export default async function DevelopersPage() {
  // El spec se lee por la URL interna; a Scalar se le da la pública, que es la que el navegador puede llamar.
  const baseServerURL = apiPublicUrl();
  // La referencia interactiva se arma con el OpenAPI de la API: si no está disponible (portal desplegado sin backend, o
  // caída) se ofrece el resto de la documentación en vez de un 500, que es lo que pasaba.
  const [spec, { access, empresaId }] = await Promise.all([
    fetch(`${apiBaseUrl()}/openapi.json`, { cache: "no-store" })
      .then((r) => (r.ok ? r.json() : null))
      .catch(() => null),
    getServerSession(),
  ]);
  if (!spec) {
    const t = (await getMessages()).developers.referencia.sinSpec;
    return (
      <div className="mx-auto w-full max-w-6xl px-4 py-10 md:px-6">
        <h1 className="font-heading text-2xl font-semibold tracking-tight text-foreground">{t.titulo}</h1>
        <p className="mt-2 max-w-2xl text-[14px] leading-relaxed text-muted-foreground">
          <Rico texto={t.texto} enlace="underline" />
        </p>
      </div>
    );
  }

  return <DevelopersView spec={spec} baseServerURL={baseServerURL} mostrarGenerarKey={Boolean(access && empresaId)} />;
}
