import Link from "next/link";
import type { ComponentProps } from "react";
import { REVISADO_POR_LEGAL } from "@/content/legal/version";
import { messages } from "@/lib/messages";

const ENLACE = "font-medium text-foreground underline underline-offset-2 hover:text-primary";

/**
 * La casilla de «Acepto los términos del servicio y la política de privacidad» (#342), con un enlace a cada texto. La usan el registro y la
 * pantalla que pide volver a aceptar. Los enlaces abren otra pestaña: leerlos no debe hacer perder lo que ya se llenó del formulario.
 * El error se anuncia y se ata a la casilla, como en `FormField`. Mientras los textos sean un borrador, se dice aquí mismo: no solo a quien
 * abre el enlace. `revisado` es para las pruebas: la casilla usa la bandera de `version.ts`.
 */
export function CasillaDeTerminos({
  id,
  error,
  revisado = REVISADO_POR_LEGAL,
  ...input
}: { id: string; error?: string; revisado?: boolean } & Omit<ComponentProps<"input">, "type" | "id">) {
  const t = messages.auth.registro;
  const describe = [error ? `${id}-error` : null, revisado ? null : `${id}-borrador`].filter(Boolean).join(" ") || undefined;
  return (
    <div className="grid gap-1.5">
      <div className="flex items-start gap-2.5 text-sm">
        <input
          id={id}
          type="checkbox"
          className="mt-0.5 size-4 shrink-0 accent-primary"
          aria-invalid={!!error}
          aria-describedby={describe}
          {...input}
        />
        <label htmlFor={id} className="text-foreground/80">
          {t.aceptoLos}{" "}
          <Link href="/terminos" target="_blank" rel="noopener noreferrer" className={ENLACE}>
            {t.terminos}
          </Link>{" "}
          {t.yLa}{" "}
          <Link href="/privacidad" target="_blank" rel="noopener noreferrer" className={ENLACE}>
            {t.privacidad}
          </Link>
        </label>
      </div>
      {revisado ? null : (
        <p id={`${id}-borrador`} data-testid="borrador-legal-casilla" className="pl-6.5 text-xs text-muted-foreground">
          {messages.legal.borradorBreve}
        </p>
      )}
      {error ? (
        <p id={`${id}-error`} role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}
