"use client";

import { FloppyDiskIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { type FormEvent, useState } from "react";
import { Interruptor } from "khipu-design-system/formularios/casilla";
import { guardarPreferenciasDeEmision, type PreferenciasDeEmision } from "@/lib/api/preferencias-emision";
import { BOTON_PRIMARIO } from "@/lib/estilos";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";
import { useMensajeError } from "@/i18n/use-mensaje-error";

/**
 * «Mostrar opciones avanzadas al emitir»: con ella apagada (por defecto) el modal de emisión no ofrece el tipo de operación/exportación, los anticipos, la percepción, la
 * retención, «Más opciones» ni las referencias; el crédito, la detracción y el correo están siempre. Solo un administrador la cambia: a los demás roles se les muestra
 * deshabilitada con el motivo, como el resto de la configuración (#21).
 */
export function PreferenciasEmisionForm({ inicial }: { inicial: PreferenciasDeEmision }) {
  const router = useRouter();
  const messages = useMessages();
  const t = messages.empresa.preferenciasEmision;
  const mensajeError = useMensajeError();
  const sinPermiso = useSinPermisoDeConfigurar();
  const [activadas, setActivadas] = useState(inicial.opciones_avanzadas);
  const [guardadas, setGuardadas] = useState(inicial.opciones_avanzadas);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (sinPermiso) return;
    setEnviando(true);
    setError(null);
    setOk(false);
    const res = await guardarPreferenciasDeEmision(activadas);
    setEnviando(false);
    if (res.estado !== "exito" || !res.datos) {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setGuardadas(res.datos.opciones_avanzadas);
    setActivadas(res.datos.opciones_avanzadas);
    setOk(true);
    router.refresh();
  }

  return (
    <form onSubmit={onSubmit} className="grid grid-cols-1 gap-4" data-testid="form-preferencias-emision">
      <Interruptor
        id="opciones-avanzadas-emision"
        etiqueta={t.interruptor}
        descripcion={t.interruptorAyuda}
        checked={activadas}
        disabled={sinPermiso !== null || enviando}
        onCheckedChange={(v) => {
          setActivadas(v);
          setOk(false);
        }}
      />
      {sinPermiso ? <p className="text-control text-muted-foreground">{t.soloAdmin}</p> : null}

      {error ? <p className="text-cuerpo text-destructive">{error}</p> : null}
      {ok ? (
        <p className="text-cuerpo text-success-foreground" role="status">
          {guardadas ? t.activadas : t.desactivadas}
        </p>
      ) : null}

      <div className="flex flex-wrap items-center justify-end gap-3 border-t border-border/60 pt-4">
        <button type="submit" disabled={sinPermiso !== null || enviando || activadas === guardadas} className={BOTON_PRIMARIO}>
          <FloppyDiskIcon className="size-4" />
          {enviando ? messages.empresa.comun.guardando : t.guardar}
        </button>
      </div>
    </form>
  );
}
