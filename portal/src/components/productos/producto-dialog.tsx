"use client";

import { PackageIcon, PencilIcon, PlusIcon } from "lucide-react";
import { type ReactNode, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import type { ProductoGuardado } from "@/lib/api/productos";
import { ACCION_PRINCIPAL } from "@/lib/estilos";
import { useSoloLectura } from "@/lib/solo-lectura";
import { cn } from "@/lib/utils";
import { ProductoForm } from "./producto-form";

/** Alta (sin `existente`) o edición de un producto guardado, en el mismo `Dialog` que clientes, series y establecimientos. */
export function ProductoDialog({ existente, trigger, className }: { existente?: ProductoGuardado | null; trigger?: ReactNode; className?: string }) {
  const soloLectura = useSoloLectura();
  const [abierto, setAbierto] = useState(false);
  return (
    <Dialog open={abierto} onOpenChange={setAbierto}>
      <DialogTrigger disabled={soloLectura !== null} title={soloLectura ?? undefined} className={className ?? cn(ACCION_PRINCIPAL, "self-start md:self-auto")} data-testid={existente ? "editar-producto" : "nuevo-producto"}>
        {trigger ?? (
          <>
            {existente ? <PencilIcon className="size-3.5" /> : <PlusIcon className="size-4" />}
            {existente ? "Editar" : "Nuevo producto"}
          </>
        )}
      </DialogTrigger>
      <DialogContent className="gap-0 p-0 sm:max-w-xl">
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
              <PackageIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">{existente ? "Editar producto" : "Nuevo producto"}</DialogTitle>
              <DialogDescription className="text-[13px]">{existente ? "Los comprobantes ya emitidos conservan sus datos" : "Lo verás al buscar en los ítems de «Nuevo comprobante»"}</DialogDescription>
            </div>
          </div>
        </DialogHeader>
        <div className="px-5 py-4">
          <ProductoForm existente={existente} onGuardado={() => setAbierto(false)} onCancelar={() => setAbierto(false)} />
        </div>
      </DialogContent>
    </Dialog>
  );
}
