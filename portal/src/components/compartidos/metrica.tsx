import type { IconoComponente } from "khipu-design-system/lib/iconos";
import { Metrica as MetricaDelDiseno, type TonoMetrica, type VisualMetrica } from "khipu-design-system/patrones/metricas";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/**
 * Indicador de cabecera de página: la tarjeta de métrica del design system (etiqueta con ícono, valor grande, mini visual opcional y ayuda). `pendiente` la atenúa cuando el
 * dato todavía no existe y deja «—» como valor. Va dentro de una `FilaMetricas`.
 */
export function Metrica({
  etiqueta,
  children,
  sufijo,
  ayuda,
  icono,
  tono,
  visual,
  destacada,
  pendiente = false,
}: {
  etiqueta: string;
  children?: ReactNode;
  /** Texto menor junto al valor («/ 30», «registrada»). */
  sufijo?: ReactNode;
  ayuda: ReactNode;
  icono?: IconoComponente;
  tono?: TonoMetrica;
  visual?: VisualMetrica;
  destacada?: boolean;
  pendiente?: boolean;
}) {
  return (
    <div data-metrica={etiqueta} className={cn("min-w-0 [&>*]:h-full", pendiente && "opacity-60")}>
      <MetricaDelDiseno etiqueta={etiqueta} sufijo={sufijo} ayuda={ayuda} icono={icono} tono={tono} visual={visual} destacada={destacada}>
        {children ?? "—"}
      </MetricaDelDiseno>
    </div>
  );
}
