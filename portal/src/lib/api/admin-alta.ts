import { CABECERA_IDEMPOTENCIA } from "@/lib/idempotencia";
import { backendFetch } from "./client";

/** Cuerpo del alta asistida (#188), con la forma real del JSON. Sin contraseña: la elige el cliente al aceptar la invitación. */
export type AltaAsistida = {
  nombre: string;
  email: string;
  telefono?: string;
  empresa: { ruc: string; razon_social: string; entorno: "BETA" | "PRODUCCION" };
  serie: { tipo: string; serie: string };
};

export type AltaAsistidaCreada = {
  cuenta_id: string;
  tenant_id: string;
  ruc: string;
  /** Solo viaja en esta respuesta: el backend guarda un hash. */
  api_key: string;
  serie: { tipo: string; serie: string };
  /** `false` si el correo no salió: el alta quedó hecha y el cliente puede pedir un enlace con «olvidé mi contraseña». */
  invitacion_enviada: boolean;
};

/**
 * Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. `origen` es la IP del cliente ya resuelta por el
 * BFF (`cabecerasDeOrigen`, #208) para que la bitácora registre la del administrador y no la del portal. `clave`: la de idempotencia
 * del intento (#219); con ella, un reintento devuelve la misma API key en vez de un 409.
 */
export function altaAsistida(access: string, body: AltaAsistida, origen: Record<string, string> = {}, clave?: string) {
  return backendFetch<AltaAsistidaCreada>("/v1/admin/cuentas", {
    method: "POST",
    body,
    headers: { Authorization: `Bearer ${access}`, ...origen, ...(clave ? { [CABECERA_IDEMPOTENCIA]: clave } : {}) },
  });
}
