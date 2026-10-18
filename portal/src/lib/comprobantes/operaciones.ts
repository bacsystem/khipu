/**
 * #311: tipos de operación del catálogo 51 que ofrece «Nuevo comprobante». Hoy la venta interna y la exportación (0200–0208 menos 0202/0205, que piden los datos del
 * huésped); los de detracción y percepción (1001–1004, 2001) los agregan #288 y #297. Lo que el backend no emite no se ofrece: el selector promete lo que se puede cumplir.
 */
export type Operacion = { codigo: string; etiqueta: string; grupo: "Venta interna" | "Exportación" };

export const OPERACIONES: Operacion[] = [
  { codigo: "0101", etiqueta: "Venta interna", grupo: "Venta interna" },
  { codigo: "0200", etiqueta: "Exportación de bienes", grupo: "Exportación" },
  { codigo: "0201", etiqueta: "Exportación de servicios realizados íntegramente en el país", grupo: "Exportación" },
  { codigo: "0203", etiqueta: "Exportación de servicios: transporte de navieras", grupo: "Exportación" },
  { codigo: "0204", etiqueta: "Exportación de servicios a naves y aeronaves de bandera extranjera", grupo: "Exportación" },
  { codigo: "0206", etiqueta: "Exportación de servicios complementarios al transporte de carga", grupo: "Exportación" },
  { codigo: "0207", etiqueta: "Exportación de servicios: energía eléctrica a sujetos domiciliados en ZED", grupo: "Exportación" },
  { codigo: "0208", etiqueta: "Exportación de servicios realizados parcialmente en el extranjero", grupo: "Exportación" },
];

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
export function errorDeExportacion(d: DatosDeExportacion, boleta: boolean): string | null {
  if (!esExportacion(d.operacion)) return null;
  if (!boleta && !d.paisDelComprador) return "El cliente del exterior lleva su país (obligatorio en una factura de exportación).";
  if (EXIGE_PAIS_DE_USO.has(d.operacion) && !d.paisDeUso) return "Una exportación de servicios de este tipo indica el país donde se usa o aprovecha el servicio (3098).";
  if (EXIGE_PAIS_DE_USO.has(d.operacion) && d.paisDeUso === "PE") return "El país de uso del servicio no puede ser Perú (3099).";
  return null;
}
