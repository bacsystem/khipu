"use client";

import { useMessages } from "next-intl";
import { createContext, useContext, type ReactNode } from "react";
import type { Mensajes } from "@/i18n/mensajes";
import es from "@/messages/es.json";

/**
 * Por qué no se puede escribir, en tres niveles:
 * - `sesion`: lo de la propia sesión (cambiar la contraseña, pedir otro enlace de verificación). Solo lo impide el modo soporte.
 * - `emitir`: emitir y lo que se hace con un comprobante, y los clientes y productos guardados. Lo impiden el modo soporte y el rol «Solo lectura».
 * - `configurar`: empresas, datos fiscales, certificado, credenciales SOL y API SUNAT, envío automático al cliente, series, establecimientos, API keys y webhooks.
 *   Solo un administrador.
 */
type Motivos = { sesion: string | null; emitir: string | null; configurar: string | null };

const SIN_RESTRICCION: Motivos = { sesion: null, emitir: null, configurar: null };
const Contexto = createContext<Motivos>(SIN_RESTRICCION);

/** Lo que el rol (#21) y el modo soporte (#184) no dejan hacer. Un rol que el portal no conoce se trata como el más restringido. */
export function motivosDeRestriccion(soporte: boolean, rol: string, messages: Mensajes = es): Motivos {
  const t = messages.roles;
  if (soporte) {
    const m = messages.soporte.soloLectura;
    return { sesion: m, emitir: m, configurar: m };
  }
  if (rol === "ADMIN") return SIN_RESTRICCION;
  if (rol === "EMISOR") return { sesion: null, emitir: null, configurar: t.sinPermiso.configurar };
  return { sesion: null, emitir: t.sinPermiso.emitir, configurar: t.sinPermiso.configurar };
}

/**
 * La sesión de soporte (#184) solo mira y el backend rechaza cualquier escritura; el rol del usuario (#21) también acota lo que puede hacer. Sin esto el portal dejaba
 * abrir y llenar un comprobante entero y el rechazo llegaba recién al enviar (H10). El layout privado envuelve el portal con el modo soporte y el rol, y cada acción
 * que escribe se deshabilita con el hook de su nivel. Fuera del proveedor (pruebas, otras áreas) no hay restricción: el backend sigue decidiendo.
 *
 * Decisión de #21 (H5): lo que el rol no permite se **deshabilita con el motivo en el `title`**, no se oculta, igual que en modo soporte: así el emisor ve que la
 * acción existe y a quién pedírsela, y la página no cambia de forma según quién entra. Solo se ocultan las secciones que el backend ni siquiera deja leer (API keys
 * y Equipo, en el menú lateral) y la acción de invitar, que vive en la página del equipo.
 */
export function SoloLectura({ activa, rol = "ADMIN", children }: { activa: boolean; rol?: string; children: ReactNode }) {
  const messages = useMessages();
  return <Contexto.Provider value={motivosDeRestriccion(activa, rol, messages)}>{children}</Contexto.Provider>;
}

/** Por qué no puede emitir ni tocar comprobantes, clientes o productos (para `title`), o null si puede. */
export function useSoloLectura(): string | null {
  return useContext(Contexto).emitir;
}

/** Por qué no puede cambiar la configuración de la empresa, sus series, establecimientos, API keys o webhooks, o null si puede. */
export function useSinPermisoDeConfigurar(): string | null {
  return useContext(Contexto).configurar;
}

/** Por qué no puede hacer lo de su propia sesión (cambiar la contraseña, pedir otro enlace): solo en modo soporte. */
export function useSoloLecturaDeLaSesion(): string | null {
  return useContext(Contexto).sesion;
}

/**
 * Un grupo de formularios que queda deshabilitado entero sin el permiso: `<fieldset disabled>` apaga todos sus campos y botones de una vez. `contents` para no
 * cambiar el layout de lo que envuelve. `permiso`: el de lo que escriben esos formularios.
 */
export function FormulariosDeEscritura({ permiso = "emitir", children }: { permiso?: "emitir" | "configurar"; children: ReactNode }) {
  const motivos = useContext(Contexto);
  const motivo = motivos[permiso];
  return (
    <fieldset disabled={motivo !== null} title={motivo ?? undefined} className="contents">
      {children}
    </fieldset>
  );
}
