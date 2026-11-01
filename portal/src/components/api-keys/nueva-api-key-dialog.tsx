"use client";

import { KeyIcon, PlusIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "khipu-design-system/ui/dialog";
import { apiRequest } from "@/lib/api/browser";
import { ACCION_PRINCIPAL, BOTON_PRIMARIO, BOTON_SECUNDARIO } from "@/lib/estilos";
import { cn } from "@/lib/utils";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { ApiKeyRevelada } from "./api-key-revelada";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";

/** Crea una API key y la muestra una sola vez; al cerrar, refresca la lista del servidor. */
export function NuevaApiKeyDialog({ className }: { className?: string }) {
  const soloLectura = useSinPermisoDeConfigurar();
  const messages = useMessages();
  const t = messages.apiKeys.nueva;
  const mensajeError = useMensajeError();
  const router = useRouter();
  const [abierto, setAbierto] = useState(false);
  const [creando, setCreando] = useState(false);
  const [apiKey, setApiKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  function cambiarAbierto(valor: boolean) {
    setAbierto(valor);
    if (!valor) {
      if (apiKey) router.refresh();
      setApiKey(null);
      setError(null);
    }
  }

  async function crear() {
    setCreando(true);
    setError(null);
    const res = await apiRequest<{ api_key: string }>("/api/proxy/empresa/api-keys", { method: "POST" });
    setCreando(false);
    if (res.estado !== "exito" || !res.datos) {
      setError(mensajeError(res.codigo, res.mensaje));
      return;
    }
    setApiKey(res.datos.api_key);
  }

  return (
    <Dialog open={abierto} onOpenChange={cambiarAbierto}>
      <DialogTrigger disabled={soloLectura !== null} title={soloLectura ?? undefined}
        className={
          className ??
          cn(ACCION_PRINCIPAL, "self-start md:self-auto")
        }
      >
        <PlusIcon weight="bold" className="size-4" />
        {t.boton}
      </DialogTrigger>
      <DialogContent className="gap-0 p-0">
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
              <KeyIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">{t.titulo}</DialogTitle>
              <DialogDescription className="text-[13px]">{t.descripcion}</DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="grid gap-4 px-5 py-4">
          {apiKey ? (
            <ApiKeyRevelada apiKey={apiKey} />
          ) : (
            <>
              <p className="text-[13px] leading-relaxed text-muted-foreground">
                {t.explicacion}
              </p>
              {error ? <p className="text-sm text-destructive">{error}</p> : null}
            </>
          )}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-border/60 px-5 py-3">
          {apiKey ? (
            <button type="button" onClick={() => cambiarAbierto(false)} className={BOTON_PRIMARIO}>
              {t.listo}
            </button>
          ) : (
            <>
              <button type="button" onClick={() => cambiarAbierto(false)} className={BOTON_SECUNDARIO}>
                {messages.comun.cancelar}
              </button>
              <button type="button" disabled={creando} onClick={crear} className={BOTON_PRIMARIO}>
                {creando ? t.generando : t.generar}
              </button>
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
