import Link from "next/link";
import { SeriesTable } from "@/components/series/series-table";
import { ETIQUETAS_TIPO, listarFacturas } from "@/lib/api/facturas";
import { listarSeries } from "@/lib/api/series";
import { formatearFecha } from "@/lib/formato";
import { getServerSession } from "@/lib/session-server";
import { cn } from "@/lib/utils";

const TIPOS_SOPORTADOS = ["01", "03", "07", "08"] as const;

const ABREV_TIPO: Record<string, string> = { "01": "FAC", "03": "BOL", "07": "NC", "08": "ND" };

function numero(n: number): string {
  return String(n).padStart(8, "0");
}

function Metrica({ etiqueta, children, ayuda }: { etiqueta: string; children: React.ReactNode; ayuda: React.ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5">
      <span className="truncate text-[11px] font-medium tracking-wider text-muted-foreground uppercase">{etiqueta}</span>
      <div className="flex min-w-0 flex-wrap items-baseline gap-x-1.5 font-mono text-lg font-semibold tracking-tight text-foreground">{children}</div>
      <div className="flex min-w-0 items-center gap-1 truncate text-[11px] text-muted-foreground">{ayuda}</div>
    </div>
  );
}

export default async function SeriesPage() {
  const { access, empresaId } = await getServerSession();

  if (!access || !empresaId) {
    return (
      <p className="text-sm text-muted-foreground">
        Configura primero una empresa para crear series.{" "}
        <Link href="/onboarding" className="text-primary hover:underline">
          Configurar empresa
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
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <section className="grid grid-cols-2 gap-x-5 gap-y-4 rounded-xl border border-border bg-card px-5 py-3 shadow-xs lg:grid-cols-4 lg:divide-x lg:divide-border lg:[&>*:not(:first-child)]:pl-5">
        <Metrica
          etiqueta="Series habilitadas"
          ayuda={
            <span className={cn("flex items-center gap-1", porcentaje === 100 ? "text-success-foreground" : "text-warning-foreground")}>
              <span className={cn("size-1.5 shrink-0 rounded-full", porcentaje === 100 ? "bg-success-solid" : "bg-warning-solid")} />
              {porcentaje}% operativas
            </span>
          }
        >
          {activas}
          <span className="text-[12px] font-normal text-muted-foreground">/ {series.length} configuradas</span>
        </Metrica>

        <Metrica
          etiqueta="Último correlativo emitido"
          ayuda={
            ultimo ? (
              <>
                <span className="truncate">{ETIQUETAS_TIPO[ultimo.tipo] ?? ultimo.tipo} electrónica</span>
                <span className="text-muted-foreground/40">·</span>
                <span className="shrink-0 font-mono">{formatearFecha(ultimo.fecha_emision)}</span>
              </>
            ) : (
              "Sin emisiones todavía"
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
          etiqueta="Cobertura de comprobantes"
          ayuda={
            <>
              <span className="truncate">
                {cubiertos.length === 0 ? "Sin series configuradas" : cubiertos.map((t) => `${t} ${ABREV_TIPO[t]}`).join(", ")}
              </span>
              <span className="text-muted-foreground/40">·</span>
              <span className="shrink-0 text-primary">UBL 2.1</span>
            </>
          }
        >
          {cubiertos.length}
          <span className="text-[12px] font-normal text-muted-foreground">/ {TIPOS_SOPORTADOS.length} tipos</span>
        </Metrica>

        <Metrica
          etiqueta="Tipos sin serie"
          ayuda={
            sinSerie.length > 0 ? (
              <span className="flex min-w-0 items-center gap-1 text-destructive">
                <span className="size-1.5 shrink-0 rounded-full bg-destructive" />
                <span className="truncate">{sinSerie.map((t) => `${t} ${ABREV_TIPO[t]}`).join(", ")}</span>
              </span>
            ) : (
              <span className="text-success-foreground">Todos los tipos cubiertos</span>
            )
          }
        >
          <span className={cn(sinSerie.length > 0 && "text-destructive")}>{sinSerie.length}</span>
        </Metrica>
      </section>

      <SeriesTable series={series} />
    </div>
  );
}
