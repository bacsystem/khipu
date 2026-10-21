import { useLocale, useMessages } from "next-intl";
import { atencionEnPalabras } from "@/components/comprobantes/resumen-de-comprobantes";
import { Metrica } from "@/components/compartidos/metrica";
import type { ResumenDeFacturas } from "@/lib/api/facturas";
import type { Locale } from "@/i18n/config";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import { formatearMonto } from "@/lib/formato";
import { facturadoEn, variacion, variacionEnPalabras } from "@/lib/inicio/panel";

/** La comparación con el mismo tramo del mes anterior, o lo que se diga cuando no hay base (`sinBase`). */
function comparacion(actual: number, anterior: number | null, sinBase: string, messages: Mensajes) {
  const v = variacion(actual, anterior);
  return v === null ? sinBase : variacionEnPalabras(v, messages);
}

/** Con una sola moneda (o ninguna: soles) se compara con el mes anterior; con varias, una sola comparación no diría de cuál. */
function ayudaDeFacturado(resumen: ResumenDeFacturas, anterior: ResumenDeFacturas | null, messages: Mensajes): string {
  const t = messages.inicio.kpis;
  if (resumen.facturado.length > 1) return t.netoPorMoneda;
  const moneda = resumen.facturado[0]?.moneda ?? "PEN";
  return comparacion(facturadoEn(resumen, moneda), anterior ? facturadoEn(anterior, moneda) : null, t.netoDeNotas, messages);
}

/**
 * Los indicadores del mes en el panel de inicio (#24), del resumen (#15): lo facturado por moneda y lo emitido, comparados con el mismo tramo del mes anterior cuando ese
 * resumen cargó; lo aceptado con su CDR, lo pendiente (en camino a SUNAT) y lo que pide atención. Sin resumen (`null`) quedan atenuados con «—» y lo dicen.
 */
export function KpisDelMes({ resumen, anterior }: { resumen: ResumenDeFacturas | null; anterior: ResumenDeFacturas | null }) {
  const messages = useMessages();
  const locale = useLocale() as Locale;
  const t = messages.inicio.kpis;
  return (
    <section
      aria-label={t.titulo}
      data-testid="kpis-del-mes"
      data-cargado={resumen !== null}
      className="grid grid-cols-2 gap-x-5 gap-y-4 rounded-xl border border-border bg-card px-5 py-3 shadow-xs sm:grid-cols-3 lg:grid-cols-5 lg:divide-x lg:divide-border lg:[&>*:not(:first-child)]:pl-5"
    >
      {resumen === null ? (
        [t.facturado, t.emitidos, t.aceptados, t.pendientes, t.atencion].map((etiqueta) => <Metrica key={etiqueta} etiqueta={etiqueta} ayuda={t.sinDatos} pendiente />)
      ) : (
        <>
          <Metrica
            etiqueta={t.facturado}
            ayuda={
              <span data-testid="kpi-facturado-variacion">{ayudaDeFacturado(resumen, anterior, messages)}</span>
            }
          >
            {resumen.facturado.length === 0 ? (
              <span data-testid="kpi-facturado">{formatearMonto("PEN", 0, locale)}</span>
            ) : (
              resumen.facturado.map((f) => (
                <span key={f.moneda} data-testid="kpi-facturado" data-moneda={f.moneda}>
                  {formatearMonto(f.moneda, f.total, locale)}
                </span>
              ))
            )}
          </Metrica>
          <Metrica etiqueta={t.emitidos} ayuda={<span data-testid="kpi-emitidos-variacion">{comparacion(resumen.emitidos, anterior?.emitidos ?? null, t.esteMes, messages)}</span>}>
            <span data-testid="kpi-emitidos">{resumen.emitidos}</span>
          </Metrica>
          <Metrica
            etiqueta={t.aceptados}
            ayuda={resumen.emitidos > 0 ? interpolar(t.porcentajeEmitido, { n: Math.floor((resumen.aceptados_con_cdr * 100) / resumen.emitidos) }) : t.sinComprobantes}
          >
            <span data-testid="kpi-aceptados">{resumen.aceptados_con_cdr}</span>
          </Metrica>
          <Metrica etiqueta={t.pendientes} ayuda={t.pendientesAyuda}>
            <span data-testid="kpi-pendientes">{resumen.en_camino}</span>
          </Metrica>
          <Metrica etiqueta={t.atencion} ayuda={<span data-testid="kpi-atencion-detalle">{atencionEnPalabras(resumen.atencion_requerida, messages)}</span>}>
            <span data-testid="kpi-atencion" className={resumen.atencion_requerida.total > 0 ? "text-destructive" : undefined}>
              {resumen.atencion_requerida.total}
            </span>
          </Metrica>
        </>
      )}
    </section>
  );
}
