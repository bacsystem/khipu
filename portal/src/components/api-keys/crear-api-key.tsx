"use client";

import { Check, Copy } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { apiRequest } from "@/lib/api/browser";
import { mensajeError } from "@/lib/messages";

export function CrearApiKey() {
  const [creando, setCreando] = useState(false);
  const [apiKey, setApiKey] = useState<string | null>(null);
  const [copiada, setCopiada] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function crear() {
    setCreando(true);
    setError(null);
    setApiKey(null);
    const res = await apiRequest<{ api_key: string }>("/api/proxy/empresa/api-keys", { method: "POST" });
    setCreando(false);
    if (res.estado !== "exito" || !res.datos) {
      setError(mensajeError(res.codigo));
      return;
    }
    setApiKey(res.datos.api_key);
    setCopiada(false);
  }

  async function copiar() {
    if (!apiKey) return;
    await navigator.clipboard.writeText(apiKey);
    setCopiada(true);
  }

  if (apiKey) {
    return (
      <div className="grid gap-3">
        <p className="text-sm text-muted-foreground">
          Guarda esta llave ahora: no volverá a mostrarse completa.
        </p>
        <div className="flex items-center gap-2">
          <code className="flex-1 truncate rounded-md border border-border bg-muted px-3 py-2 font-mono text-sm">
            {apiKey}
          </code>
          <Button type="button" variant="outline" size="icon-sm" onClick={copiar}>
            {copiada ? <Check className="size-4" /> : <Copy className="size-4" />}
            <span className="sr-only">Copiar</span>
          </Button>
        </div>
        <Button type="button" variant="outline" size="sm" className="w-fit" onClick={() => setApiKey(null)}>
          Crear otra
        </Button>
      </div>
    );
  }

  return (
    <div className="grid gap-3">
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      <Button type="button" size="sm" className="w-fit" disabled={creando} onClick={crear}>
        {creando ? "Creando…" : "Crear API key"}
      </Button>
    </div>
  );
}
