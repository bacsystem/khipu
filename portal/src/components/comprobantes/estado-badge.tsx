import { useMessages } from "next-intl";
import { PillEstado } from "khipu-design-system/patrones/pill-estado";
import type { Mensajes } from "@/i18n/mensajes";
import type { EstadoDocumento } from "@/lib/api/facturas";

/** Los estados del comprobante con las palabras del portal en el idioma de `messages` (#25). */
export const etiquetasEstado = (messages: Mensajes): Record<EstadoDocumento, string> => messages.documentos.estados;

type TonoEstado = "ok" | "aviso" | "error" | "neutro";

/** El tono de la pill del DS por estado: neutro mientras nada se resolvió, verde aceptado, ámbar con observaciones, rojo si falló. */
export const TONOS_ESTADO: Record<EstadoDocumento, TonoEstado> = {
  RECIBIDO: "neutro",
  FIRMADO: "neutro",
  PENDIENTE_AGRUPACION: "neutro",
  ENVIADO: "neutro",
  ACEPTADO: "ok",
  ACEPTADO_CON_OBS: "aviso",
  ERROR_ENVIO: "error",
  INVALIDO: "error",
  RECHAZADO: "error",
  ANULADO: "neutro",
  FUERA_DE_PLAZO: "error",
  DESCARTADO: "neutro",
};

/** El color sólido del punto por tono (el mismo que pinta `PillEstado`), para la línea de tiempo del detalle. */
const PUNTO_DE_TONO: Record<TonoEstado, string> = {
  ok: "bg-success-solid",
  aviso: "bg-warning-solid",
  error: "bg-destructive",
  neutro: "bg-muted-foreground/50",
};

export const PUNTOS = Object.fromEntries(
  Object.entries(TONOS_ESTADO).map(([estado, tono]) => [estado, PUNTO_DE_TONO[tono]]),
) as Record<EstadoDocumento, string>;

export function EstadoBadge({ estado, etiqueta }: { estado: EstadoDocumento; etiqueta?: string }) {
  const messages = useMessages();
  return <PillEstado tono={TONOS_ESTADO[estado]}>{etiqueta ?? etiquetasEstado(messages)[estado]}</PillEstado>;
}
