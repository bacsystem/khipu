"use client";

import { useMessages } from "next-intl";
import { useState } from "react";
import { Button } from "khipu-design-system/ui/button";
import { BotonCopiar } from "khipu-design-system/patrones/boton-copiar";
import { apiRequest } from "@/lib/api/browser";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";
import { BOTON_SECUNDARIO, TARJETA } from "@/lib/estilos";
import { cn } from "@/lib/utils";

export function GenerarKeyPrueba({ onGenerada }: { onGenerada: (key: string) => void }) {
  const soloLectura = useSinPermisoDeConfigurar();
  const t = useMessages().developers.generarKey;
  const mensajeError = useMensajeError();
  const [generando, setGenerando] = useState(false);
  const [apiKey, setApiKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function generar() {
    setGenerando(true);
    setError(null);
    const res = await apiRequest<{ api_key: string }>("/api/proxy/empresa/api-keys", { method: "POST" });
    setGenerando(false);
    if (res.estado !== "exito" || !res.datos) {
      setError(mensajeError(res.codigo, res.mensaje));
      return;
    }
    setApiKey(res.datos.api_key);
    onGenerada(res.datos.api_key);
  }

  if (apiKey) {
    return (
      <div className={cn(TARJETA, "grid gap-2 p-4")}>
        <p className="text-control text-muted-foreground">
          {t.cargada}
        </p>
        <div className="flex items-center gap-2">
          <code className="flex h-control min-w-0 flex-1 items-center rounded-md border border-border bg-muted px-3 font-mono text-control">
            <span className="truncate">{apiKey}</span>
          </code>
          <BotonCopiar texto={apiKey} titulo={t.copiar} className={cn(BOTON_SECUNDARIO, "size-control shrink-0 px-0")} />
        </div>
      </div>
    );
  }

  return (
    <div className={cn(TARJETA, "grid gap-2 p-4")}>
      {error ? <p role="alert" className="text-control text-destructive">{error}</p> : null}
      <p className="text-control text-muted-foreground">{t.intro}</p>
      <Button type="button" size="sm" className="w-fit" disabled={generando || soloLectura !== null} title={soloLectura ?? undefined} onClick={generar}>
        {generando ? t.generando : t.generar}
      </Button>
    </div>
  );
}
