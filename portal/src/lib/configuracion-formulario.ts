import type { BannerConfigurado } from "@/lib/api/admin-configuracion";

/** Los mismos límites del backend (`BannerDeMantenimiento`): acá solo guían al administrador; quien decide es el backend. */
export const TEXTO_DE_AVISO_MAX = 300;
export const DIAS_MAX_DE_AVISO = 90;

/** Lima no tiene horario de verano: siempre UTC−5. Las horas del backoffice se escriben y se leen en hora de Lima, sin importar la zona del navegador. */
const HORAS_DE_LIMA = 5;
const MS_POR_HORA = 3_600_000;
const FECHA_HORA_LOCAL = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/;

/** «2026-10-15T22:00» (hora de Lima, como lo da un campo `datetime-local`) → el instante en ISO (UTC). `null` si no es una fecha y hora. */
export function limaAInstante(valor: string): string | null {
  if (!FECHA_HORA_LOCAL.test(valor)) return null;
  const ms = Date.parse(`${valor}:00-05:00`);
  return Number.isNaN(ms) ? null : new Date(ms).toISOString();
}

/** El instante en ISO → «2026-10-15T22:00» en hora de Lima, para un campo `datetime-local`. */
export function instanteALima(iso: string): string {
  return new Date(Date.parse(iso) - HORAS_DE_LIMA * MS_POR_HORA).toISOString().slice(0, 16);
}

export type ValoresDeAviso = { texto: string; desde: string; hasta: string };

/** Lo que muestra el formulario al abrirse: nada escrito, empezando ahora. */
export function valoresNuevoAviso(ahora: Date): ValoresDeAviso {
  return { texto: "", desde: instanteALima(ahora.toISOString()), hasta: "" };
}

/** Lo que muestra el formulario cuando ya hay un aviso publicado: sus datos, para reemplazarlos. */
export function valoresDeAviso(b: BannerConfigurado): ValoresDeAviso {
  return { texto: b.texto, desde: instanteALima(b.desde), hasta: instanteALima(b.hasta) };
}

export type ErroresDeAviso = { texto?: string; desde?: string; hasta?: string };

/** Solo lo que se puede decir sin preguntarle al backend: que haya texto y que las dos fechas sean fechas. Los límites y el orden los pone el backend, una sola vez. */
export function cuerpoDeAviso(v: ValoresDeAviso): { cuerpo: { texto: string; desde: string; hasta: string } } | { errores: ErroresDeAviso } {
  const errores: ErroresDeAviso = {};
  if (v.texto.trim() === "") errores.texto = "Escribe el texto del aviso.";
  const desde = limaAInstante(v.desde);
  const hasta = limaAInstante(v.hasta);
  if (desde === null) errores.desde = "Indica desde cuándo se muestra.";
  if (hasta === null) errores.hasta = "Indica hasta cuándo se muestra.";
  if (errores.texto || errores.desde || errores.hasta || desde === null || hasta === null) return { errores };
  return { cuerpo: { texto: v.texto, desde, hasta } };
}

export type EstadoDelAviso = "vigente" | "programado" | "vencido";

/** Si el aviso se está mostrando, todavía no empezó o ya terminó. */
export function estadoDelAviso(b: Pick<BannerConfigurado, "desde" | "vigente_ahora">, ahora: Date): EstadoDelAviso {
  if (b.vigente_ahora) return "vigente";
  return Date.parse(b.desde) > ahora.getTime() ? "programado" : "vencido";
}

/** Pone {@code marca} donde estaba el cursor (reemplazando lo seleccionado) y dice dónde queda el cursor después. */
export function insertarEnPosicion(texto: string, inicio: number, fin: number, marca: string): { texto: string; cursor: number } {
  const desde = Math.max(0, Math.min(inicio, texto.length));
  const hasta = Math.max(desde, Math.min(fin, texto.length));
  return { texto: texto.slice(0, desde) + marca + texto.slice(hasta), cursor: desde + marca.length };
}
