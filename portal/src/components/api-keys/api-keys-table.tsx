"use client";

import { ArrowClockwiseIcon, KeyIcon, ProhibitIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useLocale, useMessages } from "next-intl";
import { useMemo, useState, useTransition } from "react";
import { BotonCopiar } from "khipu-design-system/patrones/boton-copiar";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "khipu-design-system/ui/select";
import { PieTabla } from "khipu-design-system/patrones/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import type { ApiKeyResumen } from "@/lib/api/api-keys";
import { apiRequest } from "@/lib/api/browser";
import { formatearFechaHora } from "@/lib/formato";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { POR_PAGINA_DEFECTO } from "khipu-design-system/lib/paginacion";
import { cn } from "@/lib/utils";
import { CABECERA_TABLA, CONTROL_FILTRO, TARJETA } from "@/lib/estilos";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";

const TODAS = "todas";

const ACCION =
  "inline-flex h-control items-center gap-1 rounded-lg px-2.5 text-control font-medium whitespace-nowrap transition-colors disabled:cursor-not-allowed disabled:opacity-60";

function Estado({ activa }: { activa: boolean }) {
  const t = useMessages().apiKeys.tabla;
  return activa ? (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-success-border bg-success px-2.5 py-0.5 font-mono text-etiqueta font-medium text-success-foreground">
      <span className="size-1.5 rounded-full bg-success-solid" />
      {t.activa}
    </span>
  ) : (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-secondary px-2.5 py-0.5 font-mono text-etiqueta font-medium text-muted-foreground">
      <span className="size-1.5 rounded-full bg-muted-foreground/60" />
      {t.revocada}
    </span>
  );
}

/** Acciones de una fila: "Revocar" pide confirmación en línea antes de llamar a la API (es irreversible). */
function Acciones({ apiKey }: { apiKey: ApiKeyResumen }) {
  const soloLectura = useSinPermisoDeConfigurar();
  const messages = useMessages();
  const t = messages.apiKeys.tabla;
  const mensajeError = useMensajeError();
  const router = useRouter();
  const [confirmando, setConfirmando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [revocando, startTransition] = useTransition();

  if (!apiKey.activa) return <span className="text-etiqueta text-muted-foreground/60">—</span>;

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
            <span className="mr-1 text-etiqueta text-muted-foreground">{t.confirmarRevocar}</span>
            <button type="button" disabled={revocando} onClick={revocar} className={cn(ACCION, "bg-destructive text-destructive-foreground shadow-xs hover:bg-destructive/90")}>
              {revocando ? <ArrowClockwiseIcon weight="bold" className="size-3.5 animate-spin" /> : <ProhibitIcon className="size-3.5" />}
              {t.siRevocar}
            </button>
            <button
              type="button"
              disabled={revocando}
              onClick={() => setConfirmando(false)}
              className={cn(ACCION, "border border-border bg-card text-foreground/80 hover:bg-muted")}
            >
              {messages.comun.cancelar}
            </button>
          </>
        ) : (
          // Sin «Regenerar» deshabilitado (C6): para rotar una llave se crea otra con «Crear API key» y se revoca esta.
          <button type="button" disabled={soloLectura !== null} title={soloLectura ?? undefined} onClick={() => setConfirmando(true)} className={cn(ACCION, "text-destructive-texto hover:bg-destructive-suave")}>
            <ProhibitIcon className="size-3.5" />
            {t.revocar}
          </button>
        )}
      </div>
      {error ? <span className="text-etiqueta text-destructive">{error}</span> : null}
    </div>
  );
}

export function ApiKeysTable({ apiKeys }: { apiKeys: ApiKeyResumen[] }) {
  const locale = useLocale();
  const t = useMessages().apiKeys.tabla;
  const itemsEstado: Record<string, string> = { [TODAS]: t.filtro.todas, activa: t.filtro.activas, revocada: t.filtro.revocadas };
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
            <KeyIcon className="size-4" />
          </div>
          <div className="min-w-0">
            <h2 className="text-cuerpo font-semibold text-foreground">{t.titulo}</h2>
            <p className="truncate font-mono text-etiqueta text-muted-foreground">{t.subtitulo}</p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <Select
            items={itemsEstado}
            value={estado}
            onValueChange={(v) => {
              setEstado(v ?? TODAS);
              setPagina(1);
            }}
          >
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

          <button
            type="button"
            onClick={() => startTransition(() => router.refresh())}
            title={t.refrescar}
            className={cn(CONTROL_FILTRO, "inline-flex size-control items-center justify-center text-muted-foreground transition-colors hover:text-foreground")}
          >
            <ArrowClockwiseIcon weight="bold" className={cn("size-4", refrescando && "animate-spin")} />
          </button>
        </div>
      </div>

      <div className={cn(TARJETA, "min-w-0 overflow-hidden transition-opacity", refrescando && "opacity-60")}>
        <Table>
          <TableHeader>
            <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
              <TableHead className={cn(CABECERA_TABLA, "pl-4")}>{t.columnas.llave}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.creada}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.revocada}</TableHead>
              <TableHead className={cn(CABECERA_TABLA, "px-4")}>{t.columnas.estado}</TableHead>
              <TableHead className={cn(CABECERA_TABLA, "pr-4 pl-2 text-right")}>{t.columnas.acciones}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-control">
            {data.map((k) => (
              <TableRow key={k.id} className={cn("group border-b border-border/60 hover:bg-muted/80", !k.activa && "opacity-80")}>
                <TableCell className="py-2 pr-3 pl-4">
                  <div className="flex items-center gap-1.5">
                    <span
                      className={cn(
                        "rounded px-2 py-0.5 font-mono text-cuerpo font-semibold tracking-tight",
                        k.activa ? "bg-secondary text-primary" : "bg-muted text-muted-foreground line-through",
                      )}
                    >
                      {k.prefijo}
                    </span>
                    <span className="font-mono text-control tracking-[0.2em] text-muted-foreground/50 select-none">••••••••••</span>
                    <BotonCopiar texto={k.prefijo} titulo={t.copiarPrefijo} className="opacity-0 group-hover:opacity-100" />
                  </div>
                  <div className="mt-0.5 font-mono text-etiqueta text-muted-foreground/80" title={k.id}>
                    {interpolar(t.id, { id: k.id.slice(0, 8) })}
                  </div>
                </TableCell>
                <TableCell className="px-3 py-2 font-mono text-control whitespace-nowrap text-foreground tabular-nums">{formatearFechaHora(k.creada_en, locale)}</TableCell>
                <TableCell className="px-3 py-2 font-mono text-control whitespace-nowrap text-muted-foreground tabular-nums">
                  {k.revocada_en ? formatearFechaHora(k.revocada_en, locale) : "—"}
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
                    <KeyIcon className="size-6" />
                    <p className="text-cuerpo">
                      {apiKeys.length === 0 ? t.vacia : t.sinResultados}
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
