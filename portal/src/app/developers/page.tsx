import { DevelopersView } from "@/components/developers/developers-view";
import { apiBaseUrl, apiPublicUrl } from "@/lib/api/client";
import { messages } from "@/lib/messages";
import { getServerSession } from "@/lib/session-server";

export const metadata = { title: `Desarrolladores · ${messages.app.nombre}` };

export default async function DevelopersPage() {
  // El spec se lee por la URL interna; a Scalar se le da la pública, que es la que el navegador puede llamar.
  const baseServerURL = apiPublicUrl();
  const [spec, { access, empresaId }] = await Promise.all([
    fetch(`${apiBaseUrl()}/openapi.json`, { cache: "no-store" }).then((r) => r.json()),
    getServerSession(),
  ]);

  return <DevelopersView spec={spec} baseServerURL={baseServerURL} mostrarGenerarKey={Boolean(access && empresaId)} />;
}
