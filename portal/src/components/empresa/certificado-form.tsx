"use client";

import { EyeIcon, EyeOffIcon, FileKey2Icon, KeyRoundIcon, LockIcon, RefreshCcwIcon, UploadCloudIcon, XIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { type DragEvent, type FormEvent, useId, useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import { AYUDA_CAMPO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

const EXTENSIONES = [".p12", ".pfx"];

function esCertificado(f: File) {
  const nombre = f.name.toLowerCase();
  return EXTENSIONES.some((ext) => nombre.endsWith(ext));
}

function tamano(bytes: number) {
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`;
}

export function CertificadoForm({ tieneCertificado }: { tieneCertificado: boolean }) {
  const router = useRouter();
  const inputId = useId();
  const [archivo, setArchivo] = useState<File | null>(null);
  const [clave, setClave] = useState("");
  const [verClave, setVerClave] = useState(false);
  const [arrastrando, setArrastrando] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState(false);

  function elegir(f: File | null | undefined) {
    setError(null);
    setOk(false);
    if (!f) return;
    if (!esCertificado(f)) {
      setError("El archivo debe ser un certificado .p12 o .pfx");
      return;
    }
    setArchivo(f);
  }

  function onDrop(e: DragEvent) {
    e.preventDefault();
    setArrastrando(false);
    elegir(e.dataTransfer.files?.[0]);
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!archivo) {
      setError("Selecciona el archivo .p12 de tu certificado");
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
      setError(mensajeError(res.codigo));
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
        <span className={ETIQUETA_CAMPO}>{tieneCertificado ? "Reemplazar o renovar certificado" : "Cargar certificado"}</span>
        {archivo ? (
          <div className="flex items-center gap-3 rounded-lg border border-border bg-muted px-3 py-2.5">
            <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
              <FileKey2Icon className="size-4" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate font-mono text-[13px] font-medium text-foreground">{archivo.name}</p>
              <p className={AYUDA_CAMPO}>{tamano(archivo.size)} · listo para subir</p>
            </div>
            <button
              type="button"
              onClick={() => setArchivo(null)}
              title="Quitar archivo"
              className="flex size-7 shrink-0 items-center justify-center rounded text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
            >
              <XIcon className="size-4" />
            </button>
          </div>
        ) : (
          <label
            htmlFor={inputId}
            onDragOver={(e) => {
              e.preventDefault();
              setArrastrando(true);
            }}
            onDragLeave={() => setArrastrando(false)}
            onDrop={onDrop}
            className={cn(
              "flex cursor-pointer flex-col items-center justify-center gap-1.5 rounded-lg border border-dashed px-4 py-7 text-center transition-colors",
              arrastrando ? "border-primary bg-accent/60" : "border-border bg-muted hover:border-primary/50 hover:bg-accent/40",
            )}
          >
            <UploadCloudIcon className="size-6 text-primary" />
            <span className="text-[13px] font-medium text-foreground">Arrastra tu archivo .p12 o .pfx aquí</span>
            <span className={AYUDA_CAMPO}>o haz clic para examinar desde tu equipo</span>
          </label>
        )}
        <input
          id={inputId}
          type="file"
          accept={EXTENSIONES.join(",")}
          className="sr-only"
          onChange={(e) => {
            elegir(e.target.files?.[0]);
            e.target.value = "";
          }}
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="clave-cert" className={ETIQUETA_CAMPO}>
          Contraseña de la clave privada
        </label>
        <div className="relative">
          <KeyRoundIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground/70" />
          <input
            id="clave-cert"
            type={verClave ? "text" : "password"}
            value={clave}
            onChange={(e) => setClave(e.target.value)}
            autoComplete="off"
            required
            className={cn(CAMPO, "pr-10 pl-9 font-mono")}
          />
          <button
            type="button"
            onClick={() => setVerClave((v) => !v)}
            title={verClave ? "Ocultar contraseña" : "Mostrar contraseña"}
            className="absolute top-1/2 right-2 flex size-7 -translate-y-1/2 items-center justify-center rounded text-muted-foreground transition-colors hover:text-foreground"
          >
            {verClave ? <EyeOffIcon className="size-4" /> : <EyeIcon className="size-4" />}
          </button>
        </div>
        <span className={AYUDA_CAMPO}>El RUC de la empresa debe figurar en el campo OU del certificado</span>
      </div>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      {ok ? <p className="text-sm text-success-foreground">Certificado actualizado.</p> : null}

      <button
        type="submit"
        disabled={enviando || !archivo}
        className="flex h-9 w-full items-center justify-center gap-1.5 rounded-lg bg-foreground text-[12px] font-medium text-background shadow-2xs transition-all hover:bg-foreground/90 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-60"
      >
        <RefreshCcwIcon className="size-4" />
        {enviando ? "Subiendo…" : tieneCertificado ? "Actualizar certificado digital" : "Cargar certificado digital"}
      </button>

      <div className={cn(AYUDA_CAMPO, "flex items-center gap-1.5 border-t border-border/60 pt-3")}>
        <LockIcon className="size-3.5 shrink-0" />
        PKCS#12 cifrado en reposo con la clave maestra del servicio; la contraseña no se almacena en claro.
      </div>
    </form>
  );
}
