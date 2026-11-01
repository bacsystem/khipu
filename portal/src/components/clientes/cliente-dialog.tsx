"use client";

import { PencilSimpleIcon, PlusIcon, UsersIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import { type ReactNode, useState } from "react";
import { Dialog, DialogContent, DialogTrigger } from "khipu-design-system/ui/dialog";
import type { ClienteGuardado } from "@/lib/api/clientes";
import { ACCION_PRINCIPAL } from "@/lib/estilos";
import { useSoloLectura } from "@/lib/solo-lectura";
import { cn } from "@/lib/utils";
import { ClienteForm } from "./cliente-form";
import { CabeceraDialogo } from "khipu-design-system/patrones/cabecera-dialogo";

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
            {existente ? <PencilSimpleIcon className="size-3.5" /> : <PlusIcon weight="bold" className="size-4" />}
            {existente ? t.editar : t.nuevo}
          </>
        )}
      </DialogTrigger>
      <DialogContent className="gap-0 p-0 sm:max-w-xl">
        <CabeceraDialogo icon={UsersIcon} titulo={existente ? t.tituloEditar : t.tituloNuevo} descripcion={existente ? t.descripcionEditar : t.descripcionNuevo} />
        <div className="px-5 py-4">
          <ClienteForm existente={existente} onGuardado={() => setAbierto(false)} onCancelar={() => setAbierto(false)} />
        </div>
      </DialogContent>
    </Dialog>
  );
}
