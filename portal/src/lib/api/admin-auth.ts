import { backendFetch } from "./client";

export type Administrador = {
  id: string;
  email: string;
};

/** Login del backoffice en dos pasos (#177): la contraseña da un desafío; la sesión sale del segundo factor. */
export type PasoAdmin = "CONFIGURAR_SEGUNDO_FACTOR" | "VERIFICAR_SEGUNDO_FACTOR";

export type AdminDesafio = {
  desafio: string;
  paso: PasoAdmin;
};

/** `qr_png`: PNG del QR en Base64. `secreto`: el mismo secreto en texto, por si la app no puede leer el QR. */
export type AdminConfiguracion = {
  secreto: string;
  uri: string;
  qr_png: string;
};

/** `expira_en`: segundos de vida del token; el BFF fija con esto la vida de la cookie. */
export type AdminSesion = {
  access_token: string;
  expira_en: number;
  administrador: Administrador;
};

/** Los códigos de recuperación solo viajan en esta respuesta: no se pueden volver a consultar. */
export type AdminSesionNueva = AdminSesion & {
  codigos_recuperacion: string[];
};

/** `origen`: la IP del cliente ya resuelta por el BFF (`cabecerasDeOrigen`, #208); sin ella el backend ve la del portal. */
export function loginAdministrador(email: string, password: string, origen: Record<string, string> = {}) {
  return backendFetch<AdminDesafio>("/v1/admin/auth/login", { method: "POST", body: { email, password }, headers: origen });
}

export function configurarSegundoFactor(desafio: string) {
  return backendFetch<AdminConfiguracion>("/v1/admin/auth/segundo-factor/configurar", { method: "POST", body: { desafio } });
}

/** La bitácora registra el inicio de sesión con la IP del administrador: por eso viaja `origen` (#208). */
export function confirmarSegundoFactor(desafio: string, codigo: string, origen: Record<string, string> = {}) {
  return backendFetch<AdminSesionNueva>("/v1/admin/auth/segundo-factor/confirmar", { method: "POST", body: { desafio, codigo }, headers: origen });
}

export function verificarSegundoFactor(desafio: string, codigo: string, origen: Record<string, string> = {}) {
  return backendFetch<AdminSesion>("/v1/admin/auth/segundo-factor/verificar", { method: "POST", body: { desafio, codigo }, headers: origen });
}

export function meAdministrador(access: string) {
  return backendFetch<Administrador>("/v1/admin/auth/me", { headers: { Authorization: `Bearer ${access}` } });
}
