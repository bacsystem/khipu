import { interpolar, type Mensajes } from "@/i18n/mensajes";
import es from "@/messages/es.json";

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

/** Las guías de remisión (catálogo 01: 09 y 31) como las ofrece el selector: «09 · Guía de remisión remitente», en el idioma de `messages` (#25). */
export const tiposDeGuia = (messages: Mensajes = es): { codigo: string; etiqueta: string }[] =>
  (["09", "31"] as const).map((codigo) => ({ codigo, etiqueta: `${codigo} · ${messages.documentos.tipos[codigo]}` }));
export const TIPOS_DE_GUIA = tiposDeGuia();

const DOCUMENTOS = [
  { codigo: "04" },
  { codigo: "05" },
  { codigo: "06", soloFactura: true },
  { codigo: "07", soloFactura: true },
  { codigo: "08" },
  { codigo: "09" },
  { codigo: "99" },
] as const;

/** Catálogo 12 que acepta la API; los tipos 06 y 07 solo en factura (4009 en una boleta). */
export const tiposDeDocumento = (messages: Mensajes = es): { codigo: string; etiqueta: string; soloFactura?: boolean }[] =>
  DOCUMENTOS.map((d) => ({ ...d, etiqueta: `${d.codigo} · ${messages.documentos.relacionados[d.codigo]}` }));
export const TIPOS_DE_DOCUMENTO = tiposDeDocumento();

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
export function errorDeReferencias(r: ReferenciasForm, boleta: boolean, messages: Mensajes = es): string | null {
  const t = messages.comprobantes.validacion.referencias;
  const oc = r.ordenCompra.trim();
  if (!boleta && oc !== "" && (oc.length > MAX_ORDEN_COMPRA || /[\u0000-\u001f\u007f-\u009f]/u.test(oc))) return interpolar(t.ordenCompra, { max: MAX_ORDEN_COMPRA });
  for (const g of r.guias) {
    if (g.numero.trim() === "") return t.guiaSinNumero;
    if (!numeroDeGuiaValido(g.numero)) return interpolar(t.guiaFormato, { numero: g.numero.trim() });
  }
  if (repetidos(r.guias)) return t.guiaRepetida;
  for (const d of r.documentos) {
    const n = d.numero.trim();
    if (n === "") return t.documentoSinNumero;
    if (n.length > MAX_DOCUMENTO || !SIN_ESPACIOS_NI_CONTROL.test(n)) return interpolar(t.documentoFormato, { numero: n, max: MAX_DOCUMENTO });
    if (boleta && DOCUMENTOS.some((x) => x.codigo === d.tipo && "soloFactura" in x)) return interpolar(t.documentoSoloFactura, { tipo: d.tipo });
  }
  if (repetidos(r.documentos)) return t.documentoRepetido;
  if (r.observaciones.length > MAX_OBSERVACIONES) return interpolar(t.observaciones, { max: MAX_OBSERVACIONES });
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
