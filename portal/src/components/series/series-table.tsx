"use client";

import {
  CalendarIcon,
  ChevronDownIcon,
  InboxIcon,
  MoreHorizontalIcon,
  PencilIcon,
  ReceiptTextIcon,
  RefreshCwIcon,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useMemo, useState, useTransition } from "react";
import { BotonCopiar } from "@/components/ui/boton-copiar";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PieTabla } from "@/components/ui/pie-tabla";
import { POR_PAGINA_DEFECTO } from "@/lib/paginacion";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ETIQUETAS_TIPO } from "@/lib/api/facturas";
import type { Serie } from "@/lib/api/series";
import { cn } from "@/lib/utils";

const TODOS = "todos";

const ITEMS_ESTADO: Record<string, string> = {
  [TODOS]: "Estado: Todas",
  activa: "Activas",
  inactiva: "Inactivas",
};

const TIPOS_FILTRO = ["01", "03", "07", "08"] as const;
const ITEMS_TIPO: Record<string, string> = {
  [TODOS]: "Tipo: Todos",
  ...Object.fromEntries(TIPOS_FILTRO.map((t) => [t, `${t} · ${ETIQUETAS_TIPO[t] ?? t}`])),
};

const SUBTITULO_TIPO: Record<string, string> = {
  "01": "UBL 2.1 · Tributario",
  "03": "Consumidor final",
  "07": "Afecta facturas o boletas",
  "08": "Penalidades / ajustes",
};

const TABS_DESHABILITADOS = ["Facturas", "Boletas", "Notas de crédito"];

const CONTROL = "h-9 rounded-lg border border-border bg-card text-[12px] font-medium text-foreground shadow-2xs";
const CONTROL_DESHABILITADO = "cursor-not-allowed text-muted-foreground opacity-70";
const CABECERA = "h-auto px-3 py-2 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase";
const ACCION = "flex size-6 cursor-not-allowed items-center justify-center rounded text-muted-foreground/60";

function mascara(serie: string): string {
  return serie.replace(/[0-9]/g, "#");
}

function numero(n: number): string {
  return String(n).padStart(8, "0");
}


export function SeriesTable({ series }: { series: Serie[] }) {
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
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="inline-flex h-9 shrink-0 items-center gap-1 rounded-lg border border-border/60 bg-secondary/80 p-1">
          <span className="inline-flex h-7 items-center rounded-md bg-card px-3 text-[12px] font-medium text-foreground shadow-2xs">Todas</span>
          {TABS_DESHABILITADOS.map((tab) => (
            <button
              key={tab}
              type="button"
              disabled
              title="Pestañas por tipo de comprobante: próximamente (usa el filtro Tipo)"
              className="inline-flex h-7 items-center rounded-md px-3 text-[12px] font-medium text-muted-foreground disabled:cursor-not-allowed"
            >
              {tab}
            </button>
          ))}
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <Select items={ITEMS_ESTADO} value={estado} onValueChange={cambiarEstado}>
            <SelectTrigger className={cn(CONTROL, "w-auto min-w-36 pl-3")}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(ITEMS_ESTADO).map(([valor, etiqueta]) => (
                <SelectItem key={valor} value={valor}>
                  {etiqueta}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select items={ITEMS_TIPO} value={tipo} onValueChange={cambiarTipo}>
            <SelectTrigger className={cn(CONTROL, "w-auto min-w-32 pl-3")}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(ITEMS_TIPO).map(([valor, etiqueta]) => (
                <SelectItem key={valor} value={valor}>
                  {etiqueta}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <button
            type="button"
            disabled
            title="Filtro por período de creación: próximamente"
            className={cn(CONTROL, CONTROL_DESHABILITADO, "inline-flex items-center gap-1.5 px-3")}
          >
            <CalendarIcon className="size-4 text-muted-foreground/70" />
            Período: Todos
            <ChevronDownIcon className="size-4 text-muted-foreground/70" />
          </button>

          <button
            type="button"
            onClick={() => startTransition(() => router.refresh())}
            title="Refrescar lista"
            className={cn(CONTROL, "inline-flex size-9 items-center justify-center text-muted-foreground transition-colors hover:text-foreground")}
          >
            <RefreshCwIcon className={cn("size-4", refrescando && "animate-spin")} />
          </button>
        </div>
      </div>

      <div className={cn("min-w-0 overflow-hidden rounded-xl border border-border/90 bg-card shadow-2xs transition-opacity", refrescando && "opacity-60")}>
        <Table>
          <TableHeader>
            <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
              <TableHead className="h-auto w-8 py-2 pr-2 pl-4">
                <input type="checkbox" disabled title="Selección múltiple: próximamente" className="size-3.5 cursor-not-allowed rounded border-input" />
              </TableHead>
              <TableHead className={CABECERA}>Tipo de comprobante</TableHead>
              <TableHead className={CABECERA}>Código serie</TableHead>
              <TableHead className={cn(CABECERA, "px-4 text-right")}>Último número (correlativo)</TableHead>
              <TableHead className={CABECERA}>Establecimiento</TableHead>
              <TableHead className={CABECERA}>Formato / longitud</TableHead>
              <TableHead className={cn(CABECERA, "px-4")}>Estado</TableHead>
              <TableHead className={cn(CABECERA, "pr-4 pl-2 text-right")}>Acciones</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-[13px]">
            {data.map((s) => (
              <TableRow key={`${s.tipo}-${s.serie}`} className={cn("group border-b border-border/60 hover:bg-muted/80", !s.activa && "opacity-80")}>
                <TableCell className="py-2 pr-2 pl-4">
                  <input type="checkbox" disabled title="Selección múltiple: próximamente" className="size-3.5 cursor-not-allowed rounded border-input" />
                </TableCell>
                <TableCell className="px-3 py-2">
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
                      <span className="leading-snug font-semibold text-foreground">{ETIQUETAS_TIPO[s.tipo] ?? s.tipo} electrónica</span>
                      <span className="mt-0.5 font-mono text-[11px] text-muted-foreground/80">{SUBTITULO_TIPO[s.tipo] ?? "Comprobante SUNAT"}</span>
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
                    <BotonCopiar texto={s.serie} titulo="Copiar serie" className="opacity-0 group-hover:opacity-100" />
                  </div>
                </TableCell>
                <TableCell className="px-4 py-2 text-right whitespace-nowrap">
                  <div className={cn("font-mono text-[13px] font-semibold tracking-tight tabular-nums", s.ultimo_numero === 0 ? "text-muted-foreground" : "text-foreground")}>
                    {numero(s.ultimo_numero)}
                  </div>
                  <div className="mt-0.5 font-mono text-[11px] text-muted-foreground/80 tabular-nums">
                    {s.ultimo_numero === 0 ? "Sin emisiones previas" : `Siguiente: #${numero(s.ultimo_numero + 1)}`}
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
                    <span>({s.serie.length} caracteres)</span>
                  </div>
                </TableCell>
                <TableCell className="px-4 py-2 whitespace-nowrap">
                  {s.activa ? (
                    <span className="inline-flex items-center gap-1.5 rounded-full border border-success-border bg-success px-2.5 py-0.5 font-mono text-[11px] font-medium text-success-foreground">
                      <span className="size-1.5 rounded-full bg-success-solid" />
                      Activa
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-secondary px-2.5 py-0.5 font-mono text-[11px] font-medium text-muted-foreground">
                      <span className="size-1.5 rounded-full bg-muted-foreground/60" />
                      Inactiva
                    </span>
                  )}
                </TableCell>
                <TableCell className="py-2 pr-4 pl-2">
                  <div className="flex items-center justify-end gap-1 opacity-80 transition-opacity group-hover:opacity-100">
                    <button disabled title="Editar serie: próximamente" className={ACCION}>
                      <PencilIcon className="size-4" />
                    </button>
                    <button disabled title="Historial por serie: próximamente (requiere filtro por serie en la API)" className={ACCION}>
                      <ReceiptTextIcon className="size-4" />
                    </button>
                    <button disabled title="Más acciones: próximamente" className={ACCION}>
                      <MoreHorizontalIcon className="size-4" />
                    </button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
            {data.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={8} className="py-14 text-center">
                  <div className="flex flex-col items-center gap-2 text-muted-foreground">
                    <InboxIcon className="size-6" />
                    <p className="text-sm">
                      {series.length === 0 ? "Todavía no tienes series. Crea la primera con «Nueva serie»." : "No hay series con los filtros seleccionados."}
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
          unidad="series"
          porPagina={porPagina}
          onPorPagina={(n) => {
            setPorPagina(n);
            setPagina(1);
          }}
          nota="Los correlativos se asignan de forma atómica y secuencial por cada emisión"
          pagina={paginaActual}
          ultimaPagina={ultimaPagina}
          onPagina={setPagina}
        />
      </div>
    </div>
  );
}
