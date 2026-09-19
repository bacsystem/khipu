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
  /** Nombre comercial del emisor (cac:PartyName); null si no lo configuró. */
  nombre_comercial?: string | null;
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

export type PlantillaPdf = "clasico" | "moderno" | "sutil" | "corporativo" | "gris";

/** Diseño de la representación impresa (`GET/PUT /v1/empresa/personalizacion-pdf`); el logo va por `/v1/empresa/logo`. */
export type PersonalizacionPdf = {
  plantilla: PlantillaPdf;
  color_primario: string;
  tiene_logo: boolean;
  pie_de_pagina: string | null;
  observaciones_por_defecto: string | null;
};

export const PLANTILLAS_PDF: Array<{ id: PlantillaPdf; nombre: string; descripcion: string }> = [
  { id: "clasico", nombre: "Clásico", descripcion: "Formal, bordes definidos" },
  { id: "moderno", nombre: "Moderno", descripcion: "Aireado, con acento de color" },
  { id: "sutil", nombre: "Sutil", descripcion: "Minimalista, líneas finas" },
  { id: "corporativo", nombre: "Corporativo", descripcion: "Cabecera y tabla en color" },
  { id: "gris", nombre: "Gris", descripcion: "Monocromo, sin color" },
];

export function obtenerPersonalizacionPdf(access: string, empresaId: string) {
  return backendFetch<PersonalizacionPdf>("/v1/empresa/personalizacion-pdf", { headers: tenantHeaders(access, empresaId) });
}
