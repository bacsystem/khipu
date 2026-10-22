"use client";

import Link from "next/link";
import { useState } from "react";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { postJson } from "@/lib/api/browser";
import { BOTON_PRIMARIO } from "@/lib/estilos";
import { mensajeError, messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

const t = messages.auth.verificar;

/**
 * Página del enlace de verificación (#22). Verifica con un botón y no al abrir la página: los antivirus y los filtros de correo
 * abren los enlaces para revisarlos, y un enlace de un solo uso se gastaría antes de que llegue la persona.
 */
export function VerificarCorreoForm({ token }: { token: string }) {
  const [estado, setEstado] = useState<"quieto" | "enviando" | "listo">("quieto");
  const [error, setError] = useState<string | null>(null);

  async function verificar() {
    setEstado("enviando");
    setError(null);
    const res = await postJson<null>("/api/auth/verificar", { token });
    if (res.estado === "exito") {
      setEstado("listo");
      return;
    }
    setEstado("quieto");
    setError(res.codigo === "TOKEN_INVALIDO" ? t.enlaceInvalido : mensajeError(res.codigo));
  }

  if (estado === "listo") {
    return (
      <div className="grid gap-4">
        <Alerta tono="ok">{t.exito}</Alerta>
        <Link href="/onboarding" className={cn(BOTON_PRIMARIO, "w-full")}>
          {t.continuar}
        </Link>
      </div>
    );
  }
  return (
    <div className="grid gap-4">
      {error ? <Alerta tono="error">{error}</Alerta> : null}
      <BotonAsync pendiente={estado === "enviando"} textoPendiente={t.enviando} onClick={verificar} className={cn(BOTON_PRIMARIO, "w-full")}>
        {t.enviar}
      </BotonAsync>
    </div>
  );
}
