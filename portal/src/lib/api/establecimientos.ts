import { backendFetch } from "./client";
import type { Domicilio } from "./empresas";
import { tenantHeaders } from "./tenant";

/** Punto desde el que emite la empresa: el `0000` es el domicilio fiscal (`principal`), el resto anexos declarados en el RUC. */
export type Establecimiento = {
  codigo: string;
  nombre: string;
  domicilio: Domicilio;
  activo: boolean;
  principal: boolean;
};

export function listarEstablecimientos(access: string, empresaId: string) {
  return backendFetch<Establecimiento[]>("/v1/empresa/establecimientos", { headers: tenantHeaders(access, empresaId) });
}
