"use client";

import { useEffect } from "react";
import { escucharAvisosDeSesion } from "@/lib/sesion/canal-de-sesion";
import { RUTA_LOGIN_POR_INACTIVIDAD, RUTA_LOGIN_POR_OTRA_PESTANA } from "./use-vigilante-de-inactividad";

/**
 * Lleva al login a esta pestaña cuando otra cerró la sesión (#653): `"inactividad"` explica el corte por inactividad y `"salir"` el cierre voluntario. Es independiente del corte por
 * inactividad (#637): con `SESION_INACTIVIDAD_MINUTOS=0` o en una sesión de soporte no hay vigilante que cuente, pero cerrar la sesión en una pestaña igual debe sacar a las demás,
 * así que se monta siempre donde hay una sesión viva. Navega con `irA` (una carga completa).
 */
export function useAvisosDeOtrasPestanas(irA: (url: string) => void): void {
  useEffect(
    () => escucharAvisosDeSesion((aviso) => irA(aviso === "inactividad" ? RUTA_LOGIN_POR_INACTIVIDAD : RUTA_LOGIN_POR_OTRA_PESTANA)),
    [irA],
  );
}
