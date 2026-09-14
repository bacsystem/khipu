"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRightIcon, InboxIcon } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import type { MouseEvent } from "react";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { esEstadoFinal, type Comprobante, type EstadoDocumento } from "@/lib/api/facturas";
import { cn } from "@/lib/utils";
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
const POR_PAGINA = 20;

const ITEMS_ESTADO: Record<string, string> = {
  [TODOS_LOS_ESTADOS]: "Todos los estados",
  ...Object.fromEntries(ESTADOS.map((e) => [e, ETIQUETAS_ESTADO[e]])),
};

async function fetchComprobantes(estado: string | undefined, pagina: number): Promise<Comprobante[]> {
  const qs = new URLSearchParams({ pagina: String(pagina), por_pagina: String(POR_PAGINA) });
  if (estado) qs.set("estado", estado);
  const res = await fetch(`/api/proxy/facturas?${qs}`);
  const json = await res.json();
  if (json.estado !== "exito") throw new Error(json.mensaje ?? "Error al listar comprobantes");
  return json.datos as Comprobante[];
}

function esClickSimple(e: MouseEvent) {
  return e.button === 0 && !e.metaKey && !e.ctrlKey && !e.shiftKey && !e.altKey;
}

export function ComprobantesTable({
  inicial,
  estado,
  pagina,
}: {
  inicial: Comprobante[];
  estado?: EstadoDocumento;
  pagina: number;
}) {
  const router = useRouter();
  const params = useSearchParams();

  const { data, isFetching } = useQuery({
    queryKey: ["facturas", estado ?? null, pagina],
    queryFn: () => fetchComprobantes(estado, pagina),
    initialData: inicial,
    refetchInterval: (query) => {
      const rows = query.state.data ?? [];
      return rows.some((c) => !esEstadoFinal(c.estado_documento)) ? 10_000 : false;
    },
  });

  function conPagina(next: URLSearchParams, p: number) {
    if (p > 1) next.set("pagina", String(p));
    else next.delete("pagina");
  }

  function irA(nuevaPagina: number, nuevoEstado?: string) {
    const next = new URLSearchParams(params.toString());
    conPagina(next, nuevaPagina);
    if (nuevoEstado !== undefined) {
      if (nuevoEstado) next.set("estado", nuevoEstado);
      else next.delete("estado");
    }
    const qs = next.toString();
    router.push(qs ? `/comprobantes?${qs}` : "/comprobantes");
  }

  function hrefPagina(p: number) {
    const next = new URLSearchParams(params.toString());
    conPagina(next, p);
    const qs = next.toString();
    return qs ? `/comprobantes?${qs}` : "/comprobantes";
  }

  const hayAnterior = pagina > 1;
  const haySiguiente = data.length >= POR_PAGINA;

  return (
    <div>
      <div className="mb-4 flex items-center gap-3">
        <Select
          items={ITEMS_ESTADO}
          value={estado ?? TODOS_LOS_ESTADOS}
          onValueChange={(value) => irA(1, !value || value === TODOS_LOS_ESTADOS ? "" : value)}
        >
          <SelectTrigger className="w-56">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={TODOS_LOS_ESTADOS}>Todos los estados</SelectItem>
            {ESTADOS.map((e) => (
              <SelectItem key={e} value={e}>
                {ETIQUETAS_ESTADO[e]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className={cn("rounded-xl bg-card ring-1 ring-foreground/10 transition-opacity", isFetching && "opacity-60")}>
        <Table>
          <TableHeader>
            <TableRow className="bg-muted hover:bg-muted">
              <TableHead className="px-4 text-muted-foreground">Serie - número</TableHead>
              <TableHead className="px-4 text-muted-foreground">Fecha</TableHead>
              <TableHead className="px-4 text-right text-muted-foreground">Total</TableHead>
              <TableHead className="px-4 text-muted-foreground">Estado</TableHead>
              <TableHead className="px-4">
                <span className="sr-only">Ver detalle</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((c) => (
              <TableRow key={c.id} className="group relative">
                <TableCell className="px-4 py-2.5 font-mono">
                  <Link
                    href={`/comprobantes/${c.id}`}
                    aria-label={`Ver comprobante ${c.serie}-${String(c.numero).padStart(8, "0")}, estado ${ETIQUETAS_ESTADO[c.estado_documento]}`}
                    className="outline-none before:absolute before:inset-0 focus-visible:underline"
                  >
                    {c.serie}-{String(c.numero).padStart(8, "0")}
                  </Link>
                </TableCell>
                <TableCell className="px-4 py-2.5 text-muted-foreground">{c.fecha_emision}</TableCell>
                <TableCell className="px-4 py-2.5 text-right font-mono">
                  {c.moneda} {Number(c.totales.total).toFixed(2)}
                </TableCell>
                <TableCell className="px-4 py-2.5">
                  <EstadoBadge estado={c.estado_documento} />
                </TableCell>
                <TableCell className="px-4 py-2.5 text-right">
                  <ArrowRightIcon className="ml-auto size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5 group-hover:text-foreground" />
                </TableCell>
              </TableRow>
            ))}
            {data.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={5} className="py-14 text-center">
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
      </div>

      <Pagination className="mt-4 justify-end">
        <PaginationContent>
          <PaginationItem>
            <PaginationPrevious
              text="Anterior"
              href={hrefPagina(pagina - 1)}
              aria-disabled={!hayAnterior}
              tabIndex={hayAnterior ? undefined : -1}
              className={cn(!hayAnterior && "pointer-events-none opacity-50")}
              onClick={(e) => {
                if (!hayAnterior || !esClickSimple(e)) return;
                e.preventDefault();
                irA(pagina - 1);
              }}
            />
          </PaginationItem>
          <PaginationItem>
            <span className="px-2 text-sm text-muted-foreground">Página {pagina}</span>
          </PaginationItem>
          <PaginationItem>
            <PaginationNext
              text="Siguiente"
              href={hrefPagina(pagina + 1)}
              aria-disabled={!haySiguiente}
              tabIndex={haySiguiente ? undefined : -1}
              className={cn(!haySiguiente && "pointer-events-none opacity-50")}
              onClick={(e) => {
                if (!haySiguiente || !esClickSimple(e)) return;
                e.preventDefault();
                irA(pagina + 1);
              }}
            />
          </PaginationItem>
        </PaginationContent>
      </Pagination>
    </div>
  );
}
