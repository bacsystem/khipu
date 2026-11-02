"use client";

import { TrayIcon } from "@phosphor-icons/react";
import { useLocale, useMessages } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { CertificadoEtiqueta, Etiqueta } from "@/components/admin/etiquetas";
import { FiltroDeBajas } from "@/components/admin/filtro-de-bajas";
import { BusquedaDelListado } from "@/components/compartidos/busqueda-del-listado";
import { LimpiarFiltros } from "@/components/compartidos/limpiar-filtros";
import { SelectDeFiltro } from "@/components/compartidos/select-de-filtro";
import { CONTENEDOR_TABLA, FilaVacia, PieTabla } from "khipu-design-system/patrones/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
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
import { MAX_BUSQUEDA } from "@/lib/api/admin-errores";
import { CABECERA_TABLA } from "@/lib/estilos";
import { formatearFecha } from "@/lib/formato";

/**
 * Listado de empresas del backoffice (#185), paginado y filtrado en el servidor: cambiar un filtro o la página solo cambia la URL, y
 * el Server Component de la página vuelve a renderizar. No hay refetch desde el navegador: el proxy genérico usa las cookies del
 * cliente, no la sesión del administrador.
 */
export function EmpresasTabla({ datos, total, params }: { datos: EmpresaAdmin[]; total: number; params: ParamsEmpresas }) {
  const messages = useMessages();
  const locale = useLocale();
  const t = messages.admin.empresas;
  const router = useRouter();

  const ultimaPagina = Math.max(1, Math.ceil(total / params.porPagina));
  const primero = total === 0 ? 0 : (params.pagina - 1) * params.porPagina + 1;
  const ultimo = (params.pagina - 1) * params.porPagina + datos.length;
  const hayFiltros = Boolean(params.q || params.entorno || params.certificado || params.bajas);

  /** H17: buscar por RUC o razón social, como en Cuentas; la búsqueda se combina con los filtros y vuelve a la primera página. */
  function buscar(texto: string) {
    const q = texto.trim();
    router.push(hrefEmpresas({ ...params, q: q ? q : undefined, pagina: 1 }));
  }

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      {/* La barra de filtros del design system (§12), la misma del portal del cliente: cada filtro se aplica al cambiarlo, la búsqueda tras una pausa. */}
      <div role="search" aria-label={t.barraDeFiltros} className="flex flex-wrap items-center justify-end gap-2.5">
        <BusquedaDelListado
          id="buscar-empresas"
          etiqueta={t.buscar}
          ayuda={t.buscar}
          placeholder={t.buscar}
          valor={params.q ?? ""}
          onCambio={buscar}
          ancho="w-full sm:w-72"
          maxLength={MAX_BUSQUEDA}
        />
        <SelectDeFiltro
          id="filtro-entorno"
          etiqueta={t.filtroEntorno}
          opciones={[{ value: "", label: t.todosEntornos }, ...ENTORNOS.map((x) => ({ value: x, label: x === "BETA" ? t.beta : t.produccion }))]}
          valor={params.entorno ?? ""}
          onCambio={(v) => router.push(hrefEmpresas({ ...params, entorno: (v || undefined) as EntornoAdmin | undefined, pagina: 1 }))}
        />
        <SelectDeFiltro
          id="filtro-certificado"
          etiqueta={t.filtroCertificado}
          opciones={[{ value: "", label: t.todosCertificados }, ...ESTADOS_CERTIFICADO.map((x) => ({ value: x, label: t.estados[x] }))]}
          valor={params.certificado ?? ""}
          onCambio={(v) => router.push(hrefEmpresas({ ...params, certificado: (v || undefined) as EstadoCertificadoAdmin | undefined, pagina: 1 }))}
        />
        <FiltroDeBajas valor={params.bajas} onCambio={(bajas) => router.push(hrefEmpresas({ ...params, bajas, pagina: 1 }))} />
        {hayFiltros ? <LimpiarFiltros href={hrefEmpresas({ pagina: 1, porPagina: params.porPagina })}>{t.quitarFiltros}</LimpiarFiltros> : null}
      </div>

      <div className={CONTENEDOR_TABLA}>
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className={`${CABECERA_TABLA} pl-4`}>{t.columnas.empresa}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.cuenta}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.entorno}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.certificado}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.sol}</TableHead>
              <TableHead className={`${CABECERA_TABLA} text-right`}>{t.columnas.series}</TableHead>
              <TableHead className={`${CABECERA_TABLA} text-right`} title={t.columnas.delMesAyuda}>
                {t.columnas.delMes}
              </TableHead>
              <TableHead className={`${CABECERA_TABLA} pr-4`}>{t.columnas.ultimaEmision}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-control">
            {datos.map((e) => (
              // La fila no se tiñe (#185): la pill del certificado (`CertificadoEtiqueta`, roja o ámbar) dice el estado.
              <TableRow key={e.id} data-certificado={e.certificado}>
                <TableCell className="py-2 pr-3 pl-4">
                  <div className="flex flex-col">
                    <Link href={hrefDetalleEmpresa(e.id)} className="font-medium text-foreground hover:text-primary hover:underline">
                      {e.razon_social}
                    </Link>
                    <span className="font-mono text-etiqueta text-muted-foreground">{e.ruc}</span>
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
                <TableCell className="py-2 pr-4 pl-3 text-control text-foreground/80">
                  {e.ultima_emision ? formatearFecha(e.ultima_emision, locale) : <span className="text-muted-foreground">{t.nuncaEmitio}</span>}
                </TableCell>
              </TableRow>
            ))}
            {datos.length === 0 ? (
              <FilaVacia columnas={8} icon={<TrayIcon className="size-6" />}>
                {hayFiltros ? t.sinResultados : t.vacio}
              </FilaVacia>
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
