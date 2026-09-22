import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/**
 * Estado vacío de página o de sección: icono en caja accent, título, explicación y acción principal.
 * Para el cuerpo de una tabla usa `TablaVacia` (patrones/pie-tabla).
 */
export function EstadoVacio({
  icon: Icon,
  titulo,
  children,
  accion,
  className,
}: {
  icon: LucideIcon;
  titulo: ReactNode;
  children?: ReactNode;
  accion?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-border bg-card px-6 py-14 text-center", className)}>
      <div className="flex size-10 items-center justify-center rounded-lg bg-accent text-primary">
        <Icon className="size-5" />
      </div>
      <div className="max-w-sm">
        <h3 className="text-[14px] font-semibold text-foreground">{titulo}</h3>
        {children ? <p className="mt-1 text-[13px] leading-relaxed text-muted-foreground">{children}</p> : null}
      </div>
      {accion ? <div className="mt-1">{accion}</div> : null}
    </div>
  );
}
