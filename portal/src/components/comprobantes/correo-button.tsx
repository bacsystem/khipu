"use client";

import { EnvelopeSimpleIcon, PaperPlaneTiltIcon } from "@phosphor-icons/react";
import { useMessages, useTranslations } from "next-intl";
import { useState } from "react";
import { AreaTexto, Campo, Entrada } from "khipu-design-system/formularios/campo";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { CabeceraDialogo, PieDialogo } from "khipu-design-system/patrones/cabecera-dialogo";
import { Dialog, DialogContent, DialogTrigger } from "khipu-design-system/ui/dialog";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { apiRequest } from "@/lib/api/browser";
import { ACCION_SECUNDARIA, BOTON_PRIMARIO, BOTON_SECUNDARIO } from "@/lib/estilos";
import { useSoloLectura } from "@/lib/solo-lectura";

/**
 * `POST /v1/facturas/{id}/correo`: envía al adquirente el PDF, el XML firmado y el CDR. Propone el correo con que se emitió el comprobante (#325), si lo tiene; se puede
 * cambiar por cualquier otro. El mensaje es opcional y encabeza el cuerpo. El formulario va en un `Dialog` del DS (antes, un `div role=dialog` que empujaba la barra de acciones).
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

  function cambiarAbierto(valor: boolean) {
    if (enviando) return;
    setAbierto(valor);
    if (!valor) setError(null);
  }

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

  return (
    <div className="flex items-center gap-2">
      <Dialog open={abierto} onOpenChange={cambiarAbierto}>
        <DialogTrigger disabled={soloLectura !== null} title={soloLectura ?? undefined} className={ACCION_SECUNDARIA} data-testid="enviar-correo">
          <EnvelopeSimpleIcon weight="bold" className="size-4 text-muted-foreground" />
          {t.boton}
        </DialogTrigger>
        <DialogContent className="gap-0 p-0 sm:max-w-md" data-testid="correo-form" showCloseButton={!enviando}>
          <CabeceraDialogo
            icon={EnvelopeSimpleIcon}
            titulo={interpolar(t.dialogo, { numero })}
            descripcion={tr.rich("seEnviara", { numero, n: (texto) => <span className="font-mono font-semibold">{texto}</span> })}
          />
          <div className="grid gap-4 px-5 py-4">
            <Campo id="correo-email" etiqueta={t.correoCliente}>
              <Entrada id="correo-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} maxLength={254} placeholder="compras@cliente.pe" autoFocus />
            </Campo>
            <Campo id="correo-mensaje" etiqueta={t.mensaje}>
              <AreaTexto id="correo-mensaje" value={mensaje} onChange={(e) => setMensaje(e.target.value)} maxLength={1000} rows={2} placeholder={t.mensajePlaceholder} />
            </Campo>
            {error ? <p className="text-cuerpo text-destructive" role="alert">{error}</p> : null}
          </div>
          <PieDialogo>
            <button type="button" disabled={enviando} onClick={() => cambiarAbierto(false)} className={BOTON_SECUNDARIO}>
              {messages.comun.cancelar}
            </button>
            <BotonAsync
              pendiente={enviando}
              disabled={!email.includes("@")}
              onClick={enviar}
              className={BOTON_PRIMARIO}
              icon={<PaperPlaneTiltIcon weight="bold" className="size-3.5" />}
              textoPendiente={t.enviando}
            >
              {t.enviar}
            </BotonAsync>
          </PieDialogo>
        </DialogContent>
      </Dialog>
      {enviadoA ? <span className="text-etiqueta text-success-foreground" role="status" data-testid="correo-enviado">{interpolar(t.enviadoA, { email: enviadoA })}</span> : null}
    </div>
  );
}
