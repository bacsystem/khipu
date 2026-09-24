"use client";

import { BadgeCheckIcon, EyeIcon, EyeOffIcon, LockIcon, SaveIcon, UserIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import { AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

export function CredencialesSolForm({ configuradas }: { configuradas: boolean }) {
  const router = useRouter();
  const [usuario, setUsuario] = useState("");
  const [clave, setClave] = useState("");
  const [verClave, setVerClave] = useState(false);
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
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="usuario-sol" className={ETIQUETA_CAMPO}>
            Usuario SOL secundario
          </label>
          <div className="relative">
            <UserIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground/70" />
            <input
              id="usuario-sol"
              value={usuario}
              onChange={(e) => setUsuario(e.target.value.toUpperCase())}
              placeholder="MODDATOS"
              autoComplete="off"
              required
              className={cn(CAMPO, "pl-9 font-mono uppercase")}
            />
          </div>
          <span className={AYUDA_CAMPO}>Solo el usuario; el RUC lo antepone el sistema</span>
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="clave-sol" className={ETIQUETA_CAMPO}>
            Clave SOL
          </label>
          <div className="relative">
            <LockIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground/70" />
            <input
              id="clave-sol"
              type={verClave ? "text" : "password"}
              value={clave}
              onChange={(e) => setClave(e.target.value)}
              placeholder={configuradas ? "••••••••  (guardada, no se muestra)" : "••••••••"}
              autoComplete="new-password"
              required
              className={cn(CAMPO, "pr-10 pl-9 font-mono")}
            />
            <button
              type="button"
              onClick={() => setVerClave((v) => !v)}
              title={verClave ? "Ocultar clave" : "Mostrar clave"}
              className="absolute top-1/2 right-2 flex size-7 -translate-y-1/2 items-center justify-center rounded text-muted-foreground transition-colors hover:text-foreground"
            >
              {verClave ? <EyeOffIcon className="size-4" /> : <EyeIcon className="size-4" />}
            </button>
          </div>
          <span className={AYUDA_CAMPO}>Cifrada en reposo con la clave maestra del servicio</span>
        </div>
      </div>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      {ok ? <p className="text-sm text-success-foreground">Credenciales SOL actualizadas.</p> : null}

      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border/60 pt-4">
        <div className="flex flex-wrap items-center gap-3">
          <button type="button" disabled title="Validación de credenciales contra SUNAT: próximamente" className={cn(BOTON_SECUNDARIO, "h-9 text-[12px]")}>
            <BadgeCheckIcon className="size-4" />
            Verificar credenciales ante SUNAT
          </button>
          <span className={cn(AYUDA_CAMPO, "opacity-60")} title="Requiere la validación contra SUNAT">
            Última validación: —
          </span>
        </div>
        <button type="submit" disabled={enviando} className={cn(BOTON_PRIMARIO, "h-9 text-[12px]")}>
          <SaveIcon className="size-4" />
          {enviando ? "Guardando…" : configuradas ? "Reemplazar credenciales" : "Guardar credenciales"}
        </button>
      </div>
    </form>
  );
}
