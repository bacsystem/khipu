"use client";

import { useQuery } from "@tanstack/react-query";
import {
  CalendarIcon,
  ChevronDownIcon,
  FileCheck2Icon,
  InboxIcon,
  MoreHorizontalIcon,
  RefreshCwIcon,
} from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PieTabla } from "@/components/ui/pie-tabla";
import { esClickSimple } from "@/lib/navegacion";
import { POR_PAGINA_DEFECTO } from "@/lib/paginacion";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  esEstadoFinal,
  ETIQUETAS_TIPO,
  ETIQUETAS_TIPO_DOC,
  normalizarComprobante,
  tieneConstanciaCdr,
  totalDesdeHeaders,
  type Comprobante,
  type EstadoDocumento,
  type PaginaComprobantes,
} from "@/lib/api/facturas";
import { formatearFecha, formatearMonto } from "@/lib/formato";
import { cn } from "@/lib/utils";
import { BotonCopiar } from "@/components/ui/boton-copiar";
import { ETIQUETAS_ESTADO, EstadoBadge } from "./estado-badge";

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
  "INVALIDO",
];

const TODOS_LOS_ESTADOS = "todos";

const ITEMS_ESTADO: Record<string, string> = {
  [TODOS_LOS_ESTADOS]: "Estado SUNAT: Todos",
  ...Object.fromEntries(ESTADOS.map((e) => [e, ETIQUETAS_ESTADO[e]])),
};

const TABS_DESHABILITADOS = ["Facturas", "Boletas", "Notas de crédito"];

const CONTROL = "h-9 rounded-lg border border-border bg-card text-[12px] font-medium text-foreground shadow-2xs";
const CONTROL_DESHABILITADO = "cursor-not-allowed text-muted-foreground opacity-70";
const ACCION = "flex h-6 items-center justify-center rounded border border-border font-mono text-[10px] font-semibold shadow-2xs transition-colors";

async function fetchComprobantes(estado: string | undefined, pagina: number, porPagina: number): Promise<PaginaComprobantes> {
  const qs = new URLSearchParams({ pagina: String(pagina), por_pagina: String(porPagina) });
  if (estado) qs.set("estado", estado);
  const res = await fetch(`/api/proxy/facturas?${qs}`);
  const json = await res.json();
  if (json.estado !== "exito") throw new Error(json.mensaje ?? "Error al listar comprobantes");
  const datos = (json.datos as Comprobante[]).map(normalizarComprobante);
  return { datos, total: totalDesdeHeaders(res.headers, datos.length) };
}


function etiquetaEstado(c: Comprobante): string | undefined {
  if (c.estado_documento === "ACEPTADO" && tieneConstanciaCdr(c)) return "Aceptado con CDR";
  if (c.estado_documento === "RECHAZADO" && c.cdr?.codigo) return `Rechazado (${c.cdr.codigo})`;
  return undefined;
}

export function ComprobantesTable({
  inicial,
  pagina,
  porPagina,
}: {
  inicial: PaginaComprobantes;
  pagina: number;
  porPagina: number;
}) {
  const router = useRouter();
  const params = useSearchParams();
  const [estado, setEstado] = useState<EstadoDocumento | undefined>(undefined);

  const { data: paginaActual, isFetching, refetch } = useQuery({
    queryKey: ["facturas", estado ?? null, pagina, porPagina],
    queryFn: () => fetchComprobantes(estado, pagina, porPagina),
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

  function cambiarEstado(value: string | null) {
    setEstado(!value || value === TODOS_LOS_ESTADOS ? undefined : (value as EstadoDocumento));
    irA(1);
  }

  function hrefPagina(p: number) {
    const next = new URLSearchParams(params.toString());
    conPagina(next, p);
    const qs = next.toString();
    return qs ? `/comprobantes?${qs}` : "/comprobantes";
  }

  const ultimaPagina = Math.max(1, Math.ceil(total / porPagina));
  const desde = data.length === 0 ? 0 : (pagina - 1) * porPagina + 1;
  const hasta = (pagina - 1) * porPagina + data.length;

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="inline-flex h-9 shrink-0 items-center gap-1 rounded-lg border border-border/60 bg-secondary/80 p-1">
          <span className="inline-flex h-7 items-center rounded-md bg-card px-3 text-[12px] font-medium text-foreground shadow-2xs">
            Todos
          </span>
          {TABS_DESHABILITADOS.map((tab) => (
            <button
              key={tab}
              type="button"
              disabled
              title="Filtro por tipo de comprobante: próximamente"
              className="inline-flex h-7 items-center rounded-md px-3 text-[12px] font-medium text-muted-foreground disabled:cursor-not-allowed"
            >
              {tab}
            </button>
          ))}
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <Select items={ITEMS_ESTADO} value={estado ?? TODOS_LOS_ESTADOS} onValueChange={cambiarEstado}>
            <SelectTrigger className={cn(CONTROL, "w-auto min-w-44 pl-3")}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={TODOS_LOS_ESTADOS}>Estado SUNAT: Todos</SelectItem>
              {ESTADOS.map((e) => (
                <SelectItem key={e} value={e}>
                  {ETIQUETAS_ESTADO[e]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <button
            type="button"
            disabled
            title="Filtro por serie: próximamente"
            className={cn(CONTROL, CONTROL_DESHABILITADO, "inline-flex items-center gap-1.5 pr-2.5 pl-3")}
          >
            Serie: Todas
            <ChevronDownIcon className="size-4 text-muted-foreground/70" />
          </button>

          <button
            type="button"
            disabled
            title="Filtro por período: próximamente"
            className={cn(CONTROL, CONTROL_DESHABILITADO, "inline-flex items-center gap-1.5 px-3")}
          >
            <CalendarIcon className="size-4 text-muted-foreground/70" />
            Período: Todos
            <ChevronDownIcon className="size-4 text-muted-foreground/70" />
          </button>

          <button
            type="button"
            onClick={() => refetch()}
            title="Refrescar lista"
            className={cn(CONTROL, "inline-flex size-9 items-center justify-center text-muted-foreground transition-colors hover:text-foreground")}
          >
            <RefreshCwIcon className={cn("size-4", isFetching && "animate-spin")} />
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
              <TableHead className="h-auto w-8 py-2 pr-2 pl-4">
                <input
                  type="checkbox"
                  disabled
                  title="Selección múltiple: próximamente"
                  className="size-3.5 cursor-not-allowed rounded border-input"
                />
              </TableHead>
              <TableHead className="h-auto px-3 py-2 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">Comprobante</TableHead>
              <TableHead className="h-auto px-3 py-2 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">Cliente / Receptor</TableHead>
              <TableHead className="h-auto px-3 py-2 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">Fecha & Emisión</TableHead>
              <TableHead className="h-auto px-4 py-2 text-right text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">Importe Total</TableHead>
              <TableHead className="h-auto px-4 py-2 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">Estado SUNAT</TableHead>
              <TableHead className="h-auto py-2 pr-4 pl-2 text-right text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">Acciones</TableHead>
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
                  <TableCell className="py-2 pr-2 pl-4" onClick={(e) => e.stopPropagation()}>
                    <input
                      type="checkbox"
                      disabled
                      title="Selección múltiple: próximamente"
                      className="size-3.5 cursor-not-allowed rounded border-input"
                    />
                  </TableCell>
                  <TableCell className="px-3 py-2">
                    <div className="flex flex-col">
                      <div className="flex items-center gap-1.5">
                        <Link
                          href={`/comprobantes/${c.id}`}
                          aria-label={`Ver comprobante ${numeroCompleto}, estado ${ETIQUETAS_ESTADO[c.estado_documento]}`}
                          onClick={(e) => e.stopPropagation()}
                          className="rounded font-mono text-[13px] font-semibold tracking-tight text-foreground outline-none hover:underline focus-visible:ring-2 focus-visible:ring-ring"
                        >
                          {numeroCompleto}
                        </Link>
                        <BotonCopiar texto={numeroCompleto} titulo="Copiar número" className="opacity-0 group-hover:opacity-100" />
                        {c.moneda !== "PEN" ? (
                          <span className="rounded border border-warning-border bg-warning px-1.5 py-0.5 font-mono text-[10px] font-medium text-warning-foreground uppercase">
                            {c.moneda}
                          </span>
                        ) : null}
                      </div>
                      <div className="mt-0.5 flex items-center gap-1 text-[11px] text-muted-foreground/80">
                        <span className="size-1.5 rounded-full bg-border" />
                        {ETIQUETAS_TIPO[c.tipo] ?? c.tipo} electrónica
                      </div>
                    </div>
                  </TableCell>
                  <TableCell className="max-w-xs px-3 py-2">
                    {c.receptor ? (
                      <div className="flex flex-col overflow-hidden">
                        <span className="truncate leading-snug font-medium text-foreground">{c.receptor.razon_social}</span>
                        <span className="mt-0.5 font-mono text-[11px] text-muted-foreground/80">
                          {ETIQUETAS_TIPO_DOC[c.receptor.tipo_doc] ?? "Doc."} {c.receptor.num_doc}
                        </span>
                      </div>
                    ) : (
                      <span className="text-[12px] text-muted-foreground/60">—</span>
                    )}
                  </TableCell>
                  <TableCell className="px-3 py-2 whitespace-nowrap">
                    <div className="leading-snug font-medium text-foreground/90">{formatearFecha(c.fecha_emision)}</div>
                  </TableCell>
                  <TableCell className="px-4 py-2 text-right whitespace-nowrap">
                    <div className="font-mono text-[13px] font-semibold tracking-tight text-foreground tabular-nums">
                      {formatearMonto(c.moneda, c.totales.total)}
                    </div>
                    <div className="mt-0.5 font-mono text-[11px] text-muted-foreground/80 tabular-nums">
                      IGV {formatearMonto(c.moneda, c.totales.igv)}
                    </div>
                  </TableCell>
                  <TableCell className="px-4 py-2 whitespace-nowrap">
                    <EstadoBadge estado={c.estado_documento} etiqueta={etiquetaEstado(c)} />
                  </TableCell>
                  <TableCell className="py-2 pr-4 pl-2" onClick={(e) => e.stopPropagation()}>
                    <div className="flex items-center justify-end gap-1 opacity-80 transition-opacity group-hover:opacity-100">
                      <button
                        disabled
                        title="Descargar PDF: próximamente"
                        className={cn(ACCION, "cursor-not-allowed px-1.5 text-muted-foreground/60")}
                      >
                        PDF
                      </button>
                      <a
                        href={`/api/proxy/facturas/${c.id}/xml`}
                        title="Descargar XML firmado"
                        className={cn(ACCION, "px-1.5 text-muted-foreground hover:bg-secondary hover:text-foreground")}
                      >
                        XML
                      </a>
                      {tieneConstanciaCdr(c) ? (
                        <a
                          href={`/api/proxy/facturas/${c.id}/cdr`}
                          title="Descargar constancia CDR"
                          className={cn(ACCION, "w-6 text-muted-foreground hover:bg-secondary hover:text-foreground")}
                        >
                          <FileCheck2Icon className="size-3.5" />
                        </a>
                      ) : null}
                      <button
                        disabled
                        title="Más acciones: próximamente"
                        className="flex size-6 cursor-not-allowed items-center justify-center rounded text-muted-foreground/60"
                      >
                        <MoreHorizontalIcon className="size-4" />
                      </button>
                    </div>
                  </TableCell>
                </TableRow>
              );
            })}
            {data.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={7} className="py-14 text-center">
                  <div className="flex flex-col items-center gap-2 text-muted-foreground">
                    <InboxIcon className="size-6" />
                    <p className="text-sm">
                      {estado
                        ? `No hay comprobantes en estado "${ETIQUETAS_ESTADO[estado]}".`
                        : "Todavía no emitiste ningún comprobante."}
                    </p>
                  </div>
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>

        <PieTabla
          desde={desde}
          hasta={hasta}
          total={total}
          unidad="comprobantes"
          porPagina={porPagina}
          onPorPagina={(n) => irA(1, n)}
          nota="Plazo máx. envío SUNAT: 3 días calendario"
          pagina={pagina}
          ultimaPagina={ultimaPagina}
          onPagina={irA}
          hrefPagina={hrefPagina}
        />
      </div>
    </div>
  );
}
