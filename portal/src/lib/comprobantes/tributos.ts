/**
 * #298: afectaciones del IGV (catálogo 07), ISC (catálogo 08) e ICBPER de una línea de «Nuevo comprobante». Lo que sale de aquí es lo que acepta `FacturaRequest`
 * (`IscDto`, `icbper`, `tipo_afectacion_igv`); el importe de cada tributo lo calcula el backend (`/calcular`, #405).
 */

/** Afectaciones que ofrece una venta interna (`tipo_operacion` 0101), agrupadas como SUNAT: la 40 (exportación) pide otro tipo de operación y no se ofrece. */
type Opcion = { codigo: string; etiqueta: string };
export const GRUPOS_AFECTACION: ReadonlyArray<{ grupo: string; opciones: ReadonlyArray<Opcion> }> = [
  { grupo: "Onerosas", opciones: [{ codigo: "10", etiqueta: "Gravado" }, { codigo: "20", etiqueta: "Exonerado" }, { codigo: "30", etiqueta: "Inafecto" }, { codigo: "17", etiqueta: "IVAP (arroz pilado, 4 %)" }] },
  {
    grupo: "Gratuitas gravadas",
    opciones: [
      { codigo: "11", etiqueta: "Retiro por premio" }, { codigo: "12", etiqueta: "Retiro por donación" }, { codigo: "13", etiqueta: "Retiro" },
      { codigo: "14", etiqueta: "Retiro por publicidad" }, { codigo: "15", etiqueta: "Bonificación" }, { codigo: "16", etiqueta: "Retiro por entrega a trabajadores" },
    ],
  },
  { grupo: "Gratuitas exoneradas", opciones: [{ codigo: "21", etiqueta: "Transferencia gratuita" }] },
  {
    grupo: "Gratuitas inafectas",
    opciones: [
      { codigo: "31", etiqueta: "Retiro por bonificación" }, { codigo: "32", etiqueta: "Retiro" }, { codigo: "33", etiqueta: "Retiro por muestras médicas" },
      { codigo: "34", etiqueta: "Retiro por convenio colectivo" }, { codigo: "35", etiqueta: "Retiro por premio" }, { codigo: "36", etiqueta: "Retiro por publicidad" },
      { codigo: "37", etiqueta: "Transferencia gratuita" },
    ],
  },
];

export const AFECTACIONES: Opcion[] = GRUPOS_AFECTACION.flatMap((g) => [...g.opciones]);

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
export function errorDeIsc(isc: IscForm): string | null {
  if (isc.sistema === "") return null;
  if (isc.sistema === "02") return positivoHasta5(isc.montoUnitario) ? null : "El monto por unidad es un número mayor que cero, con hasta 5 decimales.";
  if (!positivoHasta5(isc.tasa)) return "La tasa es un porcentaje mayor que cero, con hasta 5 decimales.";
  if (isc.sistema === "03" && !positivoHasta5(isc.basePvp)) return "El precio de venta al público sugerido es un número mayor que cero, con hasta 5 decimales.";
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
export function incompatibilidadDeTributos(lineas: LineaTributos[], operacion = "0101"): string | null {
  if (operacion === "0113" && lineas.some((l) => l.icbper)) return "3240 - El ICBPER no aplica al NRUS (0113): quite las bolsas de plástico de las líneas o emita con venta interna.";
  const hayIvap = lineas.some((l) => ES_IVAP(l.tipoAfectacionIgv));
  if (hayIvap && lineas.some((l) => !ES_IVAP(l.tipoAfectacionIgv)))
    return "Un comprobante con IVAP (17) lleva todos sus ítems con IVAP: no se mezcla con gravado, exonerado, inafecto ni gratuitas.";
  const n = (l: LineaTributos) => `«${l.descripcion.trim() || "ítem"}»`;
  for (const l of lineas) {
    if (ES_IVAP(l.tipoAfectacionIgv) && (l.isc.sistema !== "" || l.icbper)) return `El ítem ${n(l)} es IVAP: no lleva ISC ni ICBPER.`;
    if (l.icbper) {
      if (l.unidad !== "NIU") return `El ítem ${n(l)} lleva ICBPER: las bolsas van en unidades (NIU), una bolsa por unidad.`;
      const c = l.cantidad ?? 0;
      if (!Number.isInteger(c) || c < 1 || c > 99999) return `El ítem ${n(l)} lleva ICBPER: la cantidad es el número de bolsas, un entero de 1 a 99999.`;
    }
  }
  return null;
}

/**
 * Lo que es el precio de la línea según su afectación (#117): con IGV en la gravada, con IVAP en la IVAP, y sin impuesto en la exonerada, la inafecta, la gratuita (valor
 * referencial) y la exportación. «con IGV» en una exonerada inducía a cargar el precio mal. `""`: líneas de afectaciones distintas, el encabezado no afirma una.
 */
export function etiquetaDelPrecio(afectacion: string): string {
  if (afectacion === "") return "Precio unit.";
  if (afectacion === "10") return "Precio unit. (con IGV)";
  if (afectacion === "17") return "Precio unit. (con IVAP)";
  return "Precio unit. (sin IGV)";
}
