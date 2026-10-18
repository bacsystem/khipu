/**
 * #300: datos adicionales de un comprobante que la API ya acepta y no cambian importes: orden de compra (`cac:OrderReference`, solo factura), guías de remisión (catálogo 01:
 * 09 y 31), otros documentos relacionados (catálogo 12) y observaciones (solo PDF). Las reglas replican a `Referencias`, `GuiaRelacionada` y `DocumentoRelacionado`.
 */
export type GuiaForm = { tipo: string; numero: string };
export type DocumentoForm = { tipo: string; numero: string };

export type ReferenciasForm = {
  ordenCompra: string;
  guias: GuiaForm[];
  documentos: DocumentoForm[];
  observaciones: string;
};

export const REFERENCIAS_VACIAS: ReferenciasForm = { ordenCompra: "", guias: [], documentos: [], observaciones: "" };

export const TIPOS_DE_GUIA: { codigo: string; etiqueta: string }[] = [
  { codigo: "09", etiqueta: "09 · Guía de remisión remitente" },
  { codigo: "31", etiqueta: "31 · Guía de remisión transportista" },
];

/** Catálogo 12 que acepta la API; los tipos 06 y 07 solo en factura (4009 en una boleta). */
export const TIPOS_DE_DOCUMENTO: { codigo: string; etiqueta: string; soloFactura?: boolean }[] = [
  { codigo: "04", etiqueta: "04 · Ticket de salida ENAPU" },
  { codigo: "05", etiqueta: "05 · Código SCOP" },
  { codigo: "06", etiqueta: "06 · Factura electrónica remitente", soloFactura: true },
  { codigo: "07", etiqueta: "07 · Guía de remisión remitente", soloFactura: true },
  { codigo: "08", etiqueta: "08 · Declaración de salida del depósito franco" },
  { codigo: "09", etiqueta: "09 · Declaración simplificada de importación" },
  { codigo: "99", etiqueta: "99 · Otros" },
];

export const MAX_ORDEN_COMPRA = 20;
export const MAX_DOCUMENTO = 30;
export const MAX_OBSERVACIONES = 1000;

/** Regla 4006 tal como la publica SUNAT (la misma expresión que `GuiaRelacionada.NUMERO`). */
const NUMERO_DE_GUIA = /^(T[A-Z0-9]{3}|[0-9]{4}|EG0[1-4]|EG07|G[0-9]{3}|V[A-Z0-9]{3})-[0-9]{1,8}$/;

export const numeroDeGuiaValido = (numero: string) => NUMERO_DE_GUIA.test(numero.trim());

const SIN_ESPACIOS_NI_CONTROL = /^[^\s\u0000-\u001f\u007f-\u009f]+$/u;

function repetidos(lista: Array<{ tipo: string; numero: string }>): boolean {
  const vistos = new Set(lista.map((d) => `${d.tipo}|${d.numero.trim()}`));
  return vistos.size < lista.length;
}

/** Por qué los datos adicionales no valen tal como están (la primera regla que falla), o `null`. */
export function errorDeReferencias(r: ReferenciasForm, boleta: boolean): string | null {
  const oc = r.ordenCompra.trim();
  if (!boleta && oc !== "" && (oc.length > MAX_ORDEN_COMPRA || /[\u0000-\u001f\u007f-\u009f]/u.test(oc))) return `La orden de compra tiene de 1 a ${MAX_ORDEN_COMPRA} caracteres, sin saltos de línea (4233).`;
  for (const g of r.guias) {
    if (g.numero.trim() === "") return "Falta el número de una guía de remisión (serie-número, por ejemplo T001-123).";
    if (!numeroDeGuiaValido(g.numero)) return `El número de guía «${g.numero.trim()}» no tiene el formato serie-número esperado, por ejemplo T001-123 o EG01-45 (4006).`;
  }
  if (repetidos(r.guias)) return "La misma guía de remisión aparece más de una vez (2364).";
  for (const d of r.documentos) {
    const n = d.numero.trim();
    if (n === "") return "Falta el número de un documento relacionado.";
    if (n.length > MAX_DOCUMENTO || !SIN_ESPACIOS_NI_CONTROL.test(n)) return `El número del documento relacionado «${n}» tiene de 1 a ${MAX_DOCUMENTO} caracteres sin espacios (4010).`;
    if (boleta && TIPOS_DE_DOCUMENTO.some((t) => t.codigo === d.tipo && t.soloFactura)) return `El documento relacionado de tipo ${d.tipo} solo se admite en una factura (4009).`;
  }
  if (repetidos(r.documentos)) return "El mismo documento relacionado aparece más de una vez (2365).";
  if (r.observaciones.length > MAX_OBSERVACIONES) return `Las observaciones admiten hasta ${MAX_OBSERVACIONES} caracteres.`;
  return null;
}

export type ReferenciasApi = {
  orden_compra?: string;
  guias?: GuiaForm[];
  documentos_relacionados?: DocumentoForm[];
  observaciones?: string;
};

/** Lo que va en el cuerpo de `POST /v1/facturas`: solo lo que se llenó; nada si algo no vale (el formulario ya lo dice y no emite). */
export function referenciasAApi(r: ReferenciasForm, boleta: boolean): ReferenciasApi {
  if (errorDeReferencias(r, boleta) !== null) return {};
  const oc = r.ordenCompra.trim();
  const obs = r.observaciones.trim();
  return {
    orden_compra: !boleta && oc !== "" ? oc : undefined,
    guias: r.guias.length ? r.guias.map((g) => ({ tipo: g.tipo, numero: g.numero.trim() })) : undefined,
    documentos_relacionados: r.documentos.length ? r.documentos.map((d) => ({ tipo: d.tipo, numero: d.numero.trim() })) : undefined,
    observaciones: obs !== "" ? obs : undefined,
  };
}

/** ¿Hay algo escrito? Decide si la sección se muestra abierta. */
export const hayReferencias = (r: ReferenciasForm): boolean => r.ordenCompra.trim() !== "" || r.guias.length > 0 || r.documentos.length > 0 || r.observaciones.trim() !== "";
