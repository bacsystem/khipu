import Link from "next/link";
import type { MiCuenta } from "@/lib/api/cuenta";
import { resumenDePlan } from "@/lib/cuenta/resumen-de-plan";
import { cn } from "@/lib/utils";

/**
 * El plan y el consumo del mes en el menú lateral (C1): reemplaza al «— / —» fijo. Lleva al detalle en `/cuenta/plan`. Si no se pudo leer la cuenta lo dice, y el
 * enlace sigue llevando a la página, que vuelve a intentarlo.
 */
export function PlanYConsumo({ cuenta }: { cuenta: MiCuenta | null }) {
  if (!cuenta) {
    return (
      <Link href="/cuenta/plan" className="rounded-lg border border-border/60 bg-muted/90 p-2.5 text-[11px] text-muted-foreground hover:bg-secondary">
        No se pudo leer tu plan. Ver mi plan
      </Link>
    );
  }
  const r = resumenDePlan(cuenta);
  return (
    <Link
      href="/cuenta/plan"
      aria-label={`Plan ${r.plan}: ${r.consumo} documentos este mes${r.aviso ? `. ${r.aviso}` : ""}`}
      className={cn(
        "grid w-full grid-cols-1 gap-1.5 overflow-hidden rounded-lg border bg-muted/90 p-2.5 transition-colors hover:bg-secondary",
        r.tono === "ok" && "border-border/60",
        r.tono === "aviso" && "border-warning-border",
        r.tono === "error" && "border-destructive/40",
      )}
    >
      <div className="flex items-center justify-between gap-2 text-[11px]">
        <span className="truncate font-medium text-foreground/80">Plan {r.plan}</span>
        <span className="shrink-0 font-mono text-[10px] whitespace-nowrap text-muted-foreground">{r.consumo}</span>
      </div>
      {r.porcentaje !== null ? (
        <div className="h-1 w-full overflow-hidden rounded-full bg-border">
          <div
            className={cn("h-full rounded-full", r.tono === "ok" && "bg-primary", r.tono === "aviso" && "bg-warning-solid", r.tono === "error" && "bg-destructive")}
            style={{ width: `${r.porcentaje}%` }}
          />
        </div>
      ) : null}
      {r.aviso ? (
        <span className={cn("text-[10px] leading-snug", r.tono === "aviso" ? "text-warning-foreground" : "text-destructive")}>{r.aviso}</span>
      ) : null}
    </Link>
  );
}
