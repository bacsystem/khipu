"use client";

import { FloppyDiskIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { type FormEvent, useState } from "react";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { apiRequest } from "@/lib/api/browser";
import type { CuerpoDeEnvioAlCliente, PreferenciaDeEnvioAlCliente } from "@/lib/api/empresas";
import { AYUDA_CAMPO, BOTON_PRIMARIO } from "@/lib/estilos";
import { useMensajeError } from "@/i18n/use-mensaje-error";

/**
 * #325: la preferencia «enviar automáticamente» el comprobante aceptado a su cliente. Sin correo en el servidor (`correo_disponible: false`) no se puede activar —la API lo rechaza
 * con 503—, así que se dice antes en vez de dejar que la persona lo descubra al guardar; desactivarla sí se puede siempre.
 */
export function EnvioAlClienteForm({ inicial }: { inicial: PreferenciaDeEnvioAlCliente }) {
  const router = useRouter();
  const messages = useMessages();
  const t = messages.empresa.envioAlCliente;
  const mensajeError = useMensajeError();
  const [activado, setActivado] = useState(inicial.enviar_automaticamente);
  const [guardado, setGuardado] = useState(inicial.enviar_automaticamente);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState(false);
  const sinCorreo = !inicial.correo_disponible;
  // Sin correo solo se puede apagar: la casilla se bloquea cuando está apagada, para no ofrecer lo que la API va a rechazar.
  const bloqueado = sinCorreo && !activado;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setEnviando(true);
    setError(null);
    setOk(false);
    const body: CuerpoDeEnvioAlCliente = { enviar_automaticamente: activado };
    const res = await apiRequest<PreferenciaDeEnvioAlCliente>("/api/proxy/empresa/envio-al-cliente", { method: "PUT", body });
    setEnviando(false);
    if (res.estado !== "exito" || !res.datos) {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setGuardado(res.datos.enviar_automaticamente);
    setActivado(res.datos.enviar_automaticamente);
    setOk(true);
    router.refresh();
  }

  return (
    <form onSubmit={onSubmit} className="grid grid-cols-1 gap-4" data-testid="form-envio-al-cliente">
      {sinCorreo ? (
        <Alerta tono="aviso" titulo={t.sinCorreoTitulo}>
          {t.sinCorreoTexto}
        </Alerta>
      ) : null}
      <label className="flex cursor-pointer items-start gap-2.5 text-[13px] text-foreground has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-60">
        <input
          type="checkbox"
          checked={activado}
          disabled={bloqueado || enviando}
          onChange={(e) => {
            setActivado(e.target.checked);
            setOk(false);
          }}
          className="mt-0.5 size-4 accent-primary"
        />
        <span className="grid gap-0.5">
          <span className="font-medium">{t.casilla}</span>
          <span className={AYUDA_CAMPO}>
            {t.casillaAyuda}
          </span>
        </span>
      </label>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      {ok ? (
        <p className="text-sm text-success-foreground" role="status">
          {guardado ? t.activado : t.desactivado}
        </p>
      ) : null}

      <div className="flex flex-wrap items-center justify-end gap-3 border-t border-border/60 pt-4">
        <button type="submit" disabled={enviando || activado === guardado} className={BOTON_PRIMARIO}>
          <FloppyDiskIcon className="size-4" />
          {enviando ? messages.empresa.comun.guardando : t.guardar}
        </button>
      </div>
    </form>
  );
}
