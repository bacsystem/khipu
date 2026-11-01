"use client";

import { EnvelopeSimpleIcon, PaperPlaneTiltIcon, XIcon } from "@phosphor-icons/react";
import { useMessages, useTranslations } from "next-intl";
import { useState } from "react";
import { buttonVariants } from "khipu-design-system/ui/button";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { apiRequest } from "@/lib/api/browser";
import { ACCION_SECUNDARIA, CAMPO } from "@/lib/estilos";
import { cn } from "@/lib/utils";
import { useSoloLectura } from "@/lib/solo-lectura";

/**
 * `POST /v1/facturas/{id}/correo`: envía al adquirente el PDF, el XML firmado y el CDR. Propone el correo con que se emitió el comprobante (#325), si lo tiene; se puede
 * cambiar por cualquier otro. El mensaje es opcional y encabeza el cuerpo.
 */
export function CorreoButton({ id, numero, correoDelCliente = null }: { id: string; numero: string; correoDelCliente?: string | null }) {
  const soloLectura = useSoloLectura();
  const messages = useMessages();
  const t = messages.comprobantes.correo;
  const tr = useTranslations("comprobantes.correo");
  const mensajeError = useMensajeError();
  const [abierto, setAbierto] = useState(false);
  const [email, setEmail] = useState(correoDelCliente ?? "");
  const [mensaje, setMensaje] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [enviadoA, setEnviadoA] = useState<string | null>(null);

  async function enviar() {
    setEnviando(true);
    setError(null);
    const res = await apiRequest<null>(`/api/proxy/facturas/${id}/correo`, { method: "POST", body: { email: email.trim(), mensaje: mensaje.trim() || null } });
    setEnviando(false);
    if (res.estado !== "exito") {
      setError(res.errores?.email?.[0] ?? res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setEnviadoA(email.trim());
    setAbierto(false);
    setEmail(correoDelCliente ?? "");
    setMensaje("");
  }

  if (!abierto) {
    return (
      <div className="flex items-center gap-2">
        <button type="button" disabled={soloLectura !== null} title={soloLectura ?? undefined} onClick={() => setAbierto(true)} className={ACCION_SECUNDARIA} data-testid="enviar-correo">
          <EnvelopeSimpleIcon className="size-4 text-muted-foreground" />
          {t.boton}
        </button>
        {enviadoA ? <span className="text-xs text-success-foreground" role="status" data-testid="correo-enviado">{interpolar(t.enviadoA, { email: enviadoA })}</span> : null}
      </div>
    );
  }
  return (
    <div className="flex w-full flex-col gap-2 rounded-lg border border-border bg-card p-3 text-xs shadow-2xs md:w-auto md:min-w-96" role="dialog" aria-label={interpolar(t.dialogo, { numero })} data-testid="correo-form">
      <p className="leading-relaxed text-foreground">
        {tr.rich("seEnviara", { numero, n: (texto) => <span className="font-mono font-semibold">{texto}</span> })}
      </p>
      <label htmlFor="correo-email" className="text-[12px] font-medium text-foreground">{t.correoCliente}</label>
      <input id="correo-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} maxLength={254} placeholder="compras@cliente.pe" className={CAMPO} autoFocus />
      <label htmlFor="correo-mensaje" className="text-[12px] font-medium text-foreground">{t.mensaje}</label>
      <textarea id="correo-mensaje" value={mensaje} onChange={(e) => setMensaje(e.target.value)} maxLength={1000} rows={2} placeholder={t.mensajePlaceholder} className={cn(CAMPO, "h-auto py-2")} />
      {error ? <p className="text-destructive" role="alert">{error}</p> : null}
      <div className="flex flex-wrap gap-2">
        <button type="button" disabled={!email.includes("@") || enviando} onClick={enviar} className={cn(buttonVariants())}>
          <PaperPlaneTiltIcon className="size-3.5" />
          {enviando ? t.enviando : t.enviar}
        </button>
        <button type="button" onClick={() => setAbierto(false)} className={ACCION_SECUNDARIA}>
          <XIcon weight="bold" className="size-3.5" />
          {messages.comun.cancelar}
        </button>
      </div>
    </div>
  );
}
