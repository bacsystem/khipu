import { interpolar, type Mensajes } from "@/i18n/mensajes";
import es from "@/messages/es.json";

/**
 * #298: afectaciones del IGV (catálogo 07), ISC (catálogo 08) e ICBPER de una línea de «Nuevo comprobante». Lo que sale de aquí es lo que acepta `FacturaRequest`
 * (`IscDto`, `icbper`, `tipo_afectacion_igv`); el importe de cada tributo lo calcula el backend (`/calcular`, #405).
 */

/** Afectaciones que ofrece una venta interna (`tipo_operacion` 0101), agrupadas como SUNAT: la 40 (exportación) pide otro tipo de operación y no se ofrece. */
type Opcion = { codigo: string; etiqueta: string };
const GRUPOS = [
  { grupo: "onerosas", codigos: ["10", "20", "30", "17"] },
  { grupo: "gratuitasGravadas", codigos: ["11", "12", "13", "14", "15", "16"] },
  { grupo: "gratuitasExoneradas", codigos: ["21"] },
  { grupo: "gratuitasInafectas", codigos: ["31", "32", "33", "34", "35", "36", "37"] },
] as const;

/** Los grupos con sus opciones, con las etiquetas en el idioma de `messages` (#25). */
export function gruposAfectacion(messages: Mensajes = es): ReadonlyArray<{ grupo: string; opciones: ReadonlyArray<Opcion> }> {
  const t = messages.documentos;
  return GRUPOS.map((g) => ({ grupo: t.grupoAfectacion[g.grupo], opciones: g.codigos.map((codigo) => ({ codigo, etiqueta: t.afectacionCorta[codigo] })) }));
}

export const GRUPOS_AFECTACION = gruposAfectacion();

export const afectaciones = (messages: Mensajes = es): Opcion[] => gruposAfectacion(messages).flatMap((g) => [...g.opciones]);
export const AFECTACIONES: Opcion[] = afectaciones();

export const ES_IVAP = (afectacion: string) => afectacion === "17";

/** ISC tal como se teclea: el sistema decide qué campos valen (01 tasa, 02 monto por unidad, 03 tasa y PVP sugerido). Vacío es «sin ISC». */
export type IscForm = { sistema: "" | "01" | "02" | "03"; tasa: string; montoUnitario: string; basePvp: string };
export const ISC_VACIO: IscForm = { sistema: "", tasa: "", montoUnitario: "", basePvp: "" };

export type IscApi = { sistema: string; tasa?: number; monto_unitario?: number; base_pvp?: number };

/** Positivo y con hasta 5 decimales: el formato de `Isc` (3104 y los demás). */
function positivoHasta5(texto: string): boolean {
  const t = texto.trim();
  if (!/^\d*\.?\d+$/.test(t)) return false;
  const i = t.indexOf(".");
  return Number(t) > 0 && (i < 0 || t.length - i - 1 <= 5);
}

/** Por qué no vale el ISC, o `null` (vale o está vacío). Espeja `Isc` del dominio. */
export function errorDeIsc(isc: IscForm, messages: Mensajes = es): string | null {
  const t = messages.comprobantes.validacion.isc;
  if (isc.sistema === "") return null;
  if (isc.sistema === "02") return positivoHasta5(isc.montoUnitario) ? null : t.montoUnitario;
  if (!positivoHasta5(isc.tasa)) return t.tasa;
  if (isc.sistema === "03" && !positivoHasta5(isc.basePvp)) return t.basePvp;
  return null;
}

export function iscAApi(isc: IscForm): IscApi | undefined {
  if (isc.sistema === "" || errorDeIsc(isc) !== null) return undefined;
  if (isc.sistema === "02") return { sistema: "02", monto_unitario: Number(isc.montoUnitario) };
  if (isc.sistema === "03") return { sistema: "03", tasa: Number(isc.tasa), base_pvp: Number(isc.basePvp) };
  return { sistema: "01", tasa: Number(isc.tasa) };
}

/** Lo mínimo de una línea que decide si sus tributos son compatibles. */
export type LineaTributos = { descripcion: string; cantidad: number | null; unidad: string; tipoAfectacionIgv: string; isc: IscForm; icbper: boolean };

/**
 * Las combinaciones que el dominio rechaza antes de numerar, dichas antes de emitir: el IVAP es de todo el comprobante (no se mezcla con otras afectaciones, 2650 no
 * lleva ISC ni ICBPER), las bolsas del ICBPER son unidades enteras (2892, 4320) y el NRUS (0113) no cobra el ICBPER (3240). Devuelve el primer motivo, o `null`.
 */
export function incompatibilidadDeTributos(lineas: LineaTributos[], operacion = "0101", messages: Mensajes = es): string | null {
  const t = messages.comprobantes.validacion.tributos;
  if (operacion === "0113" && lineas.some((l) => l.icbper)) return t.icbperNrus;
  const hayIvap = lineas.some((l) => ES_IVAP(l.tipoAfectacionIgv));
  if (hayIvap && lineas.some((l) => !ES_IVAP(l.tipoAfectacionIgv))) return t.ivapMezcla;
  const n = (l: LineaTributos) => ({ item: interpolar(t.nombreItem, { descripcion: l.descripcion.trim() || t.itemSinNombre }) });
  for (const l of lineas) {
    if (ES_IVAP(l.tipoAfectacionIgv) && (l.isc.sistema !== "" || l.icbper)) return interpolar(t.itemIvap, n(l));
    if (l.icbper) {
      if (l.unidad !== "NIU") return interpolar(t.itemIcbperUnidad, n(l));
      const c = l.cantidad ?? 0;
      if (!Number.isInteger(c) || c < 1 || c > 99999) return interpolar(t.itemIcbperCantidad, n(l));
    }
  }
  return null;
}

/**
 * Lo que es el precio de la línea según su afectación (#117): con IGV en la gravada, con IVAP en la IVAP, y sin impuesto en la exonerada, la inafecta, la gratuita (valor
 * referencial) y la exportación. «con IGV» en una exonerada inducía a cargar el precio mal. `""`: líneas de afectaciones distintas, el encabezado no afirma una.
 */
export function etiquetaDelPrecio(afectacion: string, messages: Mensajes = es): string {
  const t = messages.comprobantes.precio;
  if (afectacion === "") return t.sinAfectacion;
  if (afectacion === "10") return t.conIgv;
  if (afectacion === "17") return t.conIvap;
  return t.sinIgv;
}
