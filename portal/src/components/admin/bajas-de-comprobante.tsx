import type { BajaAdmin } from "@/lib/api/admin-comprobante";
import { AVISO_ALTA_FUERA_DE_PLAZO, bajaVencida, etiquetaDeBaja } from "@/lib/baja-etiqueta";
import { ETIQUETA_DATO, TARJETA, TITULO_SECCION } from "@/lib/estilos";
import { formatearFecha } from "@/lib/formato";
import { messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

function Dato({ etiqueta, children, testId }: { etiqueta: string; children: React.ReactNode; testId: string }) {
  return (
    <div className="min-w-0">
      <dt className={cn(ETIQUETA_DATO, "mb-1")}>{etiqueta}</dt>
      <dd className="text-[13px] break-words text-foreground" data-testid={testId}>
        {children}
      </dd>
    </div>
  );
}

/**
 * #362 H9: la baja (RA) o el resumen diario (RC) de un comprobante en la ficha del backoffice, con el mismo criterio que la ficha del cliente (`BajaEstado`): el
 * estado con las mismas palabras, el aviso de informada fuera de plazo y la baja vencida. Solo lectura: acá no se reconsulta el ticket ni se reintenta nada.
 * `tipo` es el del comprobante: dice si un alta informa una boleta o una nota. Sin bajas no se pinta nada.
 */
export function BajasDeComprobante({ bajas, tipo }: { bajas: BajaAdmin[]; tipo: string }) {
  if (bajas.length === 0) return null;
  const t = messages.admin.comprobanteDetalle.bajas;
  const que = tipo === "03" ? "boleta" : "nota";
  return (
    <section className={cn(TARJETA, "p-5 lg:col-span-2")} aria-labelledby="titulo-bajas" data-testid="comprobante-bajas">
      <h2 id="titulo-bajas" className={cn(TITULO_SECCION, "border-b border-border/60 pb-3")}>
        {t.titulo}
      </h2>
      <p className="pt-3 text-[12px] text-muted-foreground">{t.ayuda}</p>
      <div className="grid gap-4 pt-4">
        {bajas.map((b) => {
          const resumenDiario = b.identificador.startsWith("RC-");
          const alta = b.condicion === "ALTA";
          const vencida = bajaVencida(b);
          return (
            <article key={b.id} className="rounded-lg border border-border/60 p-4" data-testid={`comprobante-baja-${b.identificador}`}>
              <h3 className="mb-3 text-[13px] font-semibold text-foreground">
                {resumenDiario ? t.resumenDiario : t.comunicacionDeBaja} <span className="font-mono">{b.identificador}</span>
                {alta ? ` ${t.alta}` : null}
              </h3>
              <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                <Dato etiqueta={t.estado} testId="baja-estado">
                  {etiquetaDeBaja(b, que)}
                </Dato>
                <Dato etiqueta={t.ticket} testId="baja-ticket">
                  <span className="font-mono">{b.ticket ?? t.sinDato}</span>
                </Dato>
                <Dato etiqueta={t.motivo} testId="baja-motivo">
                  {b.motivo}
                  {/* El RC no lleva el motivo de una anulación: queda en khipu. El de un alta lo escribe khipu y tampoco viaja, pero no es de nadie que lo pidiera. */}
                  {resumenDiario && !alta ? <span className="mt-1 block text-[12px] text-muted-foreground">{t.motivoSoloKhipu}</span> : null}
                </Dato>
                <Dato etiqueta={t.generada} testId="baja-generada">
                  {formatearFecha(b.fecha_generacion)}
                </Dato>
                <Dato etiqueta={t.emision} testId="baja-emision">
                  {formatearFecha(b.fecha_referencia)}
                </Dato>
                <Dato etiqueta={vencida ? t.detalle : b.cdr ? t.respuesta : t.ultimoIntento} testId="baja-respuesta">
                  {b.cdr ? (
                    <>
                      <span className="font-mono">{b.cdr.codigo}</span>
                      {b.cdr.descripcion ? ` — ${b.cdr.descripcion}` : null}
                    </>
                  ) : (
                    <span className="font-mono text-[12px]">
                      {b.ultimo_error ?? t.sinDato}
                      {b.intentos > 1 ? ` (${b.intentos} intentos)` : ""}
                    </span>
                  )}
                </Dato>
              </dl>
              {alta && b.fuera_de_plazo ? (
                <p className="mt-3 text-[12px] text-warning-foreground" data-testid="baja-fuera-de-plazo">
                  {AVISO_ALTA_FUERA_DE_PLAZO}
                </p>
              ) : null}
            </article>
          );
        })}
      </div>
    </section>
  );
}
