import { backendFetch } from "./client";

/** Los roles de un usuario en su cuenta (#21). */
export type Rol = "ADMIN" | "EMISOR" | "LECTURA";
export const ROLES: readonly Rol[] = ["ADMIN", "EMISOR", "LECTURA"];

/** Un usuario de la cuenta. `es_usted`: el de la sesión. */
export type MiembroDelEquipo = {
  id: string;
  email: string;
  rol: Rol;
  activo: boolean;
  correo_verificado: boolean;
  es_usted: boolean;
};

/** Una invitación pendiente. `vencida`: el enlace ya no sirve, pero se puede reenviar. `envios`: cuántas veces se mandó (hasta 5). */
export type InvitacionAlEquipo = {
  id: string;
  email: string;
  rol: Rol;
  expira_en: string;
  vencida: boolean;
  envios: number;
  creada_en: string;
};

/** `correo_enviado: false` si el correo no salió: la invitación queda y se puede reenviar. */
export type InvitacionEnviada = { invitacion: InvitacionAlEquipo; correo_enviado: boolean };

/** Lo que ve el invitado al abrir el enlace, sin sesión. */
export type InvitacionRecibida = { email: string; cuenta: string; rol: Rol; expira_en: string };

export type CuerpoDeInvitacion = { email: string; rol: Rol };
export type CuerpoDeCambioDeRol = { rol: Rol };
export type CuerpoDeAceptacion = { token: string; password: string };

const conSesion = (access: string) => ({ headers: { Authorization: `Bearer ${access}` } });

/** Solo desde el servidor. Solo un administrador: los demás roles reciben 403 ROL_INSUFICIENTE. */
export function listarMiembros(access: string) {
  return backendFetch<MiembroDelEquipo[]>("/v1/cuenta/usuarios", conSesion(access));
}

export function listarInvitaciones(access: string) {
  return backendFetch<InvitacionAlEquipo[]>("/v1/cuenta/invitaciones", conSesion(access));
}

/** Público: el token va en el cuerpo, no en la URL. Lanza ApiError 422 TOKEN_INVALIDO si el enlace no sirve. */
export function consultarInvitacion(token: string) {
  return backendFetch<InvitacionRecibida>("/v1/auth/invitacion", { method: "POST", body: { token } });
}

export function aceptarInvitacion(token: string, password: string) {
  return backendFetch<void>("/v1/auth/invitacion/aceptar", { method: "POST", body: { token, password } satisfies CuerpoDeAceptacion });
}
