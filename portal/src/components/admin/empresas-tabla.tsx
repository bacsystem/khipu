"use client";

import { InboxIcon } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { CertificadoEtiqueta, Etiqueta } from "@/components/admin/etiquetas";
import { FiltroDeBajas } from "@/components/admin/filtro-de-bajas";
import { PieTabla } from "@/components/ui/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { hrefDetalleCuenta } from "@/lib/api/admin-cuenta-detalle";
import { hrefDetalleEmpresa } from "@/lib/api/admin-empresa-detalle";
import {
  ENTORNOS,
  ESTADOS_CERTIFICADO,
  estadoCertificadoDeEmpresa,
  hrefEmpresas,
  type EmpresaAdmin,
  type EntornoAdmin,
  type EstadoCertificadoAdmin,
  type ParamsEmpresas,
} from "@/lib/api/admin-empresas";
import { CABECERA_TABLA, SELECT_NATIVO } from "@/lib/estilos";
import { formatearFecha } from "@/lib/formato";
import { messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

const t = messages.admin.empresas;

/** Una fila con el certificado vencido o por vencer se tiñe: el administrador las ve sin leer la columna (#185). */
const TINTE: Partial<Record<EstadoCertificadoAdmin, string>> = {
  VENCIDO: "bg-destructive/5 hover:bg-destructive/10",
  POR_VENCER: "bg-warning/40 hover:bg-warning/60",
};

/**
 * Listado de empresas del backoffice (#185), paginado y filtrado en el servidor: cambiar un filtro o la página solo cambia la URL, y
 * el Server Component de la página vuelve a renderizar. No hay refetch desde el navegador: el proxy genérico usa las cookies del
 * cliente, no la sesión del administrador.
 */
export function EmpresasTabla({ datos, total, params }: { datos: EmpresaAdmin[]; total: number; params: ParamsEmpresas }) {
  const router = useRouter();

  const ultimaPagina = Math.max(1, Math.ceil(total / params.porPagina));
  const primero = total === 0 ? 0 : (params.pagina - 1) * params.porPagina + 1;
  const ultimo = (params.pagina - 1) * params.porPagina + datos.length;
  const hayFiltros = Boolean(params.entorno || params.certificado || params.bajas);

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      <div className="flex flex-wrap items-end gap-3">
        <div className="grid gap-1">
          <label htmlFor="filtro-entorno" className="text-[11px] font-medium text-muted-foreground">
            {t.filtroEntorno}
          </label>
          <select
            id="filtro-entorno"
            value={params.entorno ?? ""}
            onChange={(e) => router.push(hrefEmpresas({ ...params, entorno: (e.target.value || undefined) as EntornoAdmin | undefined, pagina: 1 }))}
            className={SELECT_NATIVO}
          >
            <option value="">{t.todosEntornos}</option>
            {ENTORNOS.map((x) => (
              <option key={x} value={x}>
                {x === "BETA" ? t.beta : t.produccion}
              </option>
            ))}
          </select>
        </div>
        <div className="grid gap-1">
          <label htmlFor="filtro-certificado" className="text-[11px] font-medium text-muted-foreground">
            {t.filtroCertificado}
          </label>
          <select
            id="filtro-certificado"
            value={params.certificado ?? ""}
            onChange={(e) => router.push(hrefEmpresas({ ...params, certificado: (e.target.value || undefined) as EstadoCertificadoAdmin | undefined, pagina: 1 }))}
            className={SELECT_NATIVO}
          >
            <option value="">{t.todosCertificados}</option>
            {ESTADOS_CERTIFICADO.map((x) => (
              <option key={x} value={x}>
                {t.estados[x]}
              </option>
            ))}
          </select>
        </div>
        <FiltroDeBajas valor={params.bajas} onCambio={(bajas) => router.push(hrefEmpresas({ ...params, bajas, pagina: 1 }))} />
        {hayFiltros ? (
          <Link href={hrefEmpresas({ pagina: 1, porPagina: params.porPagina })} className="pb-2 text-xs text-primary hover:underline">
            {t.quitarFiltros}
          </Link>
        ) : null}
      </div>

      <div className="overflow-x-auto rounded-xl border border-border/90 bg-card shadow-2xs">
        <Table>
          <TableHeader>
            <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
              <TableHead className={`${CABECERA_TABLA} pl-4`}>{t.columnas.empresa}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.cuenta}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.entorno}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.certificado}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.sol}</TableHead>
              <TableHead className={`${CABECERA_TABLA} text-right`}>{t.columnas.series}</TableHead>
              <TableHead className={`${CABECERA_TABLA} text-right`}>{t.columnas.delMes}</TableHead>
              <TableHead className={`${CABECERA_TABLA} pr-4`}>{t.columnas.ultimaEmision}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-[13px]">
            {datos.map((e) => (
              <TableRow key={e.id} data-certificado={e.certificado} className={cn("border-b border-border/60 hover:bg-muted/80", TINTE[e.certificado])}>
                <TableCell className="py-2 pr-3 pl-4">
                  <div className="flex flex-col">
                    <Link href={hrefDetalleEmpresa(e.id)} className="font-medium text-foreground hover:text-primary hover:underline">
                      {e.razon_social}
                    </Link>
                    <span className="font-mono text-[11px] text-muted-foreground">{e.ruc}</span>
                  </div>
                </TableCell>
                <TableCell className="px-3 py-2">
                  {e.cuenta_id ? (
                    <div className="flex flex-wrap items-center gap-1.5">
                      <Link href={hrefDetalleCuenta(e.cuenta_id)} className="text-foreground hover:text-primary hover:underline">
                        {e.cuenta_nombre}
                      </Link>
                      {e.cuenta_de_baja_en ? <Etiqueta tono="neutro">{messages.admin.bajas.cuentaDeBaja}</Etiqueta> : null}
                    </div>
                  ) : (
                    <span className="text-muted-foreground">{t.sinCuenta}</span>
                  )}
                </TableCell>
                <TableCell className="px-3 py-2">
                  <Etiqueta tono={e.entorno === "PRODUCCION" ? "ok" : "neutro"}>{e.entorno === "PRODUCCION" ? t.produccion : t.beta}</Etiqueta>
                </TableCell>
                <TableCell className="px-3 py-2">
                  <CertificadoEtiqueta estado={estadoCertificadoDeEmpresa(e)} />
                </TableCell>
                <TableCell className="px-3 py-2">
                  <Etiqueta tono={e.tiene_credenciales_sol ? "ok" : "neutro"}>{e.tiene_credenciales_sol ? t.solCargadas : t.solSinCargar}</Etiqueta>
                </TableCell>
                <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{e.series}</TableCell>
                <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{e.comprobantes_del_mes}</TableCell>
                <TableCell className="py-2 pr-4 pl-3 text-[12px] text-foreground/80">
                  {e.ultima_emision ? formatearFecha(e.ultima_emision) : <span className="text-muted-foreground">{t.nuncaEmitio}</span>}
                </TableCell>
              </TableRow>
            ))}
            {datos.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={8} className="py-14 text-center">
                  <div className="flex flex-col items-center gap-2 text-muted-foreground">
                    <InboxIcon className="size-6" />
                    <p className="text-sm">{hayFiltros ? t.sinResultados : t.vacio}</p>
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
          unidad="empresas"
          porPagina={params.porPagina}
          onPorPagina={(n) => router.push(hrefEmpresas({ ...params, pagina: 1, porPagina: n }))}
          pagina={params.pagina}
          ultimaPagina={ultimaPagina}
          onPagina={(p) => router.push(hrefEmpresas({ ...params, pagina: p }))}
          hrefPagina={(p) => hrefEmpresas({ ...params, pagina: p })}
        />
      </div>
    </div>
  );
}
