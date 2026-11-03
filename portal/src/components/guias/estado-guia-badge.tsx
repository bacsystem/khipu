import { useMessages } from "next-intl";
import { PillEstado } from "khipu-design-system/patrones/pill-estado";
import type { EstadoDeGuia } from "@/lib/api/guias";

/** Mismo lenguaje de color que los comprobantes: neutro mientras nada se resolvió, verde si SUNAT aceptó, ámbar con observaciones, rojo si falló o se rechazó. */
const TONOS: Record<EstadoDeGuia, "ok" | "aviso" | "error" | "neutro"> = {
  RECIBIDO: "neutro",
  FIRMADO: "neutro",
  ENVIANDO: "neutro",
  ENVIADO_PENDIENTE_TICKET: "neutro",
  ACEPTADO: "ok",
  ACEPTADO_CON_OBS: "aviso",
  ERROR_ENVIO: "error",
  RECHAZADO: "error",
};

export function EstadoGuiaBadge({ estado }: { estado: EstadoDeGuia }) {
  const messages = useMessages();
  return <PillEstado tono={TONOS[estado]}>{messages.guias.estados[estado]}</PillEstado>;
}
