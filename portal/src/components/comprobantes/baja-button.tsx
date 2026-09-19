"use client";

import { BanIcon, XIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import type { Baja } from "@/lib/api/facturas";
import { BOTON_SECUNDARIO, CAMPO } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

/**
 * Comunicación de baja (`POST /v1/facturas/{id}/baja`). Es irreversible ante SUNAT, así que pide el motivo y una confirmación
 * explícita en la propia página (sin diálogos nativos); al terminar recarga el detalle, que muestra el estado de la baja.
 */
export function BajaButton({ id, numero }: { id: string; numero: string }) {
  const router = useRouter();
  const [abierto, setAbierto] = useState(false);
  const [motivo, setMotivo] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirmar() {
    setEnviando(true);
    setError(null);
    const res = await apiRequest<Baja>(`/api/proxy/facturas/${id}/baja`, { method: "POST", body: { motivo: motivo.trim() } });
    setEnviando(false);
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setAbierto(false);
    router.refresh();
  }

  if (!abierto) {
    return (
      <button type="button" onClick={() => setAbierto(true)} className={cn(BOTON_SECUNDARIO, "h-8 text-xs text-destructive hover:text-destructive")} data-testid="dar-de-baja">
        <BanIcon className="size-4" />
        Dar de baja
      </button>
    );
  }
  return (
    <div className="flex w-full flex-col gap-2 rounded-lg border border-destructive/40 bg-destructive/5 p-3 text-xs md:w-auto md:min-w-96" role="dialog" aria-label={`Dar de baja ${numero}`} data-testid="baja-confirmacion">
      <p className="leading-relaxed text-foreground">
        Se enviará a SUNAT una <strong>comunicación de baja</strong> de <span className="font-mono font-semibold">{numero}</span>. Si SUNAT la acepta, el comprobante queda <strong>anulado</strong> y no se puede revertir.
      </p>
      <label htmlFor="baja-motivo" className="text-[12px] font-medium text-foreground">Motivo (3–100 caracteres)</label>
      <input id="baja-motivo" value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={100} placeholder="Ej.: error en el RUC del cliente" className={cn(CAMPO, "h-9")} autoFocus />
      {error ? <p className="text-destructive" role="alert">{error}</p> : null}
      <div className="flex flex-wrap gap-2">
        <button type="button" disabled={motivo.trim().length < 3 || enviando} onClick={confirmar} className="inline-flex h-8 items-center gap-1.5 rounded-md bg-destructive px-3 text-xs font-semibold text-white shadow-2xs disabled:cursor-not-allowed disabled:opacity-60">
          <BanIcon className="size-3.5" />
          {enviando ? "Enviando a SUNAT…" : "Confirmar la baja"}
        </button>
        <button type="button" onClick={() => setAbierto(false)} className={cn(BOTON_SECUNDARIO, "h-8 text-xs")}>
          <XIcon className="size-3.5" />
          Cancelar
        </button>
      </div>
    </div>
  );
}
