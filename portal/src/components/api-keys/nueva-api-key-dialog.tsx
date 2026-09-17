"use client";

import { CheckIcon, CopyIcon, KeyRoundIcon, PlusIcon, TriangleAlertIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { apiRequest } from "@/lib/api/browser";
import { BOTON_PRIMARIO, BOTON_SECUNDARIO, ETIQUETA_DATO } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

/** Crea una API key y la muestra una sola vez; al cerrar, refresca la lista del servidor. */
export function NuevaApiKeyDialog({ className }: { className?: string }) {
  const router = useRouter();
  const [abierto, setAbierto] = useState(false);
  const [creando, setCreando] = useState(false);
  const [apiKey, setApiKey] = useState<string | null>(null);
  const [copiada, setCopiada] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function cambiarAbierto(valor: boolean) {
    setAbierto(valor);
    if (!valor) {
      if (apiKey) router.refresh();
      setApiKey(null);
      setCopiada(false);
      setError(null);
    }
  }

  async function crear() {
    setCreando(true);
    setError(null);
    const res = await apiRequest<{ api_key: string }>("/api/proxy/empresa/api-keys", { method: "POST" });
    setCreando(false);
    if (res.estado !== "exito" || !res.datos) {
      setError(mensajeError(res.codigo));
      return;
    }
    setApiKey(res.datos.api_key);
  }

  async function copiar() {
    if (!apiKey) return;
    try {
      await navigator.clipboard.writeText(apiKey);
      setCopiada(true);
    } catch {
      // el navegador puede denegar el portapapeles; la llave sigue visible para copiarla a mano
    }
  }

  return (
    <Dialog open={abierto} onOpenChange={cambiarAbierto}>
      <DialogTrigger
        className={
          className ??
          "inline-flex items-center gap-1 self-start rounded-lg bg-primary px-3.5 py-2 text-sm font-semibold text-primary-foreground shadow-xs transition-all hover:opacity-95 active:scale-[0.99] md:self-auto"
        }
      >
        <PlusIcon className="size-4" />
        Crear API key
      </DialogTrigger>
      <DialogContent className="gap-0 p-0">
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
              <KeyRoundIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">Nueva API key</DialogTitle>
              <DialogDescription className="text-[13px]">Credencial para autenticar tu integración con el header X-Api-Key</DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="grid gap-4 px-5 py-4">
          {apiKey ? (
            <>
              <div>
                <span className={cn(ETIQUETA_DATO, "mb-1.5")}>Tu API key</span>
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
                <p>
                  Guárdala ahora en un lugar seguro: <strong className="font-semibold">no volverá a mostrarse</strong>. Solo se conserva un hash, así que si la
                  pierdes tendrás que revocarla y crear otra.
                </p>
              </div>
            </>
          ) : (
            <>
              <p className="text-[13px] leading-relaxed text-muted-foreground">
                Se generará una llave aleatoria para la empresa activa. Cada integración (ERP, e-commerce, punto de venta) debería usar su propia llave, así
                puedes revocarla sin afectar a las demás.
              </p>
              {error ? <p className="text-sm text-destructive">{error}</p> : null}
            </>
          )}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-border/60 px-5 py-3">
          {apiKey ? (
            <button type="button" onClick={() => cambiarAbierto(false)} className={cn(BOTON_PRIMARIO, "h-9 px-3.5 text-[13px]")}>
              Listo, la guardé
            </button>
          ) : (
            <>
              <button type="button" onClick={() => cambiarAbierto(false)} className={cn(BOTON_SECUNDARIO, "h-9 px-3.5 text-[13px]")}>
                Cancelar
              </button>
              <button type="button" disabled={creando} onClick={crear} className={cn(BOTON_PRIMARIO, "h-9 px-3.5 text-[13px]")}>
                {creando ? "Generando…" : "Generar llave"}
              </button>
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
