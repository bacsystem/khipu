import Link from "next/link";
import type { ReactNode } from "react";

/** El «Limpiar» de una barra de filtros, el mismo de comprobantes y guías: un enlace discreto a la ruta sin filtros (que conserva lo que no es filtro, como las filas por página). */
export function LimpiarFiltros({ href, children }: { href: string; children: ReactNode }) {
  return (
    <Link href={href} className="flex h-control items-center px-1 text-control text-muted-foreground underline-offset-2 hover:text-foreground hover:underline">
      {children}
    </Link>
  );
}
