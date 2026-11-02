"use client";

import { LifebuoyIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import { createContext, type ReactNode, useContext } from "react";
import { correoDeSoporte, SIN_SOPORTE, type Soporte } from "@/lib/soporte";
import { cn } from "@/lib/utils";

/**
 * El soporte se lee en el servidor (las variables no llegan al navegador) y baja por contexto desde el layout raíz, así lo ve también
 * la pantalla de error, que es un componente de cliente.
 */
const SoporteContext = createContext<Soporte>(SIN_SOPORTE);

export function SoporteProvider({ soporte, children }: { soporte: Soporte; children: ReactNode }) {
  return <SoporteContext.Provider value={soporte}>{children}</SoporteContext.Provider>;
}

export function useSoporte(): Soporte {
  return useContext(SoporteContext);
}

/** «¿Necesitas ayuda?» con el enlace y el correo de soporte (#250). Sin ninguno configurado no se muestra. */
export function AyudaDeSoporte({ className }: { className?: string }) {
  const { url, email } = useSoporte();
  const t = useMessages().soporte.ayuda;
  if (!url && !email) return null;
  return (
    <p className={cn("flex flex-wrap items-center gap-x-2 gap-y-1 text-control text-muted-foreground", className)}>
      <LifebuoyIcon className="size-3.5 shrink-0" aria-hidden />
      <span>{t.pregunta}</span>
      {url ? (
        <a href={url} target="_blank" rel="noopener noreferrer" className="font-medium text-primary underline-offset-2 hover:underline">
          {t.centro}
        </a>
      ) : null}
      {url && email ? <span aria-hidden>·</span> : null}
      {email ? (
        <a href={correoDeSoporte(email)} className="font-medium text-primary underline-offset-2 hover:underline">
          {email}
        </a>
      ) : null}
    </p>
  );
}
