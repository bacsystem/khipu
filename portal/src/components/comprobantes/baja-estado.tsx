"use client";

import { BanIcon, RefreshCwIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { buttonVariants } from "@/components/ui/button";
import { apiRequest } from "@/lib/api/browser";
import type { Baja } from "@/lib/api/facturas";
import { TITULO_SECCION } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

const ETIQUETAS: Record<Baja["estado"], string> = {
  GENERADA: "Generada, pendiente de envío",
  ENVIADA: "Enviada: SUNAT la está procesando",
  ERROR_ENVIO: "Error de envío: se reintentará",
  ACEPTADA: "Aceptada: comprobante anulado",
  RECHAZADA: "Rechazada por SUNAT",
};

/** Un resumen de alta informa la boleta en vez de anularla: lo que cambia es qué significa que SUNAT lo acepte (274-H1). */
const ETIQUETAS_ALTA: Record<Baja["estado"], string> = {
  ...ETIQUETAS,
  ACEPTADA: "Aceptado: boleta informada a SUNAT",
  RECHAZADA: "Rechazado por SUNAT: la boleta no quedó informada",
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
  const alta = baja.condicion === "ALTA";

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
      className={cn(
        "rounded-xl border p-5 shadow-2xs",
        baja.estado === "ACEPTADA" && !alta ? "border-destructive/40 bg-destructive/5" : baja.estado === "RECHAZADA" ? "border-warning-border bg-warning/40" : "border-border bg-card",
      )}
      data-testid="baja"
    >
      <div className={cn(TITULO_SECCION, "mb-3 justify-between")}>
        <span className="flex items-center gap-1.5">
          <BanIcon className="size-4" />
          {/* #20: la baja de una boleta va en un resumen diario (RC-…), la de los demás en una comunicación de baja (RA-…). */}
          {baja.tipo_comprobante === "03" ? "Resumen diario" : "Comunicación de baja"} {baja.identificador}
          {alta ? " (alta)" : null}
        </span>
        {pendiente ? (
          <button type="button" onClick={actualizar} disabled={consultando} className={cn(buttonVariants({ variant: "outline", size: "sm" }), "normal-case tracking-normal")}>
            <RefreshCwIcon className={cn("size-3.5", consultando ? "animate-spin" : "")} />
            {consultando ? "Consultando a SUNAT…" : "Actualizar estado"}
          </button>
        ) : null}
      </div>
      <div className="grid grid-cols-1 gap-4 text-xs md:grid-cols-4">
        <div className="flex flex-col gap-1">
          <span className="text-[11px] tracking-wider text-muted-foreground uppercase">Estado</span>
          <span className="font-semibold text-foreground" role="status">{(alta ? ETIQUETAS_ALTA : ETIQUETAS)[baja.estado]}</span>
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
      {pendiente ? (
        <p className="mt-3 text-[12px] text-muted-foreground">
          {alta
            ? "Pasó el envío individual: SUNAT la recibe en el resumen diario. Mientras no responda, la boleta queda enviada. Esta ficha se actualiza sola cada 30 s."
            : "Mientras SUNAT no responda, el comprobante no admite otra baja ni notas. Esta ficha se actualiza sola cada 30 s."}
        </p>
      ) : null}
      {error ? <p className="mt-2 text-sm text-destructive" role="alert">{error}</p> : null}
    </section>
  );
}
