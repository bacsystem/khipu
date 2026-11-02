/** Una sección de un texto legal: párrafos y, si hace falta, una lista. Texto plano a propósito: se edita sin saber React. */
export type SeccionLegal = {
  titulo: string;
  parrafos: string[];
  lista?: string[];
  /** Párrafos que van después de la lista. */
  cierre?: string[];
};

export type DocumentoLegal = {
  titulo: string;
  /** Fecha AAAA-MM-DD de la versión. */
  version: string;
  /** Una o dos frases para la parte de arriba de la página. */
  resumen: string;
  secciones: SeccionLegal[];
};
