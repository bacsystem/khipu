import { InboxIcon } from "lucide-react";
import type { ReactNode } from "react";
import { TableCell, TableRow } from "@/components/ui/table";

/** Fila de cabecera de las tablas de detalle del backoffice. */
export const CABECERA_FILA = "border-b border-border/80 bg-muted hover:bg-muted";

/** Una sección de una página de detalle del backoffice: un título y su contenido en una tarjeta que desplaza si no cabe. */
export function Seccion({ titulo, id, children }: { titulo: string; id: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="grid min-w-0 gap-2">
      <h2 id={id} className="font-heading text-base">
        {titulo}
      </h2>
      <div className="overflow-x-auto rounded-xl border border-border/90 bg-card shadow-2xs">{children}</div>
    </section>
  );
}

/** La fila de una tabla de detalle sin datos: lo dice, en vez de dejar la tabla en blanco. */
export function Vacio({ columnas, texto }: { columnas: number; texto: string }) {
  return (
    <TableRow className="hover:bg-transparent">
      <TableCell colSpan={columnas} className="py-10 text-center">
        <div className="flex flex-col items-center gap-2 text-muted-foreground">
          <InboxIcon className="size-5" />
          <p className="text-sm">{texto}</p>
        </div>
      </TableCell>
    </TableRow>
  );
}
