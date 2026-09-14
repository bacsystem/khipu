import { DevelopersView } from "@/components/developers/developers-view";
import { apiBaseUrl } from "@/lib/api/client";
import { messages } from "@/lib/messages";
import { getServerSession } from "@/lib/session-server";

export const metadata = { title: `Desarrolladores · ${messages.app.nombre}` };

export default async function DevelopersPage() {
  const baseServerURL = apiBaseUrl();
  const [spec, { access, empresaId }] = await Promise.all([
    fetch(`${baseServerURL}/openapi.json`, { cache: "no-store" }).then((r) => r.json()),
    getServerSession(),
  ]);

  return (
    <div className="min-h-screen">
      <DevelopersView spec={spec} baseServerURL={baseServerURL} mostrarGenerarKey={Boolean(access && empresaId)} />
    </div>
  );
}
