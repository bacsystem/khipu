"use client";

import { TrayIcon } from "@phosphor-icons/react";
import { useLocale, useMessages } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { EstadoCuentaEtiqueta } from "@/components/admin/etiquetas";
import { FiltroDeBajas } from "@/components/admin/filtro-de-bajas";
import { BusquedaDelListado } from "@/components/compartidos/busqueda-del-listado";
import { LimpiarFiltros } from "@/components/compartidos/limpiar-filtros";
import { CONTENEDOR_TABLA, FilaVacia, PieTabla } from "khipu-design-system/patrones/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { hrefDetalleCuenta } from "@/lib/api/admin-cuenta-detalle";
import { hrefCuentas, type CuentaAdmin, type ParamsCuentas } from "@/lib/api/admin-cuentas";
import { MAX_BUSQUEDA } from "@/lib/api/admin-errores";
import { CABECERA_TABLA } from "@/lib/estilos";
import { formatearFechaHora } from "@/lib/formato";

/**
 * Listado de cuentas del backoffice (#180), paginado y filtrado en el servidor: buscar, paginar y cambiar las filas por
 * página solo cambian la URL, y el Server Component de la página vuelve a renderizar. No hay refetch desde el navegador:
 * el proxy genérico usa las cookies del cliente, no la sesión del administrador.
 */
export function CuentasTabla({ datos, total, params }: { datos: CuentaAdmin[]; total: number; params: ParamsCuentas }) {
  const messages = useMessages();
  const locale = useLocale();
  const columnas = messages.admin.cuentas.columnas;
  const router = useRouter();
  const t = messages.admin.cuentas;

  const ultimaPagina = Math.max(1, Math.ceil(total / params.porPagina));
  const primero = total === 0 ? 0 : (params.pagina - 1) * params.porPagina + 1;
  const ultimo = (params.pagina - 1) * params.porPagina + datos.length;
  const hayFiltros = Boolean(params.q || params.bajas);

  /** Buscar vuelve a la primera página y conserva el filtro de bajas y el tamaño de página. */
  function buscar(texto: string) {
    const q = texto.trim();
    router.push(hrefCuentas({ q: q ? q : undefined, bajas: params.bajas, pagina: 1, porPagina: params.porPagina }));
  }

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      {/* La barra de filtros del design system (§12), la misma del portal del cliente: cada filtro se aplica al cambiarlo, la búsqueda tras una pausa. */}
      <div role="search" aria-label={t.barraDeFiltros} className="flex flex-wrap items-center justify-end gap-2.5">
        <BusquedaDelListado
          id="buscar-cuentas"
          etiqueta={t.buscar}
          ayuda={t.buscar}
          placeholder={t.buscar}
          valor={params.q ?? ""}
          onCambio={buscar}
          ancho="w-full sm:w-96"
          maxLength={MAX_BUSQUEDA}
        />
        <FiltroDeBajas valor={params.bajas} onCambio={(bajas) => router.push(hrefCuentas({ ...params, bajas, pagina: 1 }))} />
        {hayFiltros ? <LimpiarFiltros href={hrefCuentas({ pagina: 1, porPagina: params.porPagina })}>{t.quitarFiltros}</LimpiarFiltros> : null}
      </div>

      <div className={CONTENEDOR_TABLA}>
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className={`${CABECERA_TABLA} pl-4`}>{columnas.cuenta}</TableHead>
              <TableHead className={CABECERA_TABLA}>{columnas.telefono}</TableHead>
              <TableHead className={`${CABECERA_TABLA} text-right`}>{columnas.empresas}</TableHead>
              <TableHead className={CABECERA_TABLA}>{columnas.estado}</TableHead>
              <TableHead className={CABECERA_TABLA}>{columnas.alta}</TableHead>
              <TableHead className={`${CABECERA_TABLA} pr-4`}>{columnas.ultimoAcceso}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-control">
            {datos.map((c) => (
              <TableRow key={c.id} data-estado-cuenta={c.estado}>
                <TableCell className="py-2 pr-3 pl-4">
                  <div className="flex flex-col">
                    <Link href={hrefDetalleCuenta(c.id)} className="font-medium text-foreground hover:text-primary hover:underline">
                      {c.nombre}
                    </Link>
                    <span className="font-mono text-etiqueta text-muted-foreground">{c.email}</span>
                  </div>
                </TableCell>
                <TableCell className="px-3 py-2 font-mono text-control text-foreground/80">{c.telefono ?? "—"}</TableCell>
                <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{c.empresas}</TableCell>
                <TableCell className="px-3 py-2">
                  <EstadoCuentaEtiqueta estado={c.estado} />
                </TableCell>
                <TableCell className="px-3 py-2 text-control text-foreground/80">{formatearFechaHora(c.creada_en, locale)}</TableCell>
                <TableCell className="py-2 pr-4 pl-3 text-control text-foreground/80">
                  {c.ultimo_acceso ? formatearFechaHora(c.ultimo_acceso, locale) : <span className="text-muted-foreground">{messages.admin.cuentas.nunca}</span>}
                </TableCell>
              </TableRow>
            ))}
            {datos.length === 0 ? (
              <FilaVacia columnas={6} icon={<TrayIcon className="size-6" />}>
                {hayFiltros ? messages.admin.cuentas.sinResultados : messages.admin.cuentas.vacio}
              </FilaVacia>
            ) : null}
          </TableBody>
        </Table>

        <PieTabla
          desde={primero}
          hasta={ultimo}
          total={total}
          unidad="cuentas"
          porPagina={params.porPagina}
          onPorPagina={(n) => router.push(hrefCuentas({ ...params, pagina: 1, porPagina: n }))}
          pagina={params.pagina}
          ultimaPagina={ultimaPagina}
          onPagina={(p) => router.push(hrefCuentas({ ...params, pagina: p }))}
          hrefPagina={(p) => hrefCuentas({ ...params, pagina: p })}
        />
      </div>
    </div>
  );
}
