import Link from "next/link";
import { DevelopersView } from "@/components/developers/developers-view";
import { apiBaseUrl, apiPublicUrl } from "@/lib/api/client";
import { messages } from "@/lib/messages";
import { getServerSession } from "@/lib/session-server";

export const metadata = { title: `Desarrolladores · ${messages.app.nombre}` };

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
    return (
      <div className="mx-auto max-w-3xl px-6 py-16">
        <h1 className="font-heading text-2xl font-semibold tracking-tight text-foreground">Documentación de la API</h1>
        <p className="mt-3 text-[13px] leading-relaxed text-muted-foreground">
          La referencia interactiva se genera desde la API y ahora no está disponible. Mientras tanto puedes leer la{" "}
          <Link href="/developers/guia" className="underline">guía de integración</Link> y los{" "}
          <Link href="/developers/errores" className="underline">códigos de error</Link>.
        </p>
      </div>
    );
  }

  return <DevelopersView spec={spec} baseServerURL={baseServerURL} mostrarGenerarKey={Boolean(access && empresaId)} />;
}
