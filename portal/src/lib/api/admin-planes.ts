import type { Locale } from "@/i18n/config";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import es from "@/messages/es.json";
import { formatearMonto } from "../formato";
import { backendFetch } from "./client";

/** Un tope de un plan. «Sin límite» es `ilimitado: true` y sin `maximo`: lo omitido nunca se interpreta como ilimitado. */
export type LimiteAdmin = { maximo?: number; ilimitado: boolean };

/** Lo que un plan permite. RUC y retención siempre llevan cifra. */
export type LimitesAdmin = {
  documentos_al_mes: LimiteAdmin;
  rucs: number;
  usuarios: LimiteAdmin;
  api_keys: LimiteAdmin;
  retencion_anios: number;
};

/** Un cambio de límites ya decidido que entra al inicio del ciclo siguiente (el mes calendario en Lima, #190). */
export type CambioProgramadoAdmin = { limites: LimitesAdmin; aplica_desde: string };

export type EstadoPlanAdmin = "ACTIVO" | "INACTIVO";

/** Un plan tal como manda hoy, con cuántas cuentas lo tienen vigente (#190). */
export type PlanAdmin = {
  id: string;
  nombre: string;
  precio_mensual: number;
  limites: LimitesAdmin;
  limites_programados?: CambioProgramadoAdmin;
  estado: EstadoPlanAdmin;
  por_defecto: boolean;
  /** H20: si sale en la página de precios. Un plan a medida está activo pero no se publica. */
  visible_en_publicidad: boolean;
  cuentas: number;
};

/** Lo que se manda para crear o editar un plan. */
export type CuerpoDePlan = { nombre: string; precio_mensual: number; limites: LimitesAdmin; visible_en_publicidad: boolean };

/** «1,500» (el formato de miles del portal), o «Ilimitados»; un límite sin máximo ni marca (que el backend no debería mandar) se ve como un guion y no como «undefined». */
export function limiteEnPalabras(l: LimiteAdmin, messages: Mensajes = es): string {
  if (l.ilimitado) return messages.admin.planes.palabras.ilimitados;
  return l.maximo === undefined ? "—" : l.maximo.toLocaleString("en-US");
}

/** «S/ 29.00», o «Gratis» para cero. */
export function precioEnSoles(precio: number, messages: Mensajes = es, locale: Locale = "es"): string {
  return precio === 0 ? messages.admin.planes.palabras.gratis : formatearMonto("PEN", precio, locale);
}

/** «1 año», «5 años». */
export function retencionEnPalabras(anios: number, messages: Mensajes = es): string {
  const t = messages.admin.planes;
  return anios === 1 ? t.retencionUnAnio : interpolar(t.retencionAnios, { n: anios });
}

/** «1 usuario», «3 usuarios», o `ilimitados` («usuarios ilimitados»). Los textos llevan `{n}`. */
function cantidad(l: LimiteAdmin | number, textos: { uno: string; varios: string; ilimitados: string }): string {
  const limite = typeof l === "number" ? { maximo: l, ilimitado: false } : l;
  if (limite.ilimitado) return textos.ilimitados;
  if (limite.maximo === undefined) return interpolar(textos.varios, { n: "—" });
  return interpolar(limite.maximo === 1 ? textos.uno : textos.varios, { n: limite.maximo.toLocaleString("en-US") });
}

/** Los límites de un plan en una línea, con singulares y plurales bien puestos (H13): «1 documento al mes · 1 RUC · 1 usuario · 1 API key · 1 año de retención». */
export function limitesEnPalabras(l: LimitesAdmin, messages: Mensajes = es): string {
  const p = messages.admin.planes.palabras;
  return [
    cantidad(l.documentos_al_mes, p.documentos),
    cantidad(l.rucs, p.rucs),
    cantidad(l.usuarios, p.usuarios),
    cantidad(l.api_keys, p.apiKeys),
    interpolar(p.retencion, { retencion: retencionEnPalabras(l.retencion_anios, messages) }),
  ].join(" · ");
}

export type CampoDeLimite = "documentos" | "rucs" | "usuarios" | "apiKeys" | "retencion";

/** Lo que cambia entre dos conjuntos de límites, de qué a qué y en el orden de la tabla; sin diferencias, nada. */
export function cambiosDeLimites(
  actual: LimitesAdmin,
  nuevo: LimitesAdmin,
  messages: Mensajes = es,
): { campo: CampoDeLimite; de: string; a: string }[] {
  const limite = (l: LimiteAdmin) => limiteEnPalabras(l, messages);
  const retencion = (anios: number) => retencionEnPalabras(anios, messages);
  const pares: [CampoDeLimite, string, string][] = [
    ["documentos", limite(actual.documentos_al_mes), limite(nuevo.documentos_al_mes)],
    ["rucs", String(actual.rucs), String(nuevo.rucs)],
    ["usuarios", limite(actual.usuarios), limite(nuevo.usuarios)],
    ["apiKeys", limite(actual.api_keys), limite(nuevo.api_keys)],
    ["retencion", retencion(actual.retencion_anios), retencion(nuevo.retencion_anios)],
  ];
  return pares.filter(([, de, a]) => de !== a).map(([campo, de, a]) => ({ campo, de, a }));
}

/**
 * Solo desde el servidor: usan el JWT del administrador, que el navegador nunca ve. `origen` es la IP del cliente ya resuelta por el BFF
 * (`cabecerasDeOrigen`, #208) para que la bitácora registre la del administrador y no la del portal.
 */
const conSesion = (access: string, origen: Record<string, string> = {}) => ({ Authorization: `Bearer ${access}`, ...origen });

export function listarPlanesAdmin(access: string) {
  return backendFetch<PlanAdmin[]>("/v1/admin/planes", { headers: conSesion(access) });
}

export function crearPlan(access: string, cuerpo: unknown, origen: Record<string, string> = {}) {
  return backendFetch<PlanAdmin>("/v1/admin/planes", { method: "POST", body: cuerpo, headers: conSesion(access, origen) });
}

export function editarPlan(access: string, id: string, cuerpo: unknown, origen: Record<string, string> = {}) {
  return backendFetch<PlanAdmin>(`/v1/admin/planes/${id}`, { method: "PUT", body: cuerpo, headers: conSesion(access, origen) });
}

export function desactivarPlan(access: string, id: string, origen: Record<string, string> = {}) {
  return backendFetch<PlanAdmin>(`/v1/admin/planes/${id}/desactivar`, { method: "POST", headers: conSesion(access, origen) });
}

export function activarPlan(access: string, id: string, origen: Record<string, string> = {}) {
  return backendFetch<PlanAdmin>(`/v1/admin/planes/${id}/activar`, { method: "POST", headers: conSesion(access, origen) });
}

export function eliminarPlan(access: string, id: string, origen: Record<string, string> = {}) {
  return backendFetch<null>(`/v1/admin/planes/${id}`, { method: "DELETE", headers: conSesion(access, origen) });
}
