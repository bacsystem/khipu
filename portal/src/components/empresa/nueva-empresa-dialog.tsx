"use client";

import { Building2Icon, PlusIcon } from "lucide-react";
import { useState } from "react";
import { NuevaEmpresaForm } from "@/components/empresa/nueva-empresa-form";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";

export function NuevaEmpresaDialog({ className, etiqueta = "Nueva empresa" }: { className?: string; etiqueta?: string }) {
  const [abierto, setAbierto] = useState(false);

  return (
    <Dialog open={abierto} onOpenChange={setAbierto}>
      <DialogTrigger
        className={
          className ??
          "inline-flex items-center gap-1 rounded-lg bg-primary px-3.5 py-2 text-sm font-semibold text-primary-foreground shadow-xs transition-all hover:opacity-95 active:scale-[0.99]"
        }
      >
        <PlusIcon className="size-4" />
        {etiqueta}
      </DialogTrigger>
      <DialogContent className="gap-0 p-0">
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
              <Building2Icon className="size-5" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">Registrar empresa</DialogTitle>
              <DialogDescription className="text-[13px]">
                Asocia otra razón social a tu cuenta; cada empresa tiene sus propias series, certificado y credenciales SOL.
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>
        <div className="px-5 py-4">
          <NuevaEmpresaForm onGuardado={() => setAbierto(false)} onCancelar={() => setAbierto(false)} />
        </div>
      </DialogContent>
    </Dialog>
  );
}
