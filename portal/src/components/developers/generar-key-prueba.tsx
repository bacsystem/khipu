"use client";

import { Check, Copy } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { apiRequest } from "@/lib/api/browser";
import { mensajeError } from "@/lib/messages";

export function GenerarKeyPrueba({ onGenerada }: { onGenerada: (key: string) => void }) {
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
        <p className="text-sm text-muted-foreground">
          Ya se cargó en el campo de autenticación de abajo. Guárdala ahora: no volverá a mostrarse completa.
        </p>
        <div className="flex items-center gap-2">
          <code className="flex-1 truncate rounded-md border border-border bg-muted px-3 py-2 font-mono text-sm">{apiKey}</code>
          <Button type="button" variant="outline" size="icon-sm" onClick={copiar}>
            {copiada ? <Check className="size-4" /> : <Copy className="size-4" />}
            <span className="sr-only">Copiar</span>
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="grid gap-2 rounded-lg border border-border bg-card p-4">
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      <p className="text-sm text-muted-foreground">Probá los endpoints de abajo con una API key real de tu cuenta.</p>
      <Button type="button" size="sm" className="w-fit" disabled={generando} onClick={generar}>
        {generando ? "Generando…" : "Generar API key de prueba"}
      </Button>
    </div>
  );
}
