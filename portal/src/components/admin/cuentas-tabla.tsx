"use client";

import { InboxIcon, SearchIcon } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef } from "react";
import { PieTabla } from "@/components/ui/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { hrefCuentas, type CuentaAdmin, type ParamsCuentas } from "@/lib/api/admin-cuentas";
import { BOTON_SECUNDARIO, CAMPO } from "@/lib/estilos";
import { formatearFechaHora } from "@/lib/formato";
import { messages } from "@/lib/messages";

const TH = "h-auto px-3 py-2 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase";
const columnas = messages.admin.cuentas.columnas;

/**
 * Listado de cuentas del backoffice (#180), paginado y filtrado en el servidor: buscar, paginar y cambiar las filas por
 * página solo cambian la URL, y el Server Component de la página vuelve a renderizar. No hay refetch desde el navegador:
 * el proxy genérico usa las cookies del cliente, no la sesión del administrador.
 */
export function CuentasTabla({ datos, total, params }: { datos: CuentaAdmin[]; total: number; params: ParamsCuentas }) {
  const router = useRouter();
  const buscador = useRef<HTMLInputElement>(null);

  const ultimaPagina = Math.max(1, Math.ceil(total / params.porPagina));
  const primero = total === 0 ? 0 : (params.pagina - 1) * params.porPagina + 1;
  const ultimo = (params.pagina - 1) * params.porPagina + datos.length;

  function buscar(e: React.FormEvent) {
    e.preventDefault();
    const q = buscador.current?.value.trim();
    router.push(hrefCuentas({ q: q ? q : undefined, pagina: 1, porPagina: params.porPagina }));
  }

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      <div className="flex flex-wrap items-center gap-3">
        {/* `key`: al cambiar la búsqueda (p. ej. «Quitar filtros») el campo se vuelve a montar con el valor de la URL. */}
        <form key={params.q ?? ""} role="search" onSubmit={buscar} className="flex w-full max-w-xl items-center gap-2">
          <div className="relative min-w-0 flex-1">
            <SearchIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground/70" />
            <input
              ref={buscador}
              id="buscar-cuentas"
              type="search"
              name="q"
              defaultValue={params.q ?? ""}
              placeholder={messages.admin.cuentas.buscar}
              aria-label={messages.admin.cuentas.buscar}
              className={`${CAMPO} pl-9`}
            />
          </div>
          <button type="submit" className={BOTON_SECUNDARIO}>
            {messages.admin.cuentas.botonBuscar}
          </button>
        </form>
        {params.q ? (
          <Link href={hrefCuentas({ pagina: 1, porPagina: params.porPagina })} className="text-xs text-primary hover:underline">
            {messages.admin.cuentas.quitarFiltros}
          </Link>
        ) : null}
      </div>

      <div className="overflow-hidden rounded-xl border border-border/90 bg-card shadow-2xs">
        <Table>
          <TableHeader>
            <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
              <TableHead className={`${TH} pl-4`}>{columnas.cuenta}</TableHead>
              <TableHead className={TH}>{columnas.telefono}</TableHead>
              <TableHead className={`${TH} text-right`}>{columnas.empresas}</TableHead>
              <TableHead className={TH}>{columnas.alta}</TableHead>
              <TableHead className={`${TH} pr-4`}>{columnas.ultimoAcceso}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-[13px]">
            {datos.map((c) => (
              <TableRow key={c.id} className="border-b border-border/60 hover:bg-muted/80">
                <TableCell className="py-2 pr-3 pl-4">
                  <div className="flex flex-col">
                    <span className="font-medium text-foreground">{c.nombre}</span>
                    <span className="font-mono text-[11px] text-muted-foreground">{c.email}</span>
                  </div>
                </TableCell>
                <TableCell className="px-3 py-2 font-mono text-[12px] text-foreground/80">{c.telefono ?? "—"}</TableCell>
                <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{c.empresas}</TableCell>
                <TableCell className="px-3 py-2 text-[12px] text-foreground/80">{formatearFechaHora(c.creada_en)}</TableCell>
                <TableCell className="py-2 pr-4 pl-3 text-[12px] text-foreground/80">
                  {c.ultimo_acceso ? formatearFechaHora(c.ultimo_acceso) : <span className="text-muted-foreground">{messages.admin.cuentas.nunca}</span>}
                </TableCell>
              </TableRow>
            ))}
            {datos.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={5} className="py-14 text-center">
                  <div className="flex flex-col items-center gap-2 text-muted-foreground">
                    <InboxIcon className="size-6" />
                    <p className="text-sm">{params.q ? messages.admin.cuentas.sinResultados : messages.admin.cuentas.vacio}</p>
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
          onPorPagina={(n) => router.push(hrefCuentas({ q: params.q, pagina: 1, porPagina: n }))}
          pagina={params.pagina}
          ultimaPagina={ultimaPagina}
          onPagina={(p) => router.push(hrefCuentas({ ...params, pagina: p }))}
          hrefPagina={(p) => hrefCuentas({ ...params, pagina: p })}
        />
      </div>
    </div>
  );
}
