import type { Mensajes } from "@/i18n/mensajes";
import es from "@/messages/es.json";

/**
 * #311: tipos de operación del catálogo 51 que ofrece «Nuevo comprobante». Hoy la venta interna y la exportación (0200–0208 menos 0202/0205, que piden los datos del
 * huésped) y, en boleta, el NRUS (0113). La detracción (1001–1004) y la percepción (2001) no se eligen aquí: las fija su control (#288, #297). Lo que el backend no emite no se
 * ofrece: el selector promete lo que se puede cumplir.
 */
/** Los grupos del selector, en orden; su nombre está en `messages.documentos.grupoOperacion` (#25). */
export const GRUPOS_OPERACION = ["interna", "exportacion"] as const;
export type GrupoOperacion = (typeof GRUPOS_OPERACION)[number];

export type Operacion = { codigo: string; etiqueta: string; grupo: GrupoOperacion; soloBoleta?: boolean };

const CATALOGO = [
  { codigo: "0101", grupo: "interna" },
  // #297: el NRUS (nuevo RUS) solo emite boletas; el catálogo 51 lo liga a ese comprobante.
  { codigo: "0113", grupo: "interna", soloBoleta: true },
  { codigo: "0200", grupo: "exportacion" },
  { codigo: "0201", grupo: "exportacion" },
  { codigo: "0203", grupo: "exportacion" },
  { codigo: "0204", grupo: "exportacion" },
  { codigo: "0206", grupo: "exportacion" },
  { codigo: "0207", grupo: "exportacion" },
  { codigo: "0208", grupo: "exportacion" },
] as const;

/** Las operaciones con su etiqueta en el idioma de `messages` (#25). */
export function operaciones(messages: Mensajes = es): Operacion[] {
  return CATALOGO.map((o) => ({ ...o, etiqueta: messages.documentos.operaciones[o.codigo] }));
}

export const OPERACIONES: Operacion[] = operaciones();

export const esExportacion = (operacion: string) => /^020[0-8]$/.test(operacion);

/** Servicios cuyo uso o aprovechamiento ocurre en otro país: piden ese país (3098, 3099). */
export const EXIGE_PAIS_DE_USO = new Set(["0201", "0208"]);

/** En estas exportaciones el comprador no lleva RUC en una factura (2800); en una boleta, en ninguna ni en el 0401. */
export const EXPORTACION_SIN_RUC = new Set(["0200", "0201", "0204"]);

export const INCOTERMS = ["EXW", "FCA", "FAS", "FOB", "CFR", "CIF", "CPT", "CIP", "DAP", "DPU", "DDP"] as const;

/** Documentos del comprador de una exportación (catálogo 06, 2800–2802): el RUC solo donde la regla lo permite. */
export function documentosDeExportacion(operacion: string, boleta: boolean): string[] {
  const base = ["0", "1", "4", "7", "A", "B", "C", "D", "E", "G"];
  return boleta || EXPORTACION_SIN_RUC.has(operacion) ? base : [...base, "6"];
}

export type DatosDeExportacion = { operacion: string; incoterm: string; paisDeUso: string; paisDelComprador: string };

/** Lo que el formulario sabe decir antes de enviar; el resto de las reglas las dice el backend al calcular. */
export function errorDeExportacion(d: DatosDeExportacion, boleta: boolean, messages: Mensajes = es): string | null {
  const t = messages.comprobantes.validacion.exportacion;
  if (!esExportacion(d.operacion)) return null;
  if (!boleta && !d.paisDelComprador) return t.sinPais;
  if (EXIGE_PAIS_DE_USO.has(d.operacion) && !d.paisDeUso) return t.paisDeUso;
  if (EXIGE_PAIS_DE_USO.has(d.operacion) && d.paisDeUso === "PE") return t.paisDeUsoPeru;
  return null;
}
