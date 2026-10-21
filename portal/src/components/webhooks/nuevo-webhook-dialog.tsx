"use client";

import { PlusIcon, WebhookIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { Campo } from "khipu-design-system/formularios/campo";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "khipu-design-system/ui/dialog";
import { apiRequest } from "@/lib/api/browser";
import type { WebhookCreado } from "@/lib/api/webhooks";
import { ACCION_PRINCIPAL, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";
import { errorDeUrlWebhook, EVENTOS_WEBHOOK } from "@/lib/webhooks/eventos";
import { cn } from "@/lib/utils";
import { SecretoWebhookRevelado } from "./secreto-webhook-revelado";

const TODOS = EVENTOS_WEBHOOK.map((e) => e.evento) as string[];

/** Registra un endpoint de webhooks y muestra su secreto de firma una sola vez; al cerrar, refresca la lista del servidor. */
export function NuevoWebhookDialog({ className }: { className?: string }) {
  const soloLectura = useSinPermisoDeConfigurar();
  const router = useRouter();
  const [abierto, setAbierto] = useState(false);
  const [url, setUrl] = useState("");
  const [descripcion, setDescripcion] = useState("");
  const [eventos, setEventos] = useState<string[]>(TODOS);
  const [intento, setIntento] = useState(false);
  const [creando, setCreando] = useState(false);
  const [secreto, setSecreto] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Cada apertura del diálogo es una «sesión»: la respuesta de un registro que llega después de cerrarlo ya no es de este diálogo.
  const sesion = useRef(0);

  const errorUrl = errorDeUrlWebhook(url);
  const sinEventos = eventos.length === 0;

  function cambiarAbierto(valor: boolean) {
    setAbierto(valor);
    sesion.current += 1;
    if (!valor) {
      setCreando(false);
      if (secreto) router.refresh();
      setSecreto(null);
      setError(null);
      setIntento(false);
      setUrl("");
      setDescripcion("");
      setEventos(TODOS);
    }
  }

  function alternar(evento: string) {
    setEventos((actuales) => (actuales.includes(evento) ? actuales.filter((e) => e !== evento) : [...actuales, evento]));
  }

  async function crear() {
    setIntento(true);
    setError(null);
    if (errorUrl || sinEventos) return;
    setCreando(true);
    const mia = sesion.current;
    const res = await apiRequest<WebhookCreado>("/api/proxy/webhooks", {
      method: "POST",
      body: { url: url.trim(), descripcion: descripcion.trim() || undefined, eventos },
    });
    if (mia !== sesion.current) {
      // Se cerró mientras se registraba: el webhook ya existe, así que se refresca la lista; su secreto no se puede mostrar y se rota desde la tarjeta.
      if (res.estado === "exito") router.refresh();
      return;
    }
    setCreando(false);
    if (res.estado !== "exito" || !res.datos) {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setSecreto(res.datos.secreto);
  }

  return (
    <Dialog open={abierto} onOpenChange={cambiarAbierto}>
      <DialogTrigger disabled={soloLectura !== null} title={soloLectura ?? undefined} className={className ?? cn(ACCION_PRINCIPAL, "self-start md:self-auto")}>
        <PlusIcon className="size-4" />
        Nuevo webhook
      </DialogTrigger>
      <DialogContent className="gap-0 p-0">
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
              <WebhookIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">Nuevo webhook</DialogTitle>
              <DialogDescription className="text-[13px]">khipu avisa a esta URL cada vez que un comprobante cambia de estado</DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="grid gap-4 px-5 py-4">
          {secreto ? (
            <SecretoWebhookRevelado secreto={secreto} />
          ) : (
            <>
              <Campo id="webhook-url" etiqueta="URL del endpoint" ayuda="https://… · un servidor público; cada entrega llega firmada" error={intento ? errorUrl : null}>
                <input id="webhook-url" type="url" inputMode="url" autoComplete="off" value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://mi-sistema.pe/khipu/webhook" className={cn(CAMPO, "font-mono text-[13px]")} />
              </Campo>
              <Campo id="webhook-descripcion" etiqueta="Descripción" opcional>
                <input id="webhook-descripcion" maxLength={100} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} placeholder="ERP producción" className={CAMPO} />
              </Campo>
              <fieldset className="grid gap-1.5">
                <legend className="mb-1 text-[12px] font-medium text-foreground">Eventos</legend>
                {EVENTOS_WEBHOOK.map((e) => (
                  <label key={e.evento} className="flex cursor-pointer items-start gap-2 rounded-md px-1 py-1 text-[13px] hover:bg-muted/60">
                    <input type="checkbox" checked={eventos.includes(e.evento)} onChange={() => alternar(e.evento)} className="mt-0.5 size-3.5 accent-primary" />
                    <span>
                      <span className="font-medium text-foreground">{e.etiqueta}</span>
                      <span className="text-muted-foreground"> · {e.detalle}</span>
                    </span>
                  </label>
                ))}
                {intento && sinEventos ? (
                  <p role="alert" className="text-[12px] text-destructive">
                    Elige al menos un evento
                  </p>
                ) : null}
              </fieldset>
              {error ? (
                <p role="alert" className="text-sm text-destructive">
                  {error}
                </p>
              ) : null}
            </>
          )}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-border/60 px-5 py-3">
          {secreto ? (
            <button type="button" onClick={() => cambiarAbierto(false)} className={BOTON_PRIMARIO}>
              Listo, lo guardé
            </button>
          ) : (
            <>
              <button type="button" onClick={() => cambiarAbierto(false)} className={BOTON_SECUNDARIO}>
                Cancelar
              </button>
              <button type="button" disabled={creando} onClick={crear} className={BOTON_PRIMARIO}>
                {creando ? "Registrando…" : "Registrar webhook"}
              </button>
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
