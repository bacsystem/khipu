import { backendFetch } from "./client";

/**
 * Un acceso del equipo de soporte a la cuenta del cliente (#184). A propósito sin el administrador: para el cliente es «el equipo de soporte». `usuario` y
 * `duracion_segundos` faltan si el registro no tiene un formato que el backend entienda: el acceso se muestra igual, solo con su fecha.
 */
export type AccesoDeSoporte = { ocurrido_en: string; usuario?: string; duracion_segundos?: number };

/** Solo desde el servidor. Del portal (sesión de cuenta): con una API key no hay cuenta, y el historial es de la cuenta. */
export function listarAccesosDeSoporte(access: string) {
  return backendFetch<AccesoDeSoporte[]>("/v1/cuenta/accesos-de-soporte", { headers: { Authorization: `Bearer ${access}` } });
}
