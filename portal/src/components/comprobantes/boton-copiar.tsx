"use client";

import { CheckIcon, CopyIcon } from "lucide-react";
import { useState } from "react";
import { cn } from "@/lib/utils";

export function BotonCopiar({ texto, titulo = "Copiar", className }: { texto: string; titulo?: string; className?: string }) {
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
          // el navegador puede denegar el acceso al portapapeles; no hay nada más que hacer
        }
      }}
      className={cn("rounded p-0.5 text-muted-foreground/70 transition-colors hover:bg-secondary hover:text-foreground", className)}
    >
      {copiado ? <CheckIcon className="size-3.5" /> : <CopyIcon className="size-3.5" />}
    </button>
  );
}
