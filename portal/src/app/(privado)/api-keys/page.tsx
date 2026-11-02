import type { Metadata } from "next";
import Link from "next/link";
import { SinEmpresa } from "@/components/compartidos/sin-empresa";
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
import { AyudaMetrica, FilaMetricas } from "khipu-design-system/patrones/metricas";
import { CONTENEDOR_PAGINA } from "@/lib/estilos";
import { ClockCounterClockwiseIcon, KeyIcon, LockIcon, ProhibitIcon } from "@phosphor-icons/react/dist/ssr";

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
      <SinEmpresa texto={t.sinEmpresa} accion={t.configurarEmpresa} />
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
    <div className={CONTENEDOR_PAGINA}>
      <FilaMetricas>
        <Metrica
          etiqueta={t.activas} icono={KeyIcon} tono={activas.length > 0 ? "ok" : "aviso"}
          ayuda={
            activas.length > 0 && faltan === null ? (
              <span className="text-muted-foreground">{t.activasSinComprobar}</span>
            ) : activas.length > 0 && sinCertificado ? (
              <Link href={enlaceASeccion("certificado")} className="min-w-0 hover:underline">
                <AyudaMetrica tono="aviso">{t.faltaCertificado}</AyudaMetrica>
              </Link>
            ) : activas.length > 0 && sinSol ? (
              <Link href={enlaceASeccion("sol")} className="min-w-0 hover:underline">
                <AyudaMetrica tono="aviso">{t.faltaSol}</AyudaMetrica>
              </Link>
            ) : activas.length > 0 ? (
              <AyudaMetrica tono="ok">{t.listas}</AyudaMetrica>
            ) : (
              <AyudaMetrica tono="aviso">{t.sinAcceso}</AyudaMetrica>
            )
          }
          sufijo={interpolar(t.creadas, { total: apiKeys.length })}
        >
          {activas.length}
        </Metrica>

        <Metrica etiqueta={t.ultima} icono={ClockCounterClockwiseIcon} tono="neutro" ayuda={ultima ? formatearFechaHora(ultima.creada_en, locale) : t.ultimaVacia} texto>
          {ultima ? <span className={cn(!ultima.activa && "text-muted-foreground line-through")}>{ultima.prefijo}</span> : <span className="text-muted-foreground/60">—</span>}
        </Metrica>

        <Metrica
          etiqueta={t.revocadas} icono={ProhibitIcon} tono="neutro"
          ayuda={revocadas > 0 ? t.revocadasAyuda : t.ningunaRevocada}
        >
          <span className={cn(revocadas > 0 && "text-muted-foreground")}>{revocadas}</span>
        </Metrica>

        <Metrica
          etiqueta={t.autenticacion} icono={LockIcon} tono="acento" texto
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
      </FilaMetricas>

      <ApiKeysTable apiKeys={apiKeys} />

      <WebhooksSection webhooks={webhooks} />
    </div>
  );
}
