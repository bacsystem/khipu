"use client";

import { PencilIcon, PlusIcon, StoreIcon } from "lucide-react";
import { type ReactNode, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import type { Establecimiento } from "@/lib/api/establecimientos";
import { EstablecimientoForm } from "./establecimiento-form";

/** Alta (sin `existente`) o edición de un anexo en el mismo `Dialog` que series y API keys. */
export function EstablecimientoDialog({ existente, trigger, className }: { existente?: Establecimiento | null; trigger?: ReactNode; className?: string }) {
  const [abierto, setAbierto] = useState(false);
  return (
    <Dialog open={abierto} onOpenChange={setAbierto}>
      <DialogTrigger
        className={
          className ??
          "inline-flex items-center gap-1 self-start rounded-lg bg-primary px-3.5 py-2 text-sm font-semibold text-primary-foreground shadow-xs transition-all hover:opacity-95 active:scale-[0.99] md:self-auto"
        }
        data-testid={existente ? `editar-establecimiento-${existente.codigo}` : "nuevo-establecimiento"}
      >
        {trigger ?? (
          <>
            {existente ? <PencilIcon className="size-3.5" /> : <PlusIcon className="size-4" />}
            {existente ? "Editar" : "Nuevo establecimiento"}
          </>
        )}
      </DialogTrigger>
      <DialogContent className="gap-0 p-0 sm:max-w-2xl">
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
              <StoreIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">{existente ? `Establecimiento ${existente.codigo}` : "Nuevo establecimiento anexo"}</DialogTitle>
              <DialogDescription className="text-[13px]">
                {existente ? "Cambia el nombre o el domicilio; las series asignadas siguen emitiendo desde él" : "Sucursal, tienda o almacén declarado en la ficha RUC; sus series emiten con este domicilio"}
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>
        <div className="px-5 py-4">
          <EstablecimientoForm existente={existente} onGuardado={() => setAbierto(false)} onCancelar={() => setAbierto(false)} />
        </div>
      </DialogContent>
    </Dialog>
  );
}
