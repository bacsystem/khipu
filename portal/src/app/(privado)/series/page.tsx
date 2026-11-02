import { SinEmpresa } from "@/components/compartidos/sin-empresa";
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
import { AyudaMetrica, FilaMetricas } from "khipu-design-system/patrones/metricas";
import { CONTENEDOR_PAGINA } from "@/lib/estilos";
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
      <SinEmpresa texto={t.sinEmpresa} accion={t.configurarEmpresa} />
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
    <div className={CONTENEDOR_PAGINA}>
      <FilaMetricas>
        <Metrica
          etiqueta={t.seriesHabilitadas} icono={ListNumbersIcon} tono={porcentaje === 100 ? "ok" : "aviso"}
          ayuda={<AyudaMetrica tono={porcentaje === 100 ? "ok" : "aviso"}>{interpolar(t.operativas, { porcentaje })}</AyudaMetrica>}
          sufijo={interpolar(t.configuradas, { total: series.length })}
        >
          {activas}
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
          sufijo={interpolar(t.tipos, { total: TIPOS_SOPORTADOS.length })}
        >
          {cubiertos.length}
        </Metrica>

        <Metrica
          etiqueta={t.tiposSinSerie} icono={WarningCircleIcon} tono={sinSerie.length > 0 ? "error" : "ok"}
          ayuda={
            sinSerie.length > 0 ? (
              <AyudaMetrica tono="error">{sinSerie.map((t) => `${t} ${ABREV_TIPO[t]}`).join(", ")}</AyudaMetrica>
            ) : (
              <AyudaMetrica tono="ok">{t.todosCubiertos}</AyudaMetrica>
            )
          }
        >
          <span className={cn(sinSerie.length > 0 && "text-destructive-texto")}>{sinSerie.length}</span>
        </Metrica>
      </FilaMetricas>

      <SeriesTable series={series} />
    </div>
  );
}
