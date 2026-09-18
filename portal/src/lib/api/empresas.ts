import { backendFetch } from "./client";
import { tenantHeaders } from "./tenant";

export type Entorno = "BETA" | "PRODUCCION";

export type Empresa = {
  id: string;
  ruc: string;
  razon_social: string;
  entorno: Entorno;
  tiene_certificado: boolean;
  tiene_credenciales_sol: boolean;
  tiene_domicilio?: boolean;
};

/** Domicilio fiscal del emisor (RegistrationAddress del XML): ubigeo del catálogo 13 y dirección en una línea. */
export type Domicilio = {
  ubigeo: string;
  direccion: string;
  urbanizacion: string | null;
  distrito: string | null;
  provincia: string | null;
  departamento: string | null;
  codigo_establecimiento: string;
};

export type EmpresaDetalle = {
  id: string;
  ruc: string;
  razon_social: string;
  entorno: Entorno;
  tiene_credenciales_sol: boolean;
  certificado_vigencia_hasta: string | null;
  domicilio?: Domicilio | null;
  cuenta_detracciones?: string | null;
};

export function listarEmpresas(access: string) {
  return backendFetch<Empresa[]>("/v1/empresas", { headers: { Authorization: `Bearer ${access}` } });
}

export function crearEmpresa(access: string, body: { ruc: string; razon_social: string; entorno: Entorno }) {
  return backendFetch<Empresa>("/v1/empresas", {
    method: "POST",
    body,
    headers: { Authorization: `Bearer ${access}` },
  });
}

export function obtenerEmpresaActual(access: string, empresaId: string) {
  return backendFetch<EmpresaDetalle>("/v1/empresa", { headers: tenantHeaders(access, empresaId) });
}
