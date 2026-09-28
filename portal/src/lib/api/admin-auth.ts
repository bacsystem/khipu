import { backendFetch } from "./client";

export type Administrador = {
  id: string;
  email: string;
};

export type AdminSesion = {
  access_token: string;
  administrador: Administrador;
};

export function loginAdministrador(email: string, password: string) {
  return backendFetch<AdminSesion>("/v1/admin/auth/login", { method: "POST", body: { email, password } });
}

export function meAdministrador(access: string) {
  return backendFetch<Administrador>("/v1/admin/auth/me", { headers: { Authorization: `Bearer ${access}` } });
}
