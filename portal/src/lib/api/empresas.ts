import { backendFetch } from "./client";

export type Entorno = "BETA" | "PRODUCCION";

export type Empresa = {
  id: string;
  ruc: string;
  razon_social: string;
  entorno: Entorno;
  tiene_certificado: boolean;
  tiene_credenciales_sol: boolean;
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
