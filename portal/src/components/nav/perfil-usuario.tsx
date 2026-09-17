"use client";

import { BookOpenIcon, ChevronsUpDownIcon, KeyRoundIcon, LogOutIcon, MailCheckIcon, ShieldCheckIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Menu, MenuContent, MenuItem, MenuLinkItem, MenuSeparator, MenuTrigger } from "@/components/ui/menu";
import { ThemeToggle } from "@/components/nav/theme-toggle";
import type { Usuario } from "@/lib/api/auth";
import { postJson } from "@/lib/api/browser";
import { BOTON_PRIMARIO, BOTON_SECUNDARIO } from "@/lib/estilos";
import { mensajeError, messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

const ETIQUETA_ROL: Record<string, string> = { ADMIN: "Administrador", EMISOR: "Emisor", LECTURA: "Solo lectura" };

function iniciales(email: string): string {
  const local = email.split("@")[0] ?? email;
  return local.slice(0, 2).toUpperCase();
}

/** Pide al backend el enlace de restablecimiento para el correo de la sesión: es el mismo flujo público de "recuperar", sin salir del panel. */
function CambiarContrasenaDialog({ email, abierto, onOpenChange }: { email: string; abierto: boolean; onOpenChange: (v: boolean) => void }) {
  const [estado, setEstado] = useState<"idle" | "enviando" | "enviado">("idle");
  const [error, setError] = useState<string | null>(null);

  function cerrar() {
    onOpenChange(false);
    setEstado("idle");
    setError(null);
  }

  async function enviar() {
    setEstado("enviando");
    setError(null);
    const res = await postJson<null>("/api/auth/recuperar", { email });
    if (res.estado !== "exito") {
      setEstado("idle");
      setError(mensajeError(res.codigo));
      return;
    }
    setEstado("enviado");
  }

  return (
    <Dialog open={abierto} onOpenChange={(v) => (v ? onOpenChange(v) : cerrar())}>
      <DialogContent className="gap-0 p-0">
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
              <KeyRoundIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">Cambiar contraseña</DialogTitle>
              <DialogDescription className="text-[13px]">Te enviamos un enlace por correo para definir una nueva</DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="grid gap-3 px-5 py-4">
          {estado === "enviado" ? (
            <div className="flex items-start gap-2.5 rounded-lg border border-success-border bg-success px-3 py-2.5 text-[13px] leading-relaxed text-success-foreground">
              <MailCheckIcon className="mt-0.5 size-4 shrink-0" />
              <p>
                Enlace enviado a <strong className="font-semibold">{email}</strong>. Ábrelo desde tu correo para elegir la nueva contraseña; tu sesión actual
                sigue activa mientras tanto.
              </p>
            </div>
          ) : (
            <>
              <p className="text-[13px] leading-relaxed text-muted-foreground">
                Enviaremos un enlace de un solo uso a <strong className="font-medium text-foreground">{email}</strong>. Al abrirlo podrás escribir la nueva
                contraseña.
              </p>
              {error ? <p className="text-sm text-destructive">{error}</p> : null}
            </>
          )}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-border/60 px-5 py-3">
          {estado === "enviado" ? (
            <button type="button" onClick={cerrar} className={cn(BOTON_PRIMARIO, "h-9 px-3.5 text-[13px]")}>
              Entendido
            </button>
          ) : (
            <>
              <button type="button" onClick={cerrar} className={cn(BOTON_SECUNDARIO, "h-9 px-3.5 text-[13px]")}>
                {messages.comun.cancelar}
              </button>
              <button type="button" disabled={estado === "enviando"} onClick={enviar} className={cn(BOTON_PRIMARIO, "h-9 px-3.5 text-[13px]")}>
                {estado === "enviando" ? messages.auth.recuperar.enviando : messages.auth.recuperar.enviar}
              </button>
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

/** Bloque de usuario del sidebar: abre un menú con la cuenta, el tema, el cambio de contraseña y el cierre de sesión. */
export function PerfilUsuario({ usuario }: { usuario: Usuario }) {
  const router = useRouter();
  const [cambiandoContrasena, setCambiandoContrasena] = useState(false);
  const [saliendo, setSaliendo] = useState(false);

  const nombre = usuario.email.split("@")[0] ?? usuario.email;
  const rol = ETIQUETA_ROL[usuario.rol] ?? usuario.rol;

  async function salir() {
    setSaliendo(true);
    await fetch("/api/auth/logout", { method: "POST" });
    router.push("/login");
    router.refresh();
  }

  return (
    <>
      <Menu>
        <MenuTrigger
          aria-label="Menú de usuario"
          className={cn(
            "flex w-full min-w-0 cursor-pointer items-center justify-between gap-2 overflow-hidden rounded-lg border border-border/60 bg-muted/80 p-2 text-left transition-colors outline-none select-none",
            "hover:bg-secondary focus-visible:ring-2 focus-visible:ring-ring/50 data-popup-open:bg-secondary",
          )}
        >
          <div className="flex min-w-0 items-center gap-2.5">
            <div className="flex size-6 shrink-0 items-center justify-center rounded-md bg-primary text-[11px] font-bold text-primary-foreground shadow-xs">
              {iniciales(usuario.email)}
            </div>
            <div className="min-w-0 overflow-hidden">
              <p className="truncate text-[12px] leading-tight font-medium text-foreground">{nombre}</p>
              <p className="truncate font-mono text-[10px] leading-tight text-muted-foreground">{usuario.email}</p>
            </div>
          </div>
          <ChevronsUpDownIcon className="size-4 shrink-0 text-muted-foreground/70" />
        </MenuTrigger>

        {/* Mismo ancho y alineación que el disparador, como el selector de empresas. */}
        <MenuContent side="top" align="start" className="w-(--anchor-width) min-w-0">
          <div className="px-2 pt-1.5 pb-1 text-[10px] font-medium tracking-wider text-muted-foreground uppercase">Tu cuenta</div>
          {/* Datos completos, sin truncar: el correo se parte en dos líneas si hace falta. */}
          <div className="flex items-start gap-2.5 rounded-md bg-accent/70 py-1.5 pr-2 pl-2 text-accent-foreground">
            <div className="flex size-6 shrink-0 items-center justify-center rounded-md bg-primary text-[11px] font-bold text-primary-foreground shadow-xs">
              {iniciales(usuario.email)}
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-[12px] leading-tight font-medium">{nombre}</p>
              <p className="mt-0.5 font-mono text-[10px] leading-snug break-all text-muted-foreground">{usuario.email}</p>
              <p className="mt-0.5 text-[10px] leading-tight text-muted-foreground">{rol}</p>
            </div>
            <ShieldCheckIcon className="mt-0.5 size-4 shrink-0 text-primary" />
          </div>

          <MenuSeparator />

          {/* Tema en línea (el mismo control de la barra superior, con etiquetas), no como ítems del menú. */}
          <div className="px-2 pt-1.5 pb-1 text-[10px] font-medium tracking-wider text-muted-foreground uppercase">Tema</div>
          <div className="px-0.5 pb-1.5">
            <ThemeToggle conTexto className="h-8" />
          </div>

          <MenuSeparator />

          <MenuItem onClick={() => setCambiandoContrasena(true)}>
            <KeyRoundIcon />
            Cambiar contraseña
          </MenuItem>
          <MenuLinkItem href="/developers" target="_blank">
            <BookOpenIcon />
            Documentación API
          </MenuLinkItem>

          <MenuSeparator />

          <MenuItem variant="destructive" disabled={saliendo} onClick={salir}>
            <LogOutIcon />
            {messages.comun.cerrarSesion}
          </MenuItem>
        </MenuContent>
      </Menu>

      <CambiarContrasenaDialog email={usuario.email} abierto={cambiandoContrasena} onOpenChange={setCambiandoContrasena} />
    </>
  );
}
