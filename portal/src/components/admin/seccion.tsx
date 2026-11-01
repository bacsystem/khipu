import { TrayIcon } from "@phosphor-icons/react/dist/ssr";
import { PESO_ICONO } from "khipu-design-system/lib/iconos";
import { CONTENEDOR_TABLA, FilaVacia } from "khipu-design-system/patrones/pie-tabla";
import type { ReactNode } from "react";
import { TARJETA } from "@/lib/estilos";
import { cn } from "@/lib/utils";

/** Fila de cabecera de las tablas de detalle del backoffice: la del design system, sin banda propia (cada fila del cuerpo ya es una banda). */
export const CABECERA_FILA = "hover:bg-transparent";

/**
 * Una sección de una página de detalle del backoffice: un título y su contenido en una tarjeta que desplaza si no cabe.
 * Con `tabla`, la tarjeta es el contenedor de tabla del design system (`CONTENEDOR_TABLA`).
 */
export function Seccion({ titulo, id, tabla = false, children }: { titulo: string; id: string; tabla?: boolean; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="grid min-w-0 gap-2">
      <h2 id={id} className="font-heading text-titulo">
        {titulo}
      </h2>
      <div className={tabla ? CONTENEDOR_TABLA : cn(TARJETA, "overflow-x-auto")}>{children}</div>
    </section>
  );
}

/** La fila de una tabla de detalle sin datos: lo dice, en vez de dejar la tabla en blanco. */
export function Vacio({ columnas, texto }: { columnas: number; texto: string }) {
  return (
    <FilaVacia columnas={columnas} icon={<TrayIcon weight={PESO_ICONO} className="size-6" />}>
      {texto}
    </FilaVacia>
  );
}
