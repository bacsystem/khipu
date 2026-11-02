import { backendFetch } from "./client";

/** Las tres cosas que un administrador cambia de la plataforma sin un despliegue (#199). */
export const SECCIONES_DE_CONFIGURACION = ["correo", "plantillas", "aviso"] as const;
export type SeccionDeConfiguracion = (typeof SECCIONES_DE_CONFIGURACION)[number];

/** Una dirección de correo con la forma real del JSON: el nombre y las respuestas son opcionales y la API los omite si no se fijaron. */
export type Direccion = { nombre?: string; email: string; responder_a?: string };

/** De quién salen los correos: el vigente, si lo fijó un administrador (`personalizado`) y el del servidor, al que se vuelve al restablecer. `actualizado_en` solo viene si es personalizado. */
export type RemitenteConfigurado = {
  vigente: Direccion;
  personalizado: boolean;
  actualizado_en?: string;
  /** H11: el correo de quien lo fijó; ausente si fue la clave de la plataforma o si ese administrador ya no existe. */
  actualizado_por?: string;
  predeterminado: Direccion;
};

export type VariableDeCorreo = { nombre: string; descripcion: string; ejemplo: string; indispensable: boolean };

export type TextoDeCorreo = { asunto: string; cuerpo: string };

/** Un correo que la plataforma manda, con el texto con que sale ahora, el de fábrica y las variables que admite. `actualizada_en` solo viene si alguien lo cambió. */
export type PlantillaDeCorreo = {
  tipo: string;
  etiqueta: string;
  cuando_se_manda: string;
  vigente: TextoDeCorreo;
  defecto: TextoDeCorreo;
  personalizada: boolean;
  actualizada_en?: string;
  /** H11: el correo de quien lo cambió; ausente si fue la clave de la plataforma o si ese administrador ya no existe. */
  actualizada_por?: string;
  variables: VariableDeCorreo[];
};

/** El aviso de mantenimiento publicado y si se está mostrando ahora (puede estar programado para después o haber vencido). */
export type BannerConfigurado = { texto: string; desde: string; hasta: string; actualizado_en: string; vigente_ahora: boolean };

export type ParamsConfiguracion = { seccion: SeccionDeConfiguracion; plantilla?: string };

/** Los nombres de los correos son del dominio (`RECUPERACION_CLAVE`…): mayúsculas y guion bajo, nada que pueda salirse de la ruta. */
const TIPO_DE_CORREO = /^[A-Z_]{1,40}$/;

export function esTipoDeCorreo(tipo: string): boolean {
  return TIPO_DE_CORREO.test(tipo);
}

/** Sanea lo que llega por la URL: una sección que no existe es la del correo saliente, y un correo con un nombre imposible se ignora. */
export function paramsConfiguracionDesdeUrl(p: { seccion?: string; plantilla?: string }): ParamsConfiguracion {
  const seccion = SECCIONES_DE_CONFIGURACION.find((s) => s === p.seccion) ?? "correo";
  return { seccion, ...(p.plantilla !== undefined && esTipoDeCorreo(p.plantilla) ? { plantilla: p.plantilla } : {}) };
}

/** URL de la pantalla con su estado (compartible): solo lo que se aparta del defecto, para que la ruta base quede limpia. El correo elegido solo cuenta en «Plantillas». */
export function hrefConfiguracion(p: ParamsConfiguracion): string {
  const qs = new URLSearchParams();
  if (p.seccion !== "correo") qs.set("seccion", p.seccion);
  if (p.seccion === "plantillas" && p.plantilla) qs.set("plantilla", p.plantilla);
  const texto = qs.toString();
  return texto ? `/admin/configuracion?${texto}` : "/admin/configuracion";
}

const conSesion = (access: string, origen: Record<string, string> = {}) => ({ headers: { Authorization: `Bearer ${access}`, ...origen } });

/** Solo desde el servidor: usan el JWT del administrador, que el navegador nunca ve. */
export function obtenerRemitente(access: string) {
  return backendFetch<RemitenteConfigurado>("/v1/admin/configuracion/correo", conSesion(access));
}

export function listarPlantillas(access: string) {
  return backendFetch<PlantillaDeCorreo[]>("/v1/admin/configuracion/plantillas", conSesion(access));
}

/** El aviso publicado, o `null` si no hay ninguno (la API lo dice con `datos` nulo). */
export async function obtenerBanner(access: string): Promise<BannerConfigurado | null> {
  return (await backendFetch<BannerConfigurado | null | undefined>("/v1/admin/configuracion/banner", conSesion(access))) ?? null;
}

/**
 * Solo desde el servidor. `origen` es la IP del administrador ya resuelta por el BFF (`cabecerasDeOrigen`, #208) para que la bitácora registre la suya y no la del portal. El cuerpo
 * se reenvía sin tocarlo: las reglas de cada dato las pone el backend, una sola vez.
 */
export function cambiarRemitente(access: string, cuerpo: object, origen: Record<string, string> = {}) {
  return backendFetch<RemitenteConfigurado>("/v1/admin/configuracion/correo", { method: "PUT", body: cuerpo, ...conSesion(access, origen) });
}

export function restablecerRemitente(access: string, origen: Record<string, string> = {}) {
  return backendFetch<RemitenteConfigurado>("/v1/admin/configuracion/correo", { method: "DELETE", ...conSesion(access, origen) });
}

/** `tipo` va a la URL del backend: quien llama ya comprobó que es de la forma de un nombre de correo ({@link esTipoDeCorreo}). */
export function guardarPlantilla(access: string, tipo: string, cuerpo: object, origen: Record<string, string> = {}) {
  return backendFetch<PlantillaDeCorreo>(`/v1/admin/configuracion/plantillas/${tipo}`, { method: "PUT", body: cuerpo, ...conSesion(access, origen) });
}

export function restaurarPlantilla(access: string, tipo: string, origen: Record<string, string> = {}) {
  return backendFetch<PlantillaDeCorreo>(`/v1/admin/configuracion/plantillas/${tipo}`, { method: "DELETE", ...conSesion(access, origen) });
}

/** No guarda ni deja registro: no necesita el origen. */
export function vistaPreviaDePlantilla(access: string, tipo: string, cuerpo: object) {
  return backendFetch<TextoDeCorreo>(`/v1/admin/configuracion/plantillas/${tipo}/vista-previa`, { method: "POST", body: cuerpo, ...conSesion(access) });
}

export function publicarBanner(access: string, cuerpo: object, origen: Record<string, string> = {}) {
  return backendFetch<BannerConfigurado>("/v1/admin/configuracion/banner", { method: "PUT", body: cuerpo, ...conSesion(access, origen) });
}

export function retirarBanner(access: string, origen: Record<string, string> = {}) {
  return backendFetch<void>("/v1/admin/configuracion/banner", { method: "DELETE", ...conSesion(access, origen) });
}
