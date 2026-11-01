import { useMessages } from "next-intl";
import { Badge } from "khipu-design-system/ui/badge";
import type { EstadoDeGuia } from "@/lib/api/guias";
import { cn } from "@/lib/utils";

/** Mismo lenguaje de color que los comprobantes: gris mientras nada se resolvió, verde si SUNAT aceptó, ámbar con observaciones, rojo si falló o se rechazó. */
const ESTILOS: Record<EstadoDeGuia, string> = {
  RECIBIDO: "bg-secondary text-secondary-foreground border-border",
  FIRMADO: "bg-secondary text-secondary-foreground border-border",
  ENVIANDO: "bg-secondary text-secondary-foreground border-border",
  ENVIADO_PENDIENTE_TICKET: "bg-secondary text-secondary-foreground border-border",
  ACEPTADO: "bg-success text-success-foreground border-success-border",
  ACEPTADO_CON_OBS: "bg-warning text-warning-foreground border-warning-border",
  ERROR_ENVIO: "bg-destructive-suave text-destructive-texto border-destructive-border",
  RECHAZADO: "bg-destructive-suave text-destructive-texto border-destructive-border",
};

const PUNTOS: Record<EstadoDeGuia, string> = {
  RECIBIDO: "bg-muted-foreground/50",
  FIRMADO: "bg-muted-foreground/50",
  ENVIANDO: "bg-muted-foreground/50",
  ENVIADO_PENDIENTE_TICKET: "bg-muted-foreground/50",
  ACEPTADO: "bg-success-solid",
  ACEPTADO_CON_OBS: "bg-warning-solid",
  ERROR_ENVIO: "bg-destructive",
  RECHAZADO: "bg-destructive",
};

export function EstadoGuiaBadge({ estado }: { estado: EstadoDeGuia }) {
  const messages = useMessages();
  return (
    <Badge className={cn("gap-1.5 border px-2.5 py-1 text-etiqueta font-medium whitespace-nowrap", ESTILOS[estado])}>
      <span className={cn("size-1.5 shrink-0 rounded-full", PUNTOS[estado])} />
      {messages.guias.estados[estado]}
    </Badge>
  );
}
