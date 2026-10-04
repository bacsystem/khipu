"use client";

import { CircleCheckIcon, CircleXIcon, TriangleAlertIcon } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Etiqueta } from "@/components/admin/etiquetas";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { ColaDeEnvios, EstadoDeServicio, Franja, MonitorDeEmision } from "@/lib/api/admin-monitor";
import { apiRequest } from "@/lib/api/browser";
import { CABECERA_TABLA, TARJETA } from "@/lib/estilos";
import { messages, mensajeError } from "@/lib/messages";
import { duracionEnPalabras, formatearHoraConSegundos, formatearHoraDeLima, formatearTasa, proporcionDeBarra } from "@/lib/monitor-formato";
import { formatearFechaHora } from "@/lib/formato";
import { cn } from "@/lib/utils";

const t = messages.admin.monitor;

/** Cada cuánto se pide una lectura nueva: lo mismo que el backend guarda la de SUNAT, así que pedir más seguido no averiguaría nada más. */
export const INTERVALO_DEL_MONITOR_MS = 30_000;

type Categoria = "aceptados" | "rechazados" | "con_error" | "en_camino" | "otros";

/** De abajo hacia arriba en cada barra. Lo bueno abajo, lo que pide atención arriba, donde se ve. */
const CATEGORIAS: ReadonlyArray<{ clave: Categoria; mensaje: keyof typeof t.categoriasAyuda; color: string }> = [
  { clave: "aceptados", mensaje: "aceptados", color: "bg-success-foreground/70" },
  { clave: "en_camino", mensaje: "enCamino", color: "bg-primary/60" },
  { clave: "otros", mensaje: "otros", color: "bg-muted-foreground/30" },
  { clave: "con_error", mensaje: "conError", color: "bg-warning-foreground/80" },
  { clave: "rechazados", mensaje: "rechazados", color: "bg-destructive" },
];

/**
 * El monitor global de emisión (#195): cómo va la emisión de todos los clientes, la cola de envíos a SUNAT y si SUNAT contesta. Parte de la lectura que trajo el
 * servidor y pide una nueva cada {@code intervaloMs} por el BFF. Si una lectura falla se queda con la última y lo dice (un monitor que se congela sin avisar es
 * peor que uno que falla); si la sesión terminó deja de pedir, porque cada pedido seguiría fallando.
 */
export function PanelDelMonitor({ inicial, intervaloMs = INTERVALO_DEL_MONITOR_MS }: { inicial: MonitorDeEmision | null; intervaloMs?: number }) {
  const [monitor, setMonitor] = useState<MonitorDeEmision | null>(inicial);
  const [fallo, setFallo] = useState<string | null>(null);
  const [actualizando, setActualizando] = useState(false);
  const [sesionTerminada, setSesionTerminada] = useState(false);
  // Refs, no estado: el intervalo y dos pedidos en el mismo tick leerían el valor viejo del closure.
  const enCursoRef = useRef(false);
  const sesionTerminadaRef = useRef(false);
  const vivoRef = useRef(true);

  const leer = useCallback(async () => {
    if (enCursoRef.current || sesionTerminadaRef.current) return;
    enCursoRef.current = true;
    setActualizando(true);
    const res = await apiRequest<MonitorDeEmision>("/api/admin/monitor", { method: "GET" });
    enCursoRef.current = false;
    if (!vivoRef.current) return;
    setActualizando(false);
    if (res.estado === "exito" && res.datos) {
      setMonitor(res.datos);
      setFallo(null);
      return;
    }
    if (res.codigo === "NO_AUTORIZADO") {
      sesionTerminadaRef.current = true;
      setSesionTerminada(true);
    }
    setFallo(res.codigo === "NO_AUTORIZADO" ? t.sesionTerminada : (res.mensaje ?? mensajeError(res.codigo)));
  }, []);

  useEffect(() => {
    vivoRef.current = true;
    if (!inicial) void leer();
    const id = setInterval(() => void leer(), intervaloMs);
    return () => {
      vivoRef.current = false;
      clearInterval(id);
    };
  }, [inicial, intervaloMs, leer]);

  return (
    <div className="grid min-w-0 gap-4">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[12px] text-muted-foreground" role="status" aria-live="polite">
        {monitor ? (
          <span data-testid="monitor-ultima-lectura">{t.ultimaLectura.replace("{hora}", formatearHoraConSegundos(monitor.generado_en))}</span>
        ) : null}
        {actualizando ? <span data-testid="monitor-actualizando">{t.actualizando}</span> : null}
      </div>

      {fallo ? (
        <p data-testid="monitor-error" role="alert" className="flex flex-wrap items-center gap-3 rounded-lg border border-destructive-border bg-destructive/10 px-3 py-2.5 text-sm text-destructive">
          <span>{monitor ? t.errorAlActualizar : t.errorInicial}</span>
          <span className="text-[12px]">{fallo}</span>
          {sesionTerminada ? null : (
            <button type="button" onClick={() => void leer()} className="ml-auto text-[13px] font-medium underline">
              {t.reintentar}
            </button>
          )}
        </p>
      ) : null}

      {monitor ? <Lectura monitor={monitor} /> : null}
    </div>
  );
}

function Lectura({ monitor }: { monitor: MonitorDeEmision }) {
  return (
    <>
      {monitor.outbox.alerta ? <AlertaDelOutbox outbox={monitor.outbox} /> : null}
      <Hoy franja={monitor.hoy} />
      <Horas horas={monitor.horas} />
      <div className="grid min-w-0 gap-4 lg:grid-cols-2">
        <Cola outbox={monitor.outbox} />
        <Sunat servicios={monitor.sunat} />
      </div>
    </>
  );
}

function AlertaDelOutbox({ outbox }: { outbox: ColaDeEnvios }) {
  const tiempo = duracionEnPalabras(outbox.vencido_hace_segundos ?? 0);
  const texto = outbox.vencidos === 1 ? t.alerta.uno.replace("{tiempo}", tiempo) : t.alerta.varios.replace("{n}", String(outbox.vencidos)).replace("{tiempo}", tiempo);
  return (
    <div data-testid="monitor-alerta" role="alert" className="flex items-start gap-2.5 rounded-xl border border-destructive-border bg-destructive/10 px-4 py-3 text-sm text-destructive">
      <TriangleAlertIcon className="mt-0.5 size-4 shrink-0" />
      <div className="grid gap-0.5">
        <p className="font-medium">{t.alerta.titulo}</p>
        <p>{texto}</p>
      </div>
    </div>
  );
}

function Hoy({ franja }: { franja: Franja }) {
  return (
    <section aria-labelledby="monitor-hoy" className={cn(TARJETA, "grid gap-3 p-4")}>
      <div className="flex flex-wrap items-baseline justify-between gap-x-4">
        <div className="grid gap-0.5">
          <h2 id="monitor-hoy" className="font-heading text-base">
            {t.hoy.titulo}
          </h2>
          <p className="text-[12px] text-muted-foreground">{t.hoy.ayuda}</p>
        </div>
        <p className="text-[13px]" title={t.hoy.tasaAyuda}>
          <span className="text-muted-foreground">{t.hoy.tasa}: </span>
          <span data-testid="monitor-tasa" className="font-medium tabular-nums">
            {formatearTasa(franja.tasa_de_rechazo)}
          </span>
        </p>
      </div>
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <Cifra testid="monitor-hoy-total" etiqueta={t.categorias.total} valor={franja.total} />
        {CATEGORIAS.map((c) => (
          <Cifra key={c.clave} testid={`monitor-hoy-${c.clave}`} etiqueta={t.categorias[c.mensaje]} ayuda={t.categoriasAyuda[c.mensaje]} valor={franja[c.clave]} />
        ))}
      </dl>
    </section>
  );
}

function Cifra({ testid, etiqueta, ayuda, valor }: { testid: string; etiqueta: string; ayuda?: string; valor: number }) {
  return (
    <div className="grid gap-0.5 rounded-lg border border-border/70 px-3 py-2" title={ayuda}>
      <dt className="text-[11px] tracking-wider text-muted-foreground uppercase">{etiqueta}</dt>
      <dd data-testid={testid} className="font-heading text-xl tabular-nums">
        {valor}
      </dd>
    </div>
  );
}

function Horas({ horas }: { horas: Franja[] }) {
  const maximo = Math.max(0, ...horas.map((h) => h.total));
  return (
    <section aria-labelledby="monitor-horas" className={cn(TARJETA, "grid gap-3 p-4")}>
      <div className="grid gap-0.5">
        <h2 id="monitor-horas" className="font-heading text-base">
          {t.horas.titulo}
        </h2>
        <p className="text-[12px] text-muted-foreground">{t.horas.ayuda}</p>
      </div>

      {maximo === 0 ? (
        <p data-testid="monitor-horas-vacio" className="rounded-lg border border-border bg-muted/50 px-3 py-2.5 text-[13px] text-muted-foreground">
          {t.horas.sinDatos}
        </p>
      ) : (
        <div data-testid="monitor-horas" role="img" aria-label={t.horas.grafico} className="flex h-36 items-end gap-1">
          {horas.map((h, i) => (
            <Barra key={h.desde} franja={h} alto={proporcionDeBarra(h.total, maximo)} conEtiqueta={i % 3 === 0} />
          ))}
        </div>
      )}

      <div className="flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-muted-foreground">
        {[...CATEGORIAS].reverse().map((c) => (
          <span key={c.clave} className="flex items-center gap-1.5">
            <span className={cn("size-2.5 rounded-sm", c.color)} />
            {t.categorias[c.mensaje]}
          </span>
        ))}
      </div>

      <details className="text-[13px]">
        <summary className="cursor-pointer text-primary">{t.horas.tabla}</summary>
        <div className="mt-2 overflow-x-auto rounded-lg border border-border/90">
          <Table>
            <TableHeader>
              <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
                <TableHead className={`${CABECERA_TABLA} pl-3`}>{t.horas.hora}</TableHead>
                <TableHead className={`${CABECERA_TABLA} text-right`}>{t.categorias.total}</TableHead>
                {CATEGORIAS.map((c) => (
                  <TableHead key={c.clave} className={`${CABECERA_TABLA} text-right`}>
                    {t.categorias[c.mensaje]}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody className="text-[13px] tabular-nums">
              {horas.map((h) => (
                <TableRow key={h.desde} className="border-b border-border/60">
                  <TableCell className="py-1.5 pl-3">{formatearHoraDeLima(h.desde)}</TableCell>
                  <TableCell className="py-1.5 text-right">{h.total}</TableCell>
                  {CATEGORIAS.map((c) => (
                    <TableCell key={c.clave} className="py-1.5 text-right">
                      {h[c.clave]}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </details>
    </section>
  );
}

function Barra({ franja, alto, conEtiqueta }: { franja: Franja; alto: number; conEtiqueta: boolean }) {
  const hora = formatearHoraDeLima(franja.desde);
  return (
    <div
      data-testid="monitor-hora"
      data-desde={franja.desde}
      data-total={franja.total}
      title={t.horas.barra.replace("{hora}", hora).replace("{total}", String(franja.total))}
      className="flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-1"
    >
      <div className="flex w-full flex-col-reverse overflow-hidden rounded-t-sm" style={{ height: `${alto * 100}%` }}>
        {CATEGORIAS.map((c) =>
          franja[c.clave] === 0 ? null : <div key={c.clave} className={c.color} style={{ height: `${(franja[c.clave] / franja.total) * 100}%` }} />,
        )}
      </div>
      <span className="h-3 text-[9px] whitespace-nowrap text-muted-foreground tabular-nums">{conEtiqueta ? hora : ""}</span>
    </div>
  );
}

function Cola({ outbox }: { outbox: ColaDeEnvios }) {
  return (
    <section aria-labelledby="monitor-outbox" className={cn(TARJETA, "grid content-start gap-3 p-4")}>
      <div className="grid gap-0.5">
        <h2 id="monitor-outbox" className="font-heading text-base">
          {t.outbox.titulo}
        </h2>
        <p className="text-[12px] text-muted-foreground">{t.outbox.ayuda}</p>
      </div>
      <dl className="grid grid-cols-2 gap-3">
        <Cifra testid="monitor-outbox-pendientes" etiqueta={t.outbox.pendientes} valor={outbox.pendientes} />
        <Cifra testid="monitor-outbox-vencidos" etiqueta={t.outbox.vencidos} valor={outbox.vencidos} />
      </dl>
      {outbox.pendientes === 0 ? (
        <p data-testid="monitor-outbox-vacia" className="text-[13px] text-muted-foreground">
          {t.outbox.vacia}
        </p>
      ) : (
        <p className="text-[13px] text-muted-foreground">
          {outbox.mas_viejo_desde ? <span data-testid="monitor-outbox-antiguo">{t.outbox.masAntiguo} {formatearFechaHora(outbox.mas_viejo_desde)}. </span> : null}
          {outbox.vencido_hace_segundos !== undefined ? (
            <span data-testid="monitor-outbox-vencido-hace">{t.outbox.vencidoHace.replace("{tiempo}", duracionEnPalabras(outbox.vencido_hace_segundos))}</span>
          ) : null}
        </p>
      )}
    </section>
  );
}

function Sunat({ servicios }: { servicios: EstadoDeServicio[] }) {
  return (
    <section aria-labelledby="monitor-sunat" className={cn(TARJETA, "grid content-start gap-3 p-4")}>
      <div className="grid gap-0.5">
        <h2 id="monitor-sunat" className="font-heading text-base">
          {t.sunat.titulo}
        </h2>
        <p className="text-[12px] text-muted-foreground">{t.sunat.ayuda}</p>
      </div>
      {servicios.length === 0 ? (
        <p data-testid="monitor-sunat-vacio" className="text-[13px] text-muted-foreground">
          {t.sunat.sinServicios}
        </p>
      ) : (
        <ul className="grid gap-2">
          {servicios.map((s) => (
            <li
              key={s.servicio}
              data-testid="monitor-servicio"
              data-servicio={s.servicio}
              data-disponible={s.disponible}
              className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-border/70 px-3 py-2 text-[13px]"
            >
              {s.disponible ? <CircleCheckIcon className="size-4 shrink-0 text-success-foreground" /> : <CircleXIcon className="size-4 shrink-0 text-destructive" />}
              <span className="font-medium">{t.sunat.servicios[s.servicio]}</span>
              <Etiqueta tono={s.disponible ? "ok" : "error"}>{s.disponible ? t.sunat.disponible : t.sunat.noDisponible}</Etiqueta>
              <span className="ml-auto text-[12px] text-muted-foreground tabular-nums">
                {s.disponible && s.milisegundos !== undefined ? t.sunat.milisegundos.replace("{ms}", String(s.milisegundos)) : (s.detalle ?? "")}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
