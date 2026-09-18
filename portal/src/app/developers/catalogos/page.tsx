import { CatalogosView } from "@/components/developers/catalogos-view";
import { listarCatalogos, obtenerCatalogo } from "@/lib/api/catalogos";
import { messages } from "@/lib/messages";

export const metadata = { title: `Catálogos SUNAT · ${messages.app.nombre}` };

export default async function CatalogosPage() {
  const indice = await listarCatalogos();
  const catalogos = await Promise.all(indice.map((c) => obtenerCatalogo(c.id)));
  return <CatalogosView catalogos={catalogos} />;
}
