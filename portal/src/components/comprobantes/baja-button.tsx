"use client";

import { BanIcon, TriangleAlertIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { apiRequest } from "@/lib/api/browser";
import type { Baja } from "@/lib/api/facturas";
import { AYUDA_CAMPO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

/**
 * Comunicación de baja (`POST /v1/facturas/{id}/baja`). Es irreversible ante SUNAT, así que pide el motivo y la confirmación
 * en un modal (mismo `Dialog` que las API keys y las empresas) que deja claro el efecto antes de enviar; al aceptarse recarga
 * el detalle, que muestra el estado de la baja.
 */
export function BajaButton({ id, numero }: { id: string; numero: string }) {
  const router = useRouter();
  const [abierto, setAbierto] = useState(false);
  const [motivo, setMotivo] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function cambiarAbierto(valor: boolean) {
    if (enviando) return; // no se cierra mientras SUNAT responde: la baja ya salió y el usuario debe ver el resultado
    setAbierto(valor);
    if (!valor) {
      setMotivo("");
      setError(null);
    }
  }

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
    setMotivo("");
    router.refresh();
  }

  return (
    <Dialog open={abierto} onOpenChange={cambiarAbierto}>
      <DialogTrigger className={cn(BOTON_SECUNDARIO, "h-8 text-xs text-destructive hover:text-destructive")} data-testid="dar-de-baja">
        <BanIcon className="size-4" />
        Dar de baja
      </DialogTrigger>
      <DialogContent className="gap-0 p-0" data-testid="baja-confirmacion">
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-destructive/10 text-destructive">
              <BanIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">
                Dar de baja <span className="font-mono">{numero}</span>
              </DialogTitle>
              <DialogDescription className="text-[13px]">Comunicación de baja ante SUNAT (regla 2957: hasta 7 días desde la emisión)</DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="grid gap-4 px-5 py-4">
          <div className="flex items-start gap-2.5 rounded-lg border border-destructive/40 bg-destructive/5 px-3 py-2.5 text-[12px] leading-relaxed text-foreground">
            <TriangleAlertIcon className="mt-0.5 size-4 shrink-0 text-destructive" />
            <p>
              Si SUNAT la acepta, el comprobante queda <strong className="font-semibold">anulado</strong> y <strong className="font-semibold">no se puede revertir</strong>;
              su número no se reutiliza.
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
            <span className={AYUDA_CAMPO}>3–100 caracteres, sin saltos de línea (SUNAT 2315). Va en el XML y lo ve SUNAT.</span>
          </div>
          {error ? <p className="text-sm text-destructive" role="alert">{error}</p> : null}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-border/60 px-5 py-3">
          <button type="button" disabled={enviando} onClick={() => cambiarAbierto(false)} className={cn(BOTON_SECUNDARIO, "h-9 px-3.5 text-[13px]")}>
            Cancelar
          </button>
          <button
            type="button"
            disabled={motivo.trim().length < 3 || enviando}
            onClick={confirmar}
            className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-destructive px-3.5 text-[13px] font-semibold text-white shadow-xs transition-all hover:opacity-95 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-60"
          >
            <BanIcon className="size-4" />
            {enviando ? "Enviando a SUNAT…" : "Confirmar la baja"}
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
