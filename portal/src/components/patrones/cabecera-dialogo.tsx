import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { cn } from "@/lib/utils";
import { IconoSeccion } from "./cabecera-seccion";

/**
 * Cabecera estándar de diálogo: icono en accent + título 16px + descripción 13px.
 * Úsala dentro de `<DialogContent className="gap-0 p-0">`; el cuerpo va en `px-5 py-4` y el pie en `PieDialogo`.
 */
export function CabeceraDialogo({ icon, titulo, descripcion }: { icon: LucideIcon; titulo: ReactNode; descripcion?: ReactNode }) {
  return (
    <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
      <div className="flex items-center gap-2.5">
        <IconoSeccion icon={icon} />
        <div className="min-w-0">
          <DialogTitle className="text-base font-semibold tracking-tight">{titulo}</DialogTitle>
          {descripcion ? <DialogDescription className="text-[13px]">{descripcion}</DialogDescription> : null}
        </div>
      </div>
    </DialogHeader>
  );
}

/** Pie de diálogo; con `izquierda` (p. ej. un enlace) reparte los extremos. */
export function PieDialogo({ children, izquierda, className }: { children: ReactNode; izquierda?: ReactNode; className?: string }) {
  return (
    <div className={cn("flex flex-wrap items-center gap-2 border-t border-border/60 px-5 py-3", izquierda ? "justify-between" : "justify-end", className)}>
      {izquierda}
      <div className="flex items-center gap-2">{children}</div>
    </div>
  );
}

/** Aviso dentro de un diálogo o formulario (ámbar para advertencias, verde para confirmaciones). */
export function Aviso({ tono, icon: Icon, children }: { tono: "aviso" | "ok"; icon: LucideIcon; children: ReactNode }) {
  return (
    <div
      className={cn(
        "flex items-start gap-2.5 rounded-lg border px-3 py-2.5 text-[12px] leading-relaxed",
        tono === "aviso" && "border-warning-border bg-warning text-warning-foreground",
        tono === "ok" && "border-success-border bg-success text-success-foreground",
      )}
    >
      <Icon className="mt-0.5 size-4 shrink-0" />
      <div>{children}</div>
    </div>
  );
}
