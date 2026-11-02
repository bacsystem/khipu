"use client";

import { Alerta } from "khipu-design-system/feedback/alerta";
import { CalendarCheckIcon, ChartBarIcon, CheckCircleIcon, PulseIcon, TrayArrowUpIcon, WarningIcon, XCircleIcon } from "@phosphor-icons/react";
import { EstadoVacio } from "khipu-design-system/feedback/estado-vacio";
import { Acordeon } from "khipu-design-system/navegacion/acordeon";
import { CabeceraSeccion } from "khipu-design-system/patrones/cabecera-seccion";
import { Metrica } from "khipu-design-system/patrones/metricas";
import { useLocale, useMessages } from "next-intl";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { Etiqueta } from "@/components/admin/etiquetas";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import type { BoletasSinInformar, ColaDeEnvios, EstadoDeServicio, Franja, MonitorDeEmision, ResumenesSinRespuesta } from "@/lib/api/admin-monitor";
import { apiRequest } from "@/lib/api/browser";
import { CABECERA_TABLA, TARJETA } from "@/lib/estilos";
import type { Mensajes } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { duracionEnPalabras, formatearHoraConSegundos, formatearHoraDeLima, formatearTasa, proporcionDeBarra } from "@/lib/monitor-formato";
import { formatearFecha, formatearFechaHora } from "@/lib/formato";
import { cn } from "@/lib/utils";

/** Cada cuánto se pide una lectura nueva: lo mismo que el backend guarda la de SUNAT, así que pedir más seguido no averiguaría nada más. */
export const INTERVALO_DEL_MONITOR_MS = 30_000;

type Categoria = "aceptados" | "rechazados" | "con_error" | "en_camino" | "otros";

/** De abajo hacia arriba en cada barra. Lo bueno abajo, lo que pide atención arriba, donde se ve. */
const CATEGORIAS: ReadonlyArray<{ clave: Categoria; mensaje: keyof Mensajes["admin"]["monitor"]["categoriasAyuda"]; color: string }> = [
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
  const t = useMessages().admin.monitor;
  const mensajeError = useMensajeError();
  const [monitor, setMonitor] = useState<MonitorDeEmision | null>(inicial);
  const [fallo, setFallo] = useState<string | null>(null);
  const [actualizando, setActualizando] = useState(false);
  const [sesionTerminada, setSesionTerminada] = useState(false);
  // Refs, no estado: el intervalo y dos pedidos en el mismo tick leerían el valor viejo del closure.
  const enCursoRef = useRef(false);
  const sesionTerminadaRef = useRef(false);

  const leer = useCallback(async () => {
    if (enCursoRef.current || sesionTerminadaRef.current) return;
    enCursoRef.current = true;
    setActualizando(true);
    const res = await apiRequest<MonitorDeEmision>("/api/admin/monitor", { method: "GET" });
    enCursoRef.current = false;
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
  }, [t.sesionTerminada, mensajeError]);

  useEffect(() => {
    if (!inicial) void leer();
    const id = setInterval(() => void leer(), intervaloMs);
    return () => {
      clearInterval(id);
    };
  }, [inicial, intervaloMs, leer]);

  return (
    <div className="grid min-w-0 gap-3">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-control text-muted-foreground" role="status" aria-live="polite">
        {monitor ? (
          <span data-testid="monitor-ultima-lectura">{t.ultimaLectura.replace("{hora}", formatearHoraConSegundos(monitor.generado_en))}</span>
        ) : null}
        {actualizando ? <span data-testid="monitor-actualizando">{t.actualizando}</span> : null}
      </div>

      {fallo ? (
        <div data-testid="monitor-error">
          <Alerta
            tono="error"
            accion={
              sesionTerminada ? null : (
                <button type="button" onClick={() => void leer()} className="text-control font-medium underline">
                  {t.reintentar}
                </button>
              )
            }
          >
            {monitor ? t.errorAlActualizar : t.errorInicial} {fallo}
          </Alerta>
        </div>
      ) : null}

      {monitor ? <Lectura monitor={monitor} /> : null}
    </div>
  );
}

function Lectura({ monitor }: { monitor: MonitorDeEmision }) {
  return (
    <>
      {monitor.outbox.alerta ? <AlertaDelOutbox outbox={monitor.outbox} /> : null}
      {monitor.resumenes_sin_respuesta.alerta ? <AlertaDeResumenes resumenes={monitor.resumenes_sin_respuesta} /> : null}
      {monitor.boletas_sin_informar.alerta ? <AlertaDeBoletas boletas={monitor.boletas_sin_informar} /> : null}
      <Hoy franja={monitor.hoy} />
      <Horas horas={monitor.horas} />
      <div className="grid min-w-0 gap-3 lg:grid-cols-2">
        <Cola outbox={monitor.outbox} />
        <Sunat servicios={monitor.sunat} />
      </div>
    </>
  );
}

function AlertaDelOutbox({ outbox }: { outbox: ColaDeEnvios }) {
  const t = useMessages().admin.monitor;
  const tiempo = duracionEnPalabras(outbox.vencido_hace_segundos ?? 0);
  const texto = outbox.vencidos === 1 ? t.alerta.uno.replace("{tiempo}", tiempo) : t.alerta.varios.replace("{n}", String(outbox.vencidos)).replace("{tiempo}", tiempo);
  return (
    // La `Alerta` de error del DS ya es `role="alert"`; el `data-testid` va en el envoltorio.
    <div data-testid="monitor-alerta">
      <Alerta tono="error" icon={WarningIcon} titulo={t.alerta.titulo}>
        {texto}
      </Alerta>
    </div>
  );
}

/** #362 B14: tono de aviso (no el rojo del outbox caído): el trabajo corre, es SUNAT la que no resuelve un ticket, y pide una revisión, no un reinicio. */
function AlertaDeResumenes({ resumenes }: { resumenes: ResumenesSinRespuesta }) {
  const t = useMessages().admin.monitor;
  const tiempo = duracionEnPalabras(resumenes.mas_viejo_hace_segundos ?? 0);
  const umbral = String(resumenes.umbral_horas);
  const texto = resumenes.cantidad === 1
    ? t.alertaResumenes.uno.replace("{tiempo}", tiempo).replace("{umbral}", umbral)
    : t.alertaResumenes.varios.replace("{n}", String(resumenes.cantidad)).replace("{umbral}", umbral).replace("{tiempo}", tiempo);
  return (
    // Ámbar pero urgente para el administrador: el envoltorio conserva `role="alert"` (la `Alerta` ámbar del DS sería `status`).
    <div data-testid="monitor-alerta-resumenes" role="alert">
      <Alerta tono="aviso" titulo={t.alertaResumenes.titulo}>
        <p>{texto}</p>
        <p className="opacity-90">{t.alertaResumenes.ayuda}</p>
      </Alerta>
    </div>
  );
}

/**
 * #356: boletas que nadie informó. Tono de aviso: el sistema corre y SUNAT contesta; lo que falta es que el cliente (que las dejó sin enviar, o cuyo envío falla) las envíe antes de
 * que cierre el resumen diario. El detalle empresa por empresa está en la cola de errores.
 */
function AlertaDeBoletas({ boletas }: { boletas: BoletasSinInformar }) {
  const t = useMessages().admin.monitor;
  const fecha = formatearFecha(boletas.mas_antigua_emitida_el ?? "", useLocale());
  const texto = boletas.cantidad === 1 ? t.alertaBoletas.uno.replace("{fecha}", fecha) : t.alertaBoletas.varios.replace("{n}", String(boletas.cantidad)).replace("{fecha}", fecha);
  return (
    // Como la de resúmenes: ámbar, y el envoltorio conserva `role="alert"`.
    <div data-testid="monitor-alerta-boletas" role="alert">
      <Alerta
        tono="aviso"
        titulo={t.alertaBoletas.titulo}
        accion={
          <Link href="/admin/errores" className="text-control font-medium underline underline-offset-2">
            {t.alertaBoletas.verLista}
          </Link>
        }
      >
        <p>
          {texto}
          {boletas.por_vencer_el_plazo === 1 ? ` ${t.alertaBoletas.urgente}` : boletas.por_vencer_el_plazo > 1 ? ` ${t.alertaBoletas.urgentes.replace("{n}", String(boletas.por_vencer_el_plazo))}` : ""}
        </p>
        <p className="opacity-90">{t.alertaBoletas.ayuda}</p>
      </Alerta>
    </div>
  );
}

function Hoy({ franja }: { franja: Franja }) {
  const t = useMessages().admin.monitor;
  return (
    <section aria-labelledby="monitor-hoy" className={TARJETA}>
      <CabeceraSeccion
        icon={CalendarCheckIcon}
        titulo={<span id="monitor-hoy">{t.hoy.titulo}</span>}
        subtitulo={t.hoy.ayuda}
        derecha={
          <p className="text-control" title={t.hoy.tasaAyuda}>
            <span className="text-muted-foreground">{t.hoy.tasa}: </span>
            <span data-testid="monitor-tasa" className="font-medium tabular-nums">
              {formatearTasa(franja.tasa_de_rechazo)}
            </span>
          </p>
        }
      />
      {/* Seis cifras: FilaMetricas llega a cinco columnas, así que la rejilla es propia, con su misma separación. */}
      <div className="grid grid-cols-2 gap-3 px-4 pb-4 sm:grid-cols-3 lg:grid-cols-6">
        <Cifra testid="monitor-hoy-total" etiqueta={t.categorias.total} valor={franja.total} />
        {CATEGORIAS.map((c) => (
          <Cifra key={c.clave} testid={`monitor-hoy-${c.clave}`} etiqueta={t.categorias[c.mensaje]} ayuda={t.categoriasAyuda[c.mensaje]} valor={franja[c.clave]} />
        ))}
      </div>
    </section>
  );
}

/** Una cifra del monitor: la `Metrica` del design system; la ayuda, si no cabe, se lee entera al pasar el mouse. */
function Cifra({ testid, etiqueta, ayuda, valor }: { testid: string; etiqueta: string; ayuda?: string; valor: number }) {
  return (
    <Metrica
      etiqueta={etiqueta}
      ayuda={
        ayuda ? (
          <span title={ayuda} className="truncate">
            {ayuda}
          </span>
        ) : null
      }
    >
      <span data-testid={testid}>{valor}</span>
    </Metrica>
  );
}

function Horas({ horas }: { horas: Franja[] }) {
  const t = useMessages().admin.monitor;
  const maximo = Math.max(0, ...horas.map((h) => h.total));
  return (
    <section aria-labelledby="monitor-horas" className={TARJETA}>
      <CabeceraSeccion icon={ChartBarIcon} titulo={<span id="monitor-horas">{t.horas.titulo}</span>} subtitulo={t.horas.ayuda} />
      <div className="grid gap-3 px-4 pb-4">
        {maximo === 0 ? (
          <div data-testid="monitor-horas-vacio">
            <EstadoVacio icon={ChartBarIcon} titulo={t.horas.sinDatos} className="py-8" />
          </div>
        ) : (
          <div data-testid="monitor-horas" role="img" aria-label={t.horas.grafico} className="flex h-36 items-end gap-1">
            {horas.map((h, i) => (
              <Barra key={h.desde} franja={h} alto={proporcionDeBarra(h.total, maximo)} conEtiqueta={i % 3 === 0} />
            ))}
          </div>
        )}

        <div className="flex flex-wrap gap-x-4 gap-y-1 text-etiqueta text-muted-foreground">
          {[...CATEGORIAS].reverse().map((c) => (
            <span key={c.clave} className="flex items-center gap-1.5">
              <span className={cn("size-2.5 rounded-sm", c.color)} />
              {t.categorias[c.mensaje]}
            </span>
          ))}
        </div>

        <Acordeon
          items={[
            {
              id: "por-hora",
              titulo: t.horas.tabla,
              contenido: (
                <div data-testid="monitor-tabla-horas" className="min-w-0">
                  <Table>
                    <TableHeader>
                      <TableRow className="hover:bg-transparent">
                        <TableHead className={`${CABECERA_TABLA} pl-3`}>{t.horas.hora}</TableHead>
                        <TableHead className={`${CABECERA_TABLA} text-right`}>{t.categorias.total}</TableHead>
                        {CATEGORIAS.map((c) => (
                          <TableHead key={c.clave} className={`${CABECERA_TABLA} text-right`}>
                            {t.categorias[c.mensaje]}
                          </TableHead>
                        ))}
                      </TableRow>
                    </TableHeader>
                    <TableBody className="text-control tabular-nums">
                      {horas.map((h) => (
                        <TableRow key={h.desde}>
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
              ),
            },
          ]}
        />
      </div>
    </section>
  );
}

function Barra({ franja, alto, conEtiqueta }: { franja: Franja; alto: number; conEtiqueta: boolean }) {
  const t = useMessages().admin.monitor;
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
      <span className="h-3 text-etiqueta whitespace-nowrap text-muted-foreground tabular-nums">{conEtiqueta ? hora : ""}</span>
    </div>
  );
}

function Cola({ outbox }: { outbox: ColaDeEnvios }) {
  const t = useMessages().admin.monitor;
  const locale = useLocale();
  return (
    <section aria-labelledby="monitor-outbox" className={TARJETA}>
      <CabeceraSeccion icon={TrayArrowUpIcon} titulo={<span id="monitor-outbox">{t.outbox.titulo}</span>} subtitulo={t.outbox.ayuda} />
      <div className="grid content-start gap-3 px-4 pb-4">
        <div className="grid grid-cols-2 gap-3">
          <Cifra testid="monitor-outbox-pendientes" etiqueta={t.outbox.pendientes} valor={outbox.pendientes} />
          <Cifra testid="monitor-outbox-vencidos" etiqueta={t.outbox.vencidos} valor={outbox.vencidos} />
        </div>
        {outbox.pendientes === 0 ? (
          <p data-testid="monitor-outbox-vacia" className="text-control text-muted-foreground">
            {t.outbox.vacia}
          </p>
        ) : (
          <p className="text-control text-muted-foreground">
            {outbox.mas_viejo_desde ? <span data-testid="monitor-outbox-antiguo">{t.outbox.masAntiguo} {formatearFechaHora(outbox.mas_viejo_desde, locale)}. </span> : null}
            {outbox.vencido_hace_segundos !== undefined ? (
              <span data-testid="monitor-outbox-vencido-hace">{t.outbox.vencidoHace.replace("{tiempo}", duracionEnPalabras(outbox.vencido_hace_segundos))}</span>
            ) : null}
          </p>
        )}
      </div>
    </section>
  );
}

function Sunat({ servicios }: { servicios: EstadoDeServicio[] }) {
  const t = useMessages().admin.monitor;
  return (
    <section aria-labelledby="monitor-sunat" className={TARJETA}>
      <CabeceraSeccion icon={PulseIcon} titulo={<span id="monitor-sunat">{t.sunat.titulo}</span>} subtitulo={t.sunat.ayuda} />
      <div className="grid content-start gap-3 px-4 pb-4">
        {servicios.length === 0 ? (
          <p data-testid="monitor-sunat-vacio" className="text-control text-muted-foreground">
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
                className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg bg-secondary px-3 py-2 text-control"
              >
                {s.disponible ? <CheckCircleIcon className="size-4 shrink-0 text-success-foreground" /> : <XCircleIcon className="size-4 shrink-0 text-destructive" />}
                <span className="font-medium">{t.sunat.servicios[s.servicio]}</span>
                <Etiqueta tono={s.disponible ? "ok" : "error"}>{s.disponible ? t.sunat.disponible : t.sunat.noDisponible}</Etiqueta>
                <span className="ml-auto text-control text-muted-foreground tabular-nums">
                  {s.disponible && s.milisegundos !== undefined ? t.sunat.milisegundos.replace("{ms}", String(s.milisegundos)) : (s.detalle ?? "")}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
