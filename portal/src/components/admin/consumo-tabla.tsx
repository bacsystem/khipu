"use client";

import { InboxIcon } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Etiqueta, type Tono } from "@/components/admin/etiquetas";
import { PieTabla } from "@/components/ui/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { hrefDetalleCuenta } from "@/lib/api/admin-cuenta-detalle";
import {
  FILTROS_DE_CONSUMO,
  hrefConsumo,
  mesValido,
  ORDENES_DE_CONSUMO,
  type ConsumoDeCuentas,
  type CuentaConsumo,
  type EstadoDelPlan,
  type OrdenDeConsumo,
  type ParamsConsumo,
} from "@/lib/api/admin-consumo";
import { CABECERA_TABLA, CAMPO_FILTRO, SEGMENTADO, SEGMENTO } from "@/lib/estilos";
import { formatearFecha, ultimoDiaCubierto } from "@/lib/formato";
import { messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

const t = messages.admin.consumo;
const columnas = t.columnas;

const TONO_DEL_PLAN: Record<EstadoDelPlan, Tono> = { VIGENTE: "ok", EN_GRACIA: "aviso", VENCIDA: "error" };

/** «9,000»: el mismo separador de miles que el resto del portal, y sin decimales (son documentos, no soles). */
const enteroEnPalabras = (n: number) => n.toLocaleString("en-US");

/** El porcentaje llegó al 100 %: la cuenta ya usó todo su tope (o se pasó). */
const alTope = (c: CuentaConsumo) => c.porcentaje !== undefined && c.porcentaje >= 100;

/** La barra de uso: llena hasta el porcentaje, sin pasarse del cien; el color dice si está tranquila, cerca del límite o ya en él. */
function BarraDeUso({ cuenta }: { cuenta: CuentaConsumo }) {
  const pct = Math.min(cuenta.porcentaje ?? 0, 100);
  return (
    <div role="progressbar" aria-label={columnas.uso} aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct} className="h-1.5 w-24 overflow-hidden rounded-full bg-secondary">
      <div style={{ width: `${pct}%` }} className={cn("h-full rounded-full", alTope(cuenta) ? "bg-destructive" : cuenta.en_alerta ? "bg-warning-foreground" : "bg-primary")} />
    </div>
  );
}

/**
 * Consumo de todas las cuentas contra el tope de su plan de hoy (#193), paginado y filtrado en el servidor: cambiar de vista, de mes, de orden o de página solo
 * cambia la URL y el Server Component de la página vuelve a renderizar. La alerta la decide el backend (`en_alerta`); aquí no se vuelve a calcular con otro umbral.
 * «Exportar CSV» no está acá: es la acción principal de la página y va en la cabecera del backoffice, que lo arma con el mismo mes, filtro y orden de la URL.
 */
export function ConsumoTabla({ datos, total, params }: { datos: ConsumoDeCuentas; total: number; params: ParamsConsumo }) {
  const router = useRouter();

  const ultimaPagina = Math.max(1, Math.ceil(total / params.porPagina));
  const primero = total === 0 ? 0 : (params.pagina - 1) * params.porPagina + 1;
  const ultimo = (params.pagina - 1) * params.porPagina + datos.cuentas.length;

  const cambiar = (cambios: Partial<ParamsConsumo>) => router.push(hrefConsumo({ ...params, ...cambios, pagina: 1 }));

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      <div className="flex flex-wrap items-end gap-3">
        <nav aria-label={t.vistas} className={SEGMENTADO}>
          {FILTROS_DE_CONSUMO.map((f) => (
            <Link
              key={f}
              href={hrefConsumo({ ...params, filtro: f, pagina: 1 })}
              aria-current={params.filtro === f ? "page" : undefined}
              className={SEGMENTO}
            >
              {t.filtros[f]}
            </Link>
          ))}
        </nav>

        <div className="grid gap-1">
          <label htmlFor="consumo-mes" className="text-[11px] font-medium text-muted-foreground">
            {t.mes}
          </label>
          <input
            id="consumo-mes"
            type="month"
            value={datos.mes}
            onChange={(e) => cambiar({ mes: mesValido(e.target.value) })}
            className={cn(CAMPO_FILTRO, "w-40")}
          />
        </div>

        <div className="grid gap-1">
          <label htmlFor="consumo-orden" className="text-[11px] font-medium text-muted-foreground">
            {t.orden}
          </label>
          <select id="consumo-orden" value={params.orden} onChange={(e) => cambiar({ orden: e.target.value as OrdenDeConsumo })} className={cn(CAMPO_FILTRO, "w-auto")}>
            {ORDENES_DE_CONSUMO.map((o) => (
              <option key={o} value={o}>
                {t.ordenes[o]}
              </option>
            ))}
          </select>
        </div>
      </div>

      <ul className="grid gap-0.5 text-[12px] text-muted-foreground">
        <li>{t.ayudaPlanDeHoy}</li>
        <li>{t.ayudaAlerta.replace("{umbral}", String(datos.umbral_de_alerta))}</li>
      </ul>

      <div className="overflow-hidden rounded-xl border border-border/90 bg-card shadow-2xs">
        <Table>
          <TableHeader>
            <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
              <TableHead className={`${CABECERA_TABLA} pl-4`}>{columnas.cuenta}</TableHead>
              <TableHead className={CABECERA_TABLA}>{columnas.plan}</TableHead>
              <TableHead className={`${CABECERA_TABLA} text-right`}>{columnas.documentos}</TableHead>
              <TableHead className={`${CABECERA_TABLA} text-right`}>{columnas.limite}</TableHead>
              <TableHead className={CABECERA_TABLA}>{columnas.uso}</TableHead>
              <TableHead className={`${CABECERA_TABLA} pr-4`}>{columnas.estadoDelPlan}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-[13px]">
            {datos.cuentas.map((c) => (
              <TableRow key={c.cuenta_id} data-alerta={c.en_alerta} className={cn("border-b border-border/60 hover:bg-muted/80", c.en_alerta && "bg-warning/40 hover:bg-warning/60")}>
                <TableCell className="py-2 pr-3 pl-4">
                  <div className="flex flex-col">
                    <Link href={hrefDetalleCuenta(c.cuenta_id)} className="font-medium text-foreground hover:text-primary hover:underline">
                      {c.nombre}
                    </Link>
                    <span className="font-mono text-[11px] text-muted-foreground">{c.email}</span>
                  </div>
                </TableCell>
                <TableCell className="px-3 py-2">{c.plan}</TableCell>
                <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{enteroEnPalabras(c.documentos)}</TableCell>
                <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{c.limite === undefined ? t.ilimitado : enteroEnPalabras(c.limite)}</TableCell>
                <TableCell className="px-3 py-2">
                  {c.porcentaje === undefined ? (
                    <span className="text-muted-foreground">{t.sinTope}</span>
                  ) : (
                    <div className="grid gap-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-mono tabular-nums">{c.porcentaje} %</span>
                        {c.en_alerta ? <Etiqueta tono={alTope(c) ? "error" : "aviso"}>{alTope(c) ? t.enElLimite : t.cercaDelLimite}</Etiqueta> : null}
                      </div>
                      <BarraDeUso cuenta={c} />
                    </div>
                  )}
                </TableCell>
                <TableCell className="py-2 pr-4 pl-3">
                  <div className="grid gap-1">
                    <span data-estado={c.estado_del_plan}>
                      <Etiqueta tono={TONO_DEL_PLAN[c.estado_del_plan]}>{messages.admin.planDeCuenta.estados[c.estado_del_plan]}</Etiqueta>
                    </span>
                    {c.pagado_hasta ? <span className="text-[11px] text-muted-foreground">{t.pagadoHasta.replace("{fecha}", formatearFecha(ultimoDiaCubierto(c.pagado_hasta)))}</span> : null}
                  </div>
                </TableCell>
              </TableRow>
            ))}
            {datos.cuentas.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={6} className="py-14 text-center">
                  <div className="flex flex-col items-center gap-2 text-muted-foreground">
                    <InboxIcon className="size-6" />
                    <p className="text-sm">{t.vacio[params.filtro]}</p>
                  </div>
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>

        <PieTabla
          desde={primero}
          hasta={ultimo}
          total={total}
          unidad="cuentas"
          porPagina={params.porPagina}
          onPorPagina={(n) => router.push(hrefConsumo({ ...params, pagina: 1, porPagina: n }))}
          pagina={params.pagina}
          ultimaPagina={ultimaPagina}
          onPagina={(p) => router.push(hrefConsumo({ ...params, pagina: p }))}
          hrefPagina={(p) => hrefConsumo({ ...params, pagina: p })}
        />
      </div>
    </div>
  );
}
