"use client";

import { BanIcon, KeyRoundIcon, RefreshCwIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useMemo, useState, useTransition } from "react";
import { BotonCopiar } from "@/components/ui/boton-copiar";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PieTabla } from "@/components/ui/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { ApiKeyResumen } from "@/lib/api/api-keys";
import { apiRequest } from "@/lib/api/browser";
import { formatearFechaHora } from "@/lib/formato";
import { mensajeError } from "@/lib/messages";
import { POR_PAGINA_DEFECTO } from "@/lib/paginacion";
import { cn } from "@/lib/utils";

const TODAS = "todas";

const ITEMS_ESTADO: Record<string, string> = {
  [TODAS]: "Estado: Todas",
  activa: "Activas",
  revocada: "Revocadas",
};

const CONTROL = "h-9 rounded-lg border border-border bg-card text-[12px] font-medium text-foreground shadow-2xs";
const CABECERA = "h-auto px-3 py-2 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase";
const ACCION =
  "inline-flex h-7 items-center gap-1 rounded-md px-2 text-[12px] font-medium whitespace-nowrap transition-colors disabled:cursor-not-allowed disabled:opacity-60";

function Estado({ activa }: { activa: boolean }) {
  return activa ? (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-success-border bg-success px-2.5 py-0.5 font-mono text-[11px] font-medium text-success-foreground">
      <span className="size-1.5 rounded-full bg-success-solid" />
      Activa
    </span>
  ) : (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-secondary px-2.5 py-0.5 font-mono text-[11px] font-medium text-muted-foreground">
      <span className="size-1.5 rounded-full bg-muted-foreground/60" />
      Revocada
    </span>
  );
}

/** Acciones de una fila: "Revocar" pide confirmación en línea antes de llamar a la API (es irreversible). */
function Acciones({ apiKey }: { apiKey: ApiKeyResumen }) {
  const router = useRouter();
  const [confirmando, setConfirmando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [revocando, startTransition] = useTransition();

  if (!apiKey.activa) return <span className="text-[11px] text-muted-foreground/60">—</span>;

  function revocar() {
    setError(null);
    startTransition(async () => {
      const res = await apiRequest<null>(`/api/proxy/empresa/api-keys/${apiKey.id}`, { method: "DELETE" });
      if (res.estado !== "exito") {
        setError(mensajeError(res.codigo));
        return;
      }
      setConfirmando(false);
      router.refresh();
    });
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <div className="flex items-center justify-end gap-1">
        {confirmando ? (
          <>
            <span className="mr-1 text-[11px] text-muted-foreground">¿Revocar de forma permanente?</span>
            <button type="button" disabled={revocando} onClick={revocar} className={cn(ACCION, "bg-destructive text-white shadow-xs hover:bg-destructive/90")}>
              {revocando ? <RefreshCwIcon className="size-3.5 animate-spin" /> : <BanIcon className="size-3.5" />}
              Sí, revocar
            </button>
            <button
              type="button"
              disabled={revocando}
              onClick={() => setConfirmando(false)}
              className={cn(ACCION, "border border-border bg-card text-foreground/80 hover:bg-muted")}
            >
              Cancelar
            </button>
          </>
        ) : (
          <>
            <button
              type="button"
              disabled
              title="Regenerar (revocar y crear una nueva en un paso): próximamente. Hoy: crea una nueva y revoca esta."
              className={cn(ACCION, "text-muted-foreground")}
            >
              <RefreshCwIcon className="size-3.5" />
              Regenerar
            </button>
            <span className="text-border">|</span>
            <button type="button" onClick={() => setConfirmando(true)} className={cn(ACCION, "text-destructive hover:bg-destructive/10")}>
              <BanIcon className="size-3.5" />
              Revocar
            </button>
          </>
        )}
      </div>
      {error ? <span className="text-[11px] text-destructive">{error}</span> : null}
    </div>
  );
}

export function ApiKeysTable({ apiKeys }: { apiKeys: ApiKeyResumen[] }) {
  const router = useRouter();
  const [refrescando, startTransition] = useTransition();
  const [estado, setEstado] = useState(TODAS);
  const [pagina, setPagina] = useState(1);
  const [porPagina, setPorPagina] = useState<number>(POR_PAGINA_DEFECTO);

  // La API devuelve todas las llaves del tenant (son pocas), así que filtro y pagino en el cliente.
  const filtradas = useMemo(
    () =>
      apiKeys.filter((k) => {
        if (estado === "activa") return k.activa;
        if (estado === "revocada") return !k.activa;
        return true;
      }),
    [apiKeys, estado],
  );

  const total = filtradas.length;
  const ultimaPagina = Math.max(1, Math.ceil(total / porPagina));
  const paginaActual = Math.min(pagina, ultimaPagina);
  const data = filtradas.slice((paginaActual - 1) * porPagina, paginaActual * porPagina);
  const desde = data.length === 0 ? 0 : (paginaActual - 1) * porPagina + 1;
  const hasta = (paginaActual - 1) * porPagina + data.length;

  return (
    <div className="grid min-w-0 grid-cols-1 gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2.5">
          <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
            <KeyRoundIcon className="size-4" />
          </div>
          <div className="min-w-0">
            <h2 className="text-[13px] font-semibold text-foreground">Llaves de acceso</h2>
            <p className="truncate font-mono text-[11px] text-muted-foreground">Solo se guarda el hash · el secreto se muestra una vez, al crearla</p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <Select
            items={ITEMS_ESTADO}
            value={estado}
            onValueChange={(v) => {
              setEstado(v ?? TODAS);
              setPagina(1);
            }}
          >
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
              <TableHead className={cn(CABECERA, "pl-4")}>Llave</TableHead>
              <TableHead className={CABECERA}>Creada</TableHead>
              <TableHead className={CABECERA}>Revocada</TableHead>
              <TableHead className={cn(CABECERA, "px-4")}>Estado</TableHead>
              <TableHead className={cn(CABECERA, "pr-4 pl-2 text-right")}>Acciones</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-[13px]">
            {data.map((k) => (
              <TableRow key={k.id} className={cn("group border-b border-border/60 hover:bg-muted/80", !k.activa && "opacity-80")}>
                <TableCell className="py-2 pr-3 pl-4">
                  <div className="flex items-center gap-1.5">
                    <span
                      className={cn(
                        "rounded px-2 py-0.5 font-mono text-[13px] font-semibold tracking-tight",
                        k.activa ? "bg-secondary text-primary" : "bg-muted text-muted-foreground line-through",
                      )}
                    >
                      {k.prefijo}
                    </span>
                    <span className="font-mono text-[12px] tracking-[0.2em] text-muted-foreground/50 select-none">••••••••••</span>
                    <BotonCopiar texto={k.prefijo} titulo="Copiar prefijo" className="opacity-0 group-hover:opacity-100" />
                  </div>
                  <div className="mt-0.5 font-mono text-[11px] text-muted-foreground/80" title={k.id}>
                    id: {k.id.slice(0, 8)}
                  </div>
                </TableCell>
                <TableCell className="px-3 py-2 font-mono text-[12px] whitespace-nowrap text-foreground tabular-nums">{formatearFechaHora(k.creada_en)}</TableCell>
                <TableCell className="px-3 py-2 font-mono text-[12px] whitespace-nowrap text-muted-foreground tabular-nums">
                  {k.revocada_en ? formatearFechaHora(k.revocada_en) : "—"}
                </TableCell>
                <TableCell className="px-4 py-2 whitespace-nowrap">
                  <Estado activa={k.activa} />
                </TableCell>
                <TableCell className="py-2 pr-4 pl-2 text-right">
                  <Acciones apiKey={k} />
                </TableCell>
              </TableRow>
            ))}
            {data.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={5} className="py-14 text-center">
                  <div className="flex flex-col items-center gap-2 text-muted-foreground">
                    <KeyRoundIcon className="size-6" />
                    <p className="text-sm">
                      {apiKeys.length === 0 ? "Todavía no tienes API keys. Crea la primera con «Crear API key»." : "No hay llaves con el filtro seleccionado."}
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
          unidad="llaves"
          porPagina={porPagina}
          onPorPagina={(n) => {
            setPorPagina(n);
            setPagina(1);
          }}
          nota="Revocar es irreversible: la llave deja de autenticar al instante"
          pagina={paginaActual}
          ultimaPagina={ultimaPagina}
          onPagina={setPagina}
        />
      </div>
    </div>
  );
}
