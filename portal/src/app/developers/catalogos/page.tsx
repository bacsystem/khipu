import { CatalogosView } from "@/components/developers/catalogos-view";
import { listarCatalogosCompletos } from "@/lib/api/catalogos";
import { messages } from "@/lib/messages";

export const metadata = { title: `Catálogos SUNAT · ${messages.app.nombre}` };

export default async function CatalogosPage() {
  // Los catálogos los sirve la API: si no está disponible (landing desplegado sin backend, o caída), la página informa en vez
  // de devolver un 500 — es documentación pública y el resto del sitio no depende de ella.
  let catalogos;
  try {
    catalogos = await listarCatalogosCompletos();
  } catch {
    return (
      <div className="mx-auto max-w-3xl px-6 py-16">
        <h1 className="font-heading text-2xl font-semibold tracking-tight text-foreground">Catálogos SUNAT</h1>
        <p className="mt-3 text-[13px] leading-relaxed text-muted-foreground">
          No pudimos cargar los catálogos: los sirve la API y ahora no está disponible. Volvé a intentarlo en un momento; el
          resto de la documentación no depende de ellos.
        </p>
      </div>
    );
  }
  return <CatalogosView catalogos={catalogos} />;
}
