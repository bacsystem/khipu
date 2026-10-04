"use client";

import { EyeIcon } from "lucide-react";
import { useRef, useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import { BOTON_SECUNDARIO } from "@/lib/estilos";
import { formatearFechaHora } from "@/lib/formato";
import { messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

const t = messages.soporte.aviso;

/**
 * El aviso permanente de que se está actuando como un cliente (#184). Va arriba de todo el portal privado, fijo al hacer scroll, para que nadie —ni el
 * administrador ni quien mire su pantalla— confunda esto con una sesión normal. Salir borra las cookies de cliente de este navegador y vuelve a la cuenta en
 * el backoffice; la sesión del administrador es otra cookie y sigue abierta. `irA` es para las pruebas: por defecto navega de verdad.
 */
export function AvisoDeSoporte({
  email,
  hasta,
  cuentaId,
  irA = (url) => window.location.assign(url),
}: {
  email: string;
  hasta: string;
  cuentaId: string;
  irA?: (url: string) => void;
}) {
  const [saliendo, setSaliendo] = useState(false);
  const [error, setError] = useState(false);
  // Un ref, no el estado: dos clics en el mismo tick leen el `saliendo` viejo del closure.
  const saliendoRef = useRef(false);

  async function salir() {
    if (saliendoRef.current) return;
    saliendoRef.current = true;
    setSaliendo(true);
    setError(false);
    const res = await apiRequest<null>("/api/soporte/salir", { method: "POST" });
    if (res.estado !== "exito") {
      saliendoRef.current = false;
      setSaliendo(false);
      setError(true);
      return;
    }
    irA(`/admin/cuentas/${cuentaId}`);
  }

  return (
    <div role="region" aria-label={t.region} data-testid="aviso-de-soporte" className="sticky top-0 z-40 border-b border-warning-border bg-warning px-4 py-2 text-warning-foreground">
      <div className="mx-auto flex max-w-[1520px] flex-wrap items-center gap-x-4 gap-y-1.5">
        <EyeIcon className="size-4 shrink-0" aria-hidden="true" />
        <p className="min-w-0 flex-1 text-[13px]">
          <strong className="font-semibold">{t.titulo}.</strong> {t.descripcion.replace("{email}", email).replace("{hasta}", formatearFechaHora(hasta))}
        </p>
        {error ? (
          <span role="alert" className="text-xs font-medium text-destructive">
            {t.error}
          </span>
        ) : null}
        <button type="button" onClick={salir} disabled={saliendo} data-testid="salir-de-soporte" className={cn(BOTON_SECUNDARIO, "h-8 bg-card text-xs")}>
          {saliendo ? t.saliendo : t.salir}
        </button>
      </div>
    </div>
  );
}
