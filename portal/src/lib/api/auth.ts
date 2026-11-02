import { backendFetch } from "./client";

export type Usuario = {
  id: string;
  cuenta_id: string;
  email: string;
  rol: string;
  /** `false` hasta abrir el enlace de verificación (#22): sin verificar no se crean empresas ni se emite. */
  correo_verificado: boolean;
  /**
   * Solo en una sesión de soporte (#184: un administrador mirando el portal como este usuario): hasta cuándo vale. Ausente en una sesión normal. Lo dice el
   * backend en `/me`, que es quien conoce el token: el portal no lee ni confía en lo que el JWT pueda decir.
   */
  soporte_hasta?: string;
  /**
   * `true` si no aceptó la versión vigente de los términos y la política de privacidad (#342): el panel le pide aceptarla antes de dejarlo seguir. Viene en el login,
   * el refresh, el registro y `/me`; opcional porque la respuesta de abrir una sesión de soporte no lo trae.
   */
  terminos_pendientes?: boolean;
};

/** El cuerpo de `POST /v1/auth/registro`. `acepta_terminos` (#342): sin `true`, el backend responde `400 TERMINOS_NO_ACEPTADOS`. */
export type DatosDeRegistro = {
  nombre: string;
  email: string;
  password: string;
  telefono: string;
  acepta_terminos: boolean;
};

/** Lo que quedó guardado al aceptar los términos vigentes (#342). */
export type AceptacionDeTerminos = {
  version: string;
  aceptada_en: string;
};

export type Tokens = {
  access: string;
  refresh: string;
  usuario: Usuario;
};

export function registrar(datos: DatosDeRegistro) {
  return backendFetch<Tokens>("/v1/auth/registro", { method: "POST", body: datos });
}

/** La versión vigente de los términos, aceptada por el usuario de la sesión (#342). Una sesión de soporte recibe `403 SOPORTE_SOLO_LECTURA`. */
export function aceptarTerminos(access: string) {
  return backendFetch<AceptacionDeTerminos>("/v1/auth/terminos", { method: "POST", headers: { Authorization: `Bearer ${access}` } });
}

/**
 * `origen`: la IP del cliente ya resuelta por el BFF (`cabecerasDeOrigen`, #208). El backend cuenta los fallos también por IP (#261):
 * sin ella vería la del portal para todos los clientes y no contaría por IP.
 */
export function login(email: string, password: string, origen: Record<string, string> = {}) {
  return backendFetch<Tokens>("/v1/auth/login", { method: "POST", body: { email, password }, headers: origen });
}

export function refrescar(refresh: string) {
  return backendFetch<Tokens>("/v1/auth/refresh", { method: "POST", body: { refresh } });
}

export function me(access: string) {
  return backendFetch<Usuario>("/v1/auth/me", { headers: { Authorization: `Bearer ${access}` } });
}

export function logout(access: string, refresh: string) {
  return backendFetch<void>("/v1/auth/logout", {
    method: "POST",
    body: { refresh },
    headers: { Authorization: `Bearer ${access}` },
  });
}

/** `origen`: como en {@link login}. El backend manda como mucho 3 correos por dirección por hora (#261) y responde igual pasado el tope. */
export function recuperar(email: string, origen: Record<string, string> = {}) {
  return backendFetch<void>("/v1/auth/recuperar", { method: "POST", body: { email }, headers: origen });
}

export function restablecer(token: string, password: string) {
  return backendFetch<void>("/v1/auth/restablecer", { method: "POST", body: { token, password } });
}

/** Con el token del enlace del correo (#22). Público: el enlace puede abrirse en otro dispositivo, sin sesión. */
export function verificarCorreo(token: string) {
  return backendFetch<void>("/v1/auth/verificar", { method: "POST", body: { token } });
}

/** Otro enlace de verificación al correo del usuario de la sesión. */
export function reenviarVerificacion(access: string) {
  return backendFetch<void>("/v1/auth/verificacion", { method: "POST", headers: { Authorization: `Bearer ${access}` } });
}
