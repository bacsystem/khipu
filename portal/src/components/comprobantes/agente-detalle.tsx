import { useLocale, useMessages } from "next-intl";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import { formatearFecha, formatearMonto, formatearNumero } from "@/lib/formato";
import { CABECERA_TABLA, ETIQUETA_DATO } from "@/lib/estilos";
import type { ComprobanteDeAgente } from "@/lib/api/facturas";
import { RowsIcon } from "@phosphor-icons/react/dist/ssr";
import { CabeceraSeccion } from "khipu-design-system/patrones/cabecera-seccion";
import { CONTENEDOR_TABLA } from "khipu-design-system/patrones/pie-tabla";
import { Chip } from "khipu-design-system/patrones/pill-estado";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { cn } from "@/lib/utils";

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
    <section className={CONTENEDOR_TABLA} data-testid="documentos-de-agente">
      <CabeceraSeccion
        icon={RowsIcon}
        titulo={retencion ? t.documentosPagados : t.documentosCobrados}
        className="px-2"
        derecha={<Chip className="font-mono">{interpolar(t.regimen, { regimen: agente.regimen, descripcion: agente.descripcion_regimen, tasa: formatearNumero(agente.tasa, locale) })}</Chip>}
      />
      <Table className="text-control">
        <TableHeader>
          <TableRow className="hover:bg-transparent">
            <TableHead className={CABECERA_TABLA}>{c.documento}</TableHead>
            <TableHead className={CABECERA_TABLA}>{c.emision}</TableHead>
            <TableHead className={cn(CABECERA_TABLA, "text-right")}>{c.importeTotal}</TableHead>
            <TableHead className={CABECERA_TABLA}>{retencion ? c.pago : c.cobro}</TableHead>
            <TableHead className={cn(CABECERA_TABLA, "text-right")}>{retencion ? c.pagado : c.cobrado}</TableHead>
            <TableHead className={cn(CABECERA_TABLA, "text-right")}>{c.tipoCambio}</TableHead>
            <TableHead className={cn(CABECERA_TABLA, "text-right")}>{retencion ? c.retenido : c.percibido}</TableHead>
            <TableHead className={cn(CABECERA_TABLA, "text-right")}>{c.neto}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {agente.documentos.map((d) => (
            <TableRow key={`${d.numero}#${d.pago.numero}`}>
              <TableCell className="px-3 py-2 font-medium text-foreground">
                {tipoDeDocumento(d.tipo, messages)} <span className="font-mono">{d.numero}</span>
              </TableCell>
              <TableCell className="px-3 py-2 text-muted-foreground">{formatearFecha(d.fecha_emision, locale)}</TableCell>
              <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{formatearMonto(d.moneda, d.importe_total, locale)}</TableCell>
              <TableCell className="px-3 py-2 text-muted-foreground">
                {interpolar(t.pago, { numero: d.pago.numero, fecha: formatearFecha(d.pago.fecha, locale) })}
              </TableCell>
              <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{formatearMonto(d.moneda, d.pago.importe, locale)}</TableCell>
              <TableCell className="px-3 py-2 text-right font-mono tabular-nums text-muted-foreground">{d.tipo_cambio ? formatearNumero(d.tipo_cambio.factor, locale) : "—"}</TableCell>
              <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{formatearMonto("PEN", d.importe_agente, locale)}</TableCell>
              <TableCell className="px-3 py-2 text-right font-mono font-medium tabular-nums text-foreground">{formatearMonto("PEN", d.neto, locale)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </section>
  );
}

/** #40: los totales de una retención o una percepción, en soles: lo retenido o percibido, el redondeo y lo pagado o cobrado. */
export function ImportesDeAgente({ agente, retencion }: { agente: ComprobanteDeAgente; retencion: boolean }) {
  const t = useMessages().comprobantes.agente;
  const locale = useLocale();
  return (
    <div className="space-y-2 text-etiqueta" data-testid="importes-de-agente">
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
        <span className="font-mono text-titulo-pagina font-bold tracking-tight text-foreground tabular-nums">{formatearMonto("PEN", agente.importe_neto, locale)}</span>
      </div>
    </div>
  );
}
