import { interpolar, type Mensajes } from "@/i18n/mensajes";
import { Metrica } from "@/components/compartidos/metrica";
import type { PeriodoDelResumen, ResumenDeFacturas } from "@/lib/api/facturas";
import { formatearMonto } from "@/lib/formato";
import es from "@/messages/es.json";

const MESES = ["Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Set", "Oct", "Nov", "Dic"];

/** `2026-10-04` como «4 Oct»; sin año, que es lo que cabe en la franja. */
function diaYMes(iso: string): string {
  const [, m, d] = iso.split("-").map(Number);
  return `${d} ${MESES[m - 1]}`;
}

/** Cómo se dice el período en la franja: «Este mes», «1 Oct – 31 Oct», «Desde el 1 Oct», «Hasta el 31 Oct» o «Todo el historial». */
export function periodoEnPalabras(p: PeriodoDelResumen): string {
  if (p.esElMesEnCurso) return "Este mes";
  if (p.desde && p.hasta) return p.desde === p.hasta ? diaYMes(p.desde) : `${diaYMes(p.desde)} – ${diaYMes(p.hasta)}`;
  if (p.desde) return `Desde el ${diaYMes(p.desde)}`;
  if (p.hasta) return `Hasta el ${diaYMes(p.hasta)}`;
  return "Todo el historial";
}

/** Lo que pide atención, con las clases que haya: «3 rechazados · 2 con error de envío · 1 fuera de plazo · 4 boletas sin enviar»; sin nada, «Todo en orden». */
export function atencionEnPalabras(a: ResumenDeFacturas["atencion_requerida"], messages: Mensajes = es): string {
  const t = messages.atencion;
  const partes = [
    a.rechazados > 0 ? interpolar(a.rechazados === 1 ? t.rechazadoUno : t.rechazadoVarios, { n: a.rechazados }) : null,
    a.errores_de_envio > 0 ? interpolar(t.errorDeEnvio, { n: a.errores_de_envio }) : null,
    a.fuera_de_plazo > 0 ? interpolar(t.fueraDePlazo, { n: a.fuera_de_plazo }) : null,
    a.boletas_sin_enviar > 0 ? interpolar(a.boletas_sin_enviar === 1 ? t.boletaUno : t.boletaVarios, { n: a.boletas_sin_enviar }) : null,
  ].filter((p): p is string => p !== null);
  return partes.length > 0 ? partes.join(" · ") : t.todoEnOrden;
}

/**
 * La franja de métricas de la página de comprobantes (#15): lo facturado por moneda (neto de notas de crédito), lo emitido en el período, lo aceptado con su CDR y lo que
 * pide atención. Si el resumen no se pudo cargar (`null`) las cuatro quedan atenuadas con «—» y lo dicen: la lista de abajo sigue funcionando.
 */
export function ResumenDeComprobantes({ resumen, periodo }: { resumen: ResumenDeFacturas | null; periodo: PeriodoDelResumen }) {
  const cuando = periodoEnPalabras(periodo);
  const sinResumen = "No se pudo cargar el resumen";
  return (
    <section
      aria-label="Resumen del período"
      data-testid="resumen-de-comprobantes"
      data-cargado={resumen !== null}
      className="grid grid-cols-2 gap-x-5 gap-y-4 rounded-xl border border-border bg-card px-5 py-3 shadow-xs lg:grid-cols-4 lg:divide-x lg:divide-border lg:[&>*:not(:first-child)]:pl-5"
    >
      {resumen === null ? (
        <>
          <Metrica etiqueta="Total facturado" ayuda={sinResumen} pendiente />
          <Metrica etiqueta="Emitidos en el período" ayuda={sinResumen} pendiente />
          <Metrica etiqueta="Aceptados con CDR" ayuda={sinResumen} pendiente />
          <Metrica etiqueta="Atención requerida" ayuda={sinResumen} pendiente />
        </>
      ) : (
        <>
          <Metrica etiqueta="Total facturado" ayuda={`Neto de notas de crédito · ${cuando}`}>
            {resumen.facturado.length === 0 ? (
              <span data-testid="metrica-facturado">{formatearMonto("PEN", 0)}</span>
            ) : (
              resumen.facturado.map((f) => (
                <span key={f.moneda} data-testid="metrica-facturado" data-moneda={f.moneda}>
                  {formatearMonto(f.moneda, f.total)}
                </span>
              ))
            )}
          </Metrica>
          <Metrica etiqueta="Emitidos en el período" ayuda={cuando}>
            <span data-testid="metrica-emitidos">{resumen.emitidos}</span>
          </Metrica>
          <Metrica etiqueta="Aceptados con CDR" ayuda={resumen.emitidos > 0 ? `${Math.floor((resumen.aceptados_con_cdr * 100) / resumen.emitidos)} % de lo emitido` : "Sin comprobantes"}>
            <span data-testid="metrica-aceptados">{resumen.aceptados_con_cdr}</span>
          </Metrica>
          <Metrica etiqueta="Atención requerida" ayuda={<span data-testid="metrica-atencion-detalle">{atencionEnPalabras(resumen.atencion_requerida)}</span>}>
            <span data-testid="metrica-atencion" className={resumen.atencion_requerida.total > 0 ? "text-destructive" : undefined}>
              {resumen.atencion_requerida.total}
            </span>
          </Metrica>
        </>
      )}
    </section>
  );
}
