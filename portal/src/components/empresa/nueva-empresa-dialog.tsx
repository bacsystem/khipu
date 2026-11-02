"use client";

import { BuildingsIcon, PlusIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import { useState } from "react";
import { NuevaEmpresaForm } from "@/components/empresa/nueva-empresa-form";
import { Dialog, DialogContent, DialogTrigger } from "khipu-design-system/ui/dialog";
import { ACCION_PRINCIPAL } from "@/lib/estilos";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";
import { CabeceraDialogo } from "khipu-design-system/patrones/cabecera-dialogo";

export function NuevaEmpresaDialog({ className, etiqueta }: { className?: string; etiqueta?: string }) {
  const t = useMessages().empresa.nueva;
  const soloLectura = useSinPermisoDeConfigurar();
  const [abierto, setAbierto] = useState(false);

  return (
    <Dialog open={abierto} onOpenChange={setAbierto}>
      <DialogTrigger disabled={soloLectura !== null} title={soloLectura ?? undefined}
        className={className ?? ACCION_PRINCIPAL}
      >
        <PlusIcon weight="bold" className="size-4" />
        {etiqueta ?? t.nuevaEmpresa}
      </DialogTrigger>
      <DialogContent className="gap-0 p-0">
        <CabeceraDialogo icon={BuildingsIcon} titulo={t.titulo} descripcion={t.descripcion} />
        <div className="px-5 py-4">
          <NuevaEmpresaForm onGuardado={() => setAbierto(false)} onCancelar={() => setAbierto(false)} />
        </div>
      </DialogContent>
    </Dialog>
  );
}
