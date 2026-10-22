"use client";

import { SaveIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { Alerta } from "@/components/feedback/alerta";
import { apiRequest } from "@/lib/api/browser";
import type { CuerpoDeEnvioAlCliente, PreferenciaDeEnvioAlCliente } from "@/lib/api/empresas";
import { AYUDA_CAMPO, BOTON_PRIMARIO } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";

/**
 * #325: la preferencia «enviar automáticamente» el comprobante aceptado a su cliente. Sin correo en el servidor (`correo_disponible: false`) no se puede activar —la API lo rechaza
 * con 503—, así que se dice antes en vez de dejar que la persona lo descubra al guardar; desactivarla sí se puede siempre.
 */
export function EnvioAlClienteForm({ inicial }: { inicial: PreferenciaDeEnvioAlCliente }) {
  const router = useRouter();
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
        <Alerta tono="aviso" titulo="El servidor no tiene correo configurado">
          Por ahora khipu no puede enviar correos, así que el envío automático no se puede activar. Descarga el PDF desde el comprobante y envíalo por tu cuenta, o pide a soporte que
          configure el correo.
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
          <span className="font-medium">Enviar automáticamente el comprobante al cliente cuando SUNAT lo acepte</span>
          <span className={AYUDA_CAMPO}>
            Le llegan el PDF, el XML firmado y la constancia de SUNAT (CDR) al correo que indiques al emitir. Una nota de crédito o débito va al correo del comprobante que modifica, y una
            boleta informada en el resumen diario sale cuando SUNAT acepta el resumen. Vale para lo que se acepte desde ahora; al desactivarlo, lo que esperaba salir ya no sale.
          </span>
        </span>
      </label>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      {ok ? (
        <p className="text-sm text-success-foreground" role="status">
          {guardado ? "Envío automático activado." : "Envío automático desactivado."}
        </p>
      ) : null}

      <div className="flex flex-wrap items-center justify-end gap-3 border-t border-border/60 pt-4">
        <button type="submit" disabled={enviando || activado === guardado} className={BOTON_PRIMARIO}>
          <SaveIcon className="size-4" />
          {enviando ? "Guardando…" : "Guardar preferencia"}
        </button>
      </div>
    </form>
  );
}
