import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/** Caja de icono en accent, usada en cabeceras de sección y de diálogo. */
export function IconoSeccion({ icon: Icon, className }: { icon: LucideIcon; className?: string }) {
  return (
    <div className={cn("flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary", className)}>
      <Icon className="size-4" />
    </div>
  );
}

/** Cabecera de tarjeta/sección: icono + título 13px + subtítulo mono 11px, y un slot a la derecha (chips, acciones). */
export function CabeceraSeccion({
  icon,
  titulo,
  subtitulo,
  derecha,
  conBorde = true,
  className,
}: {
  icon: LucideIcon;
  titulo: ReactNode;
  subtitulo?: ReactNode;
  derecha?: ReactNode;
  conBorde?: boolean;
  className?: string;
}) {
  return (
    <div className={cn("flex min-w-0 flex-wrap items-center gap-x-2.5 gap-y-2 px-4 py-3", conBorde && "border-b border-border/60", className)}>
      <IconoSeccion icon={icon} />
      <div className="flex min-w-0 flex-1 basis-48 flex-col">
        <h2 className="text-[13px] font-semibold text-foreground">{titulo}</h2>
        {subtitulo ? <span className="truncate font-mono text-[11px] text-muted-foreground">{subtitulo}</span> : null}
      </div>
      {derecha}
    </div>
  );
}

/** Pie de tarjeta: mono 11px sobre muted (notas técnicas). */
export function PieSeccion({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn("flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-border/60 bg-muted/60 px-4 py-2 font-mono text-[11px] text-muted-foreground", className)}>
      {children}
    </div>
  );
}
