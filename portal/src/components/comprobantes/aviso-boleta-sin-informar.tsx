import { TriangleAlertIcon } from "lucide-react";
import type { AvisoDePlazo } from "@/lib/comprobantes/plazo";
import { formatearFecha } from "@/lib/formato";
import { cn } from "@/lib/utils";

/** «queda 1 día» / «quedan 3 días». */
function cuantoQueda(dias: number): string {
  return dias === 1 ? "queda 1 día" : `quedan ${dias} días`;
}

/**
 * La boleta (o la nota que la modifica) que sigue sin informarse a SUNAT desde el 5.º día (#356). No falló nada, pero el envío individual ya vence o venció y el resumen diario cierra
 * el 7.º: sin este aviso la única pista era una línea chica de fecha, y el cliente que dejó de enviar solas sus boletas pasaba el plazo sin enterarse. Cada fase dice lo que le queda;
 * las dos últimas se marcan como urgentes. El botón para enviarla está arriba, junto a las demás acciones de la ficha.
 */
export function AvisoBoletaSinInformar({ aviso, que }: { aviso: AvisoDePlazo; que: "boleta" | "nota" }) {
  const urgente = aviso.fase === "ultimo-dia-resumen" || aviso.fase === "fuera-de-plazo";
  const limite = formatearFecha(aviso.limite);
  return (
    <section
      role="status"
      data-testid="aviso-boleta-sin-informar"
      data-fase={aviso.fase}
      data-urgente={urgente}
      className={cn(
        "flex items-start gap-3 rounded-xl border p-4 text-sm",
        urgente ? "border-destructive/40 bg-destructive/5 text-destructive" : "border-warning-border bg-warning text-warning-foreground",
      )}
    >
      <TriangleAlertIcon className="mt-0.5 size-4 shrink-0" />
      <div className="space-y-1">
        <p className="font-semibold">Esta {que} todavía no se informó a SUNAT</p>
        <p className="text-[13px] leading-relaxed">
          {aviso.fase === "ultimo-dia-individual" ? `Hoy es el último día para enviarla sola; desde mañana solo se informa en el resumen diario, hasta el ${limite}.` : null}
          {aviso.fase === "solo-resumen" ? `Ya no se puede enviar sola: se informa en el resumen diario hasta el ${limite} (${cuantoQueda(aviso.diasRestantes)}).` : null}
          {aviso.fase === "ultimo-dia-resumen" ? `Hoy vence el plazo del resumen diario (${limite}). Pasado hoy se informa fuera de plazo y SUNAT puede observarla.` : null}
          {aviso.fase === "fuera-de-plazo" ? `El plazo venció el ${limite}: al enviarla se informa en un resumen diario fuera de plazo y SUNAT puede observarla.` : null}{" "}
          Envíala ahora con el botón de arriba.
        </p>
      </div>
    </section>
  );
}
