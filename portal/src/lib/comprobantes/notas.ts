import type { CargoAplicado, Comprobante, DescuentoAplicado, ItemComprobante } from "@/lib/api/facturas";
import { esGratuita, redondear } from "./totales";

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
 * Una cantidad tan chica que la línea, o uno de sus cargos en porcentaje, redondea a 0.00: el dominio rechaza el cargo
 * (`Cargo.montoSobre`, 2955) y SUNAT el valor/precio unitario en cero (2367/2369). El descuento NO cuenta:
 * `Descuento.montoSobre` solo rechaza que alcance la base, un descuento de 0.00 pasa. El formulario dejaba teclear
 * 0.0004 de 10 mesas con flete del 10 % (base 0.04 → cargo 0.00) y el POST volvía 422.
 *
 * Una línea **gratuita** tampoco cuenta: su importe es 0 por definición (no se cobra) y SUNAT lo exige así —con 9996 en la
 * línea, el valor unitario debe ser 0 (`NotaCredito2_0` f184, 2640) y el precio de venta solo se exige distinto de cero
 * cuando NO hay 9996 (f187, 3224)—. Sin esta excepción una NC parcial que incluyera una bonificación quedaba bloqueada con
 * un consejo imposible («subí la cantidad»), aunque el backend la emite (recert #10).
 */
export function lineaRedondeaACero(item: ItemComprobante, cantidad: number): boolean {
  if (esGratuita(item.tipo_afectacion_igv)) return false;
  const importe = importeLineaNota(item, cantidad);
  if (importe < 0.01) return true;
  if (impuestoRedondeaACero(item.tipo_afectacion_igv, importe)) return true;
  const factorIgv = item.valor_venta && item.igv != null ? 1 + item.igv / (item.valor_venta + (item.isc?.monto ?? 0)) : 1;
  const base = (item.precio_unitario * cantidad) / factorIgv;
  // Solo los cargos en porcentaje (los de monto fijo no viajan en parcial, y un porcentaje siempre viaja).
  return (item.cargos ?? []).some((a) => a.tipo === "PORCENTAJE" && redondear((base * a.valor) / 100, 2) < 0.01);
}

/**
 * SUNAT 3111 (NotaCredito2_0 f211, NotaDebito2_0 f192, y la gemela de Factura2_0): con tributo 1000/1016 y base > 0.06
 * el impuesto de la línea no puede ser 0.00. Con el IGV al 18 % nunca pasa (base 0.07 → 0.01); con el IVAP al 4 % pasa
 * entre 0.07 y 0.12 de importe con impuesto. El dominio lo rechaza antes de numerar desde #141; acá se avisa antes.
 */
export function impuestoRedondeaACero(afectacion: string, importeConImpuesto: number): boolean {
  if (afectacion !== "17") return false;
  const base = importeConImpuesto / 1.04;
  return base > 0.06 && redondear(base * 0.04, 2) < 0.01;
}

/**
 * Hasta dónde puede llegar una NC por importe según el tributo de su línea (3503, filas 114–122: base e impuesto por
 * tributo no pueden superar los de la factura). En precio con impuesto: gravado + IGV (10), exonerado (20), inafecto
 * (30); en exportación e IVAP toda la factura es del mismo tributo. Es el límite real en facturas con ISC, ICBPER,
 * cargos sin IGV, anticipos o mixtas, donde el total (3286) queda por encima.
 */
export function topePorTributo(totales: Pick<Comprobante["totales"], "gravado" | "igv" | "exonerado" | "inafecto" | "total" | "ivap" | "exportacion">, afectacion: string): number {
  switch (afectacion) {
    case "10":
      return redondear(totales.gravado + totales.igv, 2);
    case "17":
      // En una factura IVAP la base 1016 viaja en `gravado` y el impuesto en `ivap`.
      return redondear(totales.gravado + (totales.ivap ?? 0), 2);
    case "20":
      return totales.exonerado;
    case "30":
      return totales.inafecto;
    case "40":
      // Base 9995 (fila 114): con un cargo sin IGV, descuento global o redondeo el total queda por encima y el tope mentía.
      return totales.exportacion ?? totales.total;
    default:
      return totales.total;
  }
}

/**
 * Afectación de la línea propia de una NC por importe (descuento, bonificación, disminución): sigue a lo que la
 * factura cobró. Con alguna línea gravada, 10 (el descuento lleva IGV y SUNAT lo compara contra el gravado, 3503);
 * si toda la factura es exonerada o inafecta, esa afectación. Exportación e IVAP se resuelven antes (40/17).
 */
export function afectacionPredominante(items: Pick<ItemComprobante, "tipo_afectacion_igv" | "cantidad" | "precio_unitario" | "precio_venta">[]): "10" | "20" | "30" {
  if (items.some((i) => i.tipo_afectacion_igv === "10")) return "10";
  // Sin gravadas: exonerada o inafecta, la que más pesa en la factura. Con «10» de comodín la nota llevaba IGV contra un
  // gravado de 0 y el backend la rechazaba siempre (3503): callejón sin salida medido en la recert #7.
  const peso = (pred: (a: string) => boolean) => items.filter((i) => pred(i.tipo_afectacion_igv)).reduce((s, i) => s + (i.precio_venta ?? i.precio_unitario * Number(i.cantidad)), 0);
  return peso((a) => a === "20" || a === "21") >= peso((a) => /^3[0-7]$/.test(a)) ? "20" : "30";
}
