import Link from "next/link";
import { ApiKeysTable } from "@/components/api-keys/api-keys-table";
import { listarApiKeys } from "@/lib/api/api-keys";
import { formatearFechaHora } from "@/lib/formato";
import { getServerSession } from "@/lib/session-server";
import { cn } from "@/lib/utils";
import { Metrica } from "@/components/ui/metrica";

export default async function ApiKeysPage() {
  const { access, empresaId } = await getServerSession();

  if (!access || !empresaId) {
    return (
      <p className="text-sm text-muted-foreground">
        Configura primero una empresa para crear API keys.{" "}
        <Link href="/onboarding" className="text-primary hover:underline">
          Configurar empresa
        </Link>
      </p>
    );
  }

  const apiKeys = await listarApiKeys(access, empresaId);
  const activas = apiKeys.filter((k) => k.activa);
  const revocadas = apiKeys.length - activas.length;
  const ultima = apiKeys[0]; // la API devuelve la más reciente primero

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <section className="grid grid-cols-2 gap-x-5 gap-y-4 rounded-xl border border-border bg-card px-5 py-3 shadow-xs lg:grid-cols-4 lg:divide-x lg:divide-border lg:[&>*:not(:first-child)]:pl-5">
        <Metrica
          etiqueta="Llaves activas"
          ayuda={
            activas.length > 0 ? (
              <span className="flex items-center gap-1 text-success-foreground">
                <span className="size-1.5 shrink-0 rounded-full bg-success-solid" />
                Listas para emitir por API
              </span>
            ) : (
              <span className="flex items-center gap-1 text-warning-foreground">
                <span className="size-1.5 shrink-0 rounded-full bg-warning-solid" />
                Sin acceso por API todavía
              </span>
            )
          }
        >
          {activas.length}
          <span className="text-[12px] font-normal text-muted-foreground">/ {apiKeys.length} creadas</span>
        </Metrica>

        <Metrica etiqueta="Última llave creada" ayuda={ultima ? formatearFechaHora(ultima.creada_en) : "Crea la primera con «Crear API key»"}>
          {ultima ? <span className={cn(!ultima.activa && "text-muted-foreground line-through")}>{ultima.prefijo}</span> : <span className="text-muted-foreground/60">—</span>}
        </Metrica>

        <Metrica
          etiqueta="Llaves revocadas"
          ayuda={revocadas > 0 ? "Ya no autentican ninguna petición" : "Ninguna revocada hasta ahora"}
        >
          <span className={cn(revocadas > 0 && "text-muted-foreground")}>{revocadas}</span>
        </Metrica>

        <Metrica
          etiqueta="Autenticación"
          ayuda={
            <>
              <span className="truncate">Header HTTP · una llave por integración</span>
              <span className="text-muted-foreground/40">·</span>
              <span className="shrink-0 text-primary">SHA-256</span>
            </>
          }
        >
          <span className="text-primary">X-Api-Key</span>
        </Metrica>
      </section>

      <ApiKeysTable apiKeys={apiKeys} />
    </div>
  );
}
