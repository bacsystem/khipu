import { formatearFecha, formatearMonto, formatearNumero } from "@/lib/formato";
import { ETIQUETA_DATO, TITULO_SECCION } from "@/lib/estilos";
import type { ComprobanteDeAgente } from "@/lib/api/facturas";
import { Rows3Icon } from "lucide-react";

const TIPOS_DOCUMENTO: Record<string, string> = { "01": "Factura", "03": "Boleta", "08": "Nota de débito", "12": "Ticket" };

/**
 * #40: los documentos que una retención paga o una percepción cobra, en lugar de las líneas de venta que estos comprobantes no tienen: cada uno con su pago (en su moneda) y lo
 * retenido o percibido y el neto (en soles).
 */
export function DocumentosDeAgente({ agente, retencion }: { agente: ComprobanteDeAgente; retencion: boolean }) {
  return (
    <section className="overflow-hidden rounded-xl border border-border bg-card shadow-2xs" data-testid="documentos-de-agente">
      <div className="flex items-center justify-between border-b border-border/60 px-5 py-3">
        <div className={TITULO_SECCION}>
          <Rows3Icon className="size-4" />
          {retencion ? "Documentos pagados al proveedor" : "Documentos cobrados al cliente"}
        </div>
        <span className="font-mono text-xs text-muted-foreground">
          Régimen {agente.regimen} · {agente.descripcion_regimen} · {formatearNumero(agente.tasa)} %
        </span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-left text-xs">
          <thead>
            <tr className="border-b border-border/70 bg-muted font-mono text-[11px] font-medium tracking-wider text-muted-foreground uppercase">
              <th className="px-4 py-2.5 font-medium">Documento</th>
              <th className="px-3 py-2.5 font-medium">Emisión</th>
              <th className="px-3 py-2.5 text-right font-medium">Importe total</th>
              <th className="px-3 py-2.5 font-medium">{retencion ? "Pago" : "Cobro"}</th>
              <th className="px-3 py-2.5 text-right font-medium">{retencion ? "Pagado" : "Cobrado"}</th>
              <th className="px-3 py-2.5 text-right font-medium">T. cambio</th>
              <th className="px-3 py-2.5 text-right font-medium">{retencion ? "Retenido" : "Percibido"}</th>
              <th className="px-4 py-2.5 text-right font-medium">Neto</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border/60">
            {agente.documentos.map((d) => (
              <tr key={`${d.numero}#${d.pago.numero}`}>
                <td className="px-4 py-3 font-medium text-foreground">
                  {TIPOS_DOCUMENTO[d.tipo] ?? d.tipo} <span className="font-mono">{d.numero}</span>
                </td>
                <td className="px-3 py-3 text-muted-foreground">{formatearFecha(d.fecha_emision)}</td>
                <td className="px-3 py-3 text-right font-mono tabular-nums">{formatearMonto(d.moneda, d.importe_total)}</td>
                <td className="px-3 py-3 text-muted-foreground">
                  N.º {d.pago.numero} · {formatearFecha(d.pago.fecha)}
                </td>
                <td className="px-3 py-3 text-right font-mono tabular-nums">{formatearMonto(d.moneda, d.pago.importe)}</td>
                <td className="px-3 py-3 text-right font-mono tabular-nums text-muted-foreground">{d.tipo_cambio ? formatearNumero(d.tipo_cambio.factor) : "—"}</td>
                <td className="px-3 py-3 text-right font-mono tabular-nums">{formatearMonto("PEN", d.importe_agente)}</td>
                <td className="px-4 py-3 text-right font-mono font-medium tabular-nums text-foreground">{formatearMonto("PEN", d.neto)}</td>
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
  return (
    <div className="space-y-2 text-xs" data-testid="importes-de-agente">
      <div className="flex justify-between py-1 text-muted-foreground">
        <span>{retencion ? "Importe total retenido" : "Importe total percibido"}</span>
        <span className="font-mono font-medium text-foreground tabular-nums">{formatearMonto("PEN", agente.importe_total)}</span>
      </div>
      {agente.redondeo ? (
        <div className="flex justify-between py-1 text-muted-foreground">
          <span>Redondeo</span>
          <span className="font-mono tabular-nums">{formatearMonto("PEN", agente.redondeo)}</span>
        </div>
      ) : null}
      <div className="my-2 h-px bg-border" />
      <div className="flex items-baseline justify-between pt-1">
        <span className={ETIQUETA_DATO}>{retencion ? "Importe total pagado al proveedor" : "Importe total cobrado al cliente"}</span>
        <span className="font-mono text-xl font-bold tracking-tight text-foreground tabular-nums">{formatearMonto("PEN", agente.importe_neto)}</span>
      </div>
    </div>
  );
}
