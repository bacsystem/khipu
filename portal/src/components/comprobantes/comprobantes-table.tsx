"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowClockwiseIcon, CalendarBlankIcon, FileTextIcon, TrayIcon } from "@phosphor-icons/react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useLocale, useMessages } from "next-intl";
import { useMemo } from "react";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "khipu-design-system/ui/select";
import { PieTabla } from "khipu-design-system/patrones/pie-tabla";
import { esClickSimple } from "khipu-design-system/lib/navegacion";
import { POR_PAGINA_DEFECTO } from "khipu-design-system/lib/paginacion";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import {
  esEstadoFinal,
  etiquetasTipo,
  etiquetasTipoDoc,
  type FiltrosComprobantes,
  normalizarComprobante,
  paramsDeFiltros,
  tieneConstanciaCdr,
  TIPOS_FILTRABLES,
  totalDesdeHeaders,
  type Comprobante,
  type EstadoDocumento,
  type PaginaComprobantes,
} from "@/lib/api/facturas";
import { formatearFecha, formatearMonto } from "@/lib/formato";
import { cn } from "@/lib/utils";
import { BotonCopiar } from "khipu-design-system/patrones/boton-copiar";
import { sePuedeDuplicar } from "@/lib/comprobantes/duplicar";
import { BusquedaDelListado } from "./busqueda-del-listado";
import { DescargaMasiva } from "./descarga-masiva";
import { ExportarComprobantes } from "./exportar-comprobantes";
import { DuplicarButton } from "./duplicar-button";
import { etiquetasEstado, EstadoBadge } from "./estado-badge";
import { CONTROL_FILTRO } from "@/lib/estilos";

const ESTADOS: EstadoDocumento[] = [
  "RECIBIDO",
  "FIRMADO",
  "ERROR_ENVIO",
  "PENDIENTE_AGRUPACION",
  "ENVIADO",
  "ACEPTADO",
  "ACEPTADO_CON_OBS",
  "RECHAZADO",
  "ANULADO",
  "FUERA_DE_PLAZO",
  "DESCARTADO",
  "INVALIDO",
];

const TODOS_LOS_ESTADOS = "todos";
const TODOS_LOS_TIPOS = "todos";

const TODAS_LAS_SERIES = "todas";
const ACCION = "flex h-control items-center justify-center rounded-lg border border-border font-mono text-[10px] font-semibold shadow-2xs transition-colors";

async function fetchComprobantes(filtros: FiltrosComprobantes, pagina: number, porPagina: number, errorListar: string): Promise<PaginaComprobantes> {
  const qs = paramsDeFiltros(filtros, new URLSearchParams({ pagina: String(pagina), por_pagina: String(porPagina) }));
  const res = await fetch(`/api/proxy/facturas?${qs}`);
  const json = await res.json();
  if (json.estado !== "exito") throw new Error(json.mensaje ?? errorListar);
  const datos = (json.datos as Comprobante[]).map(normalizarComprobante);
  return { datos, total: totalDesdeHeaders(res.headers, datos.length) };
}


function etiquetaEstado(c: Comprobante, t: Mensajes["comprobantes"]["tabla"]): string | undefined {
  if (c.estado_documento === "ACEPTADO" && tieneConstanciaCdr(c)) return t.aceptadoConCdr;
  if (c.estado_documento === "RECHAZADO" && c.cdr?.codigo) return interpolar(t.rechazadoCodigo, { codigo: c.cdr.codigo });
  return undefined;
}

/**
 * Clave de la consulta del listado. **La empresa va primero y no es opcional**: cambiar de empresa es una navegación blanda,
 * así que la tabla no se desmonta, y sin la empresa en la clave react-query servía las filas cacheadas de la empresa
 * anterior descartando las que el servidor acababa de renderizar. El listado mostraba comprobantes ajenos bajo el RUC
 * nuevo, y se quedaba así mientras el refetch no respondiera.
 */
export function claveComprobantes(empresaId: string, filtros: FiltrosComprobantes, pagina: number, porPagina: number) {
  return ["facturas", empresaId, filtros.estado ?? null, filtros.desde ?? null, filtros.hasta ?? null, filtros.serie ?? null, filtros.tipo ?? null, filtros.cliente ?? null, filtros.numero ?? null, pagina, porPagina] as const;
}

export function ComprobantesTable({
  inicial,
  pagina,
  porPagina,
  filtros,
  empresaId,
  series,
}: {
  inicial: PaginaComprobantes;
  pagina: number;
  porPagina: number;
  /** Filtros activos, leídos de la URL por la página (#6). */
  filtros: FiltrosComprobantes;
  /**
   * Empresa activa. Va en la clave de la consulta y no es opcional: cambiar de empresa es una navegación blanda, así que
   * la tabla no se desmonta, y sin la empresa en la clave react-query servía las filas cacheadas de la empresa anterior
   * descartando las que acababa de renderizar el servidor. El listado mostraba comprobantes ajenos bajo el RUC nuevo.
   */
  empresaId: string;
  /** Series de la empresa para el selector; vacío si no se pudieron cargar. */
  series: string[];
}) {
  const router = useRouter();
  const params = useSearchParams();
  const messages = useMessages();
  const locale = useLocale();
  const t = messages.comprobantes.tabla;
  const ETIQUETAS_ESTADO = etiquetasEstado(messages);
  const ETIQUETAS_TIPO = etiquetasTipo(messages);
  const ETIQUETAS_TIPO_DOC = etiquetasTipoDoc(messages);
  const itemsEstado: Record<string, string> = useMemo(
    () => ({ [TODOS_LOS_ESTADOS]: t.estadoTodos, ...Object.fromEntries(ESTADOS.map((e) => [e, etiquetasEstado(messages)[e]])) }),
    [messages, t],
  );
  const itemsTipo: Record<string, string> = useMemo(
    () => ({
      [TODOS_LOS_TIPOS]: t.tipoTodos,
      ...Object.fromEntries(TIPOS_FILTRABLES.map((tp) => [tp, interpolar(t.tipoOpcion, { tipo: etiquetasTipo(messages)[tp] ?? tp })])),
    }),
    [messages, t],
  );
  const { estado, desde, hasta, serie, tipo, cliente, numero } = filtros;
  const hayFiltros = !!(estado || desde || hasta || serie || tipo || cliente || numero);

  const { data: paginaActual, isFetching, refetch } = useQuery({
    queryKey: claveComprobantes(empresaId, filtros, pagina, porPagina),
    queryFn: () => fetchComprobantes(filtros, pagina, porPagina, t.errorListar),
    initialData: inicial,
    refetchInterval: (query) => {
      const rows = query.state.data?.datos ?? [];
      return rows.some((c) => !esEstadoFinal(c.estado_documento)) ? 10_000 : false;
    },
  });
  const data = paginaActual.datos;
  const total = paginaActual.total;

  function conPagina(next: URLSearchParams, p: number, tamano = porPagina) {
    if (p > 1) next.set("pagina", String(p));
    else next.delete("pagina");
    if (tamano !== POR_PAGINA_DEFECTO) next.set("por_pagina", String(tamano));
    else next.delete("por_pagina");
  }

  function irA(nuevaPagina: number, tamano = porPagina) {
    const next = new URLSearchParams(params.toString());
    conPagina(next, nuevaPagina, tamano);
    const qs = next.toString();
    router.push(qs ? `/comprobantes?${qs}` : "/comprobantes");
  }

  /** Cambia un filtro en la URL (compartible) y vuelve a la página 1; un valor vacío lo quita. */
  function cambiarFiltro(nombre: "estado" | "desde" | "hasta" | "serie" | "tipo" | "cliente" | "numero", valor: string | null | undefined) {
    const next = new URLSearchParams(params.toString());
    if (valor) next.set(nombre, valor);
    else next.delete(nombre);
    conPagina(next, 1);
    const qs = next.toString();
    router.push(qs ? `/comprobantes?${qs}` : "/comprobantes");
  }

  function cambiarEstado(value: string | null) {
    cambiarFiltro("estado", !value || value === TODOS_LOS_ESTADOS ? null : (value as EstadoDocumento));
  }

  const opcionesSerie = serie && !series.includes(serie) ? [serie, ...series] : series;
  const itemsSerie: Record<string, string> = { [TODAS_LAS_SERIES]: t.serieTodas, ...Object.fromEntries(opcionesSerie.map((s) => [s, interpolar(t.serieOpcion, { serie: s })])) };

  function hrefPagina(p: number) {
    const next = new URLSearchParams(params.toString());
    conPagina(next, p);
    const qs = next.toString();
    return qs ? `/comprobantes?${qs}` : "/comprobantes";
  }

  const ultimaPagina = Math.max(1, Math.ceil(total / porPagina));
  const primerResultado = data.length === 0 ? 0 : (pagina - 1) * porPagina + 1;
  const ultimoResultado = (pagina - 1) * porPagina + data.length;

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      {/* Solo filtros y acciones que funcionan (C6): las pestañas por tipo, la selección múltiple y «Más acciones» se veían
          deshabilitados. */}
      <div className="flex flex-wrap items-center justify-end gap-3">
        <div className="flex flex-wrap items-center gap-2.5">
          <Select items={itemsEstado} value={estado ?? TODOS_LOS_ESTADOS} onValueChange={cambiarEstado}>
            <SelectTrigger className={cn(CONTROL_FILTRO, "w-auto min-w-44 pl-3")}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={TODOS_LOS_ESTADOS}>{t.estadoTodos}</SelectItem>
              {ESTADOS.map((e) => (
                <SelectItem key={e} value={e}>
                  {ETIQUETAS_ESTADO[e]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select items={itemsTipo} value={tipo ?? TODOS_LOS_TIPOS} onValueChange={(v) => cambiarFiltro("tipo", !v || v === TODOS_LOS_TIPOS ? null : v)}>
            <SelectTrigger className={cn(CONTROL_FILTRO, "w-auto min-w-36 pl-3")} aria-label={t.tipo}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={TODOS_LOS_TIPOS}>{t.tipoTodos}</SelectItem>
              {TIPOS_FILTRABLES.map((tp) => (
                <SelectItem key={tp} value={tp}>
                  {interpolar(t.tipoOpcion, { tipo: ETIQUETAS_TIPO[tp] ?? tp })}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select items={itemsSerie} value={serie ?? TODAS_LAS_SERIES} onValueChange={(v) => cambiarFiltro("serie", !v || v === TODAS_LAS_SERIES ? null : v)}>
            <SelectTrigger className={cn(CONTROL_FILTRO, "w-auto min-w-36 pl-3")} aria-label={t.serie}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={TODAS_LAS_SERIES}>{t.serieTodas}</SelectItem>
              {opcionesSerie.map((s) => (
                <SelectItem key={s} value={s}>
                  {interpolar(t.serieOpcion, { serie: s })}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <BusquedaDelListado id="filtro-cliente" etiqueta={t.cliente.etiqueta} ayuda={t.cliente.ayuda} placeholder={t.cliente.placeholder} valor={cliente ?? ""} onCambio={(v) => cambiarFiltro("cliente", v.trim() || null)} ancho="w-48" maxLength={100} />
          <BusquedaDelListado id="filtro-numero" etiqueta={t.numero.etiqueta} ayuda={t.numero.ayuda} placeholder={t.numero.placeholder} valor={numero ? String(numero) : ""} onCambio={(v) => cambiarFiltro("numero", v.replace(/\D/g, "").replace(/^0+/, "") || null)} ancho="w-24" soloDigitos maxLength={12} />

          <div className={cn(CONTROL_FILTRO, "inline-flex items-center gap-1.5 px-3")} title={t.fechaTitulo}>
            <CalendarBlankIcon className="size-4 text-muted-foreground/70" />
            <label className="sr-only" htmlFor="filtro-desde">{t.desde}</label>
            <input
              id="filtro-desde"
              type="date"
              value={desde ?? ""}
              max={hasta}
              onChange={(e) => cambiarFiltro("desde", e.target.value)}
              className="bg-transparent text-[12px] text-foreground outline-none"
            />
            <span className="text-muted-foreground/70">–</span>
            <label className="sr-only" htmlFor="filtro-hasta">{t.hasta}</label>
            <input
              id="filtro-hasta"
              type="date"
              value={hasta ?? ""}
              min={desde}
              onChange={(e) => cambiarFiltro("hasta", e.target.value)}
              className="bg-transparent text-[12px] text-foreground outline-none"
            />
          </div>

          <DescargaMasiva filtros={filtros} total={total} />

          <ExportarComprobantes filtros={filtros} total={total} />

          <button
            type="button"
            onClick={() => refetch()}
            title={t.refrescar}
            className={cn(CONTROL_FILTRO, "inline-flex size-control items-center justify-center text-muted-foreground transition-colors hover:text-foreground")}
          >
            <ArrowClockwiseIcon weight="bold" className={cn("size-4", isFetching && "animate-spin")} />
          </button>
        </div>
      </div>

      <div
        className={cn(
          "min-w-0 overflow-hidden rounded-xl border border-border/90 bg-card shadow-2xs transition-opacity",
          isFetching && "opacity-60",
        )}
      >
        <Table>
          <TableHeader>
            <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
              <TableHead className="h-auto py-2 pr-3 pl-4 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">{t.columnas.comprobante}</TableHead>
              <TableHead className="h-auto px-3 py-2 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">{t.columnas.cliente}</TableHead>
              <TableHead className="h-auto px-3 py-2 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">{t.columnas.fecha}</TableHead>
              <TableHead className="h-auto px-4 py-2 text-right text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">{t.columnas.importe}</TableHead>
              <TableHead className="h-auto px-4 py-2 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">{t.columnas.estado}</TableHead>
              <TableHead className="h-auto py-2 pr-4 pl-2 text-right text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">{t.columnas.acciones}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-[13px]">
            {data.map((c) => {
              const numeroCompleto = `${c.serie}-${String(c.numero).padStart(8, "0")}`;
              return (
                <TableRow
                  key={c.id}
                  onClick={(e) => {
                    if (!esClickSimple(e)) return;
                    router.push(`/comprobantes/${c.id}`);
                  }}
                  className="group cursor-pointer border-b border-border/60 hover:bg-muted/80"
                >
                  <TableCell className="py-2 pr-3 pl-4">
                    <div className="flex flex-col">
                      <div className="flex items-center gap-1.5">
                        <Link
                          href={`/comprobantes/${c.id}`}
                          aria-label={interpolar(t.verComprobante, { numero: numeroCompleto, estado: ETIQUETAS_ESTADO[c.estado_documento] })}
                          onClick={(e) => e.stopPropagation()}
                          className="rounded font-mono text-[13px] font-semibold tracking-tight text-foreground outline-none hover:underline focus-visible:ring-2 focus-visible:ring-ring"
                        >
                          {numeroCompleto}
                        </Link>
                        <BotonCopiar texto={numeroCompleto} titulo={t.copiarNumero} className="opacity-0 group-hover:opacity-100" />
                        {c.moneda !== "PEN" ? (
                          <span className="rounded border border-warning-border bg-warning px-1.5 py-0.5 font-mono text-[10px] font-medium text-warning-foreground uppercase">
                            {c.moneda}
                          </span>
                        ) : null}
                      </div>
                      <div className="mt-0.5 flex items-center gap-1 text-[11px] text-muted-foreground/80">
                        <span className="size-1.5 rounded-full bg-border" />
                        {interpolar(t.tipoElectronico, { tipo: ETIQUETAS_TIPO[c.tipo] ?? c.tipo })}
                      </div>
                    </div>
                  </TableCell>
                  <TableCell className="max-w-xs px-3 py-2">
                    {c.receptor ? (
                      <div className="flex flex-col overflow-hidden">
                        <span className="truncate leading-snug font-medium text-foreground">{c.receptor.razon_social}</span>
                        <span className="mt-0.5 font-mono text-[11px] text-muted-foreground/80">
                          {c.receptor.tipo_doc === "-" ? ETIQUETAS_TIPO_DOC["-"] : `${ETIQUETAS_TIPO_DOC[c.receptor.tipo_doc] ?? t.docGenerico} ${c.receptor.num_doc}`}
                        </span>
                      </div>
                    ) : (
                      <span className="text-[12px] text-muted-foreground/60">—</span>
                    )}
                  </TableCell>
                  <TableCell className="px-3 py-2 whitespace-nowrap">
                    <div className="leading-snug font-medium text-foreground/90">{formatearFecha(c.fecha_emision, locale)}</div>
                  </TableCell>
                  <TableCell className="px-4 py-2 text-right whitespace-nowrap">
                    <div className="font-mono text-[13px] font-semibold tracking-tight text-foreground tabular-nums">
                      {formatearMonto(c.moneda, c.totales.total, locale)}
                    </div>
                    <div className="mt-0.5 font-mono text-[11px] text-muted-foreground/80 tabular-nums">
                      {interpolar(t.igv, { monto: formatearMonto(c.moneda, c.totales.igv, locale) })}
                    </div>
                  </TableCell>
                  <TableCell className="px-4 py-2 whitespace-nowrap">
                    <EstadoBadge estado={c.estado_documento} etiqueta={etiquetaEstado(c, t)} />
                  </TableCell>
                  <TableCell className="py-2 pr-4 pl-2" onClick={(e) => e.stopPropagation()}>
                    <div className="flex items-center justify-end gap-1 opacity-80 transition-opacity group-hover:opacity-100">
                      {c.enlaces?.pdf ? (
                        <a
                          href={`/api/proxy/facturas/${c.id}/pdf`}
                          target="_blank"
                          rel="noopener"
                          title={t.verPdf}
                          className={cn(ACCION, "px-1.5 text-muted-foreground hover:bg-secondary hover:text-foreground")}
                        >
                          {t.pdf}
                        </a>
                      ) : (
                        <button disabled title={t.pdfNoDisponible} className={cn(ACCION, "cursor-not-allowed px-1.5 text-muted-foreground/60")}>
                          {t.pdf}
                        </button>
                      )}
                      <a
                        href={`/api/proxy/facturas/${c.id}/xml`}
                        title={t.descargarXml}
                        className={cn(ACCION, "px-1.5 text-muted-foreground hover:bg-secondary hover:text-foreground")}
                      >
                        {t.xml}
                      </a>
                      {tieneConstanciaCdr(c) ? (
                        <a
                          href={`/api/proxy/facturas/${c.id}/cdr`}
                          title={t.descargarCdr}
                          className={cn(ACCION, "w-control text-muted-foreground hover:bg-secondary hover:text-foreground")}
                        >
                          <FileTextIcon className="size-3.5" />
                        </a>
                      ) : null}
                      {sePuedeDuplicar(c) ? <DuplicarButton id={c.id} numero={numeroCompleto} compacto /> : null}
                    </div>
                  </TableCell>
                </TableRow>
              );
            })}
            {data.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={6} className="py-14 text-center">
                  <div className="flex flex-col items-center gap-2 text-muted-foreground">
                    <TrayIcon className="size-6" />
                    <p className="text-sm">
                      {hayFiltros ? t.vacioConFiltros : t.vacio}
                    </p>
                    {hayFiltros ? (
                      <Link href="/comprobantes" className="text-xs text-primary hover:underline">
                        {t.quitarFiltros}
                      </Link>
                    ) : null}
                  </div>
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>

        <PieTabla
          desde={primerResultado}
          hasta={ultimoResultado}
          total={total}
          unidad={t.unidad}
          porPagina={porPagina}
          onPorPagina={(n) => irA(1, n)}
          nota={t.notaPlazo}
          pagina={pagina}
          ultimaPagina={ultimaPagina}
          onPagina={irA}
          hrefPagina={hrefPagina}
        />
      </div>
    </div>
  );
}
