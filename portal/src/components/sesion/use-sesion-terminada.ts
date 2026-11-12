"use client";

import { useSyncExternalStore } from "react";
import { leerSesionTerminada, suscribirseASesionTerminada } from "@/lib/sesion/sesion-terminada";

/** Si la sesión murió con la página cargada (#654). En el servidor siempre `false`: el diálogo se abre en el navegador. */
export function useSesionTerminada(): boolean {
  return useSyncExternalStore(suscribirseASesionTerminada, leerSesionTerminada, () => false);
}
