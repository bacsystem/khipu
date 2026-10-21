import type { Metadata } from "next";
import Link from "next/link";
import { getLocale, getMessages } from "next-intl/server";
import { ApiKeysTable } from "@/components/api-keys/api-keys-table";
import { esRolInsuficiente, SinPermisoDePagina } from "@/components/sin-permiso";
import { WebhooksSection } from "@/components/webhooks/webhooks-section";
import { listarApiKeys } from "@/lib/api/api-keys";
import { obtenerEmpresaActual } from "@/lib/api/empresas";
import { listarWebhooks } from "@/lib/api/webhooks";
import { faltaParaEmitir } from "@/lib/comprobantes/listo-para-emitir";
import { enlaceASeccion } from "@/lib/empresa/secciones";
import { formatearFechaHora, hoyLima } from "@/lib/formato";
import { getServerSession } from "@/lib/session-server";
import { cn } from "@/lib/utils";
import { Metrica } from "@/components/compartidos/metrica";
import { interpolar } from "@/i18n/mensajes";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.apiKeys.pagina.titulo} · ${messages.app.nombre}` };
}

export default async function ApiKeysPage() {
  const { access, empresaId } = await getServerSession();
  const t = (await getMessages()).apiKeys.pagina;
  const locale = await getLocale();

  if (!access || !empresaId) {
    return (
      <p className="text-sm text-muted-foreground">
        {t.sinEmpresa}{" "}
        <Link href="/onboarding" className="text-primary hover:underline">
          {t.configurarEmpresa}
        </Link>
      </p>
    );
  }

  // La empresa solo decide un texto de ayuda: si no se puede leer, la página de llaves sigue (264-H3), y no se afirma nada sobre la emisión.
  // Los webhooks tampoco bloquean la página: si no se leen, su sección lo dice (#19).
  // #21: las API keys son de administradores; con otro rol el backend responde 403 y la página lo explica.
  const llaves = await listarApiKeys(access, empresaId).then(
    (lista) => ({ lista }),
    (error: unknown) => {
      if (esRolInsuficiente(error)) return null;
      throw error;
    },
  );
  if (llaves === null) return <SinPermisoDePagina />;

  const [apiKeys, empresa, webhooks] = await Promise.all([
    llaves.lista,
    obtenerEmpresaActual(access, empresaId).catch(() => null),
    listarWebhooks(access, empresaId).catch(() => null),
  ]);
  const activas = apiKeys.filter((k) => k.activa);
  // C4: una llave activa autentica, pero sin certificado vigente el backend no firma. Sin credenciales SOL sí firma (con `enviar_automatico: false`
  // queda FIRMADO), solo que no envía a SUNAT: son dos avisos distintos.
  const faltan = empresa ? faltaParaEmitir(empresa, hoyLima()) : null;
  const sinCertificado = faltan?.some((f) => f !== "credenciales-sol") ?? false;
  const sinSol = faltan?.includes("credenciales-sol") ?? false;
  const revocadas = apiKeys.length - activas.length;
  const ultima = apiKeys[0]; // la API devuelve la más reciente primero

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <section className="grid grid-cols-2 gap-x-5 gap-y-4 rounded-xl border border-border bg-card px-5 py-3 shadow-xs lg:grid-cols-4 lg:divide-x lg:divide-border lg:[&>*:not(:first-child)]:pl-5">
        <Metrica
          etiqueta={t.activas}
          ayuda={
            activas.length > 0 && faltan === null ? (
              <span className="text-muted-foreground">{t.activasSinComprobar}</span>
            ) : activas.length > 0 && sinCertificado ? (
              <Link href={enlaceASeccion("certificado")} className="flex items-center gap-1 text-warning-foreground hover:underline">
                <span className="size-1.5 shrink-0 rounded-full bg-warning-solid" />
                {t.faltaCertificado}
              </Link>
            ) : activas.length > 0 && sinSol ? (
              <Link href={enlaceASeccion("sol")} className="flex items-center gap-1 text-warning-foreground hover:underline">
                <span className="size-1.5 shrink-0 rounded-full bg-warning-solid" />
                {t.faltaSol}
              </Link>
            ) : activas.length > 0 ? (
              <span className="flex items-center gap-1 text-success-foreground">
                <span className="size-1.5 shrink-0 rounded-full bg-success-solid" />
                {t.listas}
              </span>
            ) : (
              <span className="flex items-center gap-1 text-warning-foreground">
                <span className="size-1.5 shrink-0 rounded-full bg-warning-solid" />
                {t.sinAcceso}
              </span>
            )
          }
        >
          {activas.length}
          <span className="text-[12px] font-normal text-muted-foreground">{interpolar(t.creadas, { total: apiKeys.length })}</span>
        </Metrica>

        <Metrica etiqueta={t.ultima} ayuda={ultima ? formatearFechaHora(ultima.creada_en, locale) : t.ultimaVacia}>
          {ultima ? <span className={cn(!ultima.activa && "text-muted-foreground line-through")}>{ultima.prefijo}</span> : <span className="text-muted-foreground/60">—</span>}
        </Metrica>

        <Metrica
          etiqueta={t.revocadas}
          ayuda={revocadas > 0 ? t.revocadasAyuda : t.ningunaRevocada}
        >
          <span className={cn(revocadas > 0 && "text-muted-foreground")}>{revocadas}</span>
        </Metrica>

        <Metrica
          etiqueta={t.autenticacion}
          ayuda={
            <>
              <span className="truncate">{t.autenticacionAyuda}</span>
              <span className="text-muted-foreground/40">·</span>
              <span className="shrink-0 text-primary">{"SHA-256"}</span>
            </>
          }
        >
          <span className="text-primary">{"X-Api-Key"}</span>
        </Metrica>
      </section>

      <ApiKeysTable apiKeys={apiKeys} />

      <WebhooksSection webhooks={webhooks} />
    </div>
  );
}
