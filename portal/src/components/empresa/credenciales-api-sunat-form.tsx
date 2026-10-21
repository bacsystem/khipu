"use client";

import { EyeIcon, EyeOffIcon, KeyRoundIcon, LockIcon, SaveIcon, Trash2Icon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { type FormEvent, useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import { type CredencialesApiSunat, RUTA_CREDENCIALES_API_SUNAT } from "@/lib/api/empresas";
import { AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { cn } from "@/lib/utils";

/**
 * Credenciales API SUNAT (#318): el «Id» y la «Clave» de la aplicación registrada en SOL («Credenciales de API SUNAT»). Con ellas la validez de un comprobante
 * se consulta por la API REST de SUNAT. Como las SOL: se reemplazan, nunca se muestran; además se pueden quitar.
 */
export function CredencialesApiSunatForm({ configuradas }: { configuradas: boolean }) {
  const router = useRouter();
  const messages = useMessages();
  const t = messages.empresa.credencialesApi;
  const tc = messages.empresa.credenciales;
  const mensajeError = useMensajeError();
  const [clientId, setClientId] = useState("");
  const [clientSecret, setClientSecret] = useState("");
  const [verSecreto, setVerSecreto] = useState(false);
  const [enviando, setEnviando] = useState<"guardar" | "quitar" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState<"actualizadas" | "quitadas" | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setEnviando("guardar");
    setError(null);
    setOk(null);
    const body: CredencialesApiSunat = { client_id: clientId.trim(), client_secret: clientSecret };
    const res = await apiRequest(RUTA_CREDENCIALES_API_SUNAT, { method: "PUT", body });
    setEnviando(null);
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setOk("actualizadas");
    setClientId("");
    setClientSecret("");
    router.refresh();
  }

  async function quitar() {
    setEnviando("quitar");
    setError(null);
    setOk(null);
    const res = await apiRequest(RUTA_CREDENCIALES_API_SUNAT, { method: "DELETE" });
    setEnviando(null);
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setOk("quitadas");
    router.refresh();
  }

  return (
    <form onSubmit={onSubmit} className="grid grid-cols-1 gap-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="api-sunat-client-id" className={ETIQUETA_CAMPO}>
            {t.clientId}
          </label>
          <div className="relative">
            <KeyRoundIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground/70" />
            <input
              id="api-sunat-client-id"
              value={clientId}
              onChange={(e) => setClientId(e.target.value)}
              placeholder={configuradas ? t.guardadoPlaceholder : "d20b421e-4ace-46d8-ac8c-4fbb44cfde17"}
              autoComplete="off"
              spellCheck={false}
              required
              className={cn(CAMPO, "pl-9 font-mono")}
            />
          </div>
          <span className={AYUDA_CAMPO}>{t.rutaEnSol}</span>
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="api-sunat-client-secret" className={ETIQUETA_CAMPO}>
            {t.clientSecret}
          </label>
          <div className="relative">
            <LockIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground/70" />
            <input
              id="api-sunat-client-secret"
              type={verSecreto ? "text" : "password"}
              value={clientSecret}
              onChange={(e) => setClientSecret(e.target.value)}
              placeholder={configuradas ? tc.guardadaPlaceholder : "••••••••"}
              autoComplete="new-password"
              required
              className={cn(CAMPO, "pr-10 pl-9 font-mono")}
            />
            <button
              type="button"
              onClick={() => setVerSecreto((v) => !v)}
              title={verSecreto ? tc.ocultarClave : tc.mostrarClave}
              className="absolute top-1/2 right-2 flex size-7 -translate-y-1/2 items-center justify-center rounded text-muted-foreground transition-colors hover:text-foreground"
            >
              {verSecreto ? <EyeOffIcon className="size-4" /> : <EyeIcon className="size-4" />}
            </button>
          </div>
          <span className={AYUDA_CAMPO}>{tc.cifradaEnReposo}</span>
        </div>
      </div>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      {ok ? <p className="text-sm text-success-foreground">{t[ok]}</p> : null}

      <div className="flex flex-wrap items-center justify-end gap-3 border-t border-border/60 pt-4">
        {configuradas ? (
          <button type="button" onClick={quitar} disabled={enviando !== null} className={BOTON_SECUNDARIO}>
            <Trash2Icon className="size-4" />
            {enviando === "quitar" ? t.quitando : t.quitar}
          </button>
        ) : null}
        <button type="submit" disabled={enviando !== null} className={BOTON_PRIMARIO}>
          <SaveIcon className="size-4" />
          {enviando === "guardar" ? messages.empresa.comun.guardando : configuradas ? tc.reemplazar : tc.guardar}
        </button>
      </div>
    </form>
  );
}
