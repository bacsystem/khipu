"use client";

import { CopyIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import { Button } from "khipu-design-system/ui/button";
import { interpolar } from "@/i18n/mensajes";
import { pedirDuplicado } from "@/lib/comprobantes/duplicar";
import { useSoloLectura } from "@/lib/solo-lectura";
import { cn } from "@/lib/utils";

/**
 * Duplicar un comprobante (#323): abre «Nuevo comprobante» con su cliente, moneda y líneas, con la fecha de hoy y sin número. En la ficha es un botón con texto; en la tabla,
 * un icono (`compacto`) con el estilo de las otras acciones de la fila.
 */
export function DuplicarButton({ id, numero, compacto = false, className }: { id: string; numero: string; compacto?: boolean; className?: string }) {
  const soloLectura = useSoloLectura();
  const t = useMessages().comprobantes.duplicarBoton;
  if (compacto) {
    return (
      <button
        type="button"
        disabled={soloLectura !== null}
        title={soloLectura ?? t.titulo}
        aria-label={interpolar(t.aria, { numero })}
        onClick={() => pedirDuplicado(id)}
        className={cn("flex h-control w-control items-center justify-center rounded-lg border border-border text-muted-foreground shadow-xs transition-colors hover:bg-secondary hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50", className)}
      >
        <CopyIcon weight="bold" className="size-3.5" />
      </button>
    );
  }
  return (
    <Button variant="outline" disabled={soloLectura !== null} title={soloLectura ?? undefined} onClick={() => pedirDuplicado(id)} data-testid="duplicar" className={className}>
      <CopyIcon weight="bold" className="size-4" />
      {t.boton}
    </Button>
  );
}
