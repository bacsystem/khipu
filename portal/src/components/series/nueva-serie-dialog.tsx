"use client";

import { PlusCircleIcon, PlusIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import { useState } from "react";
import { NuevaSerieForm } from "@/components/series/nueva-serie-form";
import { Dialog, DialogContent, DialogTrigger } from "khipu-design-system/ui/dialog";
import { ACCION_PRINCIPAL } from "@/lib/estilos";
import { cn } from "@/lib/utils";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";
import { CabeceraDialogo } from "khipu-design-system/patrones/cabecera-dialogo";

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
        <CabeceraDialogo icon={PlusCircleIcon} titulo={t.titulo} descripcion={t.descripcion} />
        <div className="px-5 py-4">
          <NuevaSerieForm onGuardado={() => setAbierto(false)} onCancelar={() => setAbierto(false)} />
        </div>
      </DialogContent>
    </Dialog>
  );
}
