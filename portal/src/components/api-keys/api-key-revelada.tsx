"use client";

import { CheckIcon, CopyIcon, WarningIcon } from "@phosphor-icons/react";
import { useMessages, useTranslations } from "next-intl";
import { useState, type ReactNode } from "react";
import { BOTON_SECUNDARIO, ETIQUETA_DATO } from "@/lib/estilos";
import { cn } from "@/lib/utils";

/**
 * Una API key recién creada, que se muestra una sola vez (el backend guarda un hash). La usan el diálogo de «Crear API key» del
 * portal de clientes y el alta asistida del backoffice, con el mismo tratamiento. `aviso` permite otro texto: en el backoffice
 * quien la ve no la guarda para sí, la entrega a un cliente.
 */
export function ApiKeyRevelada({ apiKey, etiqueta, aviso }: { apiKey: string; etiqueta?: string; aviso?: ReactNode }) {
  const t = useMessages().apiKeys.revelada;
  const tr = useTranslations("apiKeys.revelada");
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
        <span className={cn(ETIQUETA_DATO, "mb-1.5")}>{etiqueta ?? t.etiqueta}</span>
        <div className="flex items-center gap-2">
          {/* La caja mide lo mismo que el botón de al lado (h-10, la de un campo): el texto se recorta dentro de un span porque `truncate` no corta en un flex. */}
          <code
            data-testid="api-key-nueva"
            className="flex h-control min-w-0 flex-1 items-center rounded-lg border border-border bg-muted px-3 font-mono text-cuerpo font-semibold text-foreground select-all"
          >
            <span className="truncate">{apiKey}</span>
          </code>
          <button
            type="button"
            onClick={copiar}
            className={cn(BOTON_SECUNDARIO, "shrink-0", copiada && "border-success-border bg-success text-success-foreground")}
          >
            {copiada ? <CheckIcon weight="bold" className="size-4" /> : <CopyIcon weight="bold" className="size-4" />}
            {copiada ? t.copiada : t.copiar}
          </button>
        </div>
      </div>
      <div className="flex items-start gap-2.5 rounded-lg border border-warning-border bg-warning px-3 py-2.5 text-control leading-relaxed text-warning-foreground">
        <WarningIcon className="mt-0.5 size-4 shrink-0" />
        {aviso ?? <p>{tr.rich("aviso", { b: (texto) => <strong className="font-semibold">{texto}</strong> })}</p>}
      </div>
    </>
  );
}
