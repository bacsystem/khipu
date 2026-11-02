"use client";

import { FloppyDiskIcon, UserIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { type FormEvent, useState } from "react";
import { Contrasena } from "khipu-design-system/formularios/contrasena";
import { apiRequest } from "@/lib/api/browser";
import { Campo, Entrada } from "khipu-design-system/formularios/campo";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { BOTON_PRIMARIO } from "@/lib/estilos";
import { useMensajeError } from "@/i18n/use-mensaje-error";

export function CredencialesSolForm({ configuradas }: { configuradas: boolean }) {
  const router = useRouter();
  const messages = useMessages();
  const t = messages.empresa.credencialesSol;
  const tc = messages.empresa.credenciales;
  const mensajeError = useMensajeError();
  const [usuario, setUsuario] = useState("");
  const [clave, setClave] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setEnviando(true);
    setError(null);
    setOk(false);
    const res = await apiRequest("/api/proxy/empresa/credenciales-sol", {
      method: "PUT",
      body: { usuario, clave },
    });
    setEnviando(false);
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setOk(true);
    setUsuario("");
    setClave("");
    router.refresh();
  }

  return (
    <form onSubmit={onSubmit} className="grid grid-cols-1 gap-4">
      <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-2">
        <Campo id="usuario-sol" etiqueta={t.usuario} ayuda={t.usuarioAyuda}>
          <div className="relative">
            <UserIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground/70" />
            <Entrada
              id="usuario-sol"
              value={usuario}
              onChange={(e) => setUsuario(e.target.value.toUpperCase())}
              placeholder="MODDATOS"
              autoComplete="off"
              required
              mono
              aria-describedby="usuario-sol-ayuda"
              className="pl-9"
            />
          </div>
        </Campo>

        <Campo id="clave-sol" etiqueta={t.clave} ayuda={tc.cifradaEnReposo}>
          <Contrasena id="clave-sol" valor={clave} onCambio={setClave} placeholder={configuradas ? tc.guardadaPlaceholder : "••••••••"} autoComplete="new-password" etiquetaMostrar={tc.mostrarClave} etiquetaOcultar={tc.ocultarClave} className="font-mono" />
        </Campo>
      </div>

      {error ? <p className="text-cuerpo text-destructive">{error}</p> : null}
      {ok ? <p className="text-cuerpo text-success-foreground">{t.actualizadas}</p> : null}

      {/* Sin «Verificar credenciales ante SUNAT» ni «Última validación: —» (C6): se veían y no hacían nada. */}
      <div className="flex flex-wrap items-center justify-end gap-3 border-t border-border/60 pt-4">
        {/* La clave es la `Contrasena` del design system, que no lleva `required`: sin clave no se ofrece guardar, como antes no dejaba el navegador. */}
        <BotonAsync type="submit" pendiente={enviando} disabled={clave === ""} icon={<FloppyDiskIcon className="size-4" />} textoPendiente={messages.empresa.comun.guardando} className={BOTON_PRIMARIO}>
          {configuradas ? tc.reemplazar : tc.guardar}
        </BotonAsync>
      </div>
    </form>
  );
}
