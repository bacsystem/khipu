/** Una entrada de un catálogo de SUNAT tal como la sirve `GET /v1/catalogos/{id}`. */
export type EntradaCatalogo = { codigo: string; descripcion: string; extra?: Record<string, string> };

/**
 * #308: el catálogo 03 es la rec 20 completa (más de 2000 códigos, contra los que el backend valida); el selector ofrece las que el backend marca «Más usada». Si ninguna viene marcada, la
 * lista tal cual.
 */
export function unidadesDelSelector(entradas: EntradaCatalogo[]): EntradaCatalogo[] {
  const masUsadas = entradas.filter((e) => e.extra?.["Más usada"]);
  return masUsadas.length ? masUsadas : entradas;
}
