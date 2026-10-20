import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { CABECERA_TABLA, TARJETA, TITULO_SECCION } from "@/lib/estilos";
import { formatearFecha, formatearMonto } from "@/lib/formato";
import { monedasDeLaSerie, type PuntoDelDia } from "@/lib/inicio/panel";
import { cn } from "@/lib/utils";

/** Ancho de cada día en el `viewBox`; la barra ocupa 7 de 10 y deja 3 de aire, que en el ancho real nunca baja de los 2 px de separación entre barras. */
const ANCHO_DIA = 10;
const ANCHO_BARRA = 7;
const ALTO = 100;

function dia(fecha: string): number {
  return Number(fecha.slice(8, 10));
}

/** «5 Oct 2026: 3 comprobantes · S/ 1,180.00» — lo que dice el `title` de cada barra y lo que lee un lector de pantalla en la tabla. */
export function descripcionDelDia(p: PuntoDelDia): string {
  const comprobantes = `${p.emitidos} ${p.emitidos === 1 ? "comprobante" : "comprobantes"}`;
  const montos = Object.entries(p.facturado)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([moneda, total]) => formatearMonto(moneda, total));
  return [`${formatearFecha(p.fecha)}: ${comprobantes}`, ...montos].join(" · ");
}

/** El resumen que lee un lector de pantalla del gráfico entero: cuántos, cuántos días y el día de más emisión. */
export function resumenDelGrafico(serie: PuntoDelDia[]): string {
  const total = serie.reduce((s, p) => s + p.emitidos, 0);
  const pico = serie.reduce((max, p) => (p.emitidos > max.emitidos ? p : max), serie[0]);
  const conEmision = serie.filter((p) => p.emitidos > 0).length;
  return `${total} ${total === 1 ? "comprobante emitido" : "comprobantes emitidos"} en ${conEmision} de ${serie.length} días, del ${formatearFecha(serie[0].fecha)} al ${formatearFecha(serie[serie.length - 1].fecha)}. El día de más emisión fue el ${formatearFecha(pico.fecha)}, con ${pico.emitidos}.`;
}

/** Qué días llevan rótulo en el eje: el primero, cada cinco y el último (hoy). En un teléfono 31 rótulos no caben. */
function conRotulo(i: number, total: number, fecha: string): boolean {
  return i === 0 || i === total - 1 || dia(fecha) % 5 === 0;
}

/**
 * Comprobantes emitidos por día del mes (#24). Una sola serie, así que un solo color (`--chart-1`, la marca, con contraste ≥ 3:1 sobre la tarjeta en claro y en oscuro) y
 * sin leyenda: el título la nombra. SVG con `role="img"` y su descripción; cada día tiene su `title` con lo emitido y lo facturado, sobre una franja de alto completo para que
 * el puntero no tenga que acertarle a una barra baja; y la misma información como tabla, plegada. `serie` en `null`: no se pudo cargar.
 */
export function GraficoDeEmision({ serie, titulo }: { serie: PuntoDelDia[] | null; titulo: string }) {
  const maximo = serie ? Math.max(0, ...serie.map((p) => p.emitidos)) : 0;
  const monedas = serie ? monedasDeLaSerie(serie) : [];

  return (
    <section aria-labelledby="grafico-emision-titulo" data-testid="grafico-emision" data-cargado={serie !== null} className={cn(TARJETA, "grid min-w-0 gap-3 p-5")}>
      <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-border/60 pb-3">
        <h2 id="grafico-emision-titulo" className={TITULO_SECCION}>
          {titulo}
        </h2>
        {serie && maximo > 0 ? <span className="hidden font-mono text-[11px] text-muted-foreground sm:inline">Comprobantes emitidos por día</span> : null}
      </div>

      {serie === null ? (
        <p data-testid="grafico-emision-error" className="rounded-lg border border-border bg-muted/50 px-3 py-2.5 text-[13px] text-muted-foreground">
          No se pudo cargar la emisión por día. Los indicadores y los avisos de arriba siguen al día.
        </p>
      ) : maximo === 0 ? (
        <p data-testid="grafico-emision-vacio" className="rounded-lg border border-border bg-muted/50 px-3 py-2.5 text-[13px] text-muted-foreground">
          Todavía no hay comprobantes emitidos este mes.
        </p>
      ) : (
        <>
          <div className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-2">
            {/* El eje de valores: solo el máximo y el cero, en tinta apagada; la cuadrícula es una línea punteada a media altura. */}
            <div className="flex h-40 flex-col justify-between text-right font-mono text-[10px] text-muted-foreground tabular-nums" aria-hidden>
              <span data-testid="grafico-emision-maximo">{maximo}</span>
              <span>0</span>
            </div>
            <svg
              role="img"
              aria-labelledby="grafico-emision-titulo grafico-emision-desc"
              data-testid="grafico-emision-svg"
              viewBox={`0 0 ${serie.length * ANCHO_DIA} ${ALTO}`}
              preserveAspectRatio="none"
              className="h-40 w-full overflow-visible"
            >
              <desc id="grafico-emision-desc">{resumenDelGrafico(serie)}</desc>
              <line x1={0} x2={serie.length * ANCHO_DIA} y1={ALTO / 2} y2={ALTO / 2} className="stroke-border" strokeDasharray="3 3" vectorEffect="non-scaling-stroke" />
              <line x1={0} x2={serie.length * ANCHO_DIA} y1={ALTO} y2={ALTO} className="stroke-border" vectorEffect="non-scaling-stroke" />
              {serie.map((p, i) => {
                const alto = (p.emitidos / maximo) * ALTO;
                return (
                  <g key={p.fecha} data-testid="grafico-emision-dia" data-fecha={p.fecha} data-emitidos={p.emitidos} className="group">
                    <title>{descripcionDelDia(p)}</title>
                    <rect x={i * ANCHO_DIA} y={0} width={ANCHO_DIA} height={ALTO} className="fill-transparent group-hover:fill-muted/60" />
                    {p.emitidos > 0 ? (
                      <rect x={i * ANCHO_DIA + (ANCHO_DIA - ANCHO_BARRA) / 2} y={ALTO - alto} width={ANCHO_BARRA} height={alto} className="fill-chart-1 group-hover:opacity-80" />
                    ) : null}
                  </g>
                );
              })}
            </svg>
            <span aria-hidden />
            <div className="flex font-mono text-[10px] text-muted-foreground tabular-nums" aria-hidden>
              {serie.map((p, i) => (
                <span key={p.fecha} className="min-w-0 flex-1 text-center">
                  {conRotulo(i, serie.length, p.fecha) ? dia(p.fecha) : ""}
                </span>
              ))}
            </div>
          </div>

          <details className="text-[13px]">
            <summary className="cursor-pointer text-[12px] text-primary">Ver como tabla</summary>
            <div className="mt-2 overflow-x-auto rounded-lg border border-border/90">
              <Table data-testid="grafico-emision-tabla">
                <TableHeader>
                  <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
                    <TableHead className={`${CABECERA_TABLA} pl-3`}>Día</TableHead>
                    <TableHead className={`${CABECERA_TABLA} text-right`}>Emitidos</TableHead>
                    {monedas.map((m) => (
                      <TableHead key={m} className={`${CABECERA_TABLA} text-right`}>
                        Facturado {m}
                      </TableHead>
                    ))}
                  </TableRow>
                </TableHeader>
                <TableBody className="text-[13px] tabular-nums">
                  {serie.map((p) => (
                    <TableRow key={p.fecha} className="border-b border-border/60">
                      <TableCell className="py-1.5 pl-3">{formatearFecha(p.fecha)}</TableCell>
                      <TableCell className="py-1.5 text-right">{p.emitidos}</TableCell>
                      {monedas.map((m) => (
                        <TableCell key={m} className="py-1.5 text-right">
                          {formatearMonto(m, p.facturado[m] ?? 0)}
                        </TableCell>
                      ))}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </details>
        </>
      )}
    </section>
  );
}
