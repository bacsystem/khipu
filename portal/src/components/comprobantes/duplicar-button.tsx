"use client";

import { CopyPlusIcon } from "lucide-react";
import { Button } from "khipu-design-system/ui/button";
import { pedirDuplicado } from "@/lib/comprobantes/duplicar";
import { useSoloLectura } from "@/lib/solo-lectura";
import { cn } from "@/lib/utils";

/**
 * Duplicar un comprobante (#323): abre «Nuevo comprobante» con su cliente, moneda y líneas, con la fecha de hoy y sin número. En la ficha es un botón con texto; en la tabla,
 * un icono (`compacto`) con el estilo de las otras acciones de la fila.
 */
export function DuplicarButton({ id, numero, compacto = false, className }: { id: string; numero: string; compacto?: boolean; className?: string }) {
  const soloLectura = useSoloLectura();
  if (compacto) {
    return (
      <button
        type="button"
        disabled={soloLectura !== null}
        title={soloLectura ?? "Duplicar: nuevo comprobante con los mismos datos"}
        aria-label={`Duplicar ${numero}`}
        onClick={() => pedirDuplicado(id)}
        className={cn("flex h-9 w-9 items-center justify-center rounded-lg border border-border text-muted-foreground shadow-2xs transition-colors hover:bg-secondary hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50", className)}
      >
        <CopyPlusIcon className="size-3.5" />
      </button>
    );
  }
  return (
    <Button variant="outline" disabled={soloLectura !== null} title={soloLectura ?? undefined} onClick={() => pedirDuplicado(id)} data-testid="duplicar" className={className}>
      <CopyPlusIcon className="size-4" />
      Duplicar
    </Button>
  );
}
