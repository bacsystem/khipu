import { atencionEnPalabras } from "@/components/comprobantes/resumen-de-comprobantes";
import { Metrica } from "@/components/ui/metrica";
import type { ResumenDeFacturas } from "@/lib/api/facturas";
import { formatearMonto } from "@/lib/formato";
import { facturadoEn, variacion, variacionEnPalabras } from "@/lib/inicio/panel";

const SIN_DATOS = "No se pudo cargar el resumen del mes";

/** La comparación con el mismo tramo del mes anterior, o lo que se diga cuando no hay base (`sinBase`). */
function comparacion(actual: number, anterior: number | null, sinBase: string) {
  const v = variacion(actual, anterior);
  return v === null ? sinBase : variacionEnPalabras(v);
}

/** Con una sola moneda (o ninguna: soles) se compara con el mes anterior; con varias, una sola comparación no diría de cuál. */
function ayudaDeFacturado(resumen: ResumenDeFacturas, anterior: ResumenDeFacturas | null): string {
  if (resumen.facturado.length > 1) return "Neto, por moneda";
  const moneda = resumen.facturado[0]?.moneda ?? "PEN";
  return comparacion(facturadoEn(resumen, moneda), anterior ? facturadoEn(anterior, moneda) : null, "Neto de notas de crédito");
}

/**
 * Los indicadores del mes en el panel de inicio (#24), del resumen (#15): lo facturado por moneda y lo emitido, comparados con el mismo tramo del mes anterior cuando ese
 * resumen cargó; lo aceptado con su CDR, lo pendiente (en camino a SUNAT) y lo que pide atención. Sin resumen (`null`) quedan atenuados con «—» y lo dicen.
 */
export function KpisDelMes({ resumen, anterior }: { resumen: ResumenDeFacturas | null; anterior: ResumenDeFacturas | null }) {
  return (
    <section
      aria-label="Indicadores del mes"
      data-testid="kpis-del-mes"
      data-cargado={resumen !== null}
      className="grid grid-cols-2 gap-x-5 gap-y-4 rounded-xl border border-border bg-card px-5 py-3 shadow-xs sm:grid-cols-3 lg:grid-cols-5 lg:divide-x lg:divide-border lg:[&>*:not(:first-child)]:pl-5"
    >
      {resumen === null ? (
        ["Facturado", "Emitidos", "Aceptados con CDR", "Pendientes", "Atención requerida"].map((etiqueta) => <Metrica key={etiqueta} etiqueta={etiqueta} ayuda={SIN_DATOS} pendiente />)
      ) : (
        <>
          <Metrica
            etiqueta="Facturado"
            ayuda={
              <span data-testid="kpi-facturado-variacion">{ayudaDeFacturado(resumen, anterior)}</span>
            }
          >
            {resumen.facturado.length === 0 ? (
              <span data-testid="kpi-facturado">{formatearMonto("PEN", 0)}</span>
            ) : (
              resumen.facturado.map((f) => (
                <span key={f.moneda} data-testid="kpi-facturado" data-moneda={f.moneda}>
                  {formatearMonto(f.moneda, f.total)}
                </span>
              ))
            )}
          </Metrica>
          <Metrica etiqueta="Emitidos" ayuda={<span data-testid="kpi-emitidos-variacion">{comparacion(resumen.emitidos, anterior?.emitidos ?? null, "Este mes")}</span>}>
            <span data-testid="kpi-emitidos">{resumen.emitidos}</span>
          </Metrica>
          <Metrica etiqueta="Aceptados con CDR" ayuda={resumen.emitidos > 0 ? `${Math.floor((resumen.aceptados_con_cdr * 100) / resumen.emitidos)} % de lo emitido` : "Sin comprobantes"}>
            <span data-testid="kpi-aceptados">{resumen.aceptados_con_cdr}</span>
          </Metrica>
          <Metrica etiqueta="Pendientes" ayuda="En camino a SUNAT (incluye boletas sin enviar)">
            <span data-testid="kpi-pendientes">{resumen.en_camino}</span>
          </Metrica>
          <Metrica etiqueta="Atención requerida" ayuda={<span data-testid="kpi-atencion-detalle">{atencionEnPalabras(resumen.atencion_requerida)}</span>}>
            <span data-testid="kpi-atencion" className={resumen.atencion_requerida.total > 0 ? "text-destructive" : undefined}>
              {resumen.atencion_requerida.total}
            </span>
          </Metrica>
        </>
      )}
    </section>
  );
}
