"use client";

import { ArrowClockwiseIcon, PaperPlaneTiltIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { useRef, useState } from "react";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { apiRequest } from "@/lib/api/browser";
import { noSeSabeSiLlego } from "@/lib/api/corte-de-red";
import { estaEnviandoGuia, puedeConsultarTicketDeGuia, puedeEnviarGuia, type Guia } from "@/lib/api/guias";
import { ACCION_PRINCIPAL, ACCION_SECUNDARIA, AYUDA_CAMPO } from "@/lib/estilos";
import { useSoloLectura } from "@/lib/solo-lectura";

type Accion = "enviar" | "ticket" | "retomar";

/**
 * Lo que se puede hacer con una guía según su estado (#39): enviarla o reenviarla (`FIRMADO`, `ERROR_ENVIO`), consultar su ticket (`ENVIADO_PENDIENTE_TICKET`) o, si
 * un envío quedó en curso sin respuesta (`ENVIANDO`), retomarlo con `forzar=true`. Este último no se ofrece a ciegas: no se sabe si SUNAT recibió la guía, y reenviarla
 * podría duplicarla, así que pide marcar que ya se revisó en SOL.
 */
export function GuiaAcciones({ guia }: { guia: Guia }) {
  const messages = useMessages();
  const t = messages.guias.acciones;
  const mensajeError = useMensajeError();
  const router = useRouter();
  const soloLectura = useSoloLectura();
  const [haciendo, setHaciendo] = useState<Accion | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reviseEnSol, setReviseEnSol] = useState(false);
  // Un ref y no el estado: dos clics en el mismo tick leen el `haciendo` viejo del closure y mandarían dos pedidos.
  const enCurso = useRef(false);

  const enviar = puedeEnviarGuia(guia);
  const ticket = puedeConsultarTicketDeGuia(guia);
  const retomable = estaEnviandoGuia(guia);
  if (!enviar && !ticket && !retomable) return null;

  async function ejecutar(accion: Accion) {
    if (enCurso.current) return;
    enCurso.current = true;
    setHaciendo(accion);
    setError(null);
    const base = `/api/proxy/guias-remision/${encodeURIComponent(guia.id)}`;
    const ruta = accion === "ticket" ? `${base}/ticket` : accion === "retomar" ? `${base}/enviar?forzar=true` : `${base}/enviar`;
    const res = await apiRequest<Guia>(ruta, { method: "POST" });
    enCurso.current = false;
    setHaciendo(null);
    if (noSeSabeSiLlego(res)) {
      // Un corte no dice si SUNAT recibió la guía: no se reintenta a ciegas, se manda a ver el estado real.
      setError(t.sinConexion);
      return;
    }
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setReviseEnSol(false);
    router.refresh();
  }

  return (
    <div className="flex flex-col items-end gap-2" data-testid="guia-acciones">
      <div className="flex flex-wrap items-center justify-end gap-2">
        {enviar ? (
          <button
            type="button"
            onClick={() => ejecutar("enviar")}
            disabled={haciendo !== null || soloLectura !== null}
            title={soloLectura ?? undefined}
            className={ACCION_PRINCIPAL}
          >
            <PaperPlaneTiltIcon className="size-4" />
            {haciendo === "enviar" ? t.enviando : guia.estado === "ERROR_ENVIO" ? t.reenviar : t.enviar}
          </button>
        ) : null}
        {ticket ? (
          <button
            type="button"
            onClick={() => ejecutar("ticket")}
            disabled={haciendo !== null || soloLectura !== null}
            title={soloLectura ?? undefined}
            className={ACCION_SECUNDARIA}
          >
            <ArrowClockwiseIcon weight="bold" className="size-4" />
            {haciendo === "ticket" ? t.consultando : t.consultarTicket}
          </button>
        ) : null}
      </div>

      {retomable ? (
        <div className="flex max-w-md flex-col items-end gap-2 text-right">
          <p className={AYUDA_CAMPO}>{t.retomarAyuda}</p>
          <label className="flex items-center gap-2 text-control text-foreground">
            <input type="checkbox" checked={reviseEnSol} onChange={(e) => setReviseEnSol(e.target.checked)} className="size-3.5 accent-primary" />
            {t.retomarConfirmacion}
          </label>
          <button
            type="button"
            onClick={() => ejecutar("retomar")}
            disabled={!reviseEnSol || haciendo !== null || soloLectura !== null}
            title={soloLectura ?? undefined}
            className={ACCION_SECUNDARIA}
          >
            <PaperPlaneTiltIcon className="size-4" />
            {haciendo === "retomar" ? t.enviando : t.retomar}
          </button>
        </div>
      ) : null}

      {error ? (
        <p role="alert" className="max-w-md text-right text-cuerpo text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}
