import { backendFetch } from "./client";
import { tenantHeaders } from "./tenant";

/** Un endpoint de webhooks de la empresa (#19). El secreto de firma solo se entrega al crearlo o rotarlo; nunca vuelve en el listado. */
export type Webhook = {
  id: string;
  url: string;
  descripcion?: string | null;
  eventos: string[];
  /** Falso si lo apagó el usuario o khipu lo pausó por entregas fallidas seguidas (`pausado_motivo` lo explica). */
  activo: boolean;
  pausado_motivo?: string | null;
  fallos_consecutivos: number;
  creado_en: string;
};

export type EstadoEntrega = "PENDIENTE" | "EXITOSA" | "FALLIDA";

/** Una entrega de un evento a un endpoint, con lo que pasó en el último intento. */
export type EntregaWebhook = {
  id: string;
  evento: string;
  /** El comprobante del evento; ausente en la entrega de prueba. */
  comprobante_id?: string | null;
  estado: EstadoEntrega;
  intentos: number;
  /** Cuándo se reintenta; solo si está pendiente. */
  siguiente_intento?: string | null;
  ultimo_http?: number | null;
  ultimo_error?: string | null;
  ultimo_intento_en?: string | null;
  entregada_en?: string | null;
  creada_en: string;
};

/** La respuesta de crear un endpoint: el secreto de firma, que se muestra una sola vez. */
export type WebhookCreado = { webhook: Webhook; secreto: string };

export function listarWebhooks(access: string, empresaId: string) {
  return backendFetch<Webhook[]>("/v1/webhooks", { headers: tenantHeaders(access, empresaId) });
}
