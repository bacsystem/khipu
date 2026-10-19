"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { apiRequest, noSeSabeSiLlego } from "@/lib/api/browser";
import type { Comprobante } from "@/lib/api/facturas";
import { mensajeError } from "@/lib/messages";
import { useSoloLectura } from "@/lib/solo-lectura";

/**
 * Recuperar la constancia (CDR) de SUNAT (#315): para un comprobante que quedó `ENVIADO` o en `ERROR_ENVIO` porque la conexión se cortó después de que SUNAT lo aceptara.
 * Le pide a SUNAT (`getStatusCdr`) la constancia y, si la tiene, aplica el resultado sin reenviar. Solo facturas y sus notas: SUNAT no lo expone para boletas.
 */
export function RecuperarCdrButton({ id, estado }: { id: string; estado: string }) {
  const soloLectura = useSoloLectura();
  const router = useRouter();
  const [consultando, setConsultando] = useState(false);
  const [mensaje, setMensaje] = useState<{ tono: "error" | "info"; texto: string } | null>(null);
  const enCursoRef = useRef(false);

  async function recuperar() {
    if (enCursoRef.current) return;
    enCursoRef.current = true;
    setConsultando(true);
    setMensaje(null);
    const res = await apiRequest<Comprobante>(`/api/proxy/facturas/${id}/cdr/recuperar`, { method: "POST" });
    enCursoRef.current = false;
    setConsultando(false);
    if (noSeSabeSiLlego(res)) {
      setMensaje({ tono: "error", texto: "Se cortó la conexión mientras se consultaba a SUNAT. Reintente: consultar no cambia nada si SUNAT no tiene la constancia." });
      return;
    }
    // Otro proceso (el envío, el barrido horario) resolvió el comprobante mientras la ficha estaba abierta: no es un error, es que la ficha quedó vieja.
    if (res.codigo === "CDR_YA_DISPONIBLE") {
      router.refresh();
      return;
    }
    if (res.estado !== "exito" || !res.datos) {
      setMensaje({ tono: "error", texto: res.mensaje ?? mensajeError(res.codigo) });
      return;
    }
    if (res.datos.estado_documento === estado) {
      setMensaje({ tono: "info", texto: "SUNAT todavía no tiene la constancia de este comprobante. Si ya pasó un rato, reenvíelo." });
      return;
    }
    router.refresh();
  }

  return (
    <div className="flex flex-col items-end gap-1.5">
      <Button variant="outline" disabled={consultando || soloLectura !== null} title={soloLectura ?? undefined} onClick={recuperar} data-testid="recuperar-cdr">
        {consultando ? "Consultando a SUNAT…" : "Recuperar constancia de SUNAT"}
      </Button>
      {mensaje ? (
        <p role={mensaje.tono === "error" ? "alert" : "status"} className={mensaje.tono === "error" ? "max-w-md text-right text-sm text-destructive" : "max-w-md text-right text-sm text-muted-foreground"} data-testid="recuperar-cdr-mensaje">
          {mensaje.texto}
        </p>
      ) : null}
    </div>
  );
}
