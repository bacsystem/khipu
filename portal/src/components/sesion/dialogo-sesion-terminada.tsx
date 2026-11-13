"use client";

import { LockKeyIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import { useEffect, useRef } from "react";
import { CabeceraDialogo, PieDialogo } from "khipu-design-system/patrones/cabecera-dialogo";
import { Dialog, DialogContent } from "khipu-design-system/ui/dialog";
import { BOTON_PRIMARIO } from "@/lib/estilos";
import { reiniciarMarcas } from "@/lib/sesion/inactividad";
import { montarDialogo, rutaDeLoginDeVuelta } from "@/lib/sesion/sesion-terminada";
import { useSesionTerminada } from "./use-sesion-terminada";

/**
 * «Tu sesión terminó» (#654): se abre cuando una acción del portal encuentra la sesión muerta (el proxy dijo 401 y el refresh ya no sirve). No se puede cerrar —ni con Escape ni con un
 * clic afuera—, porque la página de abajo ya no funciona: solo se sale con «Volver a ingresar», que lleva al login con la ruta actual en `next` (que el login valida con `rutaSegura`).
 *
 * Va montado una sola vez junto al vigilante de inactividad (en `(privado)` y en `/onboarding`), hermano suyo y no hijo: el vigilante no renderiza nada sin `minutos`.
 */
export function DialogoSesionTerminada() {
  const t = useMessages().sesion.terminada;
  const terminada = useSesionTerminada();
  const volverRef = useRef<HTMLButtonElement>(null);

  // Declararse montado es lo que permite explicar la caída en vez de navegar directo al login.
  useEffect(() => montarDialogo(), []);

  // Al abrirse, no al pulsar el botón: la sesión siguiente no hereda la inactividad de esta, y el vigilante (que ya se desmontó) no vuelve a opinar.
  useEffect(() => {
    if (terminada) reiniciarMarcas();
  }, [terminada]);

  return (
    // Controlado: ignorar el cierre lo deja abierto ante Escape. El clic afuera ya lo ignora el `Dialog` del design system.
    <Dialog open={terminada} onOpenChange={() => undefined}>
      <DialogContent className="gap-0 p-0" showCloseButton={false} initialFocus={volverRef} data-testid="dialogo-sesion-terminada">
        <CabeceraDialogo icon={LockKeyIcon} tono="aviso" titulo={t.titulo} descripcion={t.cuerpo} />
        <PieDialogo>
          {/* Una navegación completa, no `router.push`: no debe quedar estado de React con datos de la sesión que terminó. */}
          <button ref={volverRef} type="button" onClick={() => window.location.assign(rutaDeLoginDeVuelta())} className={BOTON_PRIMARIO} data-testid="sesion-terminada-volver">
            {t.volver}
          </button>
        </PieDialogo>
      </DialogContent>
    </Dialog>
  );
}
