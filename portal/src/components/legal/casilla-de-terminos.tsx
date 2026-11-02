import Link from "next/link";
import { Casilla } from "khipu-design-system/formularios/casilla";
import { REVISADO_POR_LEGAL } from "@/content/legal/version";
import { useMessages } from "next-intl";

const ENLACE = "font-medium text-foreground underline underline-offset-2 hover:text-primary";

/**
 * La casilla de «Acepto los términos del servicio y la política de privacidad» (#342), con un enlace a cada texto. La usan el registro y la
 * pantalla que pide volver a aceptar. Los enlaces abren otra pestaña: leerlos no debe hacer perder lo que ya se llenó del formulario.
 * El error se anuncia y se ata a la casilla, como en `FormField`. Mientras los textos sean un borrador, se dice aquí mismo: no solo a quien
 * abre el enlace. `revisado` es para las pruebas: la casilla usa la bandera de `version.ts`. Es la `Casilla` del design system: controlada
 * (`checked` + `onCheckedChange`), así que con react-hook-form va por un `Controller`.
 */
export function CasillaDeTerminos({
  id,
  error,
  revisado = REVISADO_POR_LEGAL,
  checked,
  onCheckedChange,
  name,
  required,
  disabled,
}: {
  id: string;
  error?: string;
  revisado?: boolean;
  checked?: boolean;
  onCheckedChange?: (marcada: boolean) => void;
  name?: string;
  required?: boolean;
  disabled?: boolean;
}) {
  const messages = useMessages();
  const t = messages.auth.registro;
  const describe = [error ? `${id}-error` : null, revisado ? null : `${id}-borrador`].filter(Boolean).join(" ") || undefined;
  return (
    <div className="grid gap-1.5">
      <Casilla
        id={id}
        etiqueta={
          <span className="font-normal text-foreground/80">
            {t.aceptoLos}{" "}
            <Link href="/terminos" target="_blank" rel="noopener noreferrer" className={ENLACE}>
              {t.terminos}
            </Link>{" "}
            {t.yLa}{" "}
            <Link href="/privacidad" target="_blank" rel="noopener noreferrer" className={ENLACE}>
              {t.privacidad}
            </Link>
          </span>
        }
        checked={checked}
        onCheckedChange={onCheckedChange}
        invalido={!!error}
        describidoPor={describe}
        name={name}
        required={required}
        disabled={disabled}
      />
      {revisado ? null : (
        <p id={`${id}-borrador`} data-testid="borrador-legal-casilla" className="pl-7 text-etiqueta text-muted-foreground">
          {messages.legal.borradorBreve}
        </p>
      )}
      {error ? (
        <p id={`${id}-error`} role="alert" className="text-control text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}
