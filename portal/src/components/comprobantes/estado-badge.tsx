import { Badge } from "@/components/ui/badge";
import type { EstadoDocumento } from "@/lib/api/facturas";
import { cn } from "@/lib/utils";

const ETIQUETAS: Record<EstadoDocumento, string> = {
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
  RECIBIDO: "bg-secondary text-secondary-foreground",
  FIRMADO: "bg-secondary text-secondary-foreground",
  PENDIENTE_AGRUPACION: "bg-secondary text-secondary-foreground",
  ENVIADO: "bg-secondary text-secondary-foreground",
  ACEPTADO: "bg-success text-success-foreground",
  ACEPTADO_CON_OBS: "bg-warning text-warning-foreground",
  ERROR_ENVIO: "bg-destructive/10 text-destructive",
  INVALIDO: "bg-destructive/10 text-destructive",
  RECHAZADO: "bg-destructive/10 text-destructive",
  ANULADO: "border border-border text-muted-foreground",
};

export function EstadoBadge({ estado }: { estado: EstadoDocumento }) {
  return <Badge className={cn("border-transparent", ESTILOS[estado])}>{ETIQUETAS[estado]}</Badge>;
}
