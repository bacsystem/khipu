import { interpolar, type Mensajes } from "@/i18n/mensajes";
import es from "@/messages/es.json";

/**
 * #310: descuentos y cargos del formulario (catálogo 53). Un mismo `AjusteForm` sirve de descuento o de cargo, de línea o global. Viaja como lo piden
 * `FacturaRequest.DescuentoDto` y `CargoDto`: porcentaje **o** monto (nunca ambos) y `afecta_base_igv`. El importe que resulta lo calcula el backend (`/calcular`, #405).
 */
export type AjusteForm = {
  modo: "porcentaje" | "monto";
  /** Lo tecleado, tal cual: se interpreta al validar y al enviar. */
  valor: string;
  /** `true`: reduce (descuento 00/02) o aumenta (cargo 47/49) la base del IGV. `false`: solo cambia lo que se paga (01/03, 48/50). */
  afectaBase: boolean;
  /** Solo un cargo global: el recargo al consumo (código 46, Ley 25988), que no admite IGV. */
  recargoConsumo?: boolean;
};

export const AJUSTE_VACIO: AjusteForm = { modo: "porcentaje", valor: "", afectaBase: true };

export type AjusteApi = { porcentaje?: number; monto?: number; afecta_base_igv: boolean; motivo?: string };

type Clase = "descuento" | "cargo";

/** Hasta `decimales` decimales: el dominio rechaza más (2968, y 5 en el porcentaje). */
function decimalesDe(valor: string): number {
  const i = valor.indexOf(".");
  return i < 0 ? 0 : valor.length - i - 1;
}

/** Por qué no vale el ajuste, o `null` si vale (o está vacío: «sin descuento»). Espeja `Descuento` y `Cargo` del dominio. */
export function errorDeAjuste(a: AjusteForm, clase: Clase, messages: Mensajes = es): string | null {
  const t = messages.comprobantes.validacion.ajuste;
  const v = a.valor.trim();
  if (v === "") return null;
  if (!/^\d*\.?\d+$|^\d+\.$/.test(v)) return t.numero;
  const n = Number(v);
  if (!(n > 0)) return t.mayorQueCero;
  if (a.modo === "porcentaje") {
    const tope = clase === "descuento" ? 100 : 1000;
    if (n >= tope) return interpolar(t.porcentajeTope, { tope });
    if (decimalesDe(v) > 5) return t.porcentajeDecimales;
  } else if (decimalesDe(v) > 2) {
    return t.montoDecimales;
  }
  return null;
}

/** El ajuste con la forma de la API, o `undefined` si está vacío o no vale (el formulario lo muestra y no deja emitir). */
export function ajusteAApi(a: AjusteForm, clase: Clase): AjusteApi | undefined {
  if (a.valor.trim() === "" || errorDeAjuste(a, clase) !== null) return undefined;
  const n = Number(a.valor.trim());
  const base = a.modo === "porcentaje" ? { porcentaje: n } : { monto: n };
  if (a.recargoConsumo) return { ...base, afecta_base_igv: false, motivo: "recargo_consumo" };
  return { ...base, afecta_base_igv: a.afectaBase };
}
