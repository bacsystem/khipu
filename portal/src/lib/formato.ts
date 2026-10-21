import { LOCALE_POR_DEFECTO, type Locale, ZONA_HORARIA } from "@/i18n/config";

/**
 * Los símbolos de las monedas que tienen uno en el formulario (soles, dólares, euros). Las otras que admite la API (`Monedas` del dominio, 17 en total, #316) se muestran con su
 * código ISO («GBP 2,500.01»), igual en el campo de precio y en el pie de totales: lo que no tenga símbolo aquí cae al código, y los dos sitios tienen que coincidir.
 *
 * `EntradaMonto` mantiene su propia copia a propósito: es un componente vendorizado del design system y no debe
 * depender del `lib/` de esta app. Si aparece una moneda con símbolo, hay que tocar las dos.
 */
const SIMBOLOS: Record<string, string> = { PEN: "S/", USD: "$", EUR: "€" };

/**
 * #25: todo lo que pinta texto acepta el locale de la sesión (`useLocale()` / `getLocale()` de next-intl) y por defecto es `es`,
 * que conserva el formato de siempre: hora de Lima, montos como SUNAT (1,234.56) y meses abreviados de Perú («Set», no «sept.»).
 * La zona horaria no depende del locale: un comprobante de Lima se lee en hora de Lima en cualquier idioma.
 */
const INTL: Record<Locale, string> = { es: "es-PE", en: "en-US" };

// Intl en `es-PE` da «sept.» y en minúscula; el portal (y la representación impresa) usan la abreviatura peruana de tres letras.
// eslint-disable-next-line khipu/no-literal-de-ui -- datos del locale, no copia de UI
const MESES_ES = ["Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Set", "Oct", "Nov", "Dic"];

function mesCorto(mes: number, locale: Locale): string {
  if (locale === "es") return MESES_ES[mes - 1];
  return new Intl.DateTimeFormat(INTL[locale], { month: "short", timeZone: "UTC" }).format(Date.UTC(2000, mes - 1, 1));
}

/** Número con separador de miles y 2 decimales (formato SUNAT: 1,234.56; `es-PE` y `en-US` coinciden). */
export function formatearNumero(valor: number, locale: Locale = LOCALE_POR_DEFECTO): string {
  return Number(valor).toLocaleString(INTL[locale], {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

/** Un precio unitario: al menos 2 decimales y los que traiga de más (hasta 10), sin redondear: 0.0123 es un precio válido y no debe verse como 0.01. */
export function formatearPrecioUnitario(valor: number, locale: Locale = LOCALE_POR_DEFECTO): string {
  return Number(valor).toLocaleString(INTL[locale], {
    minimumFractionDigits: 2,
    maximumFractionDigits: 10,
  });
}

export function formatearMonto(moneda: string, valor: number, locale: Locale = LOCALE_POR_DEFECTO): string {
  const simbolo = SIMBOLOS[moneda] ?? moneda;
  return `${simbolo} ${formatearNumero(valor, locale)}`;
}

/** Fecha de hoy en Lima como `YYYY-MM-DD` (la zona del emisor, no la del navegador). */
export function hoyLima(): string {
  return new Date().toLocaleDateString("en-CA", { timeZone: ZONA_HORARIA });
}

/** Días transcurridos entre dos fechas `YYYY-MM-DD` (negativo si `hasta` es anterior), sin zona horaria de por medio. */
export function diasEntre(desde: string, hasta: string): number {
  const [a1, m1, d1] = desde.split("-").map(Number);
  const [a2, m2, d2] = hasta.split("-").map(Number);
  return (Date.UTC(a2, m2 - 1, d2) - Date.UTC(a1, m1 - 1, d1)) / 86_400_000;
}

/**
 * Suma (o resta, con negativo) días calendario a una fecha `YYYY-MM-DD`, sin zona horaria de por medio.
 * Con una fecha ilegible devuelve la entrada, como `formatearFecha`: sin la guarda, `toISOString()` lanza
 * `RangeError` y tumba el render, que es peor que mostrar el valor crudo.
 */
export function sumarDias(iso: string, dias: number): string {
  const [anio, mes, dia] = iso.split("-").map(Number);
  if (!anio || !mes || !dia) return iso;
  return new Date(Date.UTC(anio, mes - 1, dia + dias)).toISOString().slice(0, 10);
}

/** `YYYY-MM-DD` legible: «14 Set 2026» en castellano, «Sep 14, 2026» en inglés. */
export function formatearFecha(iso: string, locale: Locale = LOCALE_POR_DEFECTO): string {
  const [anio, mes, dia] = iso.split("-").map(Number);
  if (!anio || !mes || !dia || mes > 12) return iso;
  return locale === "en" ? `${mesCorto(mes, locale)} ${dia}, ${anio}` : `${dia} ${mesCorto(mes, locale)} ${anio}`;
}

/** Instante ISO (UTC) a fecha y hora de Lima: "15 Set 2026, 10:32". */
export function formatearFechaHora(iso: string, locale: Locale = LOCALE_POR_DEFECTO): string {
  const fecha = new Date(iso);
  if (Number.isNaN(fecha.getTime())) return iso;
  const partes = new Intl.DateTimeFormat("en-CA", {
    timeZone: ZONA_HORARIA,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(fecha);
  const v = (tipo: string) => partes.find((p) => p.type === tipo)?.value ?? "";
  return `${formatearFecha(`${v("year")}-${v("month")}-${v("day")}`, locale)}, ${v("hour")}:${v("minute")}`;
}

/** La fecha de Lima de un instante ISO (UTC): "1 Nov 2026". Un instante ilegible se devuelve tal cual. */
export function formatearFechaDeLima(iso: string, locale: Locale = LOCALE_POR_DEFECTO): string {
  const fecha = new Date(iso);
  if (Number.isNaN(fecha.getTime())) return iso;
  return formatearFecha(fecha.toLocaleDateString("en-CA", { timeZone: ZONA_HORARIA }), locale);
}

/**
 * Cuándo empieza el ciclo siguiente: la medianoche del día 1 del mes que viene, hora de Lima (UTC-5, sin horario de verano), como instante ISO.
 * Es la misma regla que aplica el backend a los cambios de límites de un plan (#190); acá solo sirve para avisar antes de guardar.
 */
export function inicioDelProximoCiclo(ahora: Date): string {
  const [anio, mes] = ahora.toLocaleDateString("en-CA", { timeZone: ZONA_HORARIA }).split("-").map(Number);
  return new Date(Date.UTC(anio, mes, 1, 5)).toISOString();
}

/**
 * Hasta qué día está pagado un plan, como instante de vencimiento (#191): el vencimiento es **exclusivo** (en ese instante ya empieza la gracia), así que «pagado
 * hasta el 31 de octubre» es la medianoche del 1 de noviembre en Lima (UTC-5, sin horario de verano). Recibe `YYYY-MM-DD`.
 */
export function venceDesdeFechaDeLima(ultimoDia: string): string {
  const [anio, mes, dia] = ultimoDia.split("-").map(Number);
  return new Date(Date.UTC(anio, mes - 1, dia + 1, 5)).toISOString();
}

/** Lo inverso: el último día (hora de Lima, `YYYY-MM-DD`) que cubre un vencimiento exclusivo. Un instante ilegible se devuelve tal cual. */
export function ultimoDiaCubierto(venceEn: string): string {
  const instante = Date.parse(venceEn);
  if (Number.isNaN(instante)) return venceEn;
  return new Date(instante - 1000).toLocaleDateString("en-CA", { timeZone: ZONA_HORARIA });
}

/** «2026-09» como «Set 2026» («Sep 2026» en inglés). Lo que no tiene ese formato se devuelve tal cual. */
export function formatearMes(mes: string, locale: Locale = LOCALE_POR_DEFECTO): string {
  const [anio, m] = mes.split("-").map(Number);
  if (!anio || !m || m < 1 || m > 12) return mes;
  return `${mesCorto(m, locale)} ${anio}`;
}
