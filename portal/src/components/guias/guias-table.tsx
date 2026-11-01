"use client";

import { TrayIcon } from "@phosphor-icons/react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useLocale, useMessages } from "next-intl";
import { type FormEvent } from "react";
import { esClickSimple } from "khipu-design-system/lib/navegacion";
import { POR_PAGINA_DEFECTO } from "khipu-design-system/lib/paginacion";
import { CONTENEDOR_TABLA, FilaVacia, PieTabla } from "khipu-design-system/patrones/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import type { Locale } from "@/i18n/config";
import { interpolar } from "@/i18n/mensajes";
import { ESTADOS_DE_GUIA, numeroDeGuia, paramsDeFiltrosDeGuias, TIPOS_DE_GUIA, type FiltrosDeGuias, type Guia } from "@/lib/api/guias";
import { CABECERA_TABLA, CAMPO_FILTRO, CONTROL_FILTRO, ETIQUETA_CAMPO, SELECT_NATIVO } from "@/lib/estilos";
import { formatearFecha, formatearPrecioUnitario } from "@/lib/formato";
import { cn } from "@/lib/utils";
import { EstadoGuiaBadge } from "./estado-guia-badge";

/**
 * El listado de guías (#39). El estado vive en la URL (filtros, página, tamaño): se puede compartir y recargar, y la página del servidor ya llega con los datos
 * de esa URL. Un clic en la fila abre la ficha; el número es un enlace real para abrirla en otra pestaña.
 */
export function GuiasTable({
  datos,
  total,
  pagina,
  porPagina,
  filtros,
}: {
  datos: Guia[];
  total: number;
  pagina: number;
  porPagina: number;
  filtros: FiltrosDeGuias;
}) {
  const messages = useMessages();
  const t = messages.guias;
  const locale = useLocale() as Locale;
  const router = useRouter();

  const hayFiltros = Object.values(filtros).some(Boolean);
  const ultimaPagina = Math.max(1, Math.ceil(total / porPagina));

  function url(f: FiltrosDeGuias, p: number, n: number): string {
    const qs = paramsDeFiltrosDeGuias(f);
    if (p > 1) qs.set("pagina", String(p));
    if (n !== POR_PAGINA_DEFECTO) qs.set("por_pagina", String(n));
    const texto = qs.toString();
    return texto ? `/guias?${texto}` : "/guias";
  }

  function filtrar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const datosForm = new FormData(e.currentTarget);
    const nuevos: FiltrosDeGuias = {};
    for (const clave of ["estado", "tipo", "serie", "desde", "hasta"] as const) {
      const valor = String(datosForm.get(clave) ?? "").trim();
      if (valor) nuevos[clave] = valor;
    }
    router.push(url(nuevos, 1, porPagina));
  }

  return (
    <div className="flex flex-col gap-3">
      {/* `key` con los filtros: al limpiar o al llegar por un enlace, el formulario no conserva lo que se había escrito. */}
      <form
        key={JSON.stringify(filtros)}
        role="search"
        aria-label={t.filtros.etiqueta}
        onSubmit={filtrar}
        className="flex flex-wrap items-end gap-3"
      >
        <label className="flex flex-col gap-1">
          <span className={ETIQUETA_CAMPO}>{t.filtros.estado}</span>
          <select name="estado" defaultValue={filtros.estado ?? ""} className={cn(CONTROL_FILTRO, SELECT_NATIVO, "min-w-40")}>
            <option value="">{t.filtros.todosLosEstados}</option>
            {ESTADOS_DE_GUIA.map((e) => (
              <option key={e} value={e}>
                {t.estados[e]}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1">
          <span className={ETIQUETA_CAMPO}>{t.filtros.tipo}</span>
          <select name="tipo" defaultValue={filtros.tipo ?? ""} className={cn(CONTROL_FILTRO, SELECT_NATIVO, "min-w-40")}>
            <option value="">{t.filtros.todosLosTipos}</option>
            {TIPOS_DE_GUIA.map((tipo) => (
              <option key={tipo} value={tipo}>
                {t.tipos[tipo]}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1">
          <span className={ETIQUETA_CAMPO}>{t.filtros.serie}</span>
          <input
            name="serie"
            defaultValue={filtros.serie ?? ""}
            placeholder={t.filtros.seriePlaceholder}
            maxLength={4}
            className={cn(CAMPO_FILTRO, "w-24 font-mono")}
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className={ETIQUETA_CAMPO}>{t.filtros.desde}</span>
          <input name="desde" type="date" defaultValue={filtros.desde ?? ""} className={cn(CAMPO_FILTRO, "w-40")} />
        </label>
        <label className="flex flex-col gap-1">
          <span className={ETIQUETA_CAMPO}>{t.filtros.hasta}</span>
          <input name="hasta" type="date" defaultValue={filtros.hasta ?? ""} className={cn(CAMPO_FILTRO, "w-40")} />
        </label>
        <button type="submit" className={cn(CONTROL_FILTRO, "px-4 font-medium transition-colors hover:bg-muted")}>
          {t.filtros.aplicar}
        </button>
        {hayFiltros ? (
          <Link href="/guias" className="flex h-control items-center text-control text-muted-foreground underline-offset-2 hover:text-foreground hover:underline">
            {t.filtros.limpiar}
          </Link>
        ) : null}
      </form>

      <div className={CONTENEDOR_TABLA}>
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className={cn(CABECERA_TABLA, "pl-4")}>{t.tabla.cabeceras.guia}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.tabla.cabeceras.fecha}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.tabla.cabeceras.destinatario}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.tabla.cabeceras.traslado}</TableHead>
              <TableHead className={cn(CABECERA_TABLA, "pr-4")}>{t.tabla.cabeceras.estado}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-control">
            {datos.length === 0 ? (
              <FilaVacia columnas={5} icon={<TrayIcon className="size-6" />}>
                {hayFiltros ? t.tabla.vacioFiltrado : t.tabla.vacio}
              </FilaVacia>
            ) : (
              datos.map((g) => {
                const numero = numeroDeGuia(g);
                return (
                  <TableRow
                    key={g.id}
                    onClick={(e) => {
                      if (esClickSimple(e)) router.push(`/guias/${g.id}`);
                    }}
                    className="cursor-pointer"
                  >
                    <TableCell className="py-2 pr-3 pl-4">
                      <div className="flex flex-col">
                        <Link
                          href={`/guias/${g.id}`}
                          aria-label={interpolar(t.tabla.verGuia, { numero })}
                          onClick={(e) => e.stopPropagation()}
                          className="w-fit rounded font-mono text-cuerpo font-semibold tracking-tight text-foreground outline-none hover:underline focus-visible:ring-2 focus-visible:ring-ring"
                        >
                          {numero}
                        </Link>
                        <span className="text-etiqueta text-muted-foreground/80">{t.tipos[g.tipo]}</span>
                      </div>
                    </TableCell>
                    <TableCell className="px-3 py-2 font-mono text-control text-muted-foreground">{formatearFecha(g.fecha_emision, locale)}</TableCell>
                    <TableCell className="max-w-xs px-3 py-2">
                      <div className="flex flex-col overflow-hidden">
                        <span className="truncate leading-snug font-medium text-foreground">{g.destinatario.nombre}</span>
                        <span className="truncate font-mono text-etiqueta text-muted-foreground/80">{g.destinatario.num_doc}</span>
                      </div>
                    </TableCell>
                    <TableCell className="px-3 py-2 text-control text-muted-foreground">
                      <div className="flex flex-col">
                        <span>{interpolar(t.tabla.inicio, { fecha: formatearFecha(g.traslado.fecha_inicio, locale) })}</span>
                        <span className="font-mono text-etiqueta">
                          {interpolar(t.tabla.peso, { peso: formatearPrecioUnitario(g.traslado.peso_bruto, locale), unidad: g.traslado.unidad_peso })}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell className="px-4 py-2">
                      <EstadoGuiaBadge estado={g.estado} />
                    </TableCell>
                  </TableRow>
                );
              })
            )}
          </TableBody>
        </Table>
      </div>

      {total > 0 ? (
        <PieTabla
          desde={(pagina - 1) * porPagina + 1}
          hasta={Math.min(total, (pagina - 1) * porPagina + datos.length)}
          total={total}
          unidad={t.tabla.unidad}
          porPagina={porPagina}
          onPorPagina={(n) => router.push(url(filtros, 1, n))}
          pagina={pagina}
          ultimaPagina={ultimaPagina}
          onPagina={(p) => router.push(url(filtros, p, porPagina))}
          hrefPagina={(p) => url(filtros, p, porPagina)}
        />
      ) : null}
    </div>
  );
}
