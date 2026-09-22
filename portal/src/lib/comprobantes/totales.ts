/**
 * Previsualización de totales mientras se completa el formulario de emisión.
 *
 * **El backend es la fuente de verdad**: estos importes se muestran antes de emitir y el comprobante emitido trae los
 * suyos. Para que no divergan, esto replica paso a paso lo que hace `ItemCalculado.de` en el dominio — incluidos los
 * puntos de redondeo, que son lo que realmente decide el resultado:
 *
 * 1. `valorReferencial = precioUnitario / (1 + factorIgv)` a **10 decimales** (gravadas); en exoneradas/inafectas el
 *    precio ya viene sin IGV y se usa tal cual.
 * 2. `baseBruta = valorReferencial × cantidad` a 2 decimales.
 * 3. `igv = baseBruta × factorIgv` a 2 decimales.
 *
 * Redondear antes de tiempo (p. ej. el valor unitario a 2) da diferencias de céntimos contra el comprobante real.
 * Cubre solo el alcance del formulario: gravado, exonerado, inafecto y gratuitas, sin ISC, ICBPER, descuentos ni cargos.
 */

/** `TasaIgv.factor`: el porcentaje a fracción con 6 decimales (18.00 → 0.18). */
export function factorIgv(tasaPorcentaje: number): number {
  return redondear(tasaPorcentaje / 100, 6);
}

/** HALF_UP como `BigDecimal`: desempata alejándose del cero, sin el ruido binario del punto flotante. */
export function redondear(valor: number, decimales: number): number {
  const escala = 10 ** decimales;
  const escalado = Number((valor * escala).toPrecision(15));
  return (Math.sign(escalado) * Math.round(Math.abs(escalado))) / escala;
}

export type ItemParaTotales = {
  cantidad: number;
  /** Precio unitario con IGV en las gravadas; sin IGV en exoneradas, inafectas y gratuitas. */
  precioUnitario: number;
  /** Catálogo 07: `10` gravado, `20` exonerado, `30` inafecto, `11`–`17`/`21`/`31`–`37` gratuitas. */
  tipoAfectacionIgv: string;
};

export type TotalesPrevisualizados = {
  gravado: number;
  exonerado: number;
  inafecto: number;
  gratuito: number;
  igv: number;
  total: number;
};

const VACIO: TotalesPrevisualizados = { gravado: 0, exonerado: 0, inafecto: 0, gratuito: 0, igv: 0, total: 0 };

/** Una afectación gratuita no se cobra: aporta al total gratuito y nunca al importe a pagar (regla 2640). */
export function esGratuita(tipoAfectacionIgv: string): boolean {
  const n = Number(tipoAfectacionIgv);
  return (n >= 11 && n <= 17) || n === 21 || (n >= 31 && n <= 37);
}

export function calcularTotales(items: ItemParaTotales[], tasaIgvPorcentaje: number): TotalesPrevisualizados {
  const factor = factorIgv(tasaIgvPorcentaje);

  const t = items.reduce<TotalesPrevisualizados>((acc, item) => {
    if (!(item.cantidad > 0) || !(item.precioUnitario >= 0)) return acc;
    const gratuita = esGratuita(item.tipoAfectacionIgv);
    const gravada = item.tipoAfectacionIgv === "10";

    const valorReferencial = gravada ? redondear(item.precioUnitario / (1 + factor), 10) : item.precioUnitario;
    const base = redondear(valorReferencial * item.cantidad, 2);

    if (gratuita) return { ...acc, gratuito: redondear(acc.gratuito + base, 2) };
    if (gravada) {
      return {
        ...acc,
        gravado: redondear(acc.gravado + base, 2),
        igv: redondear(acc.igv + redondear(base * factor, 2), 2),
      };
    }
    if (item.tipoAfectacionIgv === "20") return { ...acc, exonerado: redondear(acc.exonerado + base, 2) };
    if (item.tipoAfectacionIgv === "30") return { ...acc, inafecto: redondear(acc.inafecto + base, 2) };
    return acc;
  }, VACIO);

  return { ...t, total: redondear(t.gravado + t.exonerado + t.inafecto + t.igv, 2) };
}
