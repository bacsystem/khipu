"use client";

import { ArrowsClockwiseIcon, LockIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { type FormEvent, useState } from "react";
import { Contrasena } from "khipu-design-system/formularios/contrasena";
import { ZonaArchivos } from "khipu-design-system/formularios/zona-archivos";
import { apiRequest } from "@/lib/api/browser";
import { AYUDA_CAMPO, BOTON_PRIMARIO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { cn } from "@/lib/utils";

const EXTENSIONES = [".p12", ".pfx"];

function esCertificado(f: File) {
  const nombre = f.name.toLowerCase();
  return EXTENSIONES.some((ext) => nombre.endsWith(ext));
}

export function CertificadoForm({ tieneCertificado }: { tieneCertificado: boolean }) {
  const router = useRouter();
  const messages = useMessages();
  const t = messages.empresa.certificado;
  const mensajeError = useMensajeError();
  const [archivo, setArchivo] = useState<File | null>(null);
  const [clave, setClave] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState(false);

  function elegir(f: File | null | undefined) {
    setError(null);
    setOk(false);
    if (!f) return;
    if (!esCertificado(f)) {
      setError(t.extensionInvalida);
      return;
    }
    setArchivo(f);
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!archivo) {
      setError(t.seleccionaArchivo);
      return;
    }
    setEnviando(true);
    setError(null);
    setOk(false);
    const form = new FormData();
    form.set("archivo", archivo);
    form.set("clave", clave);
    const res = await apiRequest("/api/proxy/empresa/certificado", {
      method: "POST",
      body: form,
    });
    setEnviando(false);
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setOk(true);
    setClave("");
    setArchivo(null);
    router.refresh();
  }

  return (
    <form onSubmit={onSubmit} className="grid grid-cols-1 gap-4">
      <div className="flex flex-col gap-1.5">
        <span className={ETIQUETA_CAMPO}>{tieneCertificado ? t.reemplazar : t.cargar}</span>
        {/* La zona del design system: arrastrar o elegir, y con el archivo elegido muestra su nombre y su tamaño, con una ✕ para quitarlo. Un archivo que no es .p12/.pfx
            no se toma: `elegir` lo rechaza con su error y la zona sigue vacía. */}
        <ZonaArchivos id="certificado-archivo" archivo={archivo} onCambio={(f) => (f ? elegir(f) : setArchivo(null))} accept={EXTENSIONES.join(",")} titulo={t.arrastra} ayuda={t.examinar} etiquetaQuitar={t.quitarArchivo} />
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="clave-cert" className={ETIQUETA_CAMPO}>
          {t.contrasena}
        </label>
        <Contrasena id="clave-cert" valor={clave} onCambio={setClave} autoComplete="off" etiquetaMostrar={t.mostrarContrasena} etiquetaOcultar={t.ocultarContrasena} describidoPor="clave-cert-ayuda" className="font-mono" />
        <span id="clave-cert-ayuda" className={AYUDA_CAMPO}>{t.ayudaOu}</span>
      </div>

      {error ? <p className="text-cuerpo text-destructive">{error}</p> : null}
      {ok ? <p className="text-cuerpo text-success-foreground">{t.actualizado}</p> : null}

      {/* La clave es la `Contrasena` del design system, que no lleva `required`: sin clave no se ofrece subir, como antes no dejaba el navegador. */}
      <button
        type="submit"
        disabled={enviando || !archivo || clave === ""}
        className={cn(BOTON_PRIMARIO, "w-full")}
      >
        <ArrowsClockwiseIcon className="size-4" />
        {enviando ? messages.empresa.comun.subiendo : tieneCertificado ? t.actualizarDigital : t.cargarDigital}
      </button>

      <div className={cn(AYUDA_CAMPO, "flex items-center gap-1.5 border-t border-border/60 pt-3")}>
        <LockIcon className="size-3.5 shrink-0" />
        {t.cifrado}
      </div>
    </form>
  );
}
