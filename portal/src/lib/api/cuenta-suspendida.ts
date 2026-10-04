import { ApiError } from "./types";

/** A donde se manda a un cliente cuya cuenta está suspendida (#182): una página pública que se lo explica y le deja cerrar la sesión. */
export const RUTA_CUENTA_SUSPENDIDA = "/cuenta-suspendida";

/**
 * Si un error del backend es «tu cuenta está suspendida» (403 `CUENTA_SUSPENDIDA`). Solo ese: un 403 por otra causa (`EMPRESA_AJENA`,
 * `REQUIERE_SESION`…) no es una suspensión y no debe llevar a esa página.
 */
export function esCuentaSuspendida(err: unknown): boolean {
  return err instanceof ApiError && err.status === 403 && err.codigo === "CUENTA_SUSPENDIDA";
}
