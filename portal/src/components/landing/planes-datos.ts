import type { LimiteAdmin } from "@/lib/api/admin-planes";
import type { PlanPublicado } from "@/lib/api/planes-publicados";

export type TarjetaDePlan = { nombre: string; para: string; mensual: number; docs: string; rucs: string; incluye: string[]; destacado: boolean };

/**
 * El texto comercial de cada plan: para quién es y qué incluye además de sus límites. Es lo único que la portada escribe a mano; el precio y los límites salen de
 * lo que el backoffice publica (H20). Si el backend no responde, estos mismos son la lista de respaldo.
 */
export const PLANES_DE_RESPALDO: TarjetaDePlan[] = [
  {
    nombre: "Gratis",
    para: "Para probar la API",
    mensual: 0,
    docs: "30 documentos/mes",
    rucs: "1 RUC · 1 usuario",
    incluye: [
      "Factura, boleta, nota de crédito y de débito",
      "Portal web y API REST",
      "Entorno de pruebas de SUNAT",
      "1 API key · PDF A4",
      "XML y CDR guardados 1 año",
    ],
    destacado: false,
  },
  {
    nombre: "Emprende",
    para: "Bodegas, freelancers y tiendas",
    mensual: 29,
    docs: "300 documentos/mes",
    rucs: "1 RUC · 1 usuario",
    incluye: ["Todo lo de Gratis", "Producción además de pruebas", "2 API keys", "XML y CDR guardados 5 años", "Soporte por correo (48 h)"],
    destacado: false,
  },
  {
    nombre: "Negocio",
    para: "PYMES y estudios contables",
    mensual: 69,
    docs: "1 500 documentos/mes",
    rucs: "3 RUC · 3 usuarios",
    incluye: ["Todo lo de Emprende", "PDF con tu logo y tu color", "5 API keys", "Soporte por correo y WhatsApp (24 h)"],
    destacado: true,
  },
  {
    nombre: "Pro",
    para: "SaaS, ISV y alto volumen",
    mensual: 129,
    docs: "Documentos ilimitados",
    rucs: "10 RUC · usuarios ilimitados",
    incluye: ["Todo lo de Negocio", "API keys ilimitadas", "Soporte prioritario (8 h hábiles)"],
    destacado: false,
  },
];

/** «1 500» con espacio de miles, como el resto de la portada. */
function miles(n: number): string {
  return n.toLocaleString("en-US").replaceAll(",", " ");
}

function documentos(l: LimiteAdmin): string {
  return l.ilimitado || l.maximo === undefined ? "Documentos ilimitados" : `${miles(l.maximo)} documentos/mes`;
}

function usuarios(l: LimiteAdmin): string {
  if (l.ilimitado || l.maximo === undefined) return "usuarios ilimitados";
  return l.maximo === 1 ? "1 usuario" : `${miles(l.maximo)} usuarios`;
}

/**
 * Las tarjetas de la página de precios (H20): una por plan publicado, en el orden en que llegan (del más barato al más caro). Precio y límites salen de lo publicado;
 * el texto comercial, del plan de la portada con el mismo nombre. Uno que la portada no conoce se describe con sus límites. Sin nada publicado, el respaldo.
 */
export function tarjetasDePlanes(publicados: PlanPublicado[] | null): TarjetaDePlan[] {
  if (!publicados || publicados.length === 0) return PLANES_DE_RESPALDO;
  return publicados.map((p) => {
    const conocido = PLANES_DE_RESPALDO.find((x) => x.nombre.toLowerCase() === p.nombre.toLowerCase());
    const l = p.limites;
    const keys = l.api_keys.ilimitado || l.api_keys.maximo === undefined ? "API keys ilimitadas" : l.api_keys.maximo === 1 ? "1 API key" : `${l.api_keys.maximo} API keys`;
    return {
      nombre: p.nombre,
      para: conocido?.para ?? "",
      mensual: p.precio_mensual,
      docs: documentos(l.documentos_al_mes),
      rucs: `${l.rucs} RUC · ${usuarios(l.usuarios)}`,
      incluye: conocido?.incluye ?? [keys, `XML y CDR guardados ${l.retencion_anios === 1 ? "1 año" : `${l.retencion_anios} años`}`],
      destacado: conocido?.destacado ?? false,
    };
  });
}
