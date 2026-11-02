"use client";

import { PlusIcon } from "@phosphor-icons/react";
import Link from "next/link";
import { useMessages } from "next-intl";
import { ACCION_PRINCIPAL } from "@/lib/estilos";
import { useSoloLectura } from "@/lib/solo-lectura";
import { cn } from "@/lib/utils";

/** «Nueva guía» (#39). Con el rol «Solo lectura» o en modo soporte no se puede emitir: se ve apagado y dice por qué en vez de llevar a un formulario que el backend rechazaría. */
export function NuevaGuiaEnlace() {
  const t = useMessages().guias.pagina;
  const motivo = useSoloLectura();
  if (motivo !== null) {
    return (
      <span aria-disabled="true" title={motivo} className={cn(ACCION_PRINCIPAL, "cursor-not-allowed opacity-50")}>
        <PlusIcon weight="bold" className="size-4" />
        {t.nueva}
      </span>
    );
  }
  return (
    <Link href="/guias/nueva" className={ACCION_PRINCIPAL}>
      <PlusIcon weight="bold" className="size-4" />
      {t.nueva}
    </Link>
  );
}
