import { RegistrarPago } from "@/components/admin/registrar-pago";
import { Seccion, CABECERA_FILA, Vacio } from "@/components/admin/seccion";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { PaginaPagos } from "@/lib/api/admin-pagos";
import type { PlanDeCuentaAdmin } from "@/lib/api/admin-plan-de-cuenta";
import { CABECERA_TABLA } from "@/lib/estilos";
import { formatearFecha, formatearMonto, ultimoDiaCubierto } from "@/lib/formato";
import { messages } from "@/lib/messages";

const t = messages.admin.pagos;
const columnas = t.columnas;

/**
 * Los pagos de una cuenta en su ficha del backoffice (#194): los más recientes primero, con el periodo que cubre cada uno, el monto, el medio, la referencia y, si lo
 * movió, el vencimiento al que dejó la suscripción (el último día cubierto: el vencimiento es exclusivo). Arriba, el botón para registrar uno nuevo. Sin pasarela de
 * pago: es el apunte manual que reemplaza a la hoja de cálculo.
 */
export function PagosDeCuenta({ cuentaId, cuentaNombre, pagos, plan, hoy }: { cuentaId: string; cuentaNombre: string; pagos: PaginaPagos; plan: PlanDeCuentaAdmin | null; hoy: string }) {
  return (
    <Seccion titulo={t.titulo} id="detalle-pagos">
      <div data-testid="pagos-de-cuenta" className="grid gap-3 p-4 pb-0">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <p className="max-w-2xl text-[13px] text-muted-foreground">{t.descripcion}</p>
          <RegistrarPago cuentaId={cuentaId} cuentaNombre={cuentaNombre} plan={plan} hoy={hoy} />
        </div>
      </div>
      <Table>
        <TableHeader>
          <TableRow className={CABECERA_FILA}>
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{columnas.fecha}</TableHead>
            <TableHead className={CABECERA_TABLA}>{columnas.periodo}</TableHead>
            <TableHead className={`${CABECERA_TABLA} text-right`}>{columnas.monto}</TableHead>
            <TableHead className={CABECERA_TABLA}>{columnas.medio}</TableHead>
            <TableHead className={CABECERA_TABLA}>{columnas.referencia}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4`}>{columnas.vencimiento}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-[13px]">
          {pagos.datos.map((p) => (
            <TableRow key={p.id} data-testid="pago-fila" data-extendio={p.extendio_hasta ? "true" : "false"} className="border-b border-border/60">
              <TableCell className="py-2 pr-3 pl-4 whitespace-nowrap">{formatearFecha(p.fecha_de_pago)}</TableCell>
              <TableCell className="px-3 py-2 whitespace-nowrap">
                {t.periodo.replace("{desde}", formatearFecha(p.periodo_desde)).replace("{hasta}", formatearFecha(p.periodo_hasta))}
              </TableCell>
              <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{formatearMonto("PEN", p.monto)}</TableCell>
              <TableCell className="px-3 py-2">{t.medios[p.medio]}</TableCell>
              <TableCell className="px-3 py-2 font-mono text-[12px]">
                {p.referencia ?? <span className="text-muted-foreground">{t.sinReferencia}</span>}
                {p.nota ? <span className="block font-sans text-[11px] text-muted-foreground">{p.nota}</span> : null}
              </TableCell>
              <TableCell className="py-2 pr-4 pl-3 text-[12px]">
                {p.extendio_hasta ? t.extendio.replace("{fecha}", formatearFecha(ultimoDiaCubierto(p.extendio_hasta))) : <span className="text-muted-foreground">{t.noExtendio}</span>}
              </TableCell>
            </TableRow>
          ))}
          {pagos.datos.length === 0 ? <Vacio columnas={6} texto={t.vacio} /> : null}
        </TableBody>
      </Table>
      {pagos.total > pagos.datos.length ? (
        <p data-testid="pagos-recortados" className="border-t border-border/60 px-4 py-2 text-[12px] text-muted-foreground">
          {t.muestra.replace("{n}", String(pagos.datos.length)).replace("{total}", String(pagos.total))}
        </p>
      ) : null}
    </Seccion>
  );
}
