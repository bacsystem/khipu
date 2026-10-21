"use client";

import { useMessages } from "next-intl";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { Button } from "khipu-design-system/ui/button";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { apiRequest, noSeSabeSiLlego } from "@/lib/api/browser";
import { useSoloLectura } from "@/lib/solo-lectura";

/**
 * Reintento manual del envío a SUNAT, o el primer envío de un comprobante que se emitió sin enviarlo (FIRMADO, #356: se dice «Enviar a SUNAT»). Es el único botón de la ficha que usaba `fetch` a pelo, sin mirar la respuesta ni
 * capturar el rechazo: con la red cortada quedaba en «Reenviando…» para siempre, y con un 409 o un 422 volvía a su estado
 * sin decir nada. Eso importa acá más que en otros lados, porque un comprobante en error de envío puede estar **aceptado**
 * en SUNAT, y reintentar a ciegas sin ver el rechazo es cómo el emisor se queda sin saber cuál es el estado real.
 */
/** `etiqueta` y `enCurso`: el texto del botón y el que muestra mientras envía; sin ellos, «Reenviar» y «Reenviando…» en el idioma de la sesión. */
export function ReenviarButton({ id, etiqueta, enCurso }: { id: string; etiqueta?: string; enCurso?: string }) {
  const t = useMessages().comprobantes.reenviar;
  const mensajeError = useMensajeError();
  const soloLectura = useSoloLectura();
  const router = useRouter();
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // `enviando` es estado y React lo aplica al final del tick: dos clics seguidos entrarían los dos.
  const enviandoRef = useRef(false);

  async function reenviar() {
    if (enviandoRef.current) return;
    enviandoRef.current = true;
    setEnviando(true);
    setError(null);

    const res = await apiRequest(`/api/proxy/facturas/${id}/enviar`, { method: "POST" });

    enviandoRef.current = false;
    setEnviando(false);

    if (noSeSabeSiLlego(res)) {
      setError(t.cortado);
      return;
    }
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    router.refresh();
  }

  return (
    <div className="flex flex-col items-end gap-1.5">
      <Button disabled={enviando || soloLectura !== null} title={soloLectura ?? undefined} onClick={reenviar}>
        {enviando ? (enCurso ?? t.enCurso) : (etiqueta ?? t.etiqueta)}
      </Button>
      {error ? (
        <p role="alert" className="max-w-md text-right text-sm text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}
