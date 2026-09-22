import { CheckCircle2Icon, CircleAlertIcon, InfoIcon, TriangleAlertIcon, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

type Tono = "info" | "ok" | "aviso" | "error";

const ESTILOS: Record<Tono, { caja: string; icono: LucideIcon }> = {
  info: { caja: "border-accent-border bg-accent text-accent-foreground", icono: InfoIcon },
  ok: { caja: "border-success-border bg-success text-success-foreground", icono: CheckCircle2Icon },
  aviso: { caja: "border-warning-border bg-warning text-warning-foreground", icono: TriangleAlertIcon },
  error: { caja: "border-destructive-border bg-destructive/10 text-destructive", icono: CircleAlertIcon },
};

/**
 * Alerta de página o de sección: banda con borde, icono, título opcional, texto y acción a la derecha.
 * Para avisos dentro de un diálogo usa `Aviso` (patrones/cabecera-dialogo).
 */
export function Alerta({
  tono = "info",
  titulo,
  children,
  accion,
  icon,
  className,
}: {
  tono?: Tono;
  titulo?: ReactNode;
  children: ReactNode;
  accion?: ReactNode;
  icon?: LucideIcon;
  className?: string;
}) {
  const Icono = icon ?? ESTILOS[tono].icono;
  return (
    <div role={tono === "error" ? "alert" : "status"} className={cn("flex flex-wrap items-start gap-3 rounded-xl border px-4 py-3", ESTILOS[tono].caja, className)}>
      <Icono className="mt-0.5 size-4 shrink-0" />
      <div className="min-w-0 flex-1 basis-56 text-[13px] leading-relaxed">
        {titulo ? <p className="font-semibold">{titulo}</p> : null}
        <div className={cn(titulo && "mt-0.5 opacity-90")}>{children}</div>
      </div>
      {accion ? <div className="shrink-0 self-center">{accion}</div> : null}
    </div>
  );
}
