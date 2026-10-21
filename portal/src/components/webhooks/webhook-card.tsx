"use client";

import { BanIcon, ChevronDownIcon, KeyRoundIcon, PlayIcon, PowerIcon, Trash2Icon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useLocale, useMessages } from "next-intl";
import { useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "khipu-design-system/ui/dialog";
import { apiRequest } from "@/lib/api/browser";
import type { Webhook } from "@/lib/api/webhooks";
import { BOTON_PRIMARIO } from "@/lib/estilos";
import { formatearFechaHora } from "@/lib/formato";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";
import { cn } from "@/lib/utils";
import { PAUSA_TRAS_FALLOS, resumenDeEventos } from "@/lib/webhooks/eventos";
import { EntregasWebhook } from "./entregas-webhook";
import { SecretoWebhookRevelado } from "./secreto-webhook-revelado";

const ACCION =
  "inline-flex h-9 items-center gap-1 rounded-lg px-2.5 text-[12px] font-medium whitespace-nowrap transition-colors disabled:cursor-not-allowed disabled:opacity-60";
const ACCION_NEUTRA = cn(ACCION, "border border-border bg-card text-foreground/80 shadow-2xs hover:bg-muted");

function EstadoBadge({ webhook }: { webhook: Webhook }) {
  const t = useMessages().webhooks.tarjeta;
  if (webhook.activo)
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full border border-success-border bg-success px-2.5 py-0.5 font-mono text-[11px] font-medium text-success-foreground">
        <span className="size-1.5 rounded-full bg-success-solid" />
        {t.activo}
      </span>
    );
  const pausadoPorKhipu = Boolean(webhook.pausado_motivo);
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 font-mono text-[11px] font-medium",
        pausadoPorKhipu ? "border-warning-border bg-warning text-warning-foreground" : "border-border bg-secondary text-muted-foreground",
      )}
    >
      <span className={cn("size-1.5 rounded-full", pausadoPorKhipu ? "bg-warning-solid" : "bg-muted-foreground/60")} />
      {pausadoPorKhipu ? t.pausado : t.apagado}
    </span>
  );
}

/** Un endpoint de webhooks (#19): su estado, sus eventos y las acciones (probar, apagar o activar, rotar el secreto, eliminar) más el log de entregas. */
export function WebhookCard({ webhook }: { webhook: Webhook }) {
  const soloLectura = useSinPermisoDeConfigurar();
  const messages = useMessages();
  const locale = useLocale();
  const t = messages.webhooks.tarjeta;
  const mensajeError = useMensajeError();
  const router = useRouter();
  const [verEntregas, setVerEntregas] = useState(false);
  const [versionLog, setVersionLog] = useState(0);
  const [trabajando, setTrabajando] = useState<string | null>(null);
  const [confirmando, setConfirmando] = useState<"eliminar" | "rotar" | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [secretoNuevo, setSecretoNuevo] = useState<string | null>(null);

  async function llamar<T>(accion: string, path: string, method: string, body?: unknown) {
    setTrabajando(accion);
    setError(null);
    setAviso(null);
    const res = await apiRequest<T>(path, { method, body });
    setTrabajando(null);
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return null;
    }
    return res;
  }

  async function probar() {
    const res = await llamar("probar", `/api/proxy/webhooks/${webhook.id}/prueba`, "POST");
    if (!res) return;
    setAviso(t.pruebaEnCola);
    setVerEntregas(true);
    setVersionLog((n) => n + 1);
    // El trabajo de entrega corre cada pocos segundos: se vuelve a leer el log para ver cómo terminó.
    window.setTimeout(() => setVersionLog((n) => n + 1), 7000);
  }

  async function alternarActivo() {
    const res = await llamar("activo", `/api/proxy/webhooks/${webhook.id}`, "PATCH", { activo: !webhook.activo });
    if (res) router.refresh();
  }

  async function rotar() {
    const res = await llamar<{ secreto: string }>("rotar", `/api/proxy/webhooks/${webhook.id}/secreto`, "POST");
    setConfirmando(null);
    if (res?.datos) setSecretoNuevo(res.datos.secreto);
  }

  async function eliminar() {
    const res = await llamar("eliminar", `/api/proxy/webhooks/${webhook.id}`, "DELETE");
    setConfirmando(null);
    if (res) router.refresh();
  }

  const bloqueado = soloLectura !== null || trabajando !== null;

  return (
    <article data-testid="webhook" aria-label={interpolar(t.aria, { url: webhook.url })} className="min-w-0 overflow-hidden rounded-xl border border-border/90 bg-card shadow-2xs">
      <div className="grid gap-3 px-4 py-3">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-[13px] font-semibold break-all text-foreground">{webhook.url}</span>
              <EstadoBadge webhook={webhook} />
            </div>
            <p className="mt-0.5 text-[12px] text-muted-foreground">
              {webhook.descripcion ? <>{webhook.descripcion} · </> : null}
              {interpolar(t.creado, { eventos: resumenDeEventos(webhook.eventos, messages), fecha: formatearFechaHora(webhook.creado_en, locale) })}
            </p>
          </div>
        </div>

        {webhook.pausado_motivo ? (
          <div role="status" className="flex items-start gap-2 rounded-lg border border-warning-border bg-warning px-3 py-2 text-[12px] leading-relaxed text-warning-foreground">
            <BanIcon className="mt-0.5 size-4 shrink-0" />
            <p>
              {interpolar(t.pausa, { motivo: webhook.pausado_motivo, n: PAUSA_TRAS_FALLOS })}
            </p>
          </div>
        ) : webhook.activo && webhook.fallos_consecutivos > 0 ? (
          <p className="text-[12px] text-warning-foreground">
            {interpolar(webhook.fallos_consecutivos === 1 ? t.falloUno : t.fallosVarios, { n: webhook.fallos_consecutivos, max: PAUSA_TRAS_FALLOS })}
          </p>
        ) : null}

        <div className="flex flex-wrap items-center gap-1.5">
          <button type="button" disabled={bloqueado || !webhook.activo} title={soloLectura ?? (webhook.activo ? undefined : t.activaloParaProbar)} onClick={probar} className={ACCION_NEUTRA}>
            <PlayIcon className="size-3.5" />
            {trabajando === "probar" ? t.enviando : t.enviarPrueba}
          </button>
          <button type="button" disabled={bloqueado} title={soloLectura ?? undefined} onClick={alternarActivo} className={ACCION_NEUTRA}>
            <PowerIcon className="size-3.5" />
            {webhook.activo ? t.apagar : t.activar}
          </button>
          {confirmando === "rotar" ? (
            <>
              <span className="text-[11px] text-muted-foreground">{t.confirmarRotar}</span>
              <button type="button" disabled={bloqueado} onClick={rotar} className={cn(ACCION, "bg-destructive text-destructive-foreground shadow-xs hover:bg-destructive/90")}>
                {t.siRotar}
              </button>
              <button type="button" onClick={() => setConfirmando(null)} className={ACCION_NEUTRA}>
                {messages.comun.cancelar}
              </button>
            </>
          ) : (
            <button type="button" disabled={bloqueado} title={soloLectura ?? undefined} onClick={() => setConfirmando("rotar")} className={ACCION_NEUTRA}>
              <KeyRoundIcon className="size-3.5" />
              {t.rotar}
            </button>
          )}
          {confirmando === "eliminar" ? (
            <>
              <span className="text-[11px] text-muted-foreground">{t.confirmarEliminar}</span>
              <button type="button" disabled={bloqueado} onClick={eliminar} className={cn(ACCION, "bg-destructive text-destructive-foreground shadow-xs hover:bg-destructive/90")}>
                {t.siEliminar}
              </button>
              <button type="button" onClick={() => setConfirmando(null)} className={ACCION_NEUTRA}>
                {messages.comun.cancelar}
              </button>
            </>
          ) : (
            <button type="button" disabled={bloqueado} title={soloLectura ?? undefined} onClick={() => setConfirmando("eliminar")} className={cn(ACCION, "text-destructive hover:bg-destructive/10")}>
              <Trash2Icon className="size-3.5" />
              {t.eliminar}
            </button>
          )}
          <button type="button" aria-expanded={verEntregas} onClick={() => setVerEntregas((v) => !v)} className={cn(ACCION_NEUTRA, "ml-auto")}>
            {t.entregas}
            <ChevronDownIcon className={cn("size-3.5 transition-transform", verEntregas && "rotate-180")} />
          </button>
        </div>

        {aviso ? (
          <p role="status" className="text-[12px] text-success-foreground">
            {aviso}
          </p>
        ) : null}
        {error ? (
          <p role="alert" className="text-[12px] text-destructive">
            {error}
          </p>
        ) : null}
      </div>

      {verEntregas ? <EntregasWebhook webhookId={webhook.id} version={versionLog} /> : null}

      <Dialog open={secretoNuevo !== null} onOpenChange={(abierto) => !abierto && setSecretoNuevo(null)}>
        <DialogContent className="gap-0 p-0">
          <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
            <DialogTitle className="text-base font-semibold tracking-tight">{t.nuevoSecreto}</DialogTitle>
            <DialogDescription className="text-[13px]">{t.nuevoSecretoDescripcion}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 px-5 py-4">{secretoNuevo ? <SecretoWebhookRevelado secreto={secretoNuevo} /> : null}</div>
          <div className="flex justify-end border-t border-border/60 px-5 py-3">
            <button type="button" onClick={() => setSecretoNuevo(null)} className={BOTON_PRIMARIO}>
              {messages.webhooks.listo}
            </button>
          </div>
        </DialogContent>
      </Dialog>
    </article>
  );
}
