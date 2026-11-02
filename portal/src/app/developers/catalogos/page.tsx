import type { Metadata } from "next";
import { getMessages } from "next-intl/server";
import { CatalogosView } from "@/components/developers/catalogos-view";
import { interpolar } from "@/i18n/mensajes";
import { listarCatalogosCompletos } from "@/lib/api/catalogos";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: interpolar(messages.developers.meta.catalogos, { app: messages.app.nombre }) };
}

export default async function CatalogosPage() {
  // Los catálogos los sirve la API: si no está disponible (landing desplegado sin backend, o caída), la página informa en vez
  // de devolver un 500 — es documentación pública y el resto del sitio no depende de ella.
  let catalogos;
  try {
    catalogos = await listarCatalogosCompletos();
  } catch {
    const t = (await getMessages()).developers.catalogos;
    return (
      <div className="mx-auto max-w-3xl px-6 py-16">
        <h1 className="font-heading text-2xl font-semibold tracking-tight text-foreground">{t.titulo}</h1>
        <p className="mt-3 text-control leading-relaxed text-muted-foreground">
          {t.noDisponible}
        </p>
      </div>
    );
  }
  return <CatalogosView catalogos={catalogos} />;
}
