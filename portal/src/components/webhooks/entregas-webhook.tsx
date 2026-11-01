"use client";

import { ArrowClockwiseIcon } from "@phosphor-icons/react";
import { useLocale, useMessages } from "next-intl";
import { useCallback, useEffect, useRef, useState } from "react";
import { PieTabla } from "khipu-design-system/patrones/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { apiRequest } from "@/lib/api/browser";
import type { EntregaWebhook, EstadoEntrega } from "@/lib/api/webhooks";
import { listarEntregas } from "@/lib/api/webhooks-browser";
import { CABECERA_TABLA, CONTROL_FILTRO, TARJETA } from "@/lib/estilos";
import { formatearFechaHora } from "@/lib/formato";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { POR_PAGINA_DEFECTO } from "khipu-design-system/lib/paginacion";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";
import { cn } from "@/lib/utils";
import { etiquetaDeEvento, resultadoDelIntento } from "@/lib/webhooks/eventos";

const ESTILO_ESTADO: Record<EstadoEntrega, string> = {
  EXITOSA: "border-success-border bg-success text-success-foreground",
  PENDIENTE: "border-warning-border bg-warning text-warning-foreground",
  FALLIDA: "border-destructive/30 bg-destructive-suave text-destructive-texto",
};

function EstadoEntregaBadge({ estado }: { estado: EstadoEntrega }) {
  const etiquetas = useMessages().webhooks.estadoEntrega;
  return <span className={cn("inline-flex rounded-full border px-2.5 py-0.5 font-mono text-etiqueta font-medium", ESTILO_ESTADO[estado])}>{etiquetas[estado]}</span>;
}

/**
 * El log de entregas de un webhook (#19): cada evento mandado, su estado, los intentos y lo que respondió el servidor en el último. Se guarda 30 días. Una entrega cerrada
 * (entregada o fallida) se puede reenviar con el mismo cuerpo. `version` fuerza una lectura nueva (la prueba que se acaba de enviar).
 */
export function EntregasWebhook({ webhookId, version }: { webhookId: string; version: number }) {
  const soloLectura = useSinPermisoDeConfigurar();
  const messages = useMessages();
  const locale = useLocale();
  const t = messages.webhooks.entregas;
  const mensajeError = useMensajeError();
  const [estado, setEstado] = useState<EstadoEntrega | "">("");
  const [pagina, setPagina] = useState(1);
  const [porPagina, setPorPagina] = useState<number>(POR_PAGINA_DEFECTO);
  const [lectura, setLectura] = useState(0);
  const [entregas, setEntregas] = useState<EntregaWebhook[] | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [reenviando, setReenviando] = useState<string | null>(null);
  const [leyendo, setLeyendo] = useState(false);
  // Solo vale la respuesta de la última lectura: cambiar de filtro o de página rápido no debe mostrar lo de una anterior que llegó tarde.
  const ultimaLectura = useRef(0);

  const cargar = useCallback(async () => {
    const mia = ++ultimaLectura.current;
    setLeyendo(true);
    const res = await listarEntregas(webhookId, { estado: estado || undefined, pagina, porPagina }, messages);
    if (mia !== ultimaLectura.current) return;
    setLeyendo(false);
    if (res.estado === "exito") {
      setEntregas(res.datos.entregas);
      setTotal(res.datos.total);
      setError(null);
    } else {
      setError(res.mensaje);
    }
  }, [webhookId, estado, pagina, porPagina, messages]);

  useEffect(() => {
    // La lectura es asíncrona: el estado se escribe cuando responde, no durante el efecto.
    void cargar();
  }, [cargar, version, lectura]);

  async function reenviar(id: string) {
    setReenviando(id);
    setError(null);
    const res = await apiRequest<null>(`/api/proxy/webhooks/entregas/${id}/reenviar`, { method: "POST" });
    setReenviando(null);
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setLectura((n) => n + 1);
  }

  const ultimaPagina = Math.max(1, Math.ceil(total / porPagina));
  const paginaActual = Math.min(pagina, ultimaPagina);
  const desde = entregas && entregas.length > 0 ? (paginaActual - 1) * porPagina + 1 : 0;
  const hasta = (paginaActual - 1) * porPagina + (entregas?.length ?? 0);

  return (
    <div className="grid gap-3 border-t border-border/70 px-4 py-3" data-testid="entregas-webhook">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-control font-semibold text-foreground">{t.titulo}</h3>
        <div className="flex items-center gap-2">
          <select
            aria-label={t.filtrar}
            value={estado}
            onChange={(e) => {
              setEstado(e.target.value as EstadoEntrega | "");
              setPagina(1);
            }}
            className={cn(CONTROL_FILTRO, "px-2.5")}
          >
            <option value="">{t.filtro.todas}</option>
            <option value="PENDIENTE">{t.filtro.pendientes}</option>
            <option value="EXITOSA">{t.filtro.entregadas}</option>
            <option value="FALLIDA">{t.filtro.fallidas}</option>
          </select>
          <button type="button" title={t.refrescar} aria-label={t.refrescar} onClick={() => setLectura((n) => n + 1)} className={cn(CONTROL_FILTRO, "inline-flex size-control items-center justify-center text-muted-foreground hover:text-foreground")}>
            <ArrowClockwiseIcon weight="bold" className={cn("size-4", leyendo && "animate-spin")} />
          </button>
        </div>
      </div>

      {error ? (
        <p role="alert" className="text-control text-destructive">
          {error}
        </p>
      ) : null}

      <div className={cn(TARJETA, "min-w-0 overflow-hidden")}>
        <Table>
          <TableHeader>
            <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
              <TableHead className={cn(CABECERA_TABLA, "pl-4")}>{t.columnas.creada}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.evento}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.estado}</TableHead>
              <TableHead className={cn(CABECERA_TABLA, "text-right")}>{t.columnas.intentos}</TableHead>
              <TableHead className={CABECERA_TABLA}>{t.columnas.ultimoResultado}</TableHead>
              <TableHead className={cn(CABECERA_TABLA, "pr-4 text-right")}>{t.columnas.acciones}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-control">
            {(entregas ?? []).map((e) => (
              <TableRow key={e.id} data-testid="entrega" className="border-b border-border/60 hover:bg-muted/80">
                <TableCell className="py-2 pr-3 pl-4 font-mono text-control whitespace-nowrap tabular-nums">{formatearFechaHora(e.creada_en, locale)}</TableCell>
                <TableCell className="px-3 py-2">{etiquetaDeEvento(e.evento, messages)}</TableCell>
                <TableCell className="px-3 py-2 whitespace-nowrap">
                  <EstadoEntregaBadge estado={e.estado} />
                  {e.estado === "PENDIENTE" && e.siguiente_intento ? (
                    <div className="mt-0.5 font-mono text-etiqueta text-muted-foreground">{e.intentos > 0 ? interpolar(t.reintenta, { fecha: formatearFechaHora(e.siguiente_intento, locale) }) : t.enCola}</div>
                  ) : null}
                </TableCell>
                <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{e.intentos}</TableCell>
                <TableCell className="max-w-72 px-3 py-2 font-mono text-control break-words text-muted-foreground">{resultadoDelIntento(e.ultimo_http, e.ultimo_error)}</TableCell>
                <TableCell className="py-2 pr-4 pl-2 text-right">
                  {e.estado === "PENDIENTE" ? (
                    <span className="text-etiqueta text-muted-foreground/60">—</span>
                  ) : (
                    <button
                      type="button"
                      disabled={soloLectura !== null || reenviando === e.id}
                      title={soloLectura ?? undefined}
                      onClick={() => reenviar(e.id)}
                      className="inline-flex h-control items-center gap-1 rounded-lg px-2.5 text-control font-medium whitespace-nowrap text-primary transition-colors hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      <ArrowClockwiseIcon weight="bold" className={cn("size-3.5", reenviando === e.id && "animate-spin")} />
                      {t.reenviar}
                    </button>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {entregas !== null && entregas.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={6} className="py-8 text-center text-control text-muted-foreground">
                  {estado ? t.sinResultados : t.vacia}
                </TableCell>
              </TableRow>
            ) : null}
            {entregas === null && !error ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={6} className="py-8 text-center text-control text-muted-foreground">
                  {t.cargando}
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
