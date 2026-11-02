import { ProhibitIcon } from "@phosphor-icons/react/dist/ssr";
import { ListaDatos } from "khipu-design-system/datos/lista-datos";
import { CabeceraSeccion } from "khipu-design-system/patrones/cabecera-seccion";
import { useLocale, useMessages } from "next-intl";
import { interpolar } from "@/i18n/mensajes";
import type { BajaAdmin } from "@/lib/api/admin-comprobante";
import { avisoAltaFueraDePlazo, bajaVencida, etiquetaDeBaja } from "@/lib/baja-etiqueta";
import { TARJETA } from "@/lib/estilos";
import { formatearFecha } from "@/lib/formato";
import { cn } from "@/lib/utils";

/**
 * #362 H9: la baja (RA) o el resumen diario (RC) de un comprobante en la ficha del backoffice, con el mismo criterio que la ficha del cliente (`BajaEstado`): el
 * estado con las mismas palabras, el aviso de informada fuera de plazo y la baja vencida. Solo lectura: acá no se reconsulta el ticket ni se reintenta nada.
 * `tipo` es el del comprobante: dice si un alta informa una boleta o una nota. Sin bajas no se pinta nada.
 */
export function BajasDeComprobante({ bajas, tipo }: { bajas: BajaAdmin[]; tipo: string }) {
  const messages = useMessages();
  const locale = useLocale();
  if (bajas.length === 0) return null;
  const t = messages.admin.comprobanteDetalle.bajas;
  const que = tipo === "03" ? "boleta" : "nota";
  return (
    <section className={cn(TARJETA, "lg:col-span-2")} aria-labelledby="titulo-bajas" data-testid="comprobante-bajas">
      <CabeceraSeccion icon={ProhibitIcon} titulo={<span id="titulo-bajas">{t.titulo}</span>} />
      <p className="px-4 text-control text-muted-foreground">{t.ayuda}</p>
      <div className="grid gap-3 px-4 pt-3 pb-4">
        {bajas.map((b) => {
          const resumenDiario = b.identificador.startsWith("RC-");
          const alta = b.condicion === "ALTA";
          const vencida = bajaVencida(b);
          return (
            <article key={b.id} className="rounded-lg bg-secondary/60 p-4" data-testid={`comprobante-baja-${b.identificador}`}>
              <h3 className="mb-3 text-cuerpo font-semibold text-foreground">
                {resumenDiario ? t.resumenDiario : t.comunicacionDeBaja} <span className="font-mono">{b.identificador}</span>
                {alta ? ` ${t.alta}` : null}
              </h3>
              <ListaDatos
                columnas={2}
                datos={[
                  { etiqueta: t.estado, valor: <span data-testid="baja-estado">{etiquetaDeBaja(b, que, messages)}</span> },
                  {
                    etiqueta: t.ticket,
                    valor: (
                      <span data-testid="baja-ticket" className="font-mono">
                        {b.ticket ?? t.sinDato}
                      </span>
                    ),
                  },
                  {
                    etiqueta: t.motivo,
                    valor: (
                      <span data-testid="baja-motivo" className="break-words">
                        {b.motivo}
                        {/* El RC no lleva el motivo de una anulación: queda en khipu. El de un alta lo escribe khipu y tampoco viaja, pero no es de nadie que lo pidiera. */}
                        {resumenDiario && !alta ? <span className="mt-1 block text-control text-muted-foreground">{t.motivoSoloKhipu}</span> : null}
                      </span>
                    ),
                  },
                  { etiqueta: t.generada, valor: <span data-testid="baja-generada">{formatearFecha(b.fecha_generacion, locale)}</span> },
                  { etiqueta: t.emision, valor: <span data-testid="baja-emision">{formatearFecha(b.fecha_referencia, locale)}</span> },
                  {
                    etiqueta: vencida ? t.detalle : b.cdr ? t.respuesta : t.ultimoIntento,
                    valor: (
                      <span data-testid="baja-respuesta" className="break-words">
                        {b.cdr ? (
                          <>
                            <span className="font-mono">{b.cdr.codigo}</span>
                            {b.cdr.descripcion ? ` — ${b.cdr.descripcion}` : null}
                          </>
                        ) : (
                          <span className="font-mono text-control">
                            {b.ultimo_error ?? t.sinDato}
                            {b.intentos > 1 ? interpolar(t.intentos, { n: b.intentos }) : ""}
                          </span>
                        )}
                      </span>
                    ),
                  },
                ]}
              />
              {alta && b.fuera_de_plazo ? (
                <p className="mt-3 text-control text-warning-foreground" data-testid="baja-fuera-de-plazo">
                  {avisoAltaFueraDePlazo(messages)}
                </p>
              ) : null}
            </article>
          );
        })}
      </div>
    </section>
  );
}
