/**
 * Recetas de clases compartidas: vienen de khipu-design-system (`lib/estilos`) tal cual. Este archivo es solo la fachada
 * para que el código del portal siga importando `@/lib/estilos`; una receta nueva o un cambio se hace en el DS y se trae
 * subiendo la versión del paquete.
 *
 * Todos los controles miden lo mismo: `h-9` (36 px), la altura única de docs/design-system.md §4 del design system. Nunca
 * `cn(RECETA, "h-N")`: sobrescribir la altura a mano es lo que hace que dos controles de la misma fila midan distinto
 * (lo vigila `alturas.test.ts`). Los alias obsoletos del DS (`CAMPO_FILTRO`, `BOTON_*_PIE`, `POPUP_LISTA`) también llegan
 * por aquí: el portal usa las recetas sin sufijo.
 */
export * from "khipu-design-system/lib/estilos";
