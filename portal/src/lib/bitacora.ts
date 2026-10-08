import { formatearFecha, formatearFechaHora } from "@/lib/formato";
import { messages } from "@/lib/messages";

const e = messages.admin.detalle.eventos;
const CLAVES = e.claves as Record<string, string>;
const VALORES: Record<string, string> = {
  ...(e.valores as Record<string, string>),
  ...(messages.admin.pagos.medios as Record<string, string>),
  ...(messages.admin.planDeCuenta.dialogo.direcciones as Record<string, string>),
};

/** Claves que solo sirven para cruzar con la base (un id interno): en la pantalla no le dicen nada a nadie. */
const OCULTAS = new Set(["pago"]);
/** Al pasar al texto, «desde» es «De» cuando va con «hacia» (un cambio de A a B), y «Desde» cuando va con «hasta» (un rango). */
const DESDE_DE_UN_CAMBIO = "De";

const FECHA = /^\d{4}-\d{2}-\d{2}$/;
const INSTANTE = /^\d{4}-\d{2}-\d{2}T[\d:.]+Z$/;

function valorLegible(clave: string, valor: string): string {
  if (clave === "duracion_s" && /^\d+$/.test(valor)) return e.minutos.replace("{n}", String(Math.round(Number(valor) / 60)));
  if (clave === "monto" && /^\d+(\.\d+)?$/.test(valor)) return `S/ ${valor}`;
  if (VALORES[valor]) return VALORES[valor];
  if (FECHA.test(valor)) return formatearFecha(valor);
  if (INSTANTE.test(valor)) return formatearFechaHora(valor);
  // Un periodo de pago (`2026-11-09/2026-12-08`).
  const periodo = valor.match(/^(\d{4}-\d{2}-\d{2})\/(\d{4}-\d{2}-\d{2})$/);
  if (periodo) return `${formatearFecha(periodo[1])} – ${formatearFecha(periodo[2])}`;
  // Un antes y un después: `45.50>49.90` (editar un plan) o `<a> -> <b>` (cambiar el remitente).
  return valor.replace(/\s*->\s*/g, " → ").replace(/(\S)>(\S)/g, "$1 → $2");
}

function etiqueta(clave: string): string {
  return CLAVES[clave] ?? clave.charAt(0).toUpperCase() + clave.slice(1).replaceAll("_", " ");
}

/** Los pares `clave=valor` del detalle, en orden. Un valor puede tener espacios: llega hasta la siguiente `clave=`. */
function pares(detalle: string): Array<[string, string]> {
  const claves = [...detalle.matchAll(/(?:^|[\s;]+)([a-z_]+)=/g)];
  return claves.map((m, i) => {
    const inicio = m.index + m[0].length;
    const fin = i + 1 < claves.length ? claves[i + 1].index : detalle.length;
    return [m[1], detalle.slice(inicio, fin).trim()];
  });
}

/**
 * El detalle de una acción de la bitácora, para leerlo (H12). El backend lo guarda como `clave=valor` técnico (`medio=TRANSFERENCIA vence=sin_cambio`); acá
 * cada clave se nombra, los códigos pasan a su nombre, las fechas a «9 Nov 2026» y los ids internos se omiten. Una clave nueva que este portal todavía no
 * conoce se muestra igual, con su nombre legible: la bitácora no esconde nada.
 */
export function detalleLegible(detalle: string | null | undefined): string {
  if (!detalle) return "";
  const todos = pares(detalle);
  if (todos.length === 0) return detalle;
  const esCambio = todos.some(([c]) => c === "hacia");
  return todos
    .filter(([clave]) => !OCULTAS.has(clave))
    .map(([clave, valor]) => `${clave === "desde" && esCambio ? DESDE_DE_UN_CAMBIO : etiqueta(clave)}: ${valorLegible(clave, valor)}`)
    .join(" · ");
}

/** El motivo de una suspensión o de una baja (`motivo=…`), o null si la acción no lo tiene. */
export function motivoDe(detalle: string | null | undefined): string | null {
  if (!detalle) return null;
  return pares(detalle).find(([clave]) => clave === "motivo")?.[1] ?? null;
}
