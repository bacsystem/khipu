import { MAX_LINEAS } from "@/lib/comprobantes/limites";
import { esGratuita, redondear } from "@/lib/comprobantes/totales";

/**
 * Las reglas de la línea que el backend ataja antes de numerar (#354) y que SUNAT rechaza con el número ya consumido: tope de líneas (2023/2137), ICBPER (2892,
 * 4320, NRUS 3240), precio en cero (2369/2367) y valor de venta en 0.00 (2370). Cada función devuelve el mensaje del backend (`Item.exigirValidoParaFactura`,
 * `Comprobante`, `ItemCalculado`) o `null`; el handler lo envuelve en un 422 ITEM_INVALIDO. Sin estado ni MSW, para probarlas aparte.
 */
export type LineaMock = {
  descripcion: string;
  unidad: string;
  cantidad: number;
  precio_unitario: number;
  tipo_afectacion_igv: string;
  icbper?: boolean;
  cargos?: unknown[];
};

export { MAX_LINEAS };
const MAX_BOLSAS = 99999;

/** 2023 en factura y boleta (Factura2_0/Boleta2_0 fila 145), 2137 en las notas (NotaCredito2_0 fila 161, NotaDebito2_0 fila 138). */
export function rechazoPorTopeDeLineasMock(items: unknown[], documento: "factura" | "boleta" | "nota"): string | null {
  if (items.length <= MAX_LINEAS) return null;
  return `${documento === "nota" ? "2137" : "2023"} - Una ${documento} admite hasta ${MAX_LINEAS} líneas (el número de orden del ítem es de hasta 3 dígitos); recibidas ${items.length}: divida la operación en más de un comprobante`;
}

/** `Item.exigirValidoParaFactura`, en su orden: bolsas del ICBPER (4320, 2892), precio negativo y precio en cero (2369, o 2367 en una gratuita). */
export function rechazoPorLineaMock(l: LineaMock): string | null {
  if (l.icbper) {
    if (l.unidad !== "NIU") return `4320 - Un ítem con ICBPER va en unidades (unidad NIU, una bolsa por unidad); recibida ${l.unidad}`;
    if (!Number.isInteger(l.cantidad) || l.cantidad > MAX_BOLSAS)
      return `2892 - La cantidad de bolsas del ICBPER es un entero de hasta 5 dígitos (1 a 99999); recibida ${l.cantidad}`;
  }
  if (l.precio_unitario < 0) return "El precio unitario no puede ser negativo";
  if (l.precio_unitario === 0)
    return esGratuita(l.tipo_afectacion_igv)
      ? "2367 - El valor referencial unitario de una operación gratuita debe ser mayor que cero"
      : "2369 - El precio unitario debe ser mayor que cero";
  return null;
}

/** 3240 (Boleta2_0 fila 254): el NRUS no cobra ICBPER. Solo la boleta; la factura no admite el 0113 y las notas no heredan la regla. */
export function rechazoPorNrusMock(tipoOperacion: string | undefined, items: LineaMock[]): string | null {
  if (tipoOperacion !== "0113" || !items.some((i) => i.icbper)) return null;
  return "3240 - El impuesto ICBPER no aplica para el NRUS (tipo de operación 0113): quite el icbper de las líneas o emita con el tipo de operación 0101";
}

/** `Icbper.tasaVigente` con el texto que imprime `BigDecimal` en el backend (0.10 hasta 2019, 0.2/0.3/0.4 en 2020–2022, 0.50 desde 2023). */
function tasaIcbperTexto(fechaEmision: string): { texto: string; valor: number } {
  const anio = Number(fechaEmision.slice(0, 4));
  if (anio <= 2019) return { texto: "0.10", valor: 0.1 };
  if (anio >= 2023) return { texto: "0.50", valor: 0.5 };
  return { texto: `0.${anio - 2018}`, valor: (anio - 2018) / 10 };
}

/**
 * 2370 (`ItemCalculado.exigirValorDeVentaPositivo`): el valor de venta de la línea no es cero ni negativo. Aproxima el cálculo del backend (precio sin ICBPER, sin IGV
 * en las onerosas gravadas, por la cantidad, a 2 decimales); no mira descuentos ni cargos, que no bajan la base a cero.
 */
export function rechazoPorValorDeVentaMock(l: LineaMock, tasaIgv: number, fechaEmision: string): string | null {
  const tasa = tasaIcbperTexto(fechaEmision);
  const sinIcbper = l.precio_unitario - (l.icbper ? tasa.valor : 0);
  const gravadaOnerosa = (l.tipo_afectacion_igv === "10" || l.tipo_afectacion_igv === "17") && !esGratuita(l.tipo_afectacion_igv);
  const tasaLinea = l.tipo_afectacion_igv === "17" ? 4 : tasaIgv;
  const valorUnitario = gravadaOnerosa ? sinIcbper / (1 + tasaLinea / 100) : sinIcbper;
  if (redondear(valorUnitario * l.cantidad, 2) > 0 || (l.cargos?.length ?? 0) > 0) return null;
  return `2370 - El valor de venta de la línea «${l.descripcion}» debe ser mayor que 0.00`
    + (l.icbper ? `: el precio_unitario incluye el ICBPER (S/ ${tasa.texto} por bolsa) y debe superarlo` : ": suba el precio o la cantidad");
}
