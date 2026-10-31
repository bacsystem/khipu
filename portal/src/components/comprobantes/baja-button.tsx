"use client";

import { BanIcon, TriangleAlertIcon } from "lucide-react";
import { useMessages, useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "khipu-design-system/ui/dialog";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { apiRequest } from "@/lib/api/browser";
import { seCortoLaConexion } from "@/lib/api/corte-de-red";
import { type Baja, PLAZO_BAJA_DIAS } from "@/lib/api/facturas";
import { ACCION_SECUNDARIA, AYUDA_CAMPO, BOTON_DESTRUCTIVO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { diasEntre, hoyLima } from "@/lib/formato";
import { cn } from "@/lib/utils";
import { useSoloLectura } from "@/lib/solo-lectura";

/**
 * Comunicación de baja (`POST /v1/facturas/{id}/baja`). Es irreversible ante SUNAT, así que pide el motivo y la confirmación
 * en un modal (mismo `Dialog` que las API keys y las empresas) que deja claro el efecto antes de enviar; al aceptarse recarga
 * el detalle, que muestra el estado de la baja.
 *
 * La auditoría del flujo midió tres huecos: un corte de red dejaba el modal en «Enviando a SUNAT…» sin salida (el `fetch`
 * rechaza y `enviando` no volvía a false); un 201 con `estado: RECHAZADA` o `ENVIADA` se trataba como éxito y el modal se
 * cerraba sin decir nada; y nada avisaba de cuántos días quedaban del plazo (2957) ni de las notas que quedarían sobre un
 * comprobante anulado.
 */
export function BajaButton({
  id,
  numero,
  fechaEmision,
  notasVigentes = 0,
  boleta = false,
  reversion = false,
}: {
  id: string;
  numero: string;
  fechaEmision: string;
  notasVigentes?: number;
  /** #20: una boleta no va en una comunicación de baja sino en el resumen diario (RC); el efecto y el plazo son los mismos. */
  boleta?: boolean;
  /** #40: una retención o una percepción se revierte en un resumen de reversiones (RR): SUNAT no valida su plazo contra la emisión. */
  reversion?: boolean;
}) {
  const soloLectura = useSoloLectura();
  const messages = useMessages();
  const t = messages.comprobantes.baja;
  const tr = useTranslations("comprobantes.baja");
  const mensajeError = useMensajeError();
  const router = useRouter();
  const [abierto, setAbierto] = useState(false);
  const [motivo, setMotivo] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const alertaRef = useRef<HTMLParagraphElement>(null);
  // Un ref, no el estado: dos clics en el mismo tick leen el `enviando` viejo del closure (React aplica el estado después
  // del handler), así que `if (enviando) return` no frenaba nada y el `disabled` tampoco había llegado al DOM.
  const enviandoRef = useRef(false);
  useEffect(() => {
    if (error) alertaRef.current?.focus();
  }, [error]);
  const diasRestantes = Math.max(0, PLAZO_BAJA_DIAS - diasEntre(fechaEmision, hoyLima()));

  function cambiarAbierto(valor: boolean) {
    if (enviando) return; // no se cierra mientras SUNAT responde: la baja ya salió y el usuario debe ver el resultado
    setAbierto(valor);
    if (!valor) {
      setMotivo("");
      setError(null);
    }
  }

  async function confirmar() {
    if (enviandoRef.current) return;
    enviandoRef.current = true;
    setEnviando(true);
    setError(null);
    const res = await apiRequest<Baja>(`/api/proxy/facturas/${id}/baja`, { method: "POST", body: { motivo: motivo.trim() } });
    enviandoRef.current = false;
    setEnviando(false);
    // Un corte de conexión no dice si la comunicación llegó a SUNAT, y la baja es irreversible: no se reintenta a
    // ciegas. El backend impide una segunda baja mientras haya una en curso. La respuesta que no es JSON cae más
    // abajo a propósito: su texto (`errores.RESPUESTA_INVALIDA`) manda a recargar para ver el estado real.
    if (seCortoLaConexion(res)) {
      setError(interpolar(t.cortaConexion, { numero }));
      return;
    }
    if (res.estado !== "exito" || !res.datos) {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    if (res.datos.estado === "RECHAZADA") {
      // El 201 no es un éxito: SUNAT ya respondió que no. Se muestra acá, con el CDR, y la ficha lo repite al recargar.
      setError(interpolar(t.rechazada, { cdr: res.datos.cdr ? ` (${res.datos.cdr.codigo} · ${res.datos.cdr.descripcion})` : "", numero }));
      router.refresh();
      return;
    }
    // ACEPTADA (anulado) o ENVIADA/ERROR_ENVIO (en curso): la ficha muestra el estado y, si está en curso, permite reconsultar.
    setAbierto(false);
    setMotivo("");
    router.refresh();
  }

  return (
    <Dialog open={abierto} onOpenChange={cambiarAbierto}>
      <DialogTrigger disabled={soloLectura !== null} title={soloLectura ?? undefined} className={cn(ACCION_SECUNDARIA, "text-destructive hover:text-destructive")} data-testid="dar-de-baja">
        <BanIcon className="size-4" />
        {t.darDeBaja}
      </DialogTrigger>
      <DialogContent className="gap-0 p-0" data-testid="baja-confirmacion" showCloseButton={!enviando}>
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-destructive/10 text-destructive">
              <BanIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">
                {t.darDeBaja} <span className="font-mono">{numero}</span>
              </DialogTitle>
              <DialogDescription className="text-[13px]">
                {reversion ? (
                  t.reversion
                ) : (
                  <>
                    {interpolar(t.plazo, { tramite: boleta ? t.resumenDiario : t.comunicacion, dias: PLAZO_BAJA_DIAS })}{" "}
                    {diasRestantes === 0 ? t.ultimoDia : interpolar(diasRestantes === 1 ? t.quedaUno : t.quedanVarios, { n: diasRestantes })}
                  </>
                )}
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="grid gap-4 px-5 py-4">
          <div className="flex items-start gap-2.5 rounded-lg border border-destructive/40 bg-destructive/5 px-3 py-2.5 text-[12px] leading-relaxed text-foreground">
            <TriangleAlertIcon className="mt-0.5 size-4 shrink-0 text-destructive" />
            <p>
              {tr.rich("efecto", { b: (texto) => <strong className="font-semibold">{texto}</strong> })}
              {notasVigentes > 0 ? interpolar(notasVigentes === 1 ? t.notasUno : t.notasVarias, { n: notasVigentes }) : ""}
            </p>
          </div>
          {boleta ? (
            // #362 H8: la baja de una boleta (o de una nota B) es un RC con la línea en estado 3; SUNAT solo la admite si lo emitido no llegó al adquirente.
            <p className="text-[12px] leading-relaxed text-muted-foreground" data-testid="baja-boleta-condicion">
              {t.condicionBoleta}
            </p>
          ) : null}
          <div className="flex flex-col gap-1.5">
            <label htmlFor="baja-motivo" className={ETIQUETA_CAMPO}>{t.motivo}</label>
            <input
              id="baja-motivo"
              value={motivo}
              onChange={(e) => setMotivo(e.target.value)}
              maxLength={100}
              placeholder={t.motivoPlaceholder}
              className={CAMPO}
              autoFocus
            />
            <span className={AYUDA_CAMPO}>
              {t.motivoAyuda}{" "}
              {boleta ? t.motivoSoloKhipu : t.motivoEnXml}
              {motivo.length >= 100 ? t.motivoMaximo : ""}
            </span>
          </div>
          {error ? <p ref={alertaRef} tabIndex={-1} className="text-sm text-destructive outline-none" role="alert">{error}</p> : null}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-border/60 px-5 py-3">
          <button type="button" disabled={enviando} onClick={() => cambiarAbierto(false)} className={BOTON_SECUNDARIO}>
            {messages.comun.cancelar}
          </button>
          <button type="button" disabled={motivo.trim().length < 3 || enviando} onClick={confirmar} className={BOTON_DESTRUCTIVO}>
            <BanIcon className="size-4" />
            {enviando ? t.enviando : t.confirmar}
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
