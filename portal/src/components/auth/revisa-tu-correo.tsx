"use client";

import { MailCheckIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { Button } from "khipu-design-system/ui/button";
import { postJson } from "@/lib/api/browser";
import { useMessages } from "next-intl";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { interpolar } from "@/i18n/mensajes";
import { useSoloLecturaDeLaSesion } from "@/lib/solo-lectura";

/**
 * El correo todavía no está verificado (#22): se puede mirar el portal, pero el backend rechaza crear o emitir. Explica por qué y deja
 * pedir otro enlace. «Ya lo verifiqué» vuelve a leer el estado: el enlace pudo abrirse en el teléfono.
 */
export function RevisaTuCorreo({ email }: { email: string }) {
  const t = useMessages().auth.revisaTuCorreo;
  const mensajeError = useMensajeError();
  const soloLectura = useSoloLecturaDeLaSesion();
  const router = useRouter();
  const [estado, setEstado] = useState<"quieto" | "enviando" | "enviado">("quieto");
  const [error, setError] = useState<string | null>(null);

  async function reenviar() {
    setEstado("enviando");
    setError(null);
    const res = await postJson<null>("/api/auth/verificacion", {});
    if (res.estado === "exito") {
      setEstado("enviado");
      return;
    }
    setEstado("quieto");
    // Ya verificado en otra pestaña o dispositivo: no hay nada que reenviar, se vuelve a leer el estado.
    if (res.codigo === "CORREO_YA_VERIFICADO") router.refresh();
    else setError(mensajeError(res.codigo));
  }

  return (
    <Alerta tono="aviso" icon={MailCheckIcon} titulo={t.titulo} className="mx-auto max-w-lg">
      <div className="grid gap-3" data-testid="revisa-tu-correo">
        <p>{interpolar(t.descripcion, { email })}</p>
        {estado === "enviado" ? <p role="status">{t.reenviado}</p> : null}
        {error ? <p role="alert" className="text-destructive">{error}</p> : null}
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" size="sm" onClick={reenviar} disabled={estado === "enviando" || soloLectura !== null} title={soloLectura ?? undefined}>
            {estado === "enviando" ? t.reenviando : t.reenviar}
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => router.refresh()}>
            {t.yaVerifique}
          </Button>
        </div>
      </div>
    </Alerta>
  );
}
