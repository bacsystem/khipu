"use client";

import { BanIcon, RefreshCwIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import type { Baja } from "@/lib/api/facturas";
import { BOTON_SECUNDARIO, TITULO_SECCION } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

const ETIQUETAS: Record<Baja["estado"], string> = {
  GENERADA: "Generada, pendiente de envío",
  ENVIADA: "Enviada: SUNAT la está procesando",
  ERROR_ENVIO: "Error de envío: se reintentará",
  ACEPTADA: "Aceptada: comprobante anulado",
  RECHAZADA: "Rechazada por SUNAT",
};

/**
 * Estado de la comunicación de baja en la ficha. Mientras está en curso (ENVIADA/ERROR_ENVIO/GENERADA) se puede reconsultar
 * a SUNAT (`GET /v1/bajas/{id}` continúa el trámite en el acto) y se refresca sola cada 30 s: antes la ficha decía «SUNAT la
 * está procesando» para siempre y nada en el portal volvía a preguntar.
 */
export function BajaEstado({ baja }: { baja: Baja }) {
  const router = useRouter();
  const [consultando, setConsultando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pendiente = baja.estado === "ENVIADA" || baja.estado === "ERROR_ENVIO" || baja.estado === "GENERADA";

  const actualizar = useCallback(async () => {
    setConsultando(true);
    setError(null);
    try {
      const res = await apiRequest<Baja>(`/api/proxy/bajas/${baja.id}`, { method: "GET" });
      if (res.estado !== "exito") setError(res.mensaje ?? mensajeError(res.codigo));
    } catch {
      setError("Sin conexión: no se pudo consultar a SUNAT. Vuelve a intentarlo.");
    } finally {
      setConsultando(false);
      router.refresh();
    }
  }, [baja.id, router]);

  useEffect(() => {
    if (!pendiente) return;
    const t = setInterval(actualizar, 30_000);
    return () => clearInterval(t);
  }, [pendiente, actualizar]);

  return (
    <section
      className={cn("rounded-xl border p-5 shadow-2xs", baja.estado === "ACEPTADA" ? "border-destructive/40 bg-destructive/5" : baja.estado === "RECHAZADA" ? "border-warning-border bg-warning/40" : "border-border bg-card")}
      data-testid="baja"
    >
      <div className={cn(TITULO_SECCION, "mb-3 justify-between")}>
        <span className="flex items-center gap-1.5">
          <BanIcon className="size-4" />
          Comunicación de baja {baja.identificador}
        </span>
        {pendiente ? (
          <button type="button" onClick={actualizar} disabled={consultando} className={cn(BOTON_SECUNDARIO, "h-7 text-[11px] normal-case tracking-normal")}>
            <RefreshCwIcon className={cn("size-3.5", consultando ? "animate-spin" : "")} />
            {consultando ? "Consultando a SUNAT…" : "Actualizar estado"}
          </button>
        ) : null}
      </div>
      <div className="grid grid-cols-1 gap-4 text-xs md:grid-cols-4">
        <div className="flex flex-col gap-1">
          <span className="text-[11px] tracking-wider text-muted-foreground uppercase">Estado</span>
          <span className="font-semibold text-foreground" role="status">{ETIQUETAS[baja.estado]}</span>
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-[11px] tracking-wider text-muted-foreground uppercase">Motivo</span>
          <p className="leading-snug text-foreground/80">{baja.motivo}</p>
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-[11px] tracking-wider text-muted-foreground uppercase">Ticket SUNAT</span>
          <span className="font-mono text-foreground/80">{baja.ticket ?? "—"}</span>
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-[11px] tracking-wider text-muted-foreground uppercase">{baja.cdr ? "CDR" : "Último intento"}</span>
          <span className="font-mono text-foreground/80">{baja.cdr ? `${baja.cdr.codigo} · ${baja.cdr.descripcion}` : (baja.ultimo_error ?? "—")}{!baja.cdr && baja.intentos > 1 ? ` (${baja.intentos} intentos)` : ""}</span>
        </div>
      </div>
      {pendiente ? <p className="mt-3 text-[12px] text-muted-foreground">Mientras SUNAT no responda, el comprobante no admite otra baja ni notas. Esta ficha se actualiza sola cada 30 s.</p> : null}
      {error ? <p className="mt-2 text-sm text-destructive" role="alert">{error}</p> : null}
    </section>
  );
}
