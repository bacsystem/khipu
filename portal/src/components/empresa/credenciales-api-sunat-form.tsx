"use client";

import { FloppyDiskIcon, KeyIcon, TrashIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { type FormEvent, useState } from "react";
import { Contrasena } from "khipu-design-system/formularios/contrasena";
import { apiRequest } from "@/lib/api/browser";
import { type CredencialesApiSunat, RUTA_CREDENCIALES_API_SUNAT } from "@/lib/api/empresas";
import { Campo, Entrada } from "khipu-design-system/formularios/campo";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { BOTON_PRIMARIO, BOTON_SECUNDARIO } from "@/lib/estilos";
import { useMensajeError } from "@/i18n/use-mensaje-error";

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
      <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-2">
        <Campo id="api-sunat-client-id" etiqueta={t.clientId} ayuda={t.rutaEnSol}>
          <div className="relative">
            <KeyIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground/70" />
            <Entrada
              id="api-sunat-client-id"
              value={clientId}
              onChange={(e) => setClientId(e.target.value)}
              placeholder={configuradas ? t.guardadoPlaceholder : "d20b421e-4ace-46d8-ac8c-4fbb44cfde17"}
              autoComplete="off"
              spellCheck={false}
              required
              mono
              aria-describedby="api-sunat-client-id-ayuda"
              className="pl-9"
            />
          </div>
        </Campo>

        <Campo id="api-sunat-client-secret" etiqueta={t.clientSecret} ayuda={tc.cifradaEnReposo}>
          <Contrasena id="api-sunat-client-secret" valor={clientSecret} onCambio={setClientSecret} placeholder={configuradas ? tc.guardadaPlaceholder : "••••••••"} autoComplete="new-password" etiquetaMostrar={tc.mostrarClave} etiquetaOcultar={tc.ocultarClave} className="font-mono" />
        </Campo>
      </div>

      {error ? <p className="text-cuerpo text-destructive">{error}</p> : null}
      {ok ? <p className="text-cuerpo text-success-foreground">{t[ok]}</p> : null}

      <div className="flex flex-wrap items-center justify-end gap-3 border-t border-border/60 pt-4">
        {configuradas ? (
          <BotonAsync onClick={quitar} pendiente={enviando === "quitar"} disabled={enviando !== null} icon={<TrashIcon className="size-4" />} textoPendiente={t.quitando} className={BOTON_SECUNDARIO}>
            {t.quitar}
          </BotonAsync>
        ) : null}
        {/* La clave es la `Contrasena` del design system, que no lleva `required`: sin clave no se ofrece guardar, como antes no dejaba el navegador. */}
        <BotonAsync type="submit" pendiente={enviando === "guardar"} disabled={enviando !== null || clientSecret === ""} icon={<FloppyDiskIcon className="size-4" />} textoPendiente={messages.empresa.comun.guardando} className={BOTON_PRIMARIO}>
          {configuradas ? tc.reemplazar : tc.guardar}
        </BotonAsync>
      </div>
    </form>
  );
}
