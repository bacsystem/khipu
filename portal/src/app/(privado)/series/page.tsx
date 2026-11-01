import Link from "next/link";
import { SeriesTable } from "@/components/series/series-table";
import { etiquetasTipo, listarFacturas } from "@/lib/api/facturas";
import { listarSeries } from "@/lib/api/series";
import { formatearFecha } from "@/lib/formato";
import { getServerSession } from "@/lib/session-server";
import { cn } from "@/lib/utils";
import { Metrica } from "@/components/compartidos/metrica";
import type { Metadata } from "next";
import { getLocale, getMessages } from "next-intl/server";
import { interpolar } from "@/i18n/mensajes";
import type { Locale } from "@/i18n/config";
import { FilaMetricas } from "khipu-design-system/patrones/metricas";
import { ListNumbersIcon, ReceiptIcon, ShieldCheckIcon, WarningCircleIcon } from "@phosphor-icons/react/dist/ssr";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.nav.items.series} · ${messages.app.nombre}` };
}

const TIPOS_SOPORTADOS = ["01", "03", "07", "08"] as const;

const ABREV_TIPO: Record<string, string> = { "01": "FAC", "03": "BOL", "07": "NC", "08": "ND", "09": "GRR", "31": "GRT" };

function numero(n: number): string {
  return String(n).padStart(8, "0");
}

export default async function SeriesPage() {
  const { access, empresaId } = await getServerSession();
  const messages = await getMessages();
  const locale = (await getLocale()) as Locale;
  const t = messages.series.pagina;

  if (!access || !empresaId) {
    return (
      <p className="text-cuerpo text-muted-foreground">
        {t.sinEmpresa}{" "}
        <Link href="/onboarding" className="text-primary hover:underline">
          {t.configurarEmpresa}
        </Link>
      </p>
    );
  }

  const [series, ultimos] = await Promise.all([
    listarSeries(access, empresaId),
    listarFacturas(access, empresaId, { pagina: 1, porPagina: 1 }).catch(() => ({ datos: [], total: 0 })),
  ]);
  const ultimo = ultimos.datos[0];
  const activas = series.filter((s) => s.activa).length;
  const tiposConfigurados = new Set(series.map((s) => s.tipo));
  const cubiertos = TIPOS_SOPORTADOS.filter((t) => tiposConfigurados.has(t));
  const sinSerie = TIPOS_SOPORTADOS.filter((t) => !tiposConfigurados.has(t));
  const porcentaje = series.length === 0 ? 0 : Math.round((activas / series.length) * 100);

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-3">
      <FilaMetricas>
        <Metrica
          etiqueta={t.seriesHabilitadas} icono={ListNumbersIcon} tono={porcentaje === 100 ? "ok" : "aviso"}
          ayuda={
            <span className={cn("flex items-center gap-1", porcentaje === 100 ? "text-success-foreground" : "text-warning-foreground")}>
              <span className={cn("size-1.5 shrink-0 rounded-full", porcentaje === 100 ? "bg-success-solid" : "bg-warning-solid")} />
              {interpolar(t.operativas, { porcentaje })}
            </span>
          }
        >
          {activas}
          <span className="text-control font-normal text-muted-foreground">{interpolar(t.configuradas, { total: series.length })}</span>
        </Metrica>

        <Metrica
          etiqueta={t.ultimoCorrelativo} icono={ReceiptIcon} tono="acento"
          ayuda={
            ultimo ? (
              <>
                <span className="truncate">{interpolar(t.tipoElectronico, { tipo: etiquetasTipo(messages)[ultimo.tipo] ?? ultimo.tipo })}</span>
                <span className="text-muted-foreground/40">·</span>
                <span className="shrink-0 font-mono">{formatearFecha(ultimo.fecha_emision, locale)}</span>
              </>
            ) : (
              t.sinEmisiones
            )
          }
        >
          {ultimo ? (
            <>
              <span className="text-primary">{ultimo.serie}</span>
              <span>#{numero(ultimo.numero)}</span>
            </>
          ) : (
            <span className="text-muted-foreground/60">—</span>
          )}
        </Metrica>

        <Metrica
          etiqueta={t.cobertura} icono={ShieldCheckIcon} tono="neutro"
          ayuda={
            <>
              <span className="truncate">
                {cubiertos.length === 0 ? t.sinSeriesConfiguradas : cubiertos.map((t) => `${t} ${ABREV_TIPO[t]}`).join(", ")}
              </span>
              <span className="text-muted-foreground/40">·</span>
              <span className="shrink-0 text-primary">{messages.nav.ubl}</span>
            </>
          }
        >
          {cubiertos.length}
          <span className="text-control font-normal text-muted-foreground">{interpolar(t.tipos, { total: TIPOS_SOPORTADOS.length })}</span>
        </Metrica>

        <Metrica
          etiqueta={t.tiposSinSerie} icono={WarningCircleIcon} tono={sinSerie.length > 0 ? "error" : "ok"}
          ayuda={
            sinSerie.length > 0 ? (
              <span className="flex min-w-0 items-center gap-1 text-destructive">
                <span className="size-1.5 shrink-0 rounded-full bg-destructive" />
                <span className="truncate">{sinSerie.map((t) => `${t} ${ABREV_TIPO[t]}`).join(", ")}</span>
              </span>
            ) : (
              <span className="text-success-foreground">{t.todosCubiertos}</span>
            )
          }
        >
          <span className={cn(sinSerie.length > 0 && "text-destructive")}>{sinSerie.length}</span>
        </Metrica>
      </FilaMetricas>

      <SeriesTable series={series} />
    </div>
  );
}
