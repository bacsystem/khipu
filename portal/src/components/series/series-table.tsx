"use client";

import { InboxIcon, RefreshCwIcon } from "lucide-react";
import { useMessages } from "next-intl";
import { useRouter } from "next/navigation";
import { useMemo, useState, useTransition } from "react";
import { BotonCopiar } from "khipu-design-system/patrones/boton-copiar";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "khipu-design-system/ui/select";
import { PieTabla } from "khipu-design-system/patrones/pie-tabla";
import { POR_PAGINA_DEFECTO } from "khipu-design-system/lib/paginacion";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { etiquetasTipo } from "@/lib/api/facturas";
import type { Serie } from "@/lib/api/series";
import { cn } from "@/lib/utils";
import { interpolar } from "@/i18n/mensajes";
import { CABECERA_TABLA, CONTROL_FILTRO } from "@/lib/estilos";

const TODOS = "todos";

const TIPOS_FILTRO = ["01", "03", "04", "07", "08", "09", "20", "31", "40"] as const;

function mascara(serie: string): string {
  return serie.replace(/[0-9]/g, "#");
}

function numero(n: number): string {
  return String(n).padStart(8, "0");
}


export function SeriesTable({ series }: { series: Serie[] }) {
  const messages = useMessages();
  const t = messages.series.tabla;
  const ETIQUETAS_TIPO = etiquetasTipo(messages);
  const router = useRouter();
  const [refrescando, startTransition] = useTransition();
  const [estado, setEstado] = useState(TODOS);
  const [tipo, setTipo] = useState(TODOS);
  const [pagina, setPagina] = useState(1);
  const [porPagina, setPorPagina] = useState<number>(POR_PAGINA_DEFECTO);

  // La API devuelve todas las series del tenant (son pocas), así que filtro y pagino en el cliente.
  const filtradas = useMemo(
    () =>
      series.filter((s) => {
        if (estado === "activa" && !s.activa) return false;
        if (estado === "inactiva" && s.activa) return false;
        if (tipo !== TODOS && s.tipo !== tipo) return false;
        return true;
      }),
    [series, estado, tipo],
  );

  const itemsEstado = useMemo<Record<string, string>>(() => ({ [TODOS]: t.estadoTodas, activa: t.activas, inactiva: t.inactivas }), [t]);
  const itemsTipo = useMemo<Record<string, string>>(
    () => ({ [TODOS]: t.tipoTodos, ...Object.fromEntries(TIPOS_FILTRO.map((tipo) => [tipo, `${tipo} · ${ETIQUETAS_TIPO[tipo] ?? tipo}`])) }),
    [t, ETIQUETAS_TIPO],
  );
  const subtitulos: Record<string, string> = t.subtitulos;

  const total = filtradas.length;
  const ultimaPagina = Math.max(1, Math.ceil(total / porPagina));
  const paginaActual = Math.min(pagina, ultimaPagina);
  const data = filtradas.slice((paginaActual - 1) * porPagina, paginaActual * porPagina);
  const desde = data.length === 0 ? 0 : (paginaActual - 1) * porPagina + 1;
  const hasta = (paginaActual - 1) * porPagina + data.length;

  function cambiarEstado(value: string | null) {
    setEstado(value ?? TODOS);
    setPagina(1);
  }

  function cambiarTipo(value: string | null) {
    setTipo(value ?? TODOS);
    setPagina(1);
  }

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      {/* Solo filtros que funcionan (C6): las pestañas por tipo, el período, la selección múltiple y editar/historial/más acciones se
          veían deshabilitados. El tipo se filtra con su selector. */}
      <div className="flex flex-wrap items-center justify-end gap-3">
        <div className="flex flex-wrap items-center gap-2.5">
          <Select items={itemsEstado} value={estado} onValueChange={cambiarEstado}>
            <SelectTrigger className={cn(CONTROL_FILTRO, "w-auto min-w-36 pl-3")}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(itemsEstado).map(([valor, etiqueta]) => (
                <SelectItem key={valor} value={valor}>
                  {etiqueta}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select items={itemsTipo} value={tipo} onValueChange={cambiarTipo}>
            <SelectTrigger className={cn(CONTROL_FILTRO, "w-auto min-w-32 pl-3")}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(itemsTipo).map(([valor, etiqueta]) => (
                <SelectItem key={valor} value={valor}>
                  {etiqueta}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <button
            type="button"
            onClick={() => startTransition(() => router.refresh())}
            title={t.refrescar}
            className={cn(CONTROL_FILTRO, "inline-flex size-control items-center justify-center text-muted-foreground transition-colors hover:text-foreground")}
          >
            <RefreshCwIcon className={cn("size-4", refrescando && "animate-spin")} />
          </button>
        </div>
      </div>

      <div className={cn("min-w-0 overflow-hidden rounded-xl border border-border/90 bg-card shadow-2xs transition-opacity", refrescando && "opacity-60")}>
        <Table>
          <TableHeader>
            <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
              <TableHead className={cn(CABECERA_TABLA, "pl-4")}>{t.columnas.tipo}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.codigo}</TableHead>
              <TableHead className={cn(CABECERA_TABLA, "px-4 text-right")}>{t.columnas.ultimoNumero}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.establecimiento}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.formato}</TableHead>
              <TableHead className={cn(CABECERA_TABLA, "px-4")}>{t.columnas.estado}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-[13px]">
            {data.map((s) => (
              <TableRow key={`${s.tipo}-${s.serie}`} className={cn("group border-b border-border/60 hover:bg-muted/80", !s.activa && "opacity-80")}>
                <TableCell className="py-2 pr-3 pl-4">
                  <div className="flex items-center gap-2">
                    <div
                      className={cn(
                        "flex size-7 shrink-0 items-center justify-center rounded font-mono text-[11px] font-semibold",
                        s.activa ? "bg-accent text-primary" : "bg-secondary text-muted-foreground",
                      )}
                    >
                      {s.tipo}
                    </div>
                    <div className="flex flex-col">
                      <span className="leading-snug font-semibold text-foreground">{interpolar(t.tipoElectronico, { tipo: ETIQUETAS_TIPO[s.tipo] ?? s.tipo })}</span>
                      <span className="mt-0.5 font-mono text-[11px] text-muted-foreground/80">{subtitulos[s.tipo] ?? t.comprobanteSunat}</span>
                    </div>
                  </div>
                </TableCell>
                <TableCell className="px-3 py-2">
                  <div className="flex items-center gap-1.5">
                    <span
                      className={cn(
                        "rounded px-2 py-0.5 font-mono text-[13px] font-semibold tracking-tight",
                        s.activa ? "bg-secondary text-primary" : "bg-muted text-muted-foreground",
                      )}
                    >
                      {s.serie}
                    </span>
                    <BotonCopiar texto={s.serie} titulo={t.copiar} className="opacity-0 group-hover:opacity-100" />
                  </div>
                </TableCell>
                <TableCell className="px-4 py-2 text-right whitespace-nowrap">
                  <div className={cn("font-mono text-[13px] font-semibold tracking-tight tabular-nums", s.ultimo_numero === 0 ? "text-muted-foreground" : "text-foreground")}>
                    {numero(s.ultimo_numero)}
                  </div>
                  <div className="mt-0.5 font-mono text-[11px] text-muted-foreground/80 tabular-nums">
                    {s.ultimo_numero === 0 ? t.sinEmisionesPrevias : interpolar(t.siguiente, { numero: numero(s.ultimo_numero + 1) })}
                  </div>
                </TableCell>
                <TableCell className="px-3 py-2 font-mono text-[12px] whitespace-nowrap">
                  <span className={cn("rounded px-1.5 py-0.5 font-medium", (s.establecimiento ?? "0000") === "0000" ? "bg-muted text-muted-foreground" : "bg-secondary text-primary")}>
                    {s.establecimiento ?? "0000"}
                  </span>
                </TableCell>
                <TableCell className="px-3 py-2 font-mono text-[11px] whitespace-nowrap text-muted-foreground">
                  <div className="flex items-center gap-1.5">
                    <span className="rounded bg-muted px-1.5 py-0.5 font-medium text-foreground">{mascara(s.serie)}</span>
                    <span>{interpolar(t.caracteres, { cantidad: s.serie.length })}</span>
                  </div>
                </TableCell>
                <TableCell className="px-4 py-2 whitespace-nowrap">
                  {s.activa ? (
                    <span className="inline-flex items-center gap-1.5 rounded-full border border-success-border bg-success px-2.5 py-0.5 font-mono text-[11px] font-medium text-success-foreground">
                      <span className="size-1.5 rounded-full bg-success-solid" />
                      {t.activa}
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-secondary px-2.5 py-0.5 font-mono text-[11px] font-medium text-muted-foreground">
                      <span className="size-1.5 rounded-full bg-muted-foreground/60" />
                      {t.inactiva}
                    </span>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {data.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={6} className="py-14 text-center">
                  <div className="flex flex-col items-center gap-2 text-muted-foreground">
                    <InboxIcon className="size-6" />
                    <p className="text-sm">
                      {series.length === 0 ? t.vacio : t.sinCoincidencias}
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
          unidad={t.unidad}
          porPagina={porPagina}
          onPorPagina={(n) => {
            setPorPagina(n);
            setPagina(1);
          }}
          nota={t.nota}
          pagina={paginaActual}
          ultimaPagina={ultimaPagina}
          onPagina={setPagina}
        />
      </div>
    </div>
  );
}
