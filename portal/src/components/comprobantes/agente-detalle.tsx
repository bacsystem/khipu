import { useLocale, useMessages } from "next-intl";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import { formatearFecha, formatearMonto, formatearNumero } from "@/lib/formato";
import { ETIQUETA_DATO, TITULO_SECCION } from "@/lib/estilos";
import type { ComprobanteDeAgente } from "@/lib/api/facturas";
import { RowsIcon } from "@phosphor-icons/react/dist/ssr";
import { PESO_ICONO } from "khipu-design-system/lib/iconos";

/** Los documentos que paga una retención o cobra una percepción: factura, boleta, nota de débito o ticket; otro código se muestra tal cual. */
function tipoDeDocumento(tipo: string, messages: Mensajes): string {
  if (tipo === "12") return messages.comprobantes.agente.ticket;
  return tipo === "01" || tipo === "03" || tipo === "08" ? messages.documentos.tipos[tipo] : tipo;
}

/**
 * #40: los documentos que una retención paga o una percepción cobra, en lugar de las líneas de venta que estos comprobantes no tienen: cada uno con su pago (en su moneda) y lo
 * retenido o percibido y el neto (en soles).
 */
export function DocumentosDeAgente({ agente, retencion }: { agente: ComprobanteDeAgente; retencion: boolean }) {
  const messages = useMessages();
  const locale = useLocale();
  const t = messages.comprobantes.agente;
  const c = t.columnas;
  return (
    <section className="overflow-hidden rounded-xl border border-border bg-card shadow-2xs" data-testid="documentos-de-agente">
      <div className="flex items-center justify-between border-b border-border/60 px-5 py-3">
        <div className={TITULO_SECCION}>
          <RowsIcon weight={PESO_ICONO} className="size-4" />
          {retencion ? t.documentosPagados : t.documentosCobrados}
        </div>
        <span className="font-mono text-xs text-muted-foreground">
          {interpolar(t.regimen, { regimen: agente.regimen, descripcion: agente.descripcion_regimen, tasa: formatearNumero(agente.tasa, locale) })}
        </span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-left text-xs">
          <thead>
            <tr className="border-b border-border/70 bg-muted font-mono text-[11px] font-medium tracking-wider text-muted-foreground uppercase">
              <th className="px-4 py-2.5 font-medium">{c.documento}</th>
              <th className="px-3 py-2.5 font-medium">{c.emision}</th>
              <th className="px-3 py-2.5 text-right font-medium">{c.importeTotal}</th>
              <th className="px-3 py-2.5 font-medium">{retencion ? c.pago : c.cobro}</th>
              <th className="px-3 py-2.5 text-right font-medium">{retencion ? c.pagado : c.cobrado}</th>
              <th className="px-3 py-2.5 text-right font-medium">{c.tipoCambio}</th>
              <th className="px-3 py-2.5 text-right font-medium">{retencion ? c.retenido : c.percibido}</th>
              <th className="px-4 py-2.5 text-right font-medium">{c.neto}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border/60">
            {agente.documentos.map((d) => (
              <tr key={`${d.numero}#${d.pago.numero}`}>
                <td className="px-4 py-3 font-medium text-foreground">
                  {tipoDeDocumento(d.tipo, messages)} <span className="font-mono">{d.numero}</span>
                </td>
                <td className="px-3 py-3 text-muted-foreground">{formatearFecha(d.fecha_emision, locale)}</td>
                <td className="px-3 py-3 text-right font-mono tabular-nums">{formatearMonto(d.moneda, d.importe_total, locale)}</td>
                <td className="px-3 py-3 text-muted-foreground">
                  {interpolar(t.pago, { numero: d.pago.numero, fecha: formatearFecha(d.pago.fecha, locale) })}
                </td>
                <td className="px-3 py-3 text-right font-mono tabular-nums">{formatearMonto(d.moneda, d.pago.importe, locale)}</td>
                <td className="px-3 py-3 text-right font-mono tabular-nums text-muted-foreground">{d.tipo_cambio ? formatearNumero(d.tipo_cambio.factor, locale) : "—"}</td>
                <td className="px-3 py-3 text-right font-mono tabular-nums">{formatearMonto("PEN", d.importe_agente, locale)}</td>
                <td className="px-4 py-3 text-right font-mono font-medium tabular-nums text-foreground">{formatearMonto("PEN", d.neto, locale)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

/** #40: los totales de una retención o una percepción, en soles: lo retenido o percibido, el redondeo y lo pagado o cobrado. */
export function ImportesDeAgente({ agente, retencion }: { agente: ComprobanteDeAgente; retencion: boolean }) {
  const t = useMessages().comprobantes.agente;
  const locale = useLocale();
  return (
    <div className="space-y-2 text-xs" data-testid="importes-de-agente">
      <div className="flex justify-between py-1 text-muted-foreground">
        <span>{retencion ? t.totalRetenido : t.totalPercibido}</span>
        <span className="font-mono font-medium text-foreground tabular-nums">{formatearMonto("PEN", agente.importe_total, locale)}</span>
      </div>
      {agente.redondeo ? (
        <div className="flex justify-between py-1 text-muted-foreground">
          <span>{t.redondeo}</span>
          <span className="font-mono tabular-nums">{formatearMonto("PEN", agente.redondeo, locale)}</span>
        </div>
      ) : null}
      <div className="my-2 h-px bg-border" />
      <div className="flex items-baseline justify-between pt-1">
        <span className={ETIQUETA_DATO}>{retencion ? t.totalPagado : t.totalCobrado}</span>
        <span className="font-mono text-xl font-bold tracking-tight text-foreground tabular-nums">{formatearMonto("PEN", agente.importe_neto, locale)}</span>
      </div>
    </div>
  );
}
