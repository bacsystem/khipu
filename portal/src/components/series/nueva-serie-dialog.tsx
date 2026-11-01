"use client";

import { PlusCircleIcon, PlusIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import { useState } from "react";
import { NuevaSerieForm } from "@/components/series/nueva-serie-form";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "khipu-design-system/ui/dialog";
import { ACCION_PRINCIPAL } from "@/lib/estilos";
import { cn } from "@/lib/utils";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";

export function NuevaSerieDialog({ className }: { className?: string }) {
  const t = useMessages().series.dialogo;
  const soloLectura = useSinPermisoDeConfigurar();
  const [abierto, setAbierto] = useState(false);

  return (
    <Dialog open={abierto} onOpenChange={setAbierto}>
      <DialogTrigger disabled={soloLectura !== null} title={soloLectura ?? undefined}
        className={
          className ??
          cn(ACCION_PRINCIPAL, "self-start md:self-auto")
        }
      >
        <PlusIcon weight="bold" className="size-4" />
        {t.nueva}
      </DialogTrigger>
      <DialogContent className="gap-0 p-0">
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
              <PlusCircleIcon className="size-5" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-titulo font-semibold tracking-tight">{t.titulo}</DialogTitle>
              <DialogDescription className="text-control">{t.descripcion}</DialogDescription>
            </div>
          </div>
        </DialogHeader>
        <div className="px-5 py-4">
          <NuevaSerieForm onGuardado={() => setAbierto(false)} onCancelar={() => setAbierto(false)} />
        </div>
      </DialogContent>
    </Dialog>
  );
}
