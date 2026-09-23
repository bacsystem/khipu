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
 * Con la cantidad facturada es el `precio_venta` que calculó el backend (exacto). Con menos, se parte del precio × cantidad
 * y se suman los ajustes que viajan (ver `seConserva`) en proporción a la cantidad; los que afectan la base entran con
 * su IGV, con la tasa efectiva de la propia línea (igv ÷ valor de venta), que vale tanto para el 18 % como para el
 * 10,5 % del padrón o el 4 % del IVAP.
 */
export function importeLineaNota(item: ItemComprobante, cantidad: number): number {
  const facturada = Number(item.cantidad);
  const completa = cantidad === facturada;
  if (completa && item.precio_venta != null) return item.precio_venta;
  const proporcion = facturada > 0 ? cantidad / facturada : 0;
  const factor = item.valor_venta && item.igv != null ? 1 + item.igv / item.valor_venta : 1;
  const enPrecio = (a: DescuentoAplicado | CargoAplicado) => a.monto * proporcion * (a.afecta_base_igv ? factor : 1);
  let importe = item.precio_unitario * cantidad;
  for (const c of item.cargos ?? []) if (seConserva(c, completa)) importe += enPrecio(c);
  if (item.descuento && seConserva(item.descuento, completa)) importe -= enPrecio(item.descuento);
  return redondear(importe, 2);
}
