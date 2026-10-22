"use client";

import { Check, Copy } from "lucide-react";
import { useMessages } from "next-intl";
import { useState } from "react";
import { Button } from "khipu-design-system/ui/button";
import { apiRequest } from "@/lib/api/browser";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";

export function GenerarKeyPrueba({ onGenerada }: { onGenerada: (key: string) => void }) {
  const soloLectura = useSinPermisoDeConfigurar();
  const t = useMessages().developers.generarKey;
  const mensajeError = useMensajeError();
  const [generando, setGenerando] = useState(false);
  const [apiKey, setApiKey] = useState<string | null>(null);
  const [copiada, setCopiada] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function generar() {
    setGenerando(true);
    setError(null);
    const res = await apiRequest<{ api_key: string }>("/api/proxy/empresa/api-keys", { method: "POST" });
    setGenerando(false);
    if (res.estado !== "exito" || !res.datos) {
      setError(mensajeError(res.codigo));
      return;
    }
    setApiKey(res.datos.api_key);
    setCopiada(false);
    onGenerada(res.datos.api_key);
  }

  async function copiar() {
    if (!apiKey) return;
    await navigator.clipboard.writeText(apiKey);
    setCopiada(true);
  }

  if (apiKey) {
    return (
      <div className="grid gap-2 rounded-lg border border-border bg-card p-4">
        <p className="text-[13px] text-muted-foreground">
          {t.cargada}
        </p>
        <div className="flex items-center gap-2">
          <code className="flex h-8 min-w-0 flex-1 items-center rounded-md border border-border bg-muted px-3 font-mono text-[12px]">
            <span className="truncate">{apiKey}</span>
          </code>
          <Button type="button" variant="outline" size="icon-sm" onClick={copiar}>
            {copiada ? <Check className="size-4" /> : <Copy className="size-4" />}
            <span className="sr-only">{t.copiar}</span>
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="grid gap-2 rounded-lg border border-border bg-card p-4">
      {error ? <p role="alert" className="text-[13px] text-destructive">{error}</p> : null}
      <p className="text-[13px] text-muted-foreground">{t.intro}</p>
      <Button type="button" size="sm" className="w-fit" disabled={generando || soloLectura !== null} title={soloLectura ?? undefined} onClick={generar}>
        {generando ? t.generando : t.generar}
      </Button>
    </div>
  );
}
