import type { CuerpoDePlan, LimiteAdmin, PlanAdmin } from "@/lib/api/admin-planes";
import { messages } from "@/lib/messages";

const t = messages.admin.planes.formulario.errores;

export const NOMBRE_MAX = 40;
/** El mayor entero que acepta el backend (Java `int`): más allá, la base lo rechazaría con un error poco claro. */
const ENTERO_MAX = 2_147_483_647;
/** El mayor precio que cabe en la columna del backend (`NUMERIC(10,2)`, `Plan.PRECIO_MAX`). */
export const PRECIO_MAX = 99_999_999.99;

/** Lo que el administrador tiene escrito en el formulario: todo texto, como llega de los campos. */
export type ValoresDePlan = {
  nombre: string;
  precio: string;
  documentos: string;
  documentosIlimitado: boolean;
  rucs: string;
  usuarios: string;
  usuariosIlimitado: boolean;
  apiKeys: string;
  apiKeysIlimitado: boolean;
  retencion: string;
  /** H20: si sale en la página de precios; un plan a medida para un cliente va sin marcar. */
  visibleEnPublicidad: boolean;
};

export type ErroresDePlan = Partial<Record<keyof ValoresDePlan, string>>;

export const VALORES_NUEVO_PLAN: ValoresDePlan = {
  nombre: "",
  precio: "",
  documentos: "",
  documentosIlimitado: false,
  rucs: "",
  usuarios: "",
  usuariosIlimitado: false,
  apiKeys: "",
  apiKeysIlimitado: false,
  retencion: "",
  visibleEnPublicidad: true,
};

function entero(texto: string): number | null {
  if (!/^[1-9]\d*$/.test(texto.trim())) return null;
  const n = Number(texto.trim());
  return n <= ENTERO_MAX ? n : null;
}

function limite(texto: string, ilimitado: boolean): LimiteAdmin | null {
  if (ilimitado) return { ilimitado: true };
  const n = entero(texto);
  return n === null ? null : { maximo: n, ilimitado: false };
}

/**
 * Valida lo evidente antes de enviar y arma el cuerpo que espera el backend. El backend sigue siendo la autoridad (nombre único, por ejemplo, solo él lo sabe):
 * esto evita ir y volver por un campo vacío y deja todos los errores a la vez. «Ilimitado» manda el límite sin máximo, aunque el campo conserve un número.
 */
export function validarPlan(v: ValoresDePlan): { errores: ErroresDePlan } | { cuerpo: CuerpoDePlan } {
  const errores: ErroresDePlan = {};

  const nombre = v.nombre.trim();
  if (!nombre) errores.nombre = t.nombreRequerido;
  else if (nombre.length > NOMBRE_MAX) errores.nombre = t.nombreLargo;

  const precioTexto = v.precio.trim();
  if (!/^\d+(\.\d{1,2})?$/.test(precioTexto) || Number(precioTexto) > PRECIO_MAX) errores.precio = t.precioInvalido;

  const documentos = limite(v.documentos, v.documentosIlimitado);
  if (!documentos) errores.documentos = t.limiteInvalido;
  const usuarios = limite(v.usuarios, v.usuariosIlimitado);
  if (!usuarios) errores.usuarios = t.limiteInvalido;
  const apiKeys = limite(v.apiKeys, v.apiKeysIlimitado);
  if (!apiKeys) errores.apiKeys = t.limiteInvalido;
  const rucs = entero(v.rucs);
  if (rucs === null) errores.rucs = t.numeroInvalido;
  const retencion = entero(v.retencion);
  if (retencion === null) errores.retencion = t.numeroInvalido;

  if (Object.keys(errores).length > 0 || !documentos || !usuarios || !apiKeys || rucs === null || retencion === null) return { errores };
  return {
    cuerpo: {
      nombre,
      precio_mensual: Number(precioTexto),
      limites: { documentos_al_mes: documentos, rucs, usuarios, api_keys: apiKeys, retencion_anios: retencion },
      visible_en_publicidad: v.visibleEnPublicidad,
    },
  };
}

/** Prellena el formulario con el plan. Si tiene un cambio de límites programado se muestran esos: es lo último que se decidió y lo que va a regir. */
export function valoresDePlan(plan: PlanAdmin): ValoresDePlan {
  const l = plan.limites_programados?.limites ?? plan.limites;
  return {
    nombre: plan.nombre,
    precio: plan.precio_mensual.toFixed(2),
    documentos: l.documentos_al_mes.maximo?.toString() ?? "",
    documentosIlimitado: l.documentos_al_mes.ilimitado,
    rucs: String(l.rucs),
    usuarios: l.usuarios.maximo?.toString() ?? "",
    usuariosIlimitado: l.usuarios.ilimitado,
    apiKeys: l.api_keys.maximo?.toString() ?? "",
    apiKeysIlimitado: l.api_keys.ilimitado,
    retencion: String(l.retencion_anios),
    visibleEnPublicidad: plan.visible_en_publicidad,
  };
}
