"use client";

import { PlusIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { type FormEvent, useState } from "react";
import { EstadoConsultaRuc } from "@/components/consulta-ruc/estado-consulta-ruc";
import { useAutocompletadoDelPadron } from "@/components/consulta-ruc/use-consulta-ruc";
import { apiRequest } from "@/lib/api/browser";
import type { Empresa, Entorno } from "@/lib/api/empresas";
import { SelectCampo } from "khipu-design-system/formularios/select-campo";
import { Campo, Entrada } from "khipu-design-system/formularios/campo";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { BOTON_PRIMARIO, BOTON_SECUNDARIO } from "@/lib/estilos";
import { useMensajeError } from "@/i18n/use-mensaje-error";

export function NuevaEmpresaForm({ onGuardado, onCancelar }: { onGuardado?: () => void; onCancelar?: () => void }) {
  const router = useRouter();
  const messages = useMessages();
  const t = messages.empresa.nueva;
  const tc = messages.empresa.comun;
  const mensajeError = useMensajeError();
  const [ruc, setRuc] = useState("");
  const [razonSocial, setRazonSocial] = useState("");
  const [entorno, setEntorno] = useState<Entorno>("BETA");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // #575: con el RUC completo se consulta el padrón de SUNAT y se rellena la razón social si está vacía.
  const padron = useAutocompletadoDelPadron({ ruc, campos: [{ valor: razonSocial, poner: setRazonSocial, dato: (c) => c.razon_social }] });

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setEnviando(true);
    setError(null);
    const res = await apiRequest<Empresa>("/api/proxy/empresas", {
      method: "POST",
      body: { ruc, razon_social: razonSocial, entorno },
    });
    if (res.estado !== "exito" || !res.datos) {
      setEnviando(false);
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    // La empresa nueva pasa a ser la activa de la sesión.
    await apiRequest("/api/session/empresa", {
      method: "POST",
      body: { empresaId: res.datos.id },
    });
    setEnviando(false);
    router.refresh();
    onGuardado?.();
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-2">
        <Campo id="nueva-ruc" etiqueta={tc.ruc} ayuda={t.rucAyuda}>
          <Entrada
            id="nueva-ruc"
            value={ruc}
            onChange={(e) => setRuc(e.target.value.replace(/\D/g, "").slice(0, 11))}
            inputMode="numeric"
            pattern="[0-9]{11}"
            placeholder="20123456786"
            required
            autoFocus
            mono
            aria-describedby="nueva-ruc-ayuda"
          />
        </Campo>
        <Campo id="nuevo-entorno" etiqueta={tc.entornoSunat} ayuda={t.entornoAyuda}>
          <SelectCampo
            id="nuevo-entorno"
            valor={entorno}
            onCambio={(v) => setEntorno(v as Entorno)}
            opciones={[
              { value: "BETA", label: t.beta },
              { value: "PRODUCCION", label: t.produccion },
            ]}
          />
        </Campo>
      </div>
      <EstadoConsultaRuc
        contexto="emisor"
        consultando={padron.consultando}
        resultado={padron.resultado}
        nombreActual={razonSocial}
        onReintentar={padron.reintentar}
        onUsarNombre={(n) => padron.usarDato(0, n)}
        className="-mt-2"
      />

      <Campo id="nueva-razon" etiqueta={tc.razonSocial}>
        <Entrada id="nueva-razon" value={razonSocial} onChange={(e) => setRazonSocial(e.target.value)} placeholder={t.razonSocialPlaceholder} required />
      </Campo>

      {error ? <p className="text-cuerpo text-destructive">{error}</p> : null}

      <div className="mt-1 flex items-center justify-end gap-2 border-t border-border/60 pt-4">
        {onCancelar ? (
          <button type="button" onClick={onCancelar} className={BOTON_SECUNDARIO}>
            {messages.comun.cancelar}
          </button>
        ) : null}
        <BotonAsync type="submit" pendiente={enviando} icon={<PlusIcon weight="bold" className="size-4" />} textoPendiente={t.registrando} className={BOTON_PRIMARIO}>
          {t.registrar}
        </BotonAsync>
      </div>
    </form>
  );
}
