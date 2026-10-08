/**
 * Recetas de clases compartidas (evita copiar la misma cadena en cada componente). Las de controles son las de
 * khipu-design-system v0.1.6 (`src/lib/estilos.ts`): si cambian allá, se traen aquí tal cual.
 *
 * Escala de alturas (docs/design-system.md §4 del design system), una receta por altura y contexto. Si un control necesita
 * otra altura, se usa la receta de esa altura: nunca `cn(RECETA, "h-N")`. Sobrescribirla a mano es lo que hace que dos
 * controles de la misma fila terminen midiendo distinto.
 */

// ── h-8 · chrome de página: top bar, barra de filtros, acciones sueltas en tarjetas, alertas o fichas ──
// La principal es negra (foreground) para no competir con los colores de estado; el índigo queda para confirmar formularios.
export const ACCION_PRINCIPAL =
  "inline-flex h-8 items-center gap-1.5 rounded-lg bg-foreground px-3 text-[12px] font-medium whitespace-nowrap text-background shadow-xs transition-colors hover:bg-foreground/90 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-60";
export const ACCION_SECUNDARIA =
  "inline-flex h-8 items-center gap-1.5 rounded-lg border border-border bg-card px-2.5 text-[12px] font-medium whitespace-nowrap text-foreground/80 shadow-2xs transition-colors hover:bg-muted hover:text-foreground disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-60";
// Select, buscador y refrescar de una barra de filtros: la misma altura que las acciones de arriba.
export const CONTROL_FILTRO = "h-8 rounded-lg border border-border bg-card text-[12px] font-medium text-foreground shadow-2xs";
// El aspecto de CAMPO a h-8: inputs y `<select>` nativos de una barra de filtros o de un formulario denso.
export const CAMPO_FILTRO =
  "h-8 w-full rounded-lg border border-border bg-muted px-3 text-sm text-foreground transition-colors outline-none placeholder:text-muted-foreground/70 focus:border-ring focus:bg-card focus:ring-3 focus:ring-ring/30 disabled:cursor-not-allowed disabled:opacity-60";
// Control segmentado de una barra de filtros: caja de h-8 con segmentos de h-6 (design system v0.1.6). `min-h-8` para que una lista
// larga se parta en filas en vez de desbordarse. El activo se marca con `aria-current="page"` (enlace a la vista actual) o `data-active`.
export const SEGMENTADO = "inline-flex min-h-8 w-fit flex-wrap items-center gap-1 rounded-lg border border-border/60 bg-secondary/80 p-0.5";
export const SEGMENTO =
  "inline-flex h-6 items-center gap-1.5 rounded-md px-3 text-[12px] font-medium whitespace-nowrap text-muted-foreground transition-colors outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/40 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 data-active:bg-card data-active:text-foreground data-active:shadow-2xs aria-[current=page]:bg-card aria-[current=page]:text-foreground aria-[current=page]:shadow-2xs";

// ── h-10 · formularios: el campo (también un `<select>` nativo de formulario) y su CTA ──
export const CAMPO =
  "h-10 w-full rounded-lg border border-border bg-muted px-3 text-sm text-foreground transition-colors outline-none placeholder:text-muted-foreground/70 focus:border-ring focus:bg-card focus:ring-3 focus:ring-ring/30 disabled:cursor-not-allowed disabled:opacity-60";
export const ETIQUETA_CAMPO = "text-[12px] font-medium text-foreground";
export const AYUDA_CAMPO = "font-mono text-[11px] text-muted-foreground";
/**
 * `<select>` nativo con el aspecto y la altura (h-8) de `ui/Input`: va en los formularios hechos con `FormField` (login, registro,
 * onboarding, alta asistida), que son todos de h-8. Junto a `CAMPO` va `CAMPO` y en una barra de filtros, `CAMPO_FILTRO`.
 */
export const SELECT_NATIVO =
  "h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50";

// disabled:pointer-events-none (igual que ui/button.tsx) es lo que de verdad evita que el :hover del mouse
// que se quedó sobre el botón le gane a disabled:opacity-60 en la cascada; cursor-not-allowed solo no basta.
export const BOTON_PRIMARIO =
  "inline-flex h-10 items-center justify-center gap-1.5 rounded-lg bg-primary px-3.5 text-sm font-semibold text-primary-foreground shadow-xs transition-all hover:opacity-95 active:scale-[0.99] disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-60";
export const BOTON_SECUNDARIO =
  "inline-flex h-10 items-center justify-center gap-1.5 rounded-lg border border-border bg-card px-3.5 text-sm font-medium text-foreground/80 shadow-2xs transition-colors hover:bg-muted hover:text-foreground disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-60";
/** Acción irreversible (dar de baja, revocar). */
export const BOTON_DESTRUCTIVO =
  "inline-flex h-10 items-center justify-center gap-1.5 rounded-lg bg-destructive px-3.5 text-sm font-semibold text-destructive-foreground shadow-xs transition-all hover:bg-destructive/90 active:scale-[0.99] disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-60";

// ── h-9 · solo el pie de un diálogo o panel lateral (la excepción documentada de §4) ──
// Los BOTON_* de arriba con la densidad de ese pie (h-9, 13 px); fuera de él no se usan.
export const BOTON_PRIMARIO_PIE =
  "inline-flex h-9 items-center justify-center gap-1.5 rounded-lg bg-primary px-3.5 text-[13px] font-semibold text-primary-foreground shadow-xs transition-all hover:opacity-95 active:scale-[0.99] disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-60";
export const BOTON_SECUNDARIO_PIE =
  "inline-flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border bg-card px-3.5 text-[13px] font-medium text-foreground/80 shadow-2xs transition-colors hover:bg-muted hover:text-foreground disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-60";
export const BOTON_DESTRUCTIVO_PIE =
  "inline-flex h-9 items-center justify-center gap-1.5 rounded-lg bg-destructive px-3.5 text-[13px] font-semibold text-destructive-foreground shadow-xs transition-all hover:bg-destructive/90 active:scale-[0.99] disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-60";

/**
 * Celda de cabecera de las tablas. Hoy solo la usa la tabla de cuentas del backoffice: las de api-keys, establecimientos, series y
 * comprobantes aún repiten la misma cadena, así que cambiar esta constante no cambia su estilo hasta migrarlas.
 */
export const CABECERA_TABLA = "h-auto px-3 py-2 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase";

export const TARJETA = "rounded-xl border border-border bg-card shadow-2xs";
export const TITULO_SECCION = "flex items-center gap-1.5 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase";
export const ETIQUETA_DATO = "block text-[11px] font-medium tracking-wider text-muted-foreground/80 uppercase";

// Entrada/salida de cualquier popup posicionado contra un disparador (Select, Combobox, Popover…).
export const ANIMACION_POPUP =
  "origin-(--transform-origin) duration-100 data-[side=bottom]:slide-in-from-top-2 data-[side=inline-end]:slide-in-from-left-2 data-[side=inline-start]:slide-in-from-right-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2 data-open:animate-in data-open:fade-in-0 data-open:zoom-in-95 data-closed:animate-out data-closed:fade-out-0 data-closed:zoom-out-95";
