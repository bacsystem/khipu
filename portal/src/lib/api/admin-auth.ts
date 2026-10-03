import { backendFetch } from "./client";

export type Administrador = {
  id: string;
  email: string;
};

export type AdminSesion = {
  access_token: string;
  administrador: Administrador;
};

/** `origen`: la IP del cliente ya resuelta por el BFF (`cabecerasDeOrigen`, #208); sin ella el backend ve la del portal. */
export function loginAdministrador(email: string, password: string, origen: Record<string, string> = {}) {
  return backendFetch<AdminSesion>("/v1/admin/auth/login", { method: "POST", body: { email, password }, headers: origen });
}

export function meAdministrador(access: string) {
  return backendFetch<Administrador>("/v1/admin/auth/me", { headers: { Authorization: `Bearer ${access}` } });
}
