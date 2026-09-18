import { CatalogosView } from "@/components/developers/catalogos-view";
import { listarCatalogosCompletos } from "@/lib/api/catalogos";
import { messages } from "@/lib/messages";

export const metadata = { title: `Catálogos SUNAT · ${messages.app.nombre}` };

export default async function CatalogosPage() {
  const catalogos = await listarCatalogosCompletos();
  return <CatalogosView catalogos={catalogos} />;
}
