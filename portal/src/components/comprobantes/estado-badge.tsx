import { Badge } from "@/components/ui/badge";
import type { EstadoDocumento } from "@/lib/api/facturas";
import { cn } from "@/lib/utils";

export const ETIQUETAS_ESTADO: Record<EstadoDocumento, string> = {
  RECIBIDO: "Recibido",
  INVALIDO: "Inválido",
  FIRMADO: "Firmado",
  ERROR_ENVIO: "Error de envío",
  PENDIENTE_AGRUPACION: "Pendiente de agrupación",
  ENVIADO: "Enviado",
  ACEPTADO: "Aceptado",
  ACEPTADO_CON_OBS: "Aceptado con obs.",
  RECHAZADO: "Rechazado",
  ANULADO: "Anulado",
};

const ESTILOS: Record<EstadoDocumento, string> = {
  RECIBIDO: "bg-secondary text-secondary-foreground border-border",
  FIRMADO: "bg-secondary text-secondary-foreground border-border",
  PENDIENTE_AGRUPACION: "bg-secondary text-secondary-foreground border-border",
  ENVIADO: "bg-secondary text-secondary-foreground border-border",
  ACEPTADO: "bg-success text-success-foreground border-success-border",
  ACEPTADO_CON_OBS: "bg-warning text-warning-foreground border-warning-border",
  ERROR_ENVIO: "bg-destructive/10 text-destructive border-destructive-border",
  INVALIDO: "bg-destructive/10 text-destructive border-destructive-border",
  RECHAZADO: "bg-destructive/10 text-destructive border-destructive-border",
  ANULADO: "bg-secondary text-muted-foreground border-border",
};

const PUNTOS: Record<EstadoDocumento, string> = {
  RECIBIDO: "bg-muted-foreground/50",
  FIRMADO: "bg-muted-foreground/50",
  PENDIENTE_AGRUPACION: "bg-muted-foreground/50",
  ENVIADO: "bg-muted-foreground/50",
  ACEPTADO: "bg-success-solid",
  ACEPTADO_CON_OBS: "bg-warning-solid",
  ERROR_ENVIO: "bg-destructive",
  INVALIDO: "bg-destructive",
  RECHAZADO: "bg-destructive",
  ANULADO: "bg-muted-foreground/50",
};

export function EstadoBadge({ estado, etiqueta }: { estado: EstadoDocumento; etiqueta?: string }) {
  return (
    <Badge className={cn("gap-1.5 border px-2.5 py-1 text-[11px] font-medium whitespace-nowrap", ESTILOS[estado])}>
      <span className={cn("size-1.5 shrink-0 rounded-full", PUNTOS[estado])} />
      {etiqueta ?? ETIQUETAS_ESTADO[estado]}
    </Badge>
  );
}
