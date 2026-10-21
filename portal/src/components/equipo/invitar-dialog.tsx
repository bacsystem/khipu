"use client";

import { ChevronDownIcon, MailPlusIcon, SendIcon, UserPlusIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { type FormEvent, useRef, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "khipu-design-system/ui/dialog";
import { apiRequest } from "@/lib/api/browser";
import { ROLES, type InvitacionEnviada, type Rol } from "@/lib/api/equipo";
import { AYUDA_CAMPO, ACCION_PRINCIPAL, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { mensajeError, messages } from "@/lib/messages";
import { useSinPermisoDeConfigurar } from "@/lib/solo-lectura";
import { cn } from "@/lib/utils";

const t = messages.equipo.invitar;
const roles = messages.roles;

/** #21: invitar a alguien a la cuenta con un rol. Solo un administrador (el backend lo vuelve a exigir). */
export function InvitarDialog({ className }: { className?: string }) {
  const sinPermiso = useSinPermisoDeConfigurar();
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
      setError(res.codigo === "VALIDACION" ? messages.errores.EMAIL_INVALIDO : mensajeError(res.codigo));
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
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
              <MailPlusIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">{t.titulo}</DialogTitle>
              <DialogDescription className="text-[13px]">{t.subtitulo}</DialogDescription>
            </div>
          </div>
        </DialogHeader>
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
            <div className="relative">
              <select id="invitar-rol" value={rol} onChange={(e) => setRol(e.target.value as Rol)} className={cn(CAMPO, "appearance-none pr-8")}>
                {ROLES.map((r) => (
                  <option key={r} value={r}>
                    {roles.etiquetas[r]}
                  </option>
                ))}
              </select>
              <ChevronDownIcon className="pointer-events-none absolute top-2.5 right-2.5 size-4 text-muted-foreground" />
            </div>
            <span className={AYUDA_CAMPO}>{roles.descripciones[rol]}</span>
          </div>

          {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : null}
          {aviso ? <p role="status" className="text-sm text-warning-foreground">{aviso}</p> : null}

          <div className="mt-1 flex items-center justify-end gap-2 border-t border-border/60 pt-4">
            <button type="button" onClick={() => cambiarAbierto(false)} className={BOTON_SECUNDARIO}>
              {messages.comun.cancelar}
            </button>
            <button type="submit" disabled={enviando || email.trim() === "" || aviso !== null} className={BOTON_PRIMARIO}>
              <SendIcon className="size-4" />
              {enviando ? t.enviando : t.enviar}
            </button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
