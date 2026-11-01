import { EyeIcon } from "@phosphor-icons/react/dist/ssr";
import { PESO_ICONO } from "khipu-design-system/lib/iconos";
import { useLocale, useMessages } from "next-intl";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "khipu-design-system/ui/table";
import type { AccesoDeSoporte } from "@/lib/api/accesos-de-soporte";
import { CABECERA_TABLA, TARJETA } from "@/lib/estilos";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import { formatearFechaHora } from "@/lib/formato";
import { cn } from "@/lib/utils";

/** La duración como la lee una persona: en minutos si es un número redondo de minutos, y en segundos si no. */
export function duracionEnPalabras(segundos: number, messages: Mensajes): string {
  const t = messages.soporte.accesos;
  return segundos % 60 === 0 ? interpolar(t.minutos, { n: segundos / 60 }) : interpolar(t.segundos, { n: segundos });
}

/**
 * El historial de accesos de soporte a la cuenta (#184): cuándo, como qué usuario y por cuánto tiempo como máximo. El cliente tiene derecho a ver cada acceso,
 * aunque el registro no se entienda del todo: se muestra con su fecha y un «sin detalle», no se esconde.
 */
export function AccesosDeSoporte({ accesos }: { accesos: AccesoDeSoporte[] }) {
  const messages = useMessages();
  const locale = useLocale();
  const t = messages.soporte.accesos;
  return (
    <div className={cn(TARJETA, "overflow-hidden")} data-testid="accesos-de-soporte">
      <Table>
        <TableHeader>
          <TableRow className="border-b border-border/80 bg-muted hover:bg-muted">
            <TableHead className={`${CABECERA_TABLA} pl-4`}>{t.columnas.cuando}</TableHead>
            <TableHead className={CABECERA_TABLA}>{t.columnas.usuario}</TableHead>
            <TableHead className={`${CABECERA_TABLA} pr-4`}>{t.columnas.duracion}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="text-control">
          {accesos.map((a, i) => (
            <TableRow key={`${a.ocurrido_en}-${i}`} className="border-b border-border/60">
              <TableCell className="py-2 pr-3 pl-4">{formatearFechaHora(a.ocurrido_en, locale)}</TableCell>
              <TableCell className="px-3 py-2 font-mono text-control">{a.usuario ?? <span className="font-sans text-muted-foreground">{t.sinDetalle}</span>}</TableCell>
              <TableCell className="py-2 pr-4 pl-3 text-control text-foreground/80">
                {a.duracion_segundos === undefined ? <span className="text-muted-foreground">{t.sinDetalle}</span> : duracionEnPalabras(a.duracion_segundos, messages)}
              </TableCell>
            </TableRow>
          ))}
          {accesos.length === 0 ? (
            <TableRow className="hover:bg-transparent">
              <TableCell colSpan={3} className="py-14 text-center">
                <div className="flex flex-col items-center gap-2 text-muted-foreground">
                  <EyeIcon weight={PESO_ICONO} className="size-6" />
                  <p className="text-cuerpo">{t.vacio}</p>
                </div>
              </TableCell>
            </TableRow>
          ) : null}
        </TableBody>
      </Table>
    </div>
  );
}
