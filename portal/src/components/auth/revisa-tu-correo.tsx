"use client";

import { MailCheckIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Alerta } from "@/components/feedback/alerta";
import { Button } from "@/components/ui/button";
import { postJson } from "@/lib/api/browser";
import { mensajeError, messages } from "@/lib/messages";
import { useSoloLectura } from "@/lib/solo-lectura";

const t = messages.auth.revisaTuCorreo;

/**
 * El correo todavía no está verificado (#22): se puede mirar el portal, pero el backend rechaza crear o emitir. Explica por qué y deja
 * pedir otro enlace. «Ya lo verifiqué» vuelve a leer el estado: el enlace pudo abrirse en el teléfono.
 */
export function RevisaTuCorreo({ email }: { email: string }) {
  const soloLectura = useSoloLectura();
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
        <p>{t.descripcion.replace("{email}", email)}</p>
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
