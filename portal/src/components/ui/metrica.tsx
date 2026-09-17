import { cn } from "@/lib/utils";

/** Indicador de cabecera de página (etiqueta · valor · ayuda). `pendiente` lo atenúa cuando el dato todavía no existe. */
export function Metrica({
  etiqueta,
  children,
  ayuda,
  pendiente = false,
}: {
  etiqueta: string;
  children?: React.ReactNode;
  ayuda: React.ReactNode;
  pendiente?: boolean;
}) {
  return (
    <div className={cn("flex min-w-0 flex-col gap-0.5", pendiente && "opacity-60")}>
      <span className="truncate text-[11px] font-medium tracking-wider text-muted-foreground uppercase">{etiqueta}</span>
      <div className="flex min-w-0 flex-wrap items-baseline gap-x-1.5 font-mono text-lg font-semibold tracking-tight text-foreground">
        {children ?? "—"}
      </div>
      <div className="flex min-w-0 items-center gap-1 truncate text-[11px] text-muted-foreground">{ayuda}</div>
    </div>
  );
}
