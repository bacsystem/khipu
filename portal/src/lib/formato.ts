/**
 * Las tres monedas que acepta `FacturaRequest` (`@Pattern("PEN|USD|EUR")`). Tiene que cubrirlas a todas: lo que
 * falte cae al código ISO, y el formulario de emisión mostraba «€ 2,500.01» en el campo de precio y «EUR 2,500.01»
 * en el pie de totales para el mismo importe.
 *
 * `EntradaMonto` mantiene su propia copia a propósito: es un componente vendorizado del design system y no debe
 * depender del `lib/` de esta app. Si aparece una moneda nueva, hay que tocar las dos.
 */
const SIMBOLOS: Record<string, string> = { PEN: "S/", USD: "$", EUR: "€" };

const MESES = ["Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Set", "Oct", "Nov", "Dic"];

/** Número con separador de miles y 2 decimales (formato SUNAT: 1,234.56). */
export function formatearNumero(valor: number): string {
  return Number(valor).toLocaleString("en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export function formatearMonto(moneda: string, valor: number): string {
  const simbolo = SIMBOLOS[moneda] ?? moneda;
  return `${simbolo} ${formatearNumero(valor)}`;
}

/** Fecha de hoy en Lima como `YYYY-MM-DD` (la zona del emisor, no la del navegador). */
export function hoyLima(): string {
  return new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" });
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

export function formatearFecha(iso: string): string {
  const [anio, mes, dia] = iso.split("-").map(Number);
  if (!anio || !mes || !dia) return iso;
  return `${dia} ${MESES[mes - 1]} ${anio}`;
}

/** Instante ISO (UTC) a fecha y hora de Lima: "15 Set 2026, 10:32". */
export function formatearFechaHora(iso: string): string {
  const fecha = new Date(iso);
  if (Number.isNaN(fecha.getTime())) return iso;
  const partes = new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Lima",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(fecha);
  const v = (tipo: string) => partes.find((p) => p.type === tipo)?.value ?? "";
  return `${formatearFecha(`${v("year")}-${v("month")}-${v("day")}`)}, ${v("hour")}:${v("minute")}`;
}
