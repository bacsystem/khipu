"use client";

import { RefreshCwIcon, RotateCwIcon } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { PieTabla } from "khipu-design-system/patrones/pie-tabla";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import { apiRequest } from "@/lib/api/browser";
import type { EntregaWebhook, EstadoEntrega } from "@/lib/api/webhooks";
import { listarEntregas } from "@/lib/api/webhooks-browser";
import { CABECERA_TABLA, CONTROL_FILTRO } from "@/lib/estilos";
import { formatearFechaHora } from "@/lib/formato";
import { mensajeError } from "@/lib/messages";
import { POR_PAGINA_DEFECTO } from "khipu-design-system/lib/paginacion";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";
import { cn } from "@/lib/utils";
import { ETIQUETA_ESTADO_ENTREGA, etiquetaDeEvento, resultadoDelIntento } from "@/lib/webhooks/eventos";

const ESTILO_ESTADO: Record<EstadoEntrega, string> = {
  EXITOSA: "border-success-border bg-success text-success-foreground",
  PENDIENTE: "border-warning-border bg-warning text-warning-foreground",
  FALLIDA: "border-destructive/30 bg-destructive/10 text-destructive",
};

function EstadoEntregaBadge({ estado }: { estado: EstadoEntrega }) {
  return <span className={cn("inline-flex rounded-full border px-2.5 py-0.5 font-mono text-[11px] font-medium", ESTILO_ESTADO[estado])}>{ETIQUETA_ESTADO_ENTREGA[estado]}</span>;
}

/**
 * El log de entregas de un webhook (#19): cada evento mandado, su estado, los intentos y lo que respondió el servidor en el último. Se guarda 30 días. Una entrega cerrada
 * (entregada o fallida) se puede reenviar con el mismo cuerpo. `version` fuerza una lectura nueva (la prueba que se acaba de enviar).
 */
export function EntregasWebhook({ webhookId, version }: { webhookId: string; version: number }) {
  const soloLectura = useSinPermisoDeConfigurar();
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
    const res = await listarEntregas(webhookId, { estado: estado || undefined, pagina, porPagina });
    if (mia !== ultimaLectura.current) return;
    setLeyendo(false);
    if (res.estado === "exito") {
      setEntregas(res.datos.entregas);
      setTotal(res.datos.total);
      setError(null);
    } else {
      setError(res.mensaje);
    }
  }, [webhookId, estado, pagina, porPagina]);

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
        <h3 className="text-[12px] font-semibold text-foreground">Entregas</h3>
        <div className="flex items-center gap-2">
          <select
            aria-label="Filtrar entregas por estado"
            value={estado}
            onChange={(e) => {
              setEstado(e.target.value as EstadoEntrega | "");
              setPagina(1);
            }}
            className={cn(CONTROL_FILTRO, "px-2.5")}
          >
            <option value="">Todas</option>
            <option value="PENDIENTE">Pendientes</option>
            <option value="EXITOSA">Entregadas</option>
            <option value="FALLIDA">Fallidas</option>
          </select>
          <button type="button" title="Refrescar entregas" aria-label="Refrescar entregas" onClick={() => setLectura((n) => n + 1)} className={cn(CONTROL_FILTRO, "inline-flex size-9 items-center justify-center text-muted-foreground hover:text-foreground")}>
            <RefreshCwIcon className={cn("size-4", leyendo && "animate-spin")} />
          </button>
        </div>
      </div>

      {error ? (
        <p role="alert" className="text-[12px] text-destructive">
          {error}
        </p>
      ) : null}

      <div className="min-w-0 overflow-hidden rounded-lg border border-border/90 bg-card">
        <Table>
          <TableHeader>
            <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
              <TableHead className={cn(CABECERA_TABLA, "pl-4")}>Creada</TableHead>
              <TableHead className={CABECERA_TABLA}>Evento</TableHead>
              <TableHead className={CABECERA_TABLA}>Estado</TableHead>
              <TableHead className={cn(CABECERA_TABLA, "text-right")}>Intentos</TableHead>
              <TableHead className={CABECERA_TABLA}>Último resultado</TableHead>
              <TableHead className={cn(CABECERA_TABLA, "pr-4 text-right")}>Acciones</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-[13px]">
            {(entregas ?? []).map((e) => (
              <TableRow key={e.id} data-testid="entrega" className="border-b border-border/60 hover:bg-muted/80">
                <TableCell className="py-2 pr-3 pl-4 font-mono text-[12px] whitespace-nowrap tabular-nums">{formatearFechaHora(e.creada_en)}</TableCell>
                <TableCell className="px-3 py-2">{etiquetaDeEvento(e.evento)}</TableCell>
                <TableCell className="px-3 py-2 whitespace-nowrap">
                  <EstadoEntregaBadge estado={e.estado} />
                  {e.estado === "PENDIENTE" && e.siguiente_intento ? (
                    <div className="mt-0.5 font-mono text-[11px] text-muted-foreground">{e.intentos > 0 ? `reintenta ${formatearFechaHora(e.siguiente_intento)}` : "en cola"}</div>
                  ) : null}
                </TableCell>
                <TableCell className="px-3 py-2 text-right font-mono tabular-nums">{e.intentos}</TableCell>
                <TableCell className="max-w-72 px-3 py-2 font-mono text-[12px] break-words text-muted-foreground">{resultadoDelIntento(e.ultimo_http, e.ultimo_error)}</TableCell>
                <TableCell className="py-2 pr-4 pl-2 text-right">
                  {e.estado === "PENDIENTE" ? (
                    <span className="text-[11px] text-muted-foreground/60">—</span>
                  ) : (
                    <button
                      type="button"
                      disabled={soloLectura !== null || reenviando === e.id}
                      title={soloLectura ?? undefined}
                      onClick={() => reenviar(e.id)}
                      className="inline-flex h-9 items-center gap-1 rounded-lg px-2.5 text-[12px] font-medium whitespace-nowrap text-primary transition-colors hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      <RotateCwIcon className={cn("size-3.5", reenviando === e.id && "animate-spin")} />
                      Reenviar
                    </button>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {entregas !== null && entregas.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={6} className="py-8 text-center text-[13px] text-muted-foreground">
                  {estado ? "No hay entregas con ese estado." : "Todavía no hay entregas. Envía una prueba o emite un comprobante."}
                </TableCell>
              </TableRow>
            ) : null}
            {entregas === null && !error ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={6} className="py-8 text-center text-[13px] text-muted-foreground">
                  Cargando entregas…
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
        <PieTabla
          desde={desde}
          hasta={hasta}
          total={total}
          unidad="entregas"
          porPagina={porPagina}
          onPorPagina={(n) => {
            setPorPagina(n);
            setPagina(1);
          }}
          nota="Se guardan 30 días"
          pagina={paginaActual}
          ultimaPagina={ultimaPagina}
          onPagina={setPagina}
        />
      </div>
    </div>
  );
}
