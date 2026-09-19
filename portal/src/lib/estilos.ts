/** Recetas de clases compartidas por formularios y tarjetas del portal (evita copiar la misma cadena en cada componente). */

export const CAMPO =
  "h-10 w-full rounded-lg border border-border bg-muted px-3 text-sm text-foreground transition-colors outline-none placeholder:text-muted-foreground/70 focus:border-ring focus:bg-card focus:ring-3 focus:ring-ring/30 disabled:cursor-not-allowed disabled:opacity-60";
export const ETIQUETA_CAMPO = "text-[12px] font-medium text-foreground";
export const AYUDA_CAMPO = "font-mono text-[11px] text-muted-foreground";

export const TARJETA = "rounded-xl border border-border bg-card shadow-2xs";
export const TITULO_SECCION = "flex items-center gap-1.5 text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase";
export const ETIQUETA_DATO = "block text-[11px] font-medium tracking-wider text-muted-foreground/80 uppercase";

export const BOTON_PRIMARIO =
  "inline-flex h-10 items-center justify-center gap-1.5 rounded-lg bg-primary px-3.5 text-sm font-semibold text-primary-foreground shadow-xs transition-all hover:opacity-95 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-60";
/** Acción irreversible (dar de baja, revocar): mismo trazo que el primario en `destructive`. */
export const BOTON_DESTRUCTIVO =
  "inline-flex h-10 items-center justify-center gap-1.5 rounded-lg bg-destructive px-3.5 text-sm font-semibold text-destructive-foreground shadow-xs transition-all hover:opacity-95 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-60";
export const BOTON_SECUNDARIO =
  "inline-flex h-10 items-center justify-center gap-1.5 rounded-lg border border-border bg-card px-3.5 text-sm font-medium text-foreground/80 shadow-2xs transition-colors hover:bg-muted hover:text-foreground disabled:cursor-not-allowed disabled:opacity-60";
