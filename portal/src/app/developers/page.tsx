import { ApiReference } from "@/components/developers/api-reference";
import { apiBaseUrl } from "@/lib/api/client";
import { messages } from "@/lib/messages";

export const metadata = { title: `Desarrolladores · ${messages.app.nombre}` };

export default async function DevelopersPage() {
  const baseServerURL = apiBaseUrl();
  const spec = await fetch(`${baseServerURL}/openapi.json`, { cache: "no-store" }).then((r) => r.json());

  return (
    <div className="min-h-screen">
      <ApiReference spec={spec} baseServerURL={baseServerURL} />
    </div>
  );
}
