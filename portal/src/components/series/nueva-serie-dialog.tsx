"use client";

import { PlusCircleIcon, PlusIcon } from "lucide-react";
import { useState } from "react";
import { NuevaSerieForm } from "@/components/series/nueva-serie-form";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";

export function NuevaSerieDialog({ className }: { className?: string }) {
  const [abierto, setAbierto] = useState(false);

  return (
    <Dialog open={abierto} onOpenChange={setAbierto}>
      <DialogTrigger
        className={
          className ??
          "inline-flex items-center gap-1 self-start rounded-lg bg-primary px-3.5 py-2 text-sm font-semibold text-primary-foreground shadow-xs transition-all hover:opacity-95 active:scale-[0.99] md:self-auto"
        }
      >
        <PlusIcon className="size-4" />
        Nueva serie
      </DialogTrigger>
      <DialogContent className="gap-0 p-0">
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
              <PlusCircleIcon className="size-5" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">Nueva serie o contingencia</DialogTitle>
              <DialogDescription className="text-[13px]">Alta de serie alfanumérica conforme al catálogo SUNAT N.º 01</DialogDescription>
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
