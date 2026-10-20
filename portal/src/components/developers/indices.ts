import { CASOS } from "./guia/casos-emision";

/**
 * Los índices laterales de la guía y de la página de errores, aparte de sus páginas (que no pueden exportar nada más que su componente) para que el buscador de la documentación
 * (#26) lleve a las mismas anclas que ellas muestran: una prueba renderiza las páginas y comprueba que cada ancla exista.
 */
export const INDICE_GUIA = [
  { id: "inicio-rapido", label: "Inicio rápido" },
  { id: "anatomia", label: "Anatomía de un comprobante" },
  { id: "casos", label: "Casos de emisión" },
  ...CASOS.map((c) => ({ id: c.id, label: `· ${c.titulo}` })),
  { id: "respuesta", label: "Cómo leer la respuesta" },
  { id: "errores", label: "Errores" },
  { id: "webhooks", label: "Webhooks" },
  { id: "buenas-practicas", label: "Buenas prácticas" },
];

export const INDICE_ERRORES = [
  { id: "estados", label: "Estados del comprobante" },
  { id: "http", label: "Códigos HTTP" },
  { id: "codigos", label: "Códigos de error de khipu" },
  { id: "sunat", label: "Códigos de SUNAT" },
];
