"use client";

import { CheckIcon, CopyIcon } from "lucide-react";
import { useState } from "react";
import { cn } from "@/lib/utils";

/**
 * Copia `texto` al portapapeles y confirma con un check durante 1,5 s.
 * Sin `etiqueta` es solo el icono (celdas de tabla); con ella muestra "Copiar" / "Copiado" (barras de acciones).
 */
export function BotonCopiar({
  texto,
  titulo = "Copiar",
  etiqueta = false,
  className,
}: {
  texto: string;
  titulo?: string;
  etiqueta?: boolean;
  className?: string;
}) {
  const [copiado, setCopiado] = useState(false);

  return (
    <button
      type="button"
      title={titulo}
      onClick={async (e) => {
        e.stopPropagation();
        try {
          await navigator.clipboard.writeText(texto);
          setCopiado(true);
          setTimeout(() => setCopiado(false), 1500);
        } catch {
          // el navegador puede denegar el acceso al portapapeles; el contenido sigue seleccionable
        }
      }}
      className={cn(
        "transition-colors hover:bg-secondary hover:text-foreground",
        etiqueta ? "inline-flex items-center gap-1.5 rounded-md text-muted-foreground" : "rounded p-0.5 text-muted-foreground/70",
        className,
      )}
    >
      {copiado ? <CheckIcon className={cn("size-3.5", etiqueta && "text-success-foreground")} /> : <CopyIcon className="size-3.5" />}
      {etiqueta ? (copiado ? "Copiado" : "Copiar") : null}
    </button>
  );
}
