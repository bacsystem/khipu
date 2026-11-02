import { SkeletonTabla } from "khipu-design-system/feedback/skeleton";
import { CONTENEDOR_PAGINA } from "@/lib/estilos";

/** Mientras el servidor trae la lista: la forma de la tabla, no una página en blanco. */
export default function Cargando() {
  return (
    <div className={CONTENEDOR_PAGINA}>
      <SkeletonTabla filas={8} columnas={5} />
    </div>
  );
}
