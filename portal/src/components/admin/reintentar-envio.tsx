"use client";

import { RotateCwIcon } from "lucide-react";
import { ETIQUETAS_ESTADO } from "@/components/comprobantes/estado-badge";
import { DialogoDeAccion } from "@/components/admin/dialogo-de-accion";
import type { ErrorDeEmision, ResultadoDeReintento } from "@/lib/api/admin-errores";
import type { EstadoDocumento } from "@/lib/api/facturas";
import { mensajeDeReintento } from "@/lib/errores-formato";
import { messages } from "@/lib/messages";

const t = messages.admin.errores.reintento;

const etiquetaDelEstado = (estado: string) => ETIQUETAS_ESTADO[estado as EstadoDocumento] ?? estado;

/**
 * Reintentar a mano el envío de un comprobante de la cola de errores (#196). Reenvía a SUNAT con las credenciales de la empresa dueña, así que pide confirmación y dice qué
 * hace. Que SUNAT vuelva a fallar no es un error de la acción: el diálogo se cierra y {@code alResultado} recibe lo que pasó, para decírselo al administrador. Si el comprobante
 * ya no está por enviar (otro administrador, o el trabajo del outbox, lo resolvió), el diálogo lo dice y la página se recarga con el estado real.
 */
export function ReintentarEnvio({ comprobante, alResultado }: { comprobante: ErrorDeEmision; alResultado: (mensaje: string) => void }) {
  return (
    <DialogoDeAccion
      testId="errores-reintentar"
      boton={t.boton}
      icono={RotateCwIcon}
      chico
      titulo={t.titulo}
      descripcion={t.descripcion.replace("{comprobante}", comprobante.nombre_archivo)}
      efectos={t.efectos}
      confirmar={t.confirmar}
      enviando={t.enviando}
      cancelar={t.cancelar}
      ruta={`/api/admin/comprobantes/${comprobante.comprobante_id}/reintento`}
      estadoViejo={["ESTADO_NO_ENVIABLE", "FUERA_DE_PLAZO", "NO_ENCONTRADO"]}
      alEstadoViejo={(mensaje) => alResultado(`${comprobante.nombre_archivo}: ${mensaje}`)}
      alExito={(datos) => alResultado(mensajeDeReintento(datos as ResultadoDeReintento, comprobante.nombre_archivo, etiquetaDelEstado))}
    />
  );
}
