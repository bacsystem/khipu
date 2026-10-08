"use client";

import { FilterIcon, InboxIcon, SearchIcon } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { DescartarComprobante } from "@/components/admin/descartar-comprobante";
import { Etiqueta, type Tono } from "@/components/admin/etiquetas";
import { ReintentarEnvio } from "@/components/admin/reintentar-envio";
import { ETIQUETAS_ESTADO } from "@/components/comprobantes/estado-badge";
import { PieTabla } from "@/components/ui/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { hrefDetalleEmpresa } from "@/lib/api/admin-empresa-detalle";
import { CLASES_DE_ERROR, hrefErrores, MAX_BUSQUEDA, type ClaseDeError, type ErrorDeEmision, type ParamsErrores } from "@/lib/api/admin-errores";
import { ETIQUETAS_TIPO, type EstadoDocumento } from "@/lib/api/facturas";
import { ACCION_SECUNDARIA, CABECERA_TABLA, CAMPO, SEGMENTADO, SEGMENTO } from "@/lib/estilos";
import { faultEnPalabras } from "@/lib/errores-formato";
import { formatearFecha, formatearFechaHora } from "@/lib/formato";
import { messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

const t = messages.admin.errores;

/** Un error de envío todavía se puede arreglar (aviso); un rechazo de formato y un fuera de plazo ya son terminales (error). */
const TONO_DE_CLASE: Record<ClaseDeError, Tono> = { ERROR_DE_ENVIO: "aviso", ERROR_DE_FORMATO: "error", FUERA_DE_PLAZO: "error" };

/**
 * La cola global de errores (#196): los comprobantes con problema de todos los clientes, paginados y filtrados en el servidor. Cambiar de tipo, buscar o cambiar de página
 * solo cambia la URL y el Server Component de la página vuelve a renderizar. Solo un error de envío tiene acciones (reintentar y descartar); los otros dos ya son terminales y la
 * fila lo dice. Lo que pasó con la última acción se muestra arriba y sobrevive a la recarga que sigue.
 */
export function ColaDeErroresTabla({ errores, total, params }: { errores: ErrorDeEmision[]; total: number; params: ParamsErrores }) {
  const router = useRouter();
  const [resultado, setResultado] = useState<string | null>(null);

  const ultimaPagina = Math.max(1, Math.ceil(total / params.porPagina));
  const primero = total === 0 ? 0 : (params.pagina - 1) * params.porPagina + 1;
  const ultimo = (params.pagina - 1) * params.porPagina + errores.length;
  const conFiltros = Boolean(params.clase || params.empresa || params.q);

  function buscar(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const texto = String(new FormData(e.currentTarget).get("q") ?? "").trim().slice(0, MAX_BUSQUEDA);
    router.push(hrefErrores({ ...params, q: texto || undefined, pagina: 1 }));
  }

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      <div className="flex flex-wrap items-end gap-3">
        <nav aria-label={t.vistas} className={SEGMENTADO}>
          {[undefined, ...CLASES_DE_ERROR].map((clase) => (
            <Link
              key={clase ?? "TODAS"}
              href={hrefErrores({ ...params, clase, pagina: 1 })}
              aria-current={params.clase === clase ? "page" : undefined}
              className={SEGMENTO}
            >
              {t.filtros[clase ?? "TODAS"]}
            </Link>
          ))}
        </nav>

        <form onSubmit={buscar} role="search" className="flex items-end gap-2">
          <div className="grid gap-1">
            <label htmlFor="errores-q" className="text-[11px] font-medium text-muted-foreground">
              {t.buscar}
            </label>
            <input id="errores-q" name="q" type="search" key={params.q ?? ""} defaultValue={params.q ?? ""} maxLength={MAX_BUSQUEDA} placeholder={t.buscarPlaceholder} className={cn(CAMPO, "w-64")} />
          </div>
          <button type="submit" className={ACCION_SECUNDARIA}>
            <SearchIcon className="size-4" />
            {t.botonBuscar}
          </button>
        </form>

        {params.empresa ? (
          <span data-testid="errores-empresa-filtrada" className="inline-flex items-center gap-2 rounded-md border border-border bg-card px-2.5 py-1.5 text-[12px]">
            {t.soloUnaEmpresa}
            <Link href={hrefErrores({ ...params, empresa: undefined, pagina: 1 })} className="font-medium text-primary hover:underline">
              {t.verTodas}
            </Link>
          </span>
        ) : null}

        {conFiltros ? (
          <Link href={hrefErrores({ pagina: 1, porPagina: params.porPagina })} className="pb-2 text-[13px] text-primary hover:underline">
            {t.quitarFiltros}
          </Link>
        ) : null}
      </div>

      {params.clase ? <p className="text-[12px] text-muted-foreground">{t.ayudaClase[params.clase]}</p> : null}

      {resultado ? (
        <p data-testid="errores-resultado" role="status" className="rounded-lg border border-border bg-muted/50 px-3 py-2.5 text-[13px]">
          {resultado}
        </p>
      ) : null}

      <div className="overflow-x-auto rounded-xl border border-border/90 bg-card shadow-2xs">
        <Table>
          <TableHeader>
            <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
              <TableHead className={`${CABECERA_TABLA} pl-4`}>{t.columnas.cliente}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.comprobante}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.problema}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.fault}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.intentos}</TableHead>
              <TableHead className={`${CABECERA_TABLA} pr-4`}>{t.columnas.acciones}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-[13px]">
            {errores.map((e) => (
              <TableRow key={e.comprobante_id} data-testid="errores-fila" data-clase={e.clase} data-comprobante={e.nombre_archivo} className="border-b border-border/60 align-top hover:bg-muted/80">
                <TableCell className="py-2 pr-3 pl-4">
                  <div className="flex flex-col">
                    <Link href={hrefDetalleEmpresa(e.empresa_id)} className="font-medium text-foreground hover:text-primary hover:underline">
                      {e.razon_social}
                    </Link>
                    <span className="font-mono text-[11px] text-muted-foreground">{e.ruc}</span>
                    {e.cuenta_nombre ? <span className="text-[11px] text-muted-foreground">{e.cuenta_nombre}</span> : null}
                    {params.empresa === e.empresa_id ? null : (
                      <Link
                        href={hrefErrores({ ...params, empresa: e.empresa_id, pagina: 1 })}
                        data-testid="errores-filtrar-empresa"
                        className="mt-0.5 inline-flex items-center gap-1 text-[11px] text-primary hover:underline"
                      >
                        <FilterIcon className="size-3" />
                        {t.soloUnaEmpresa}
                      </Link>
                    )}
                  </div>
                </TableCell>
                <TableCell className="px-3 py-2">
                  <div className="flex flex-col">
                    <span className="font-mono text-[12px] whitespace-nowrap">{e.nombre_archivo}</span>
                    <span className="text-[11px] text-muted-foreground">
                      {ETIQUETAS_TIPO[e.tipo] ?? e.tipo} · {formatearFecha(e.fecha_emision)}
                    </span>
                  </div>
                </TableCell>
                <TableCell className="px-3 py-2">
                  <div className="grid justify-items-start gap-1">
                    <Etiqueta tono={TONO_DE_CLASE[e.clase]}>{t.filtros[e.clase]}</Etiqueta>
                    <span className="text-[11px] text-muted-foreground">{ETIQUETAS_ESTADO[e.estado as EstadoDocumento] ?? e.estado}</span>
                  </div>
                </TableCell>
                <TableCell className="max-w-xs px-3 py-2 font-mono text-[11px] break-words text-muted-foreground" data-testid="errores-fault">
                  {faultEnPalabras(e.fault) || t.sinFault}
                </TableCell>
                <TableCell className="px-3 py-2">
                  <div className="flex flex-col">
                    <span className="font-mono tabular-nums" data-testid="errores-intentos">
                      {e.intentos}
                    </span>
                    {e.proximo_intento ? (
                      <span className="text-[11px] whitespace-nowrap text-muted-foreground">{t.proximoIntento.replace("{fecha}", formatearFechaHora(e.proximo_intento))}</span>
                    ) : e.accionable ? (
                      <span className="text-[11px] text-muted-foreground">{t.sinReintento}</span>
                    ) : null}
                  </div>
                </TableCell>
                <TableCell className="py-2 pr-4 pl-3">
                  {e.accionable ? (
                    <div className="flex flex-wrap gap-1.5">
                      <ReintentarEnvio comprobante={e} alResultado={setResultado} />
                      <DescartarComprobante comprobante={e} alResultado={setResultado} />
                    </div>
                  ) : (
                    <span className="text-[11px] text-muted-foreground">{t.terminal}</span>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {errores.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={6} className="py-14 text-center">
                  <div data-testid="errores-vacio" className="flex flex-col items-center gap-2 text-muted-foreground">
                    <InboxIcon className="size-6" />
                    <p className="text-sm">{params.q || params.empresa ? t.vacioConBusqueda : t.vacio[params.clase ?? "TODAS"]}</p>
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
          unidad="comprobantes"
          porPagina={params.porPagina}
          onPorPagina={(n) => router.push(hrefErrores({ ...params, pagina: 1, porPagina: n }))}
          pagina={params.pagina}
          ultimaPagina={ultimaPagina}
          onPagina={(p) => router.push(hrefErrores({ ...params, pagina: p }))}
          hrefPagina={(p) => hrefErrores({ ...params, pagina: p })}
        />
      </div>
    </div>
  );
}
