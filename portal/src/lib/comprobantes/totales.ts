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
 * **Todo se calcula con enteros (`bigint`), no con `number`.** El dominio usa `BigDecimal`, y en punto flotante la
 * división del paso 1 pierde el dígito que decide el redondeo: `1208.79 / 1.18` da `1024.39830508474576…`, y
 * escalar ese `double` por 1e10 ya no distingue `…0847` de `…0848`. Con cantidad `0.59` esa diferencia de un
 * diezmilmillonésimo se convierte en un céntimo en el total — previsualizar S/ 713.19 lo que se emite como S/ 713.18.
 *
 * Cubre solo el alcance del formulario: gravado, exonerado, inafecto y gratuitas, sin ISC, ICBPER, descuentos ni cargos.
 */

/**
 * Tasas del IGV (#84): la general y la reducida del Padrón de Tasa Especial (MYPE de restaurantes y hoteles,
 * Ley 31556). Viven acá y no en un componente porque las usan tanto el cálculo como los textos que las nombran.
 */
export const TASA_GENERAL = 18;
export const TASA_PADRON = 10.5;

/** Decimal exacto `v / 10^e`, el equivalente de un `BigDecimal` con su escala. */
type Decimal = { v: bigint; e: number };

const CERO: Decimal = { v: 0n, e: 0 };
const UNO: Decimal = { v: 1n, e: 0 };

/**
 * Número de JS a decimal exacto, leyendo su **representación textual** y no sus bits.
 * Es lo mismo que hace Jackson en el backend: construye el `BigDecimal` desde el literal del JSON, así que `1.005`
 * es exactamente 1005/1000 en los dos lados, y no el 1.00499999… que guarda el `double`.
 */
function aDecimal(n: number): Decimal {
  if (!Number.isFinite(n)) return CERO;
  const [mantisa, exponente] = n.toString().split("e");
  const negativo = mantisa.startsWith("-");
  const [entera, decimales = ""] = (negativo ? mantisa.slice(1) : mantisa).split(".");
  let v = BigInt(entera + decimales) * (negativo ? -1n : 1n);
  let e = decimales.length - Number(exponente ?? 0);
  // Un exponente positivo puede dejar la escala en negativo (1e21 → e = -21): se normaliza a escala 0.
  if (e < 0) {
    v *= 10n ** BigInt(-e);
    e = 0;
  }
  return { v, e };
}

function aNumero(d: Decimal): number {
  return Number(d.v) / 10 ** d.e;
}

/** División entera con HALF_UP: desempata alejándose del cero, igual que `RoundingMode.HALF_UP`. */
function dividirHalfUp(numerador: bigint, denominador: bigint): bigint {
  if (denominador === 0n) return 0n;
  const negativo = numerador < 0n !== denominador < 0n;
  const n = numerador < 0n ? -numerador : numerador;
  const d = denominador < 0n ? -denominador : denominador;
  const cociente = n / d;
  const resto = n % d;
  const redondeado = 2n * resto >= d ? cociente + 1n : cociente;
  return negativo ? -redondeado : redondeado;
}

/** Reescala a `decimales` con HALF_UP, como `BigDecimal.setScale(decimales, HALF_UP)`. */
function escalar(d: Decimal, decimales: number): Decimal {
  if (d.e === decimales) return d;
  if (d.e < decimales) return { v: d.v * 10n ** BigInt(decimales - d.e), e: decimales };
  return { v: dividirHalfUp(d.v, 10n ** BigInt(d.e - decimales)), e: decimales };
}

function sumar(a: Decimal, b: Decimal): Decimal {
  const e = Math.max(a.e, b.e);
  return { v: escalar(a, e).v + escalar(b, e).v, e };
}

function multiplicar(a: Decimal, b: Decimal): Decimal {
  return { v: a.v * b.v, e: a.e + b.e };
}

/** `a / b` a `decimales`, HALF_UP — el equivalente exacto de `a.divide(b, decimales, HALF_UP)`. */
function dividir(a: Decimal, b: Decimal, decimales: number): Decimal {
  const desplazamiento = b.e - a.e + decimales;
  const numerador = desplazamiento >= 0 ? a.v * 10n ** BigInt(desplazamiento) : a.v;
  const denominador = desplazamiento >= 0 ? b.v : b.v * 10n ** BigInt(-desplazamiento);
  return { v: dividirHalfUp(numerador, denominador), e: decimales };
}

/** `TasaIgv.factor`: el porcentaje a fracción con 6 decimales (18.00 → 0.18). */
export function factorIgv(tasaPorcentaje: number): number {
  return aNumero(dividir(aDecimal(tasaPorcentaje), { v: 100n, e: 0 }, 6));
}

/** HALF_UP como `BigDecimal`: desempata alejándose del cero, sin el ruido binario del punto flotante. */
export function redondear(valor: number, decimales: number): number {
  return aNumero(escalar(aDecimal(valor), decimales));
}

export type ItemParaTotales = {
  cantidad: number;
  /** Precio unitario con IGV en las gravadas; sin IGV en exoneradas, inafectas y gratuitas. */
  precioUnitario: number;
  /** Catálogo 07: `10` gravado, `20` exonerado, `30` inafecto, `11`–`16`/`21`/`31`–`37` gratuitas. */
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

/**
 * Una afectación gratuita no se cobra: aporta al total gratuito y nunca al importe a pagar (regla 2640).
 *
 * El rango llega hasta el **16**, no hasta el 17: el 17 es IVAP (`TipoAfectacionIgv.IVAP`, tributo 1016 al 4 %),
 * una venta gravada que sí se cobra. Contarlo como gratuita previsualizaría total S/ 0 sobre una línea facturable.
 */
export function esGratuita(tipoAfectacionIgv: string): boolean {
  const n = Number(tipoAfectacionIgv);
  return (n >= 11 && n <= 16) || n === 21 || (n >= 31 && n <= 37);
}

type Acumulado = { gravado: Decimal; exonerado: Decimal; inafecto: Decimal; gratuito: Decimal; igv: Decimal };

const VACIO: Acumulado = { gravado: CERO, exonerado: CERO, inafecto: CERO, gratuito: CERO, igv: CERO };

export function calcularTotales(items: ItemParaTotales[], tasaIgvPorcentaje: number): TotalesPrevisualizados {
  const factor = dividir(aDecimal(tasaIgvPorcentaje), { v: 100n, e: 0 }, 6);
  const unoMasFactor = sumar(UNO, factor);

  const t = items.reduce<Acumulado>((acc, item) => {
    if (!(item.cantidad > 0) || !(item.precioUnitario >= 0)) return acc;
    const gratuita = esGratuita(item.tipoAfectacionIgv);
    const gravada = item.tipoAfectacionIgv === "10";

    const precio = aDecimal(item.precioUnitario);
    const valorReferencial = gravada ? dividir(precio, unoMasFactor, 10) : precio;
    const base = escalar(multiplicar(valorReferencial, aDecimal(item.cantidad)), 2);

    if (gratuita) return { ...acc, gratuito: sumar(acc.gratuito, base) };
    if (gravada) {
      return {
        ...acc,
        gravado: sumar(acc.gravado, base),
        igv: sumar(acc.igv, escalar(multiplicar(base, factor), 2)),
      };
    }
    if (item.tipoAfectacionIgv === "20") return { ...acc, exonerado: sumar(acc.exonerado, base) };
    if (item.tipoAfectacionIgv === "30") return { ...acc, inafecto: sumar(acc.inafecto, base) };
    return acc;
  }, VACIO);

  const total = escalar(sumar(sumar(sumar(t.gravado, t.exonerado), t.inafecto), t.igv), 2);
  return {
    gravado: aNumero(escalar(t.gravado, 2)),
    exonerado: aNumero(escalar(t.exonerado, 2)),
    inafecto: aNumero(escalar(t.inafecto, 2)),
    gratuito: aNumero(escalar(t.gratuito, 2)),
    igv: aNumero(escalar(t.igv, 2)),
    total: aNumero(total),
  };
}
