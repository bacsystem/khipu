import type { Mensajes } from "@/i18n/mensajes";
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
  /** Si hay certificado cargado. Aparte de la vigencia: un certificado puede no traer fecha. */
  tiene_certificado: boolean;
  certificado_vigencia_hasta: string | null;
  domicilio?: Domicilio | null;
  cuenta_detracciones?: string | null;
  /** Nombre comercial del emisor (cac:PartyName); null si no lo configuró. */
  nombre_comercial?: string | null;
  /** Inscrita en el Padrón de Tasa Especial del IGV (restaurantes y hoteles, Ley 31556): emite con la tasa reducida. */
  padron_tasa_especial_igv?: boolean;
  /** #107: SUNAT rechazó las credenciales SOL; mientras esté, los envíos esperan a que se corrijan. Ausente si no. */
  credenciales_sol_rechazadas?: { desde: string; motivo: string } | null;
  /** #318: si cargó las credenciales API SUNAT (client_id y client_secret); nunca se devuelven. */
  tiene_credenciales_api_sunat?: boolean;
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

/** #318: cuerpo de `PUT /v1/empresa/credenciales-api-sunat` (lo manda el navegador por el proxy); `DELETE` en la misma ruta las quita. */
export type CredencialesApiSunat = { client_id: string; client_secret: string };
export const RUTA_CREDENCIALES_API_SUNAT = "/api/proxy/empresa/credenciales-api-sunat";

export type PlantillaPdf = "clasico" | "moderno" | "sutil" | "corporativo" | "gris";

/** Diseño de la representación impresa (`GET/PUT /v1/empresa/personalizacion-pdf`); el logo va por `/v1/empresa/logo`. */
export type PersonalizacionPdf = {
  plantilla: PlantillaPdf;
  color_primario: string;
  tiene_logo: boolean;
  pie_de_pagina: string | null;
  observaciones_por_defecto: string | null;
};

const IDS_PLANTILLAS_PDF: PlantillaPdf[] = ["clasico", "moderno", "sutil", "corporativo", "gris"];

/** Las plantillas del PDF con su nombre y descripción en el idioma de `messages` (#25). */
export function plantillasPdf(messages: Mensajes): Array<{ id: PlantillaPdf; nombre: string; descripcion: string }> {
  return IDS_PLANTILLAS_PDF.map((id) => ({ id, ...messages.empresa.pdf.plantillas[id] }));
}

export function obtenerPersonalizacionPdf(access: string, empresaId: string) {
  return backendFetch<PersonalizacionPdf>("/v1/empresa/personalizacion-pdf", { headers: tenantHeaders(access, empresaId) });
}

/**
 * #325: si la empresa envía automáticamente el comprobante aceptado a su cliente, y si el servidor tiene correo para hacerlo (`correo_disponible`: sin él no se puede activar
 * y la API responde 503 `CORREO_NO_CONFIGURADO`).
 */
export type PreferenciaDeEnvioAlCliente = { enviar_automaticamente: boolean; correo_disponible: boolean };

/** El cuerpo de `PUT /v1/empresa/envio-al-cliente`. */
export type CuerpoDeEnvioAlCliente = { enviar_automaticamente: boolean };

export function obtenerPreferenciaDeEnvioAlCliente(access: string, empresaId: string) {
  return backendFetch<PreferenciaDeEnvioAlCliente>("/v1/empresa/envio-al-cliente", { headers: tenantHeaders(access, empresaId) });
}
