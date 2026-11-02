"use client";

import { PencilSimpleIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import type { ReactNode } from "react";
import { Card } from "khipu-design-system/ui/card";
import { Button } from "khipu-design-system/ui/button";
import { AYUDA_CAMPO } from "@/lib/estilos";
import { cn } from "@/lib/utils";

/**
 * #557: el cliente del comprobante, una vez elegido de los guardados o resuelto por su documento. Dice quién es (nombre, documento, dirección), lo que respondió el padrón
 * de SUNAT (`estado`) y ofrece «Editar datos» (despliega los campos de siempre debajo) y «Cambiar» (vuelve al campo «Cliente»).
 */
export function TarjetaDeCliente({
  nombre,
  documento,
  direccion,
  estado,
  editando,
  idDatos,
  onEditar,
  onCambiar,
}: {
  nombre: string;
  /** «RUC 20100066603», «DNI 12345678», «Sin documento». */
  documento: string;
  direccion?: string;
  /** El estado de la consulta al padrón (`EstadoConsultaRuc`), si el documento es un RUC. */
  estado?: ReactNode;
  editando: boolean;
  /** El `id` de los campos que «Editar datos» despliega (para `aria-controls`). */
  idDatos: string;
  onEditar: () => void;
  onCambiar: () => void;
}) {
  const t = useMessages().comprobantes.formulario;
  return (
    <Card size="sm" className="gap-2 border-border bg-muted/40 px-3 shadow-none" data-testid="tarjeta-cliente">
      <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1">
        <div className="grid min-w-0 gap-0.5">
          <span className={cn("truncate text-control font-semibold", nombre.trim() === "" && "font-normal text-muted-foreground")} data-testid="tarjeta-cliente-nombre">
            {nombre.trim() === "" ? t.clienteSinNombre : nombre}
          </span>
          <span className={cn(AYUDA_CAMPO, "font-mono tabular-nums")} data-testid="tarjeta-cliente-documento">
            {documento}
          </span>
          {direccion ? (
            <span className={cn(AYUDA_CAMPO, "truncate")} data-testid="tarjeta-cliente-direccion">
              {direccion}
            </span>
          ) : null}
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <Button type="button" variant="ghost" size="sm" onClick={onEditar} aria-expanded={editando} aria-controls={editando ? idDatos : undefined}>
            <PencilSimpleIcon weight="bold" />
            {t.editarDatosCliente}
          </Button>
          <Button type="button" variant="outline" size="sm" onClick={onCambiar}>
            {t.cambiarCliente}
          </Button>
        </div>
      </div>
      {estado}
    </Card>
  );
}
