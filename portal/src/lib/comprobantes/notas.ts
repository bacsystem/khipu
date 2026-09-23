import type { CargoAplicado, DescuentoAplicado, ItemComprobante } from "@/lib/api/facturas";
import { redondear } from "./totales";

/** Descuento o cargo tal como lo piden `FacturaRequest.DescuentoDto`/`CargoDto`: porcentaje **o** monto, nunca ambos. */
type Ajuste = { porcentaje?: number; monto?: number; afecta_base_igv: boolean };

function ajuste(a: DescuentoAplicado | CargoAplicado): Ajuste {
  return a.tipo === "PORCENTAJE" ? { porcentaje: a.valor, afecta_base_igv: a.afecta_base_igv } : { monto: a.valor, afecta_base_igv: a.afecta_base_igv };
}

/**
 * Un porcentaje escala con la cantidad, así que viaja siempre; un monto fijo no (¿el flete de 25 era por las 10 unidades
 * o por las 3 que vuelven?), así que solo se conserva cuando la nota lleva la cantidad facturada.
 */
function seConserva(a: { tipo: "PORCENTAJE" | "MONTO" }, completa: boolean) {
  return completa || a.tipo === "PORCENTAJE";
}

/** ISC con la forma que exige `Isc` en el dominio: tasa en 01, `monto_unitario` en 02, tasa + `base_pvp` en 03. */
function iscParaNota(isc: NonNullable<ItemComprobante["isc"]>) {
  switch (isc.sistema) {
    case "02":
      return { sistema: "02", monto_unitario: isc.monto_unitario ?? undefined };
    case "03":
      return { sistema: "03", tasa: isc.tasa, base_pvp: isc.base_pvp ?? undefined };
    default:
      return { sistema: isc.sistema, tasa: isc.tasa };
  }
}

/**
 * Línea de una nota de crédito parcial a partir de la línea facturada: lo mismo que se pidió en la factura, con la
 * cantidad que vuelve. Reenvía todo lo que cambia el importe o el XML (descuento, cargos, ISC, ICBPER, código SUNAT,
 * GTIN): la primera versión omitía los cargos de línea, y una factura con cargo 47 acreditaba de menos sin que nadie lo
 * viera, y mandaba el ISC como `{sistema, tasa}`, que en los sistemas 02 y 03 el dominio rechaza.
 */
export function itemParaNota(item: ItemComprobante, cantidad: number) {
  const completa = cantidad === Number(item.cantidad);
  const cargos = (item.cargos ?? []).filter((c) => seConserva(c, completa)).map(ajuste);
  return {
    codigo: item.codigo ?? undefined,
    descripcion: item.descripcion,
    unidad: item.unidad,
    cantidad,
    precio_unitario: item.precio_unitario,
    tipo_afectacion_igv: item.tipo_afectacion_igv,
    descuento: item.descuento && seConserva(item.descuento, completa) ? ajuste(item.descuento) : undefined,
    cargos: cargos.length ? cargos : undefined,
    isc: item.isc ? iscParaNota(item.isc) : undefined,
    icbper: item.icbper ? true : undefined,
    codigo_sunat: item.codigo_sunat ?? undefined,
    gtin: item.gtin ?? undefined,
  };
}

/**
 * Lo que paga el cliente por la línea de la nota, para mostrarlo y compararlo con el tope (3286) antes de emitir.
 *
 * Se parte del `precio_venta` que calculó el backend para la línea completa (exacto: gratuitas en 0, ISC sobre el valor
 * con cargo, IGV sobre valor + ISC…) y se prorratea por la cantidad, porque todo lo que viaja en la línea parcial es
 * proporcional a ella. Lo único que no viaja son los ajustes de monto fijo (ver `seConserva`): se restan antes de
 * prorratear, con el ISC (solo el sistema 01 lo calcula sobre el valor) y el IGV que arrastraban.
 *
 * Reconstruir el importe desde el precio unitario no sirve: cobraba las gratuitas al valor referencial (bloqueaba NC
 * legítimas) y con ISC + cargo en porcentaje se quedaba ~2 % corto (dejaba pasar una NC que SUNAT rechaza sin
 * tolerancia). Sin `precio_venta` (backend anterior) queda precio × cantidad como aproximación.
 */
export function importeLineaNota(item: ItemComprobante, cantidad: number): number {
  const facturada = Number(item.cantidad);
  if (item.precio_venta == null || !(facturada > 0)) return redondear(item.precio_unitario * cantidad, 2);
  const completa = cantidad === facturada;
  if (completa) return item.precio_venta;
  const iscMonto = item.isc?.monto ?? 0;
  const factorIsc = item.isc?.sistema === "01" ? 1 + item.isc.tasa / 100 : 1;
  const factorIgv = item.valor_venta && item.igv != null ? 1 + item.igv / (item.valor_venta + iscMonto) : 1;
  const enPrecio = (a: DescuentoAplicado | CargoAplicado) => a.monto * (a.afecta_base_igv ? factorIsc * factorIgv : 1);
  let completo = item.precio_venta;
  for (const c of item.cargos ?? []) if (!seConserva(c, completa)) completo -= enPrecio(c);
  if (item.descuento && !seConserva(item.descuento, completa)) completo += enPrecio(item.descuento);
  return redondear((completo * cantidad) / facturada, 2);
}

/**
 * Una cantidad tan chica que la línea, o uno de sus ajustes en porcentaje, redondea a 0.00: el dominio rechaza el ajuste
 * (`Cargo.montoSobre` 2955, `Descuento.montoSobre`) y SUNAT el valor/precio unitario en cero (2367/2369). El formulario
 * dejaba teclear 0.0004 de 10 mesas con flete del 10 % (base 0.04 → cargo 0.00) y el POST volvía 422.
 */
export function lineaRedondeaACero(item: ItemComprobante, cantidad: number): boolean {
  if (importeLineaNota(item, cantidad) < 0.01) return true;
  const completa = cantidad === Number(item.cantidad);
  const factorIgv = item.valor_venta && item.igv != null ? 1 + item.igv / (item.valor_venta + (item.isc?.monto ?? 0)) : 1;
  const base = (item.precio_unitario * cantidad) / factorIgv;
  const ajustes = [...(item.cargos ?? []), ...(item.descuento ? [item.descuento] : [])];
  return ajustes.some((a) => a.tipo === "PORCENTAJE" && seConserva(a, completa) && redondear((base * a.valor) / 100, 2) < 0.01);
}
