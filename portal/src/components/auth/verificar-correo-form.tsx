"use client";

import Link from "next/link";
import { useState } from "react";
import { Button, buttonVariants } from "@/components/ui/button";
import { postJson } from "@/lib/api/browser";
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
        <p role="status" className="text-sm">{t.exito}</p>
        {/* La misma altura que el botón de verificar (los formularios de acceso son de h-8, con `ui/Button` e `Input`). */}
        <Link href="/onboarding" className={cn(buttonVariants(), "w-full")}>
          {t.continuar}
        </Link>
      </div>
    );
  }
  return (
    <div className="grid gap-4">
      {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : null}
      <Button type="button" onClick={verificar} disabled={estado === "enviando"} className="w-full">
        {estado === "enviando" ? t.enviando : t.enviar}
      </Button>
    </div>
  );
}
