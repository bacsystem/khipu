import Link from "next/link";
import { ApiKeysTable } from "@/components/api-keys/api-keys-table";
import { listarApiKeys } from "@/lib/api/api-keys";
import { obtenerEmpresaActual } from "@/lib/api/empresas";
import { faltaParaEmitir } from "@/lib/comprobantes/listo-para-emitir";
import { formatearFechaHora, hoyLima } from "@/lib/formato";
import { getServerSession } from "@/lib/session-server";
import { cn } from "@/lib/utils";
import { Metrica } from "@/components/ui/metrica";
import { messages } from "@/lib/messages";

export const metadata = { title: `API keys e integración · ${messages.app.nombre}` };

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

  const [apiKeys, empresa] = await Promise.all([listarApiKeys(access, empresaId), obtenerEmpresaActual(access, empresaId)]);
  const activas = apiKeys.filter((k) => k.activa);
  // C4: una llave activa autentica, pero la emisión la rechaza el backend si la empresa no tiene certificado o credenciales SOL.
  const puedeEmitir = faltaParaEmitir(empresa, hoyLima()).length === 0;
  const revocadas = apiKeys.length - activas.length;
  const ultima = apiKeys[0]; // la API devuelve la más reciente primero

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <section className="grid grid-cols-2 gap-x-5 gap-y-4 rounded-xl border border-border bg-card px-5 py-3 shadow-xs lg:grid-cols-4 lg:divide-x lg:divide-border lg:[&>*:not(:first-child)]:pl-5">
        <Metrica
          etiqueta="Llaves activas"
          ayuda={
            activas.length > 0 && puedeEmitir ? (
              <span className="flex items-center gap-1 text-success-foreground">
                <span className="size-1.5 shrink-0 rounded-full bg-success-solid" />
                Listas para emitir por API
              </span>
            ) : activas.length > 0 ? (
              <Link href="/empresa" className="flex items-center gap-1 text-warning-foreground hover:underline">
                <span className="size-1.5 shrink-0 rounded-full bg-warning-solid" />
                Falta el certificado o las credenciales SOL para emitir
              </Link>
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
