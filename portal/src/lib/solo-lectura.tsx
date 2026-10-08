"use client";

import { createContext, useContext, type ReactNode } from "react";
import { messages } from "@/lib/messages";

const Contexto = createContext<string | null>(null);

/**
 * La sesión de soporte (#184) solo mira: el backend rechaza cualquier escritura. Sin esto el portal dejaba abrir y llenar un comprobante entero, o pedir otro
 * enlace de verificación, y el rechazo llegaba recién al enviar (H10). El layout privado envuelve el portal con `activa` cuando la sesión es de soporte, y
 * cada acción que escribe se deshabilita con {@link useSoloLectura}. Fuera del proveedor (pruebas, otras áreas) no hay restricción.
 */
export function SoloLectura({ activa, children }: { activa: boolean; children: ReactNode }) {
  return <Contexto.Provider value={activa ? messages.soporte.soloLectura : null}>{children}</Contexto.Provider>;
}

/** El motivo por el que no se puede escribir (para `title`), o null si se puede. */
export function useSoloLectura(): string | null {
  return useContext(Contexto);
}

/**
 * Un grupo de formularios que en modo soporte queda deshabilitado entero: `<fieldset disabled>` apaga todos sus campos y botones de una vez. `contents` para
 * no cambiar el layout de lo que envuelve.
 */
export function FormulariosDeEscritura({ children }: { children: ReactNode }) {
  const motivo = useSoloLectura();
  return (
    <fieldset disabled={motivo !== null} title={motivo ?? undefined} className="contents">
      {children}
    </fieldset>
  );
}
