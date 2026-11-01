"use client";

import { BookOpenIcon, CaretRightIcon, CaretUpDownIcon, EnvelopeSimpleOpenIcon, KeyIcon, PaletteIcon, ShieldCheckIcon, SignOutIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useMessages } from "next-intl";
import { Dialog, DialogContent } from "khipu-design-system/ui/dialog";
import { Menu, MenuContent, MenuItem, MenuLinkItem, MenuSeparator, MenuTrigger } from "khipu-design-system/ui/menu";
import { PanelApariencia } from "khipu-design-system/nav/panel-apariencia";
import { ThemeToggle } from "khipu-design-system/nav/theme-toggle";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import type { Usuario } from "@/lib/api/auth";
import { postJson } from "@/lib/api/browser";
import { BOTON_PRIMARIO, BOTON_SECUNDARIO, ROTULO } from "@/lib/estilos";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { cn } from "@/lib/utils";
import { useSoloLecturaDeLaSesion } from "@/lib/solo-lectura";
import { CabeceraDialogo, PieDialogo } from "khipu-design-system/patrones/cabecera-dialogo";

/** Las dos primeras letras del nombre de la cuenta («FT» de «Ferretería Torres»), o del correo si no se pudo leer la cuenta. */
function iniciales(nombre: string): string {
  const palabras = nombre.split(/[\s@._-]+/).filter(Boolean);
  const letras = palabras.length > 1 ? palabras[0][0] + palabras[1][0] : (palabras[0] ?? nombre).slice(0, 2);
  return letras.toUpperCase();
}

/** Pide al backend el enlace de restablecimiento para el correo de la sesión: es el mismo flujo público de "recuperar", sin salir del panel. */
export function CambiarContrasenaDialog({ email, abierto, onOpenChange }: { email: string; abierto: boolean; onOpenChange: (v: boolean) => void }) {
  const messages = useMessages();
  const t = messages.nav.perfil.contrasena;
  const mensajeError = useMensajeError();
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
        <CabeceraDialogo icon={KeyIcon} titulo={t.titulo} descripcion={t.descripcion} />

        <div className="grid gap-3 px-5 py-4">
          {estado === "enviado" ? (
            <div role="status" className="flex items-start gap-2.5 rounded-lg border border-success-border bg-success px-3 py-2.5 text-control leading-relaxed text-success-foreground">
              <EnvelopeSimpleOpenIcon weight="duotone" className="mt-0.5 size-4 shrink-0" />
              <p>
                {t.enviadoA} <strong className="font-semibold">{email}</strong>
                {t.enviadoDetalle}
              </p>
            </div>
          ) : (
            <>
              <p className="text-control leading-relaxed text-muted-foreground">
                {t.enviaremosA} <strong className="font-medium text-foreground">{email}</strong>
                {t.enviaremosDetalle}
              </p>
              {/* #105: sin `role="alert"` el fallo era mudo: un lector de pantalla no lo anunciaba y el e2e solo veía que «Enlace enviado» no llegaba, sin el motivo. */}
              {error ? (
                <p role="alert" className="text-control text-destructive">
                  {error}
                </p>
              ) : null}
            </>
          )}
        </div>

        <PieDialogo>
          {estado === "enviado" ? (
            <button type="button" onClick={cerrar} className={BOTON_PRIMARIO}>
              {t.entendido}
            </button>
          ) : (
            <>
              <button type="button" onClick={cerrar} className={BOTON_SECUNDARIO}>
                {messages.comun.cancelar}
              </button>
              <BotonAsync pendiente={estado === "enviando"} textoPendiente={messages.auth.recuperar.enviando} onClick={enviar} className={BOTON_PRIMARIO}>
                {messages.auth.recuperar.enviar}
              </BotonAsync>
            </>
          )}
        </PieDialogo>
      </DialogContent>
    </Dialog>
  );
}

/** Bloque de usuario del sidebar: abre un menú con la cuenta, el tema, el cambio de contraseña y el cierre de sesión. */
/** `nombreCuenta`: el de la cuenta (C7); `null` si no se pudo leer, y entonces se usa el correo. */
export function PerfilUsuario({ usuario, nombreCuenta }: { usuario: Usuario; nombreCuenta: string | null }) {
  const messages = useMessages();
  const t = messages.nav.perfil;
  const soloLectura = useSoloLecturaDeLaSesion();
  const router = useRouter();
  const [cambiandoContrasena, setCambiandoContrasena] = useState(false);
  const [apariencia, setApariencia] = useState(false);
  const [saliendo, setSaliendo] = useState(false);

  // C7: el nombre de la cuenta («Ferretería Torres»), no la parte del correo antes de la @ («ana.torres»).
  const nombre = nombreCuenta ?? usuario.email;
  // El rol real del usuario, tal como lo devuelve `/me` (#21): las mismas etiquetas que la página de equipo.
  const rol = (messages.roles.etiquetas as Record<string, string>)[usuario.rol] ?? usuario.rol;

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
          aria-label={t.menu}
          className={cn(
            "flex w-full min-w-0 cursor-pointer items-center justify-between gap-2 overflow-hidden rounded-xl bg-secondary p-2 text-left transition-colors outline-none select-none",
            "hover:bg-secondary focus-visible:ring-2 focus-visible:ring-ring/50 data-popup-open:bg-secondary",
          )}
        >
          <div className="flex min-w-0 items-center gap-2.5">
            <div className="flex size-6 shrink-0 items-center justify-center rounded-full bg-acento text-etiqueta font-bold text-acento-texto shadow-xs">
              {iniciales(nombre)}
            </div>
            <div className="min-w-0 overflow-hidden">
              <p className="truncate text-control leading-tight font-medium text-foreground">{nombre}</p>
              <p className="truncate text-etiqueta leading-tight text-muted-foreground">{usuario.email}</p>
            </div>
          </div>
          <CaretUpDownIcon weight="bold" className="size-4 shrink-0 text-muted-foreground/70" />
        </MenuTrigger>

        {/* Mismo ancho y alineación que el disparador, como el selector de empresas. */}
        <MenuContent side="top" align="start" className="w-(--anchor-width) min-w-0">
          <div className="px-2 pt-1.5 pb-1 text-etiqueta font-medium text-muted-foreground">{t.tuCuenta}</div>
          {/* Datos completos, sin truncar: el correo se parte en dos líneas si hace falta. */}
          <div className="flex items-start gap-2.5 rounded-lg bg-accent py-1.5 pr-2 pl-2 text-accent-foreground">
            <div className="flex size-6 shrink-0 items-center justify-center rounded-full bg-acento text-etiqueta font-bold text-acento-texto shadow-xs">
              {iniciales(nombre)}
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-control leading-tight font-medium">{nombre}</p>
              <p className="mt-0.5 text-etiqueta leading-snug break-all text-muted-foreground">{usuario.email}</p>
              <p data-testid="rol-del-usuario" className="mt-0.5 text-etiqueta leading-tight text-muted-foreground">{rol}</p>
            </div>
            <ShieldCheckIcon weight="duotone" className="mt-0.5 size-4 shrink-0 text-accent-foreground" />
          </div>

          <MenuSeparator />

          {/* Tema en línea, solo con íconos, como el menú del design system, no como ítems del menú. */}
          <div className="flex items-center justify-between gap-3 px-2 py-1.5">
            <span className={ROTULO}>{t.tema}</span>
            <ThemeToggle />
          </div>
          {/* La paleta, el tono oscuro y la tipografía son del design system (v0.5.0): se eligen en su panel y se guardan en el navegador. */}
          <MenuItem onClick={() => setApariencia(true)}>
            <PaletteIcon />
            <span className="flex-1">{t.apariencia}</span>
            <CaretRightIcon weight="bold" className="size-3.5 text-texto-terciario" />
          </MenuItem>

          <MenuSeparator />

          <MenuItem disabled={soloLectura !== null} onClick={() => setCambiandoContrasena(true)}>
            <KeyIcon />
            {t.cambiarContrasena}
          </MenuItem>
          <MenuLinkItem href="/developers" target="_blank">
            <BookOpenIcon />
            {t.documentacion}
          </MenuLinkItem>

          <MenuSeparator />

          <MenuItem variant="destructive" disabled={saliendo} onClick={salir}>
            <SignOutIcon />
            {messages.comun.cerrarSesion}
          </MenuItem>
        </MenuContent>
      </Menu>

      <CambiarContrasenaDialog email={usuario.email} abierto={cambiandoContrasena} onOpenChange={setCambiandoContrasena} />
      <PanelApariencia open={apariencia} onOpenChange={setApariencia} />
    </>
  );
}
