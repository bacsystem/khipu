import { CheckIcon } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export type Paso = { titulo: ReactNode; descripcion?: ReactNode };

/**
 * Indicador de pasos (onboarding, asistentes). `actual` es 0-based; los anteriores se marcan completados.
 * Cada paso ocupa el mismo ancho, con el círculo y el texto centrados en su columna; la línea une los centros de los círculos
 * (primaria hasta el paso actual). En móvil se ocultan las descripciones para que los títulos quepan en una fila.
 */
export function Pasos({ pasos, actual, className }: { pasos: Paso[]; actual: number; className?: string }) {
  return (
    <ol className={cn("grid auto-cols-fr grid-flow-col items-start", className)}>
      {pasos.map((p, i) => {
        const estado = i < actual ? "hecho" : i === actual ? "actual" : "pendiente";
        return (
          <li key={i} className="relative flex min-w-0 flex-col items-center gap-2 px-1 text-center">
            {/* Tramo desde el paso anterior: de centro a centro de los círculos (size-7 = 28 px), con 8 px de aire a cada lado. */}
            {i > 0 ? (
              <span
                aria-hidden="true"
                className={cn("absolute top-3.5 right-[calc(50%+22px)] left-[calc(-50%+22px)] h-px", i <= actual ? "bg-primary" : "bg-border")}
              />
            ) : null}
            <span
              aria-current={estado === "actual" ? "step" : undefined}
              className={cn(
                "relative flex size-7 shrink-0 items-center justify-center rounded-full border font-mono text-[11px] font-semibold",
                estado === "hecho" && "border-primary bg-primary text-primary-foreground",
                estado === "actual" && "border-primary bg-accent text-primary ring-3 ring-ring/20",
                estado === "pendiente" && "border-border bg-muted text-muted-foreground",
              )}
            >
              {estado === "hecho" ? <CheckIcon className="size-3.5" strokeWidth={3} /> : i + 1}
            </span>
            <div className="min-w-0">
              <p className={cn("text-[13px] font-medium", estado === "pendiente" ? "text-muted-foreground" : "text-foreground")}>{p.titulo}</p>
              {p.descripcion ? <p className="hidden text-[11px] text-muted-foreground sm:block">{p.descripcion}</p> : null}
            </div>
          </li>
        );
      })}
    </ol>
  );
}

/** Tarjeta de paso del asistente: cabecera con "Paso n de N", contenido y pie con Atrás/Continuar. */
export function TarjetaPaso({
  numero,
  total,
  titulo,
  children,
  pie,
  className,
}: {
  numero: number;
  total: number;
  titulo: ReactNode;
  children: ReactNode;
  pie?: ReactNode;
  className?: string;
}) {
  return (
    <section className={cn("rounded-xl border border-border bg-card shadow-2xs", className)}>
      <div className="border-b border-border/60 px-5 py-4">
        <span className="font-mono text-[11px] tracking-wider text-muted-foreground uppercase">
          Paso {numero} de {total}
        </span>
        <h2 className="mt-0.5 text-base font-semibold tracking-tight text-foreground">{titulo}</h2>
      </div>
      <div className="grid gap-4 px-5 py-4">{children}</div>
      {pie ? <div className="flex items-center justify-end gap-2 border-t border-border/60 px-5 py-3">{pie}</div> : null}
    </section>
  );
}
