"use client";

import { useSyncExternalStore } from "react";
import { Alerta } from "@/components/feedback/alerta";
import { avisosDeEmision, leerAvisosDeEmisionCrudos } from "@/lib/comprobantes/avisos-de-emision";

const sinSuscripcion = () => () => {};

/**
 * Lo que SUNAT observará del comprobante recién emitido por el estado del RUC del comprador (4013 no activo, 4014 no habido, #378). El comprobante se emitió y SUNAT lo
 * acepta con esa observación: el aviso no es un error. Solo está en la ficha a la que se navegó al emitir (el backend no lo guarda): en el servidor y en la primera
 * pintura no hay nada, y sin avisos guardados el componente no pinta nada.
 */
export function AvisosDeEmision({ id }: { id: string }) {
  const crudos = useSyncExternalStore(sinSuscripcion, () => leerAvisosDeEmisionCrudos(id), () => "");
  const avisos = avisosDeEmision(crudos);
  if (avisos.length === 0) return null;
  return (
    <div className="flex flex-col gap-2" data-testid="avisos-de-emision">
      {avisos.map((a) => (
        <Alerta key={a.codigo} tono="aviso" titulo={`SUNAT observará este comprobante (${a.codigo})`}>
          <span data-testid={`aviso-emision-${a.codigo}`}>{a.mensaje}</span>
        </Alerta>
      ))}
    </div>
  );
}
