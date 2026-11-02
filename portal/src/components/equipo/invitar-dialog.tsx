"use client";

import { EnvelopeSimpleIcon, PaperPlaneTiltIcon, UserPlusIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { type FormEvent, useRef, useState } from "react";
import { Dialog, DialogContent, DialogTrigger } from "khipu-design-system/ui/dialog";
import { apiRequest } from "@/lib/api/browser";
import { ROLES, type InvitacionEnviada, type Rol } from "@/lib/api/equipo";
import { AYUDA_CAMPO, ACCION_PRINCIPAL, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO, SELECT_NATIVO } from "@/lib/estilos";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";
import { cn } from "@/lib/utils";
import { CabeceraDialogo } from "khipu-design-system/patrones/cabecera-dialogo";

/** #21: invitar a alguien a la cuenta con un rol. Solo un administrador (el backend lo vuelve a exigir). */
export function InvitarDialog({ className }: { className?: string }) {
  const sinPermiso = useSinPermisoDeConfigurar();
  const messages = useMessages();
  const t = messages.equipo.invitar;
  const roles = messages.roles;
  const mensajeError = useMensajeError();
  const router = useRouter();
  const [abierto, setAbierto] = useState(false);
  const [email, setEmail] = useState("");
  const [rol, setRol] = useState<Rol>("EMISOR");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  // Dos clics en el mismo tick pasarían los dos por onSubmit: el ref corta en el acto.
  const enviandoRef = useRef(false);

  function cambiarAbierto(v: boolean) {
    setAbierto(v);
    if (!v) {
      setEmail("");
      setRol("EMISOR");
      setError(null);
      setAviso(null);
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (enviandoRef.current) return;
    enviandoRef.current = true;
    setEnviando(true);
    setError(null);
    const res = await apiRequest<InvitacionEnviada>("/api/proxy/cuenta/invitaciones", { method: "POST", body: { email: email.trim(), rol } });
    enviandoRef.current = false;
    setEnviando(false);
    if (res.estado !== "exito" || !res.datos) {
      setError(res.codigo === "VALIDACION" ? messages.errores.EMAIL_INVALIDO : mensajeError(res.codigo, res.mensaje));
      return;
    }
    router.refresh();
    if (!res.datos.correo_enviado) {
      // Queda guardada: se avisa sin cerrar, para que no crea que le llegó.
      setAviso(t.noSalio);
      return;
    }
    cambiarAbierto(false);
  }

  return (
    <Dialog open={abierto} onOpenChange={cambiarAbierto}>
      <DialogTrigger disabled={sinPermiso !== null} title={sinPermiso ?? undefined} className={className ?? ACCION_PRINCIPAL}>
        <UserPlusIcon className="size-4" />
        {t.boton}
      </DialogTrigger>
      <DialogContent className="gap-0 p-0">
        <CabeceraDialogo icon={EnvelopeSimpleIcon} titulo={t.titulo} descripcion={t.subtitulo} />
        <form onSubmit={onSubmit} className="flex flex-col gap-4 px-5 py-4" noValidate>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="invitar-email" className={ETIQUETA_CAMPO}>
              {t.email}
            </label>
            <input
              id="invitar-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="contador@estudio.pe"
              required
              autoFocus
              maxLength={254}
              className={CAMPO}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="invitar-rol" className={ETIQUETA_CAMPO}>
              {t.rol}
            </label>
            <select id="invitar-rol" value={rol} onChange={(e) => setRol(e.target.value as Rol)} className={cn(SELECT_NATIVO, "w-full")}>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {roles.etiquetas[r]}
                </option>
              ))}
            </select>
            <span className={AYUDA_CAMPO}>{roles.descripciones[rol]}</span>
          </div>

          {error ? <p role="alert" className="text-cuerpo text-destructive">{error}</p> : null}
          {aviso ? <p role="status" className="text-cuerpo text-warning-foreground">{aviso}</p> : null}

          <div className="mt-1 flex items-center justify-end gap-2 border-t border-border/60 pt-4">
            <button type="button" onClick={() => cambiarAbierto(false)} className={BOTON_SECUNDARIO}>
              {messages.comun.cancelar}
            </button>
            <button type="submit" disabled={enviando || email.trim() === "" || aviso !== null} className={BOTON_PRIMARIO}>
              <PaperPlaneTiltIcon className="size-4" />
              {enviando ? t.enviando : t.enviar}
            </button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
