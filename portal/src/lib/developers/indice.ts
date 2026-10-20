import { CODIGOS, ESTADOS, HTTP } from "@/components/developers/errores-datos";
import { CASOS } from "@/components/developers/guia/casos-emision";

/** Lo que el buscador de la documentación encuentra: una sección, un caso de emisión, un estado, un código HTTP o un código de error, con la página y el ancla a la que lleva. */
export type EntradaDeBusqueda = { id: string; titulo: string; seccion: string; href: string; detalle: string };

const SECCIONES_DE_LA_GUIA: Array<[id: string, titulo: string]> = [
  ["inicio-rapido", "Inicio rápido"],
  ["anatomia", "Anatomía de un comprobante"],
  ["casos", "Casos de emisión"],
  ["respuesta", "Cómo leer la respuesta"],
  ["errores", "Errores"],
  ["webhooks", "Webhooks"],
  ["buenas-practicas", "Buenas prácticas"],
];

const SECCIONES_DE_ERRORES: Array<[id: string, titulo: string]> = [
  ["estados", "Estados del comprobante"],
  ["http", "Códigos HTTP"],
  ["codigos", "Códigos de error de khipu"],
  ["sunat", "Códigos de SUNAT"],
];

const SECCIONES_DE_ENTORNOS: Array<[id: string, titulo: string, detalle: string]> = [
  ["entornos", "BETA y producción", "entorno beta produccion e-beta homologación credenciales SOL MODDATOS"],
  ["limites", "Límites y cuotas", "límite tope cuota máximo ítems 999 página plan LIMITE_PLAN 429 rate"],
  ["reintentos", "Reintentos y vigencias", "reintento vigencia idempotency-key webhook ticket plazo baja 24 horas"],
  ["pendiente", "Lo que aún no está publicado", "IPs de salida SLA SDK conectores"],
];

/** Todo lo que se busca, en el orden en que se ofrece. Las páginas de la documentación de las que sale son las mismas que la muestran (los datos de errores están en su propio módulo). */
export const ENTRADAS: readonly EntradaDeBusqueda[] = [
  ...SECCIONES_DE_LA_GUIA.map(([id, titulo]) => ({ id: `guia-${id}`, titulo, seccion: "Guía de emisión", href: `/developers/guia#${id}`, detalle: "" })),
  ...CASOS.map((c) => ({ id: `caso-${c.id}`, titulo: c.titulo, seccion: "Guía · caso de emisión", href: `/developers/guia#${c.id}`, detalle: c.cuando })),
  ...SECCIONES_DE_ENTORNOS.map(([id, titulo, detalle]) => ({ id: `entorno-${id}`, titulo, seccion: "Entornos y límites", href: `/developers/entornos#${id}`, detalle })),
  ...SECCIONES_DE_ERRORES.map(([id, titulo]) => ({ id: `errores-${id}`, titulo, seccion: "Errores y estados", href: `/developers/errores#${id}`, detalle: "" })),
  ...ESTADOS.map(([estado, significado]) => ({ id: `estado-${estado}`, titulo: estado, seccion: "Estado del comprobante", href: "/developers/errores#estados", detalle: significado })),
  ...HTTP.map(([http, cuando]) => ({ id: `http-${http}`, titulo: http, seccion: "Código HTTP", href: "/developers/errores#http", detalle: cuando })),
  ...CODIGOS.map(([codigo, http, significado]) => ({ id: `codigo-${codigo}`, titulo: codigo, seccion: `Código de error · HTTP ${http}`, href: "/developers/errores#codigos", detalle: significado })),
];

/** Minúsculas y sin tildes, para que «validacion» encuentre «VALIDACIÓN» y «boleta» a «Boleta». */
export function normalizar(texto: string): string {
  return texto.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();
}

/**
 * Lo que coincide con lo tecleado: todas las palabras (en cualquier orden) tienen que estar en el título, la sección o el detalle. Lo que tiene la frase en el título va primero, y el
 * resto conserva el orden de {@link ENTRADAS}. Devuelve a lo más {@code maximo}.
 */
export function buscarEnLaDocumentacion(texto: string, maximo = 8, entradas: readonly EntradaDeBusqueda[] = ENTRADAS): EntradaDeBusqueda[] {
  const palabras = normalizar(texto).split(/\s+/).filter(Boolean);
  if (palabras.length === 0) return [];
  const frase = palabras.join(" ");
  const coincidencias = entradas.filter((e) => {
    const pajar = normalizar(`${e.titulo} ${e.seccion} ${e.detalle}`);
    return palabras.every((p) => pajar.includes(p));
  });
  const enElTitulo = coincidencias.filter((e) => normalizar(e.titulo).includes(frase));
  const resto = coincidencias.filter((e) => !enElTitulo.includes(e));
  return [...enElTitulo, ...resto].slice(0, maximo);
}
