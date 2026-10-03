"use client";

import { CheckIcon, CopyIcon, TriangleAlertIcon } from "lucide-react";
import { useState, type ReactNode } from "react";
import { BOTON_SECUNDARIO, ETIQUETA_DATO } from "@/lib/estilos";
import { cn } from "@/lib/utils";

const AVISO_DEL_PORTAL = (
  <p>
    Guárdala ahora en un lugar seguro: <strong className="font-semibold">no volverá a mostrarse</strong>. Solo se conserva un hash, así que si la
    pierdes tendrás que revocarla y crear otra.
  </p>
);

/**
 * Una API key recién creada, que se muestra una sola vez (el backend guarda un hash). La usan el diálogo de «Crear API key» del
 * portal de clientes y el alta asistida del backoffice, con el mismo tratamiento. `aviso` permite otro texto: en el backoffice
 * quien la ve no la guarda para sí, la entrega a un cliente.
 */
export function ApiKeyRevelada({ apiKey, etiqueta = "Tu API key", aviso = AVISO_DEL_PORTAL }: { apiKey: string; etiqueta?: string; aviso?: ReactNode }) {
  const [copiada, setCopiada] = useState(false);

  async function copiar() {
    try {
      await navigator.clipboard.writeText(apiKey);
      setCopiada(true);
    } catch {
      // el navegador puede denegar el portapapeles; la llave sigue visible para copiarla a mano
    }
  }

  return (
    <>
      <div>
        <span className={cn(ETIQUETA_DATO, "mb-1.5")}>{etiqueta}</span>
        <div className="flex items-center gap-2">
          <code
            data-testid="api-key-nueva"
            className="min-w-0 flex-1 truncate rounded-lg border border-border bg-muted px-3 py-2 font-mono text-[13px] font-semibold text-foreground select-all"
          >
            {apiKey}
          </code>
          <button
            type="button"
            onClick={copiar}
            className={cn(BOTON_SECUNDARIO, "h-9 shrink-0 px-3 text-[12px]", copiada && "border-success-border bg-success text-success-foreground")}
          >
            {copiada ? <CheckIcon className="size-4" /> : <CopyIcon className="size-4" />}
            {copiada ? "Copiada" : "Copiar"}
          </button>
        </div>
      </div>
      <div className="flex items-start gap-2.5 rounded-lg border border-warning-border bg-warning px-3 py-2.5 text-[12px] leading-relaxed text-warning-foreground">
        <TriangleAlertIcon className="mt-0.5 size-4 shrink-0" />
        {aviso}
      </div>
    </>
  );
}
