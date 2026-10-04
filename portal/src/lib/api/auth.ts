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
};

export type Tokens = {
  access: string;
  refresh: string;
  usuario: Usuario;
};

export function registrar(nombre: string, email: string, password: string, telefono: string) {
  return backendFetch<Tokens>("/v1/auth/registro", { method: "POST", body: { nombre, email, password, telefono } });
}

export function login(email: string, password: string) {
  return backendFetch<Tokens>("/v1/auth/login", { method: "POST", body: { email, password } });
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

export function recuperar(email: string) {
  return backendFetch<void>("/v1/auth/recuperar", { method: "POST", body: { email } });
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
