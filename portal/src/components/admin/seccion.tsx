import { TrayIcon } from "@phosphor-icons/react/dist/ssr";
import { PESO_ICONO, type IconoComponente } from "khipu-design-system/lib/iconos";
import { CabeceraSeccion } from "khipu-design-system/patrones/cabecera-seccion";
import { CONTENEDOR_TABLA, FilaVacia } from "khipu-design-system/patrones/pie-tabla";
import type { ReactNode } from "react";
import { TARJETA } from "@/lib/estilos";
import { cn } from "@/lib/utils";

/** Fila de cabecera de las tablas de detalle del backoffice: la del design system, sin banda propia (cada fila del cuerpo ya es una banda). */
export const CABECERA_FILA = "hover:bg-transparent";

/**
 * Una sección de una página de detalle del backoffice: una tarjeta con su cabecera (`CabeceraSeccion`, el título dentro de la tarjeta, como en el panel del
 * cliente) y su contenido, que desplaza si no cabe. Con `tabla`, la tarjeta es el contenedor de tabla del design system (`CONTENEDOR_TABLA`). `derecha` va en
 * la cabecera (una acción, un chip). El título lleva el `id` que nombra la sección.
 */
export function Seccion({
  titulo,
  id,
  icon,
  tabla = false,
  derecha,
  children,
}: {
  titulo: string;
  id: string;
  icon: IconoComponente;
  tabla?: boolean;
  derecha?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section aria-labelledby={id} className={cn(tabla ? CONTENEDOR_TABLA : TARJETA, "min-w-0")}>
      <CabeceraSeccion icon={icon} titulo={<span id={id}>{titulo}</span>} derecha={derecha} />
      {tabla ? children : <div className="overflow-x-auto">{children}</div>}
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
