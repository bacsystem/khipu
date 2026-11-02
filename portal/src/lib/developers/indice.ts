/**
 * El buscador de la documentación (#26), sin datos: solo el tipo de lo que se busca y cómo se busca. Las entradas las arma el servidor (`entradas.ts`, que importa los casos de la guía
 * y las tablas de errores) y se las pasa al buscador como propiedad: así el cliente no carga cien kilobytes de JSON de ejemplo en cada página de `/developers`.
 */

/** Una sección, un caso de emisión, un estado, un código HTTP, un código de error o una fila de límites, con la página y el ancla a la que lleva. */
export type EntradaDeBusqueda = { id: string; titulo: string; seccion: string; href: string; detalle: string };

/** Minúsculas y sin tildes, para que «validacion» encuentre «VALIDACIÓN» y «boleta» a «Boleta». */
export function normalizar(texto: string): string {
  return texto.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();
}

/** Lo que se indexa de cada entrada ya normalizado, para no repetirlo en cada tecla. */
export type EntradaIndexada = { entrada: EntradaDeBusqueda; titulo: string; pajar: string };

export function indexar(entradas: readonly EntradaDeBusqueda[]): EntradaIndexada[] {
  return entradas.map((entrada) => ({ entrada, titulo: normalizar(entrada.titulo), pajar: normalizar(`${entrada.titulo} ${entrada.seccion} ${entrada.detalle}`) }));
}

/**
 * Lo que coincide con lo tecleado: todas las palabras (en cualquier orden) tienen que estar en el título, la sección o el detalle. Primero lo que se llama exactamente así, luego lo que
 * lleva la frase en el título y al final el resto; dentro de cada grupo conserva el orden de las entradas. Devuelve a lo más {@code maximo}.
 */
export function buscarEnLaDocumentacion(texto: string, indice: readonly EntradaIndexada[], maximo = 8): EntradaDeBusqueda[] {
  const palabras = normalizar(texto).split(/\s+/).filter(Boolean);
  if (palabras.length === 0) return [];
  const frase = palabras.join(" ");
  const coincidencias = indice.filter((e) => palabras.every((p) => e.pajar.includes(p)));
  const exactas = coincidencias.filter((e) => e.titulo === frase);
  const enElTitulo = coincidencias.filter((e) => e.titulo !== frase && e.titulo.includes(frase));
  const resto = coincidencias.filter((e) => !e.titulo.includes(frase));
  return [...exactas, ...enElTitulo, ...resto].slice(0, maximo).map((e) => e.entrada);
}
