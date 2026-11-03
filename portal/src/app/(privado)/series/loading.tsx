import { SkeletonMetricas, SkeletonTabla } from "khipu-design-system/feedback/skeleton";
import { CONTENEDOR_PAGINA } from "@/lib/estilos";

/** Mientras el servidor trae las series: la fila de indicadores y la tabla, no una página en blanco. */
export default function Cargando() {
  return (
    <div className={CONTENEDOR_PAGINA}>
      <SkeletonMetricas />
      <SkeletonTabla filas={6} columnas={6} />
    </div>
  );
}
