import { backendFetch } from "./client";

export type Usuario = {
  id: string;
  cuenta_id: string;
  email: string;
  rol: string;
};

export type Tokens = {
  access: string;
  refresh: string;
  usuario: Usuario;
};

export function registrar(nombre: string, email: string, password: string) {
  return backendFetch<Tokens>("/v1/auth/registro", { method: "POST", body: { nombre, email, password } });
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
