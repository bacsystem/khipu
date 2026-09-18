"use client";

import { MailIcon, SendIcon, XIcon } from "lucide-react";
import { useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import { BOTON_SECUNDARIO, CAMPO } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

/**
 * `POST /v1/facturas/{id}/correo`: envía al adquirente el PDF, el XML firmado y el CDR. El correo lo escribe el usuario cada vez
 * (el comprobante no guarda el email del cliente); el mensaje es opcional y encabeza el cuerpo.
 */
export function CorreoButton({ id, numero }: { id: string; numero: string }) {
  const [abierto, setAbierto] = useState(false);
  const [email, setEmail] = useState("");
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
    setEmail("");
    setMensaje("");
  }

  if (!abierto) {
    return (
      <div className="flex items-center gap-2">
        <button type="button" onClick={() => setAbierto(true)} className={cn(BOTON_SECUNDARIO, "h-8 text-xs")} data-testid="enviar-correo">
          <MailIcon className="size-4 text-muted-foreground" />
          Enviar por correo
        </button>
        {enviadoA ? <span className="text-xs text-success-foreground" role="status" data-testid="correo-enviado">Enviado a {enviadoA}</span> : null}
      </div>
    );
  }
  return (
    <div className="flex w-full flex-col gap-2 rounded-lg border border-border bg-card p-3 text-xs shadow-2xs md:w-auto md:min-w-96" role="dialog" aria-label={`Enviar ${numero} por correo`} data-testid="correo-form">
      <p className="leading-relaxed text-foreground">
        Se enviará <span className="font-mono font-semibold">{numero}</span> con el PDF, el XML firmado y la constancia de SUNAT adjuntos.
      </p>
      <label htmlFor="correo-email" className="text-[12px] font-medium text-foreground">Correo del cliente</label>
      <input id="correo-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} maxLength={254} placeholder="compras@cliente.pe" className={cn(CAMPO, "h-9")} autoFocus />
      <label htmlFor="correo-mensaje" className="text-[12px] font-medium text-foreground">Mensaje (opcional)</label>
      <textarea id="correo-mensaje" value={mensaje} onChange={(e) => setMensaje(e.target.value)} maxLength={1000} rows={2} placeholder="Gracias por su compra." className={cn(CAMPO, "h-auto py-2")} />
      {error ? <p className="text-destructive" role="alert">{error}</p> : null}
      <div className="flex flex-wrap gap-2">
        <button type="button" disabled={!email.includes("@") || enviando} onClick={enviar} className="inline-flex h-8 items-center gap-1.5 rounded-md bg-primary px-3 text-xs font-semibold text-primary-foreground shadow-2xs disabled:cursor-not-allowed disabled:opacity-60">
          <SendIcon className="size-3.5" />
          {enviando ? "Enviando…" : "Enviar"}
        </button>
        <button type="button" onClick={() => setAbierto(false)} className={cn(BOTON_SECUNDARIO, "h-8 text-xs")}>
          <XIcon className="size-3.5" />
          Cancelar
        </button>
      </div>
    </div>
  );
}
