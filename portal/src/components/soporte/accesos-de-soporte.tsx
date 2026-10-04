import { EyeIcon } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { AccesoDeSoporte } from "@/lib/api/accesos-de-soporte";
import { CABECERA_TABLA } from "@/lib/estilos";
import { formatearFechaHora } from "@/lib/formato";
import { messages } from "@/lib/messages";

const t = messages.soporte.accesos;

/** La duración como la lee una persona: en minutos si es un número redondo de minutos, y en segundos si no. */
export function duracionEnPalabras(segundos: number): string {
  return segundos % 60 === 0 ? t.minutos.replace("{n}", String(segundos / 60)) : t.segundos.replace("{n}", String(segundos));
}

/**
 * El historial de accesos de soporte a la cuenta (#184): cuándo, como qué usuario y por cuánto tiempo como máximo. El cliente tiene derecho a ver cada acceso,
 * aunque el registro no se entienda del todo: se muestra con su fecha y un «sin detalle», no se esconde.
 */
export function AccesosDeSoporte({ accesos }: { accesos: AccesoDeSoporte[] }) {
  return (
    <div className="overflow-hidden rounded-xl border border-border/90 bg-card shadow-2xs" data-testid="accesos-de-soporte">
      <Table>
        <TableHeader>
          <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{t.columnas.cuando}</TableHead>
            <TableHead className={CABECERA_TABLA}>{t.columnas.usuario}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4`}>{t.columnas.duracion}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-[13px]">
          {accesos.map((a, i) => (
            <TableRow key={`${a.ocurrido_en}-${i}`} className="border-b border-border/60">
              <TableCell className="py-2 pr-3 pl-4">{formatearFechaHora(a.ocurrido_en)}</TableCell>
              <TableCell className="px-3 py-2 font-mono text-[12px]">{a.usuario ?? <span className="font-sans text-muted-foreground">{t.sinDetalle}</span>}</TableCell>
              <TableCell className="py-2 pr-4 pl-3 text-[12px] text-foreground/80">
                {a.duracion_segundos === undefined ? <span className="text-muted-foreground">{t.sinDetalle}</span> : duracionEnPalabras(a.duracion_segundos)}
              </TableCell>
            </TableRow>
          ))}
          {accesos.length === 0 ? (
            <TableRow className="hover:bg-transparent">
              <TableCell colSpan={3} className="py-14 text-center">
                <div className="flex flex-col items-center gap-2 text-muted-foreground">
                  <EyeIcon className="size-6" />
                  <p className="text-sm">{t.vacio}</p>
                </div>
              </TableCell>
            </TableRow>
          ) : null}
        </TableBody>
      </Table>
    </div>
  );
}
