"use client";

import { PencilSimpleIcon, PlusIcon, StorefrontIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import { type ReactNode, useState } from "react";
import { interpolar } from "@/i18n/mensajes";
import { Dialog, DialogContent, DialogTrigger } from "khipu-design-system/ui/dialog";
import type { Establecimiento } from "@/lib/api/establecimientos";
import { ACCION_PRINCIPAL } from "@/lib/estilos";
import { cn } from "@/lib/utils";
import { EstablecimientoForm } from "./establecimiento-form";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";
import { CabeceraDialogo } from "khipu-design-system/patrones/cabecera-dialogo";

/** Alta (sin `existente`) o edición de un anexo en el mismo `Dialog` que series y API keys. */
export function EstablecimientoDialog({ existente, trigger, className }: { existente?: Establecimiento | null; trigger?: ReactNode; className?: string }) {
  const t = useMessages().establecimientos.dialogo;
  const soloLectura = useSinPermisoDeConfigurar();
  const [abierto, setAbierto] = useState(false);
  return (
    <Dialog open={abierto} onOpenChange={setAbierto}>
      <DialogTrigger disabled={soloLectura !== null} title={soloLectura ?? undefined}
        className={className ?? cn(ACCION_PRINCIPAL, "self-start md:self-auto")}
        data-testid={existente ? `editar-establecimiento-${existente.codigo}` : "nuevo-establecimiento"}
      >
        {trigger ?? (
          <>
            {existente ? <PencilSimpleIcon className="size-3.5" /> : <PlusIcon weight="bold" className="size-4" />}
            {existente ? t.editar : t.nuevo}
          </>
        )}
      </DialogTrigger>
      <DialogContent className="gap-0 p-0 sm:max-w-2xl">
        <CabeceraDialogo icon={StorefrontIcon} titulo={<>{existente ? interpolar(t.tituloEditar, { codigo: existente.codigo }) : t.tituloNuevo}</>} descripcion={existente ? t.descripcionEditar : t.descripcionNuevo} />
        <div className="px-5 py-4">
          <EstablecimientoForm existente={existente} onGuardado={() => setAbierto(false)} onCancelar={() => setAbierto(false)} />
        </div>
      </DialogContent>
    </Dialog>
  );
}
