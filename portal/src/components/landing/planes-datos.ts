import type { Mensajes } from "@/i18n/mensajes";
import { interpolar } from "@/i18n/mensajes";
import type { LimiteAdmin } from "@/lib/api/admin-planes";
import type { PlanPublicado } from "@/lib/api/planes-publicados";
import es from "@/messages/es.json";

export type TarjetaDePlan = { nombre: string; para: string; mensual: number; docs: string; rucs: string; incluye: string[]; destacado: boolean };

/**
 * Los planes que la portada conoce, por clave. La clave es el nombre del plan en minúsculas tal como lo publica el backoffice
 * (`Gratis` → `gratis`), que es como se empareja un plan publicado con su texto comercial (`landing.planes.catalogo.<clave>`).
 */
const CATALOGO = [
  { clave: "gratis", mensual: 0, destacado: false },
  { clave: "emprende", mensual: 29, destacado: false },
  { clave: "negocio", mensual: 69, destacado: true },
  { clave: "pro", mensual: 129, destacado: false },
] as const;

/**
 * El texto comercial de cada plan: para quién es y qué incluye además de sus límites. Es lo único que la portada escribe a mano; el precio y los límites salen de
 * lo que el backoffice publica (H20). Si el backend no responde, estos mismos son la lista de respaldo.
 */
export function planesDeRespaldo(messages: Mensajes = es): TarjetaDePlan[] {
  return CATALOGO.map(({ clave, mensual, destacado }) => {
    const textos = messages.landing.planes.catalogo[clave];
    return { nombre: textos.nombre, para: textos.para, mensual, docs: textos.docs, rucs: textos.rucs, incluye: [...textos.incluye], destacado };
  });
}

export const PLANES_DE_RESPALDO: TarjetaDePlan[] = planesDeRespaldo(es);

/** «1 500» con espacio de miles, como el resto de la portada. */
function miles(n: number): string {
  return n.toLocaleString("en-US").replaceAll(",", " ");
}

function documentos(l: LimiteAdmin, t: Mensajes["landing"]["planes"]["limites"]): string {
  return l.ilimitado || l.maximo === undefined ? t.documentosIlimitados : interpolar(t.documentosAlMes, { n: miles(l.maximo) });
}

function usuarios(l: LimiteAdmin, t: Mensajes["landing"]["planes"]["limites"]): string {
  if (l.ilimitado || l.maximo === undefined) return t.usuariosIlimitados;
  return l.maximo === 1 ? t.unUsuario : interpolar(t.usuarios, { n: miles(l.maximo) });
}

/**
 * Las tarjetas de la página de precios (H20): una por plan publicado, en el orden en que llegan (del más barato al más caro). Precio y límites salen de lo publicado;
 * el texto comercial, del plan de la portada con el mismo nombre. Uno que la portada no conoce se describe con sus límites. Sin nada publicado, el respaldo.
 */
export function tarjetasDePlanes(publicados: PlanPublicado[] | null, messages: Mensajes = es): TarjetaDePlan[] {
  const respaldo = messages === es ? PLANES_DE_RESPALDO : planesDeRespaldo(messages);
  if (!publicados || publicados.length === 0) return respaldo;
  const t = messages.landing.planes.limites;
  return publicados.map((p) => {
    const i = CATALOGO.findIndex((x) => x.clave === p.nombre.toLowerCase());
    const conocido = i >= 0 ? respaldo[i] : undefined;
    const l = p.limites;
    const keys =
      l.api_keys.ilimitado || l.api_keys.maximo === undefined
        ? t.apiKeysIlimitadas
        : l.api_keys.maximo === 1
          ? t.unaApiKey
          : interpolar(t.apiKeys, { n: l.api_keys.maximo });
    const periodo = l.retencion_anios === 1 ? t.unAno : interpolar(t.anos, { n: l.retencion_anios });
    return {
      nombre: p.nombre,
      para: conocido?.para ?? "",
      mensual: p.precio_mensual,
      docs: documentos(l.documentos_al_mes, t),
      rucs: interpolar(t.rucs, { n: l.rucs, usuarios: usuarios(l.usuarios, t) }),
      incluye: conocido?.incluye ?? [keys, interpolar(t.retencion, { periodo })],
      destacado: conocido?.destacado ?? false,
    };
  });
}
