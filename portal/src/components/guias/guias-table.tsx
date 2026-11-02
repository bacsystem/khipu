"use client";

import { TrayIcon } from "@phosphor-icons/react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useLocale, useMessages } from "next-intl";
import { EntradaFecha } from "khipu-design-system/formularios/entrada-fecha";
import { esClickSimple } from "khipu-design-system/lib/navegacion";
import { POR_PAGINA_DEFECTO } from "khipu-design-system/lib/paginacion";
import { CONTENEDOR_TABLA, FilaVacia, PieTabla } from "khipu-design-system/patrones/pie-tabla";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "khipu-design-system/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { BusquedaDelListado } from "@/components/compartidos/busqueda-del-listado";
import type { Locale } from "@/i18n/config";
import { interpolar } from "@/i18n/mensajes";
import { ESTADOS_DE_GUIA, numeroDeGuia, paramsDeFiltrosDeGuias, TIPOS_DE_GUIA, type FiltrosDeGuias, type Guia } from "@/lib/api/guias";
import { CABECERA_TABLA, CONTROL_FILTRO } from "@/lib/estilos";
import { formatearFecha, formatearPrecioUnitario } from "@/lib/formato";
import { cn } from "@/lib/utils";
import { EstadoGuiaBadge } from "./estado-guia-badge";

/**
 * El listado de guías (#39). El estado vive en la URL (filtros, página, tamaño): se puede compartir y recargar, y la página del servidor ya llega con los datos
 * de esa URL. Un clic en la fila abre la ficha; el número es un enlace real para abrirla en otra pestaña.
 */
const TODOS = "todos";

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

  /** Cambia un filtro en la URL (compartible) y vuelve a la página 1; vacío lo quita. Como en comprobantes: sin botón «Filtrar». */
  function cambiarFiltro(clave: keyof FiltrosDeGuias, valor: string | null | undefined) {
    const nuevos: FiltrosDeGuias = { ...filtros };
    const limpio = valor?.trim();
    if (limpio) nuevos[clave] = clave === "serie" ? limpio.toUpperCase() : limpio;
    else delete nuevos[clave];
    router.push(url(nuevos, 1, porPagina));
  }

  // Listas y no objetos: el orden de las opciones es el de la lista («31» no salta delante de «Todos»).
  const itemsEstado = [{ value: TODOS, label: t.filtros.todosLosEstados }, ...ESTADOS_DE_GUIA.map((e) => ({ value: e, label: t.estados[e] }))];
  const itemsTipo = [{ value: TODOS, label: t.filtros.todosLosTipos }, ...TIPOS_DE_GUIA.map((tipo) => ({ value: tipo, label: t.tipos[tipo] }))];

  return (
    <div className="flex flex-col gap-3">
      {/* La barra de filtros del design system (§12), la misma de comprobantes: cada filtro se aplica al cambiarlo. */}
      <div role="search" aria-label={t.filtros.etiqueta} className="flex flex-wrap items-center justify-end gap-2.5">
        <Select items={itemsEstado} value={filtros.estado ?? TODOS} onValueChange={(v) => cambiarFiltro("estado", v === TODOS ? null : v)}>
          <SelectTrigger className={cn(CONTROL_FILTRO, "w-auto min-w-40 pl-3")} aria-label={t.filtros.estado}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {itemsEstado.map(({ value, label }) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select items={itemsTipo} value={filtros.tipo ?? TODOS} onValueChange={(v) => cambiarFiltro("tipo", v === TODOS ? null : v)}>
          <SelectTrigger className={cn(CONTROL_FILTRO, "w-auto min-w-40 pl-3")} aria-label={t.filtros.tipo}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {itemsTipo.map(({ value, label }) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <BusquedaDelListado id="filtro-serie" etiqueta={t.filtros.serie} ayuda={t.filtros.serie} placeholder={t.filtros.seriePlaceholder} valor={filtros.serie ?? ""} onCambio={(v) => cambiarFiltro("serie", v)} ancho="w-28" maxLength={4} />
        <EntradaFecha id="filtro-desde" enBarra limpiable etiquetaLimpiar={t.filtros.quitarDesde} placeholder={t.filtros.desde} valor={filtros.desde} max={filtros.hasta} onCambio={(v) => cambiarFiltro("desde", v)} className="w-40" />
        <EntradaFecha id="filtro-hasta" enBarra limpiable etiquetaLimpiar={t.filtros.quitarHasta} placeholder={t.filtros.hasta} valor={filtros.hasta} min={filtros.desde} onCambio={(v) => cambiarFiltro("hasta", v)} className="w-40" />
        {hayFiltros ? (
          <Link href="/guias" className="flex h-control items-center px-1 text-control text-muted-foreground underline-offset-2 hover:text-foreground hover:underline">
            {t.filtros.limpiar}
          </Link>
        ) : null}
      </div>

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
