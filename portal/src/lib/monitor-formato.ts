/**
 * Cómo se muestran los números y las horas del monitor de emisión (#195). Todo en hora de Lima, la del emisor, no la del navegador del administrador.
 */

function formatoDeHora(conSegundos: boolean) {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: "America/Lima",
    hour: "2-digit",
    minute: "2-digit",
    ...(conSegundos ? { second: "2-digit" } : {}),
    hourCycle: "h23",
  });
}

/** La hora de Lima de un instante ISO: «10:20». Un instante ilegible se devuelve tal cual. */
export function formatearHoraDeLima(iso: string): string {
  const fecha = new Date(iso);
  return Number.isNaN(fecha.getTime()) ? iso : formatoDeHora(false).format(fecha);
}

/** Lo mismo con segundos: «10:20:35». Es la precisión de «Última lectura». */
export function formatearHoraConSegundos(iso: string): string {
  const fecha = new Date(iso);
  return Number.isNaN(fecha.getTime()) ? iso : formatoDeHora(true).format(fecha);
}

/** La tasa de rechazo (de 0 a 1) como porcentaje con un decimal: «2.9 %». Ausente es «—», nunca «0 %»: sin resueltos no hay tasa que mostrar. */
export function formatearTasa(tasa: number | undefined): string {
  return tasa === undefined ? "—" : `${(tasa * 100).toFixed(1)} %`;
}

/** Una espera en palabras cortas: «45 s», «7 min», «2 h», «1 h 5 min». Redondea hacia abajo y nunca muestra negativos. */
export function duracionEnPalabras(segundos: number): string {
  const s = Math.max(0, Math.floor(segundos));
  if (s < 60) return `${s} s`;
  const minutos = Math.floor(s / 60);
  if (minutos < 60) return `${minutos} min`;
  const horas = Math.floor(minutos / 60);
  const resto = minutos % 60;
  return resto === 0 ? `${horas} h` : `${horas} h ${resto} min`;
}

/** Qué fracción del alto ocupa una barra de {@code total} cuando la más alta tiene {@code maximo}. Sin nada que mostrar, cero (nunca NaN). */
export function proporcionDeBarra(total: number, maximo: number): number {
  return maximo <= 0 ? 0 : Math.min(1, Math.max(0, total / maximo));
}
