"use client";

import { BellRingIcon } from "lucide-react";
import { DialogoDeAccion } from "@/components/admin/dialogo-de-accion";
import type { AvisoEnviado, MotivoDeAviso, TipoDeAviso } from "@/lib/api/admin-avisos";
import { mensajeDeAvisoEnviado } from "@/lib/avisos-formato";
import { messages } from "@/lib/messages";

const t = messages.admin.avisos.avisar;

/**
 * Mandarle a un cliente el aviso por correo (#197). Es un correo de verdad a una persona, así que pide confirmación y dice a quién le llega, qué le dice, que no se repite en
 * una semana y que queda en la bitácora. El backend decide si el aviso es cierto y si ya se mandó: si otro administrador se adelantó, o la situación cambió, el diálogo lo dice y
 * la página se recarga con el estado real; como esa recarga puede cambiar la fila, el mensaje también se guarda donde sobrevive ({@code alResultado}).
 */
export function AvisarAlCliente({
  empresaId,
  razonSocial,
  correo,
  tipo,
  motivo,
  alResultado,
}: {
  empresaId: string;
  razonSocial: string;
  correo: string;
  tipo: TipoDeAviso;
  motivo: MotivoDeAviso;
  alResultado: (mensaje: string) => void;
}) {
  return (
    <DialogoDeAccion
      testId="avisos-avisar"
      boton={t.boton}
      icono={BellRingIcon}
      chico
      titulo={t.titulo}
      descripcion={t.descripcion.replace("{empresa}", razonSocial)}
      efectos={t.efectos.map((e) => e.replace("{correo}", correo).replace("{que}", t.que[motivo]))}
      confirmar={t.confirmar}
      enviando={t.enviando}
      cancelar={t.cancelar}
      ruta={`/api/admin/empresas/${empresaId}/avisos`}
      cuerpo={() => ({ tipo })}
      estadoViejo={["AVISO_RECIENTE", "AVISO_SIN_MOTIVO", "NO_ENCONTRADO"]}
      alEstadoViejo={(mensaje) => alResultado(`${razonSocial}: ${mensaje}`)}
      alExito={(datos) => alResultado(mensajeDeAvisoEnviado(datos as AvisoEnviado, razonSocial))}
    />
  );
}
