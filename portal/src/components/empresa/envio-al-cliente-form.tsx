"use client";

import { FloppyDiskIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { type FormEvent, useState } from "react";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { Casilla } from "khipu-design-system/formularios/casilla";
import { apiRequest } from "@/lib/api/browser";
import type { CuerpoDeEnvioAlCliente, PreferenciaDeEnvioAlCliente } from "@/lib/api/empresas";
import { BOTON_PRIMARIO } from "@/lib/estilos";
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
      <Casilla
        id="envio-al-cliente"
        etiqueta={t.casilla}
        descripcion={t.casillaAyuda}
        checked={activado}
        disabled={bloqueado || enviando}
        onCheckedChange={(v) => {
          setActivado(v);
          setOk(false);
        }}
      />

      {error ? <p className="text-cuerpo text-destructive">{error}</p> : null}
      {ok ? (
        <p className="text-cuerpo text-success-foreground" role="status">
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
