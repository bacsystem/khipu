"use client";

import { PencilIcon, PlusIcon, UsersIcon } from "lucide-react";
import { useMessages } from "next-intl";
import { type ReactNode, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "khipu-design-system/ui/dialog";
import type { ClienteGuardado } from "@/lib/api/clientes";
import { ACCION_PRINCIPAL } from "@/lib/estilos";
import { useSoloLectura } from "@/lib/solo-lectura";
import { cn } from "@/lib/utils";
import { ClienteForm } from "./cliente-form";

/** Alta (sin `existente`) o edición de un cliente guardado, en el mismo `Dialog` que series y establecimientos. */
export function ClienteDialog({ existente, trigger, className }: { existente?: ClienteGuardado | null; trigger?: ReactNode; className?: string }) {
  const t = useMessages().clientes.dialogo;
  const soloLectura = useSoloLectura();
  const [abierto, setAbierto] = useState(false);
  return (
    <Dialog open={abierto} onOpenChange={setAbierto}>
      <DialogTrigger disabled={soloLectura !== null} title={soloLectura ?? undefined} className={className ?? cn(ACCION_PRINCIPAL, "self-start md:self-auto")} data-testid={existente ? "editar-cliente" : "nuevo-cliente"}>
        {trigger ?? (
          <>
            {existente ? <PencilIcon className="size-3.5" /> : <PlusIcon className="size-4" />}
            {existente ? t.editar : t.nuevo}
          </>
        )}
      </DialogTrigger>
      <DialogContent className="gap-0 p-0 sm:max-w-xl">
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
              <UsersIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">{existente ? t.tituloEditar : t.tituloNuevo}</DialogTitle>
              <DialogDescription className="text-[13px]">{existente ? t.descripcionEditar : t.descripcionNuevo}</DialogDescription>
            </div>
          </div>
        </DialogHeader>
        <div className="px-5 py-4">
          <ClienteForm existente={existente} onGuardado={() => setAbierto(false)} onCancelar={() => setAbierto(false)} />
        </div>
      </DialogContent>
    </Dialog>
  );
}
