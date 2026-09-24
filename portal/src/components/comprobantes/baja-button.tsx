"use client";

import { BanIcon, TriangleAlertIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { apiRequest } from "@/lib/api/browser";
import { type Baja, PLAZO_BAJA_DIAS } from "@/lib/api/facturas";
import { AYUDA_CAMPO, BOTON_DESTRUCTIVO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { diasEntre, hoyLima } from "@/lib/formato";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

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
export function BajaButton({ id, numero, fechaEmision, notasVigentes = 0 }: { id: string; numero: string; fechaEmision: string; notasVigentes?: number }) {
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
    // abajo a propósito: su mensaje ya trae el HTTP y manda a recargar para ver el estado real.
    if (res.codigo === "RED") {
      setError(`Se cortó la conexión mientras se enviaba. La baja de ${numero} pudo haber llegado a SUNAT: recargá la ficha y revisá su estado antes de volver a intentarlo.`);
      return;
    }
    if (res.estado !== "exito" || !res.datos) {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    if (res.datos.estado === "RECHAZADA") {
      // El 201 no es un éxito: SUNAT ya respondió que no. Se muestra acá, con el CDR, y la ficha lo repite al recargar.
      setError(`SUNAT rechazó la baja${res.datos.cdr ? ` (${res.datos.cdr.codigo} · ${res.datos.cdr.descripcion})` : ""}. El comprobante ${numero} sigue vigente.`);
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
      <DialogTrigger className={cn(BOTON_SECUNDARIO, "h-8 text-xs text-destructive hover:text-destructive")} data-testid="dar-de-baja">
        <BanIcon className="size-4" />
        Dar de baja
      </DialogTrigger>
      <DialogContent className="gap-0 p-0" data-testid="baja-confirmacion" showCloseButton={!enviando}>
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-destructive/10 text-destructive">
              <BanIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">
                Dar de baja <span className="font-mono">{numero}</span>
              </DialogTitle>
              <DialogDescription className="text-[13px]">
                Comunicación de baja ante SUNAT (regla 2957: hasta {PLAZO_BAJA_DIAS} días desde la emisión).{" "}
                {diasRestantes === 0 ? "Hoy es el último día del plazo: si el envío falla y se reintenta mañana, SUNAT la rechazará." : `Quedan ${diasRestantes} ${diasRestantes === 1 ? "día" : "días"} de plazo.`}
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="grid gap-4 px-5 py-4">
          <div className="flex items-start gap-2.5 rounded-lg border border-destructive/40 bg-destructive/5 px-3 py-2.5 text-[12px] leading-relaxed text-foreground">
            <TriangleAlertIcon className="mt-0.5 size-4 shrink-0 text-destructive" />
            <p>
              Si SUNAT la acepta, el comprobante queda <strong className="font-semibold">anulado</strong> y <strong className="font-semibold">no se puede revertir</strong>;
              su número no se reutiliza.
              {notasVigentes > 0 ? ` Tiene ${notasVigentes} ${notasVigentes === 1 ? "nota vigente que quedará" : "notas vigentes que quedarán"} sobre un comprobante anulado (SUNAT lo permite).` : ""}
            </p>
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="baja-motivo" className={ETIQUETA_CAMPO}>Motivo</label>
            <input
              id="baja-motivo"
              value={motivo}
              onChange={(e) => setMotivo(e.target.value)}
              maxLength={100}
              placeholder="Ej.: error en el RUC del cliente"
              className={cn(CAMPO, "h-9")}
              autoFocus
            />
            <span className={AYUDA_CAMPO}>3–100 caracteres, sin saltos de línea (SUNAT 2315). Va en el XML y lo ve SUNAT.{motivo.length >= 100 ? " Llegaste al máximo de 100." : ""}</span>
          </div>
          {error ? <p ref={alertaRef} tabIndex={-1} className="text-sm text-destructive outline-none" role="alert">{error}</p> : null}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-border/60 px-5 py-3">
          <button type="button" disabled={enviando} onClick={() => cambiarAbierto(false)} className={cn(BOTON_SECUNDARIO, "h-9 px-3.5 text-[13px]")}>
            Cancelar
          </button>
          <button type="button" disabled={motivo.trim().length < 3 || enviando} onClick={confirmar} className={cn(BOTON_DESTRUCTIVO, "h-9 px-3.5 text-[13px]")}>
            <BanIcon className="size-4" />
            {enviando ? "Enviando a SUNAT…" : "Confirmar la baja"}
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
