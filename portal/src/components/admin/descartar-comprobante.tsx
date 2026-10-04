"use client";

import { BanIcon } from "lucide-react";
import { useState } from "react";
import { DialogoDeAccion } from "@/components/admin/dialogo-de-accion";
import type { ErrorDeEmision } from "@/lib/api/admin-errores";
import { AYUDA_CAMPO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { mensajeDeDescarte } from "@/lib/errores-formato";
import { messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

const t = messages.admin.errores.descarte;

/** Lo mismo que el tope del backend ({@code ResolverErroresUseCase.MAX_MOTIVO}); el backend lo vuelve a comprobar. */
const MAX_MOTIVO = 200;

/**
 * Descartar un comprobante en error de envío (#196): deja de intentarse y queda terminal. El motivo es obligatorio y viaja a la bitácora; si falta o es muy largo, el backend
 * lo rechaza y el diálogo muestra su mensaje (una sola regla, la del backend). Es irreversible, así que el diálogo es de peligro y lo dice.
 */
export function DescartarComprobante({ comprobante, alResultado }: { comprobante: ErrorDeEmision; alResultado: (mensaje: string) => void }) {
  const [motivo, setMotivo] = useState("");
  const id = `errores-motivo-${comprobante.comprobante_id}`;

  return (
    <DialogoDeAccion
      testId="errores-descartar"
      boton={t.boton}
      icono={BanIcon}
      tono="peligro"
      chico
      advertencia
      titulo={t.titulo}
      descripcion={t.descripcion.replace("{comprobante}", comprobante.nombre_archivo)}
      efectos={t.efectos}
      confirmar={t.confirmar}
      enviando={t.enviando}
      cancelar={t.cancelar}
      ruta={`/api/admin/comprobantes/${comprobante.comprobante_id}/descarte`}
      cuerpo={() => ({ motivo })}
      estadoViejo={["ESTADO_NO_DESCARTABLE", "ESTADO_CONFLICTO", "NO_ENCONTRADO"]}
      alEstadoViejo={(mensaje) => alResultado(`${comprobante.nombre_archivo}: ${mensaje}`)}
      alCerrar={() => setMotivo("")}
      alExito={() => alResultado(mensajeDeDescarte(comprobante.nombre_archivo))}
    >
      <div className="grid gap-1.5">
        <label htmlFor={id} className={ETIQUETA_CAMPO}>
          {t.motivo}
        </label>
        <textarea id={id} data-testid="errores-motivo" rows={3} maxLength={MAX_MOTIVO} value={motivo} onChange={(e) => setMotivo(e.target.value)} className={cn(CAMPO, "h-auto py-2")} />
        <span className={AYUDA_CAMPO}>{t.ayudaMotivo.replace("{max}", String(MAX_MOTIVO))}</span>
      </div>
    </DialogoDeAccion>
  );
}
