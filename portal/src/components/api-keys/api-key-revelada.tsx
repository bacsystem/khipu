"use client";

import { WarningIcon } from "@phosphor-icons/react";
import { useMessages, useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { Aviso } from "khipu-design-system/patrones/cabecera-dialogo";
import { BotonCopiar } from "khipu-design-system/patrones/boton-copiar";
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
          {/* Solo el icono: el texto «Copiar»/«Copiado» del `BotonCopiar` del DS va fijo en español; el nombre accesible sale de `titulo` (traducido). */}
          <BotonCopiar texto={apiKey} titulo={t.copiar} className={cn(BOTON_SECUNDARIO, "size-control shrink-0 px-0")} />
        </div>
      </div>
      <Aviso tono="aviso" icon={WarningIcon}>
        {aviso ?? <p>{tr.rich("aviso", { b: (texto) => <strong className="font-semibold">{texto}</strong> })}</p>}
      </Aviso>
    </>
  );
}
