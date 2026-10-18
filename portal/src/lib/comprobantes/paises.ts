/**
 * Países (catálogo 04, ISO 3166-1 alfa-2). La API no sirve este catálogo y valida con la lista de Java (`Locale.getISOCountries()`), que es esta. Los nombres salen del
 * navegador en español, con el código como respaldo si no los tiene.
 */
export const CODIGOS_PAIS = [
  "AD", "AE", "AF", "AG", "AI", "AL", "AM", "AO", "AQ", "AR", "AS", "AT", "AU", "AW", "AX", "AZ", "BA", "BB", "BD", "BE", "BF", "BG", "BH", "BI", "BJ", "BL", "BM", "BN", "BO",
  "BQ", "BR", "BS", "BT", "BV", "BW", "BY", "BZ", "CA", "CC", "CD", "CF", "CG", "CH", "CI", "CK", "CL", "CM", "CN", "CO", "CR", "CU", "CV", "CW", "CX", "CY", "CZ", "DE", "DJ",
  "DK", "DM", "DO", "DZ", "EC", "EE", "EG", "EH", "ER", "ES", "ET", "FI", "FJ", "FK", "FM", "FO", "FR", "GA", "GB", "GD", "GE", "GF", "GG", "GH", "GI", "GL", "GM", "GN", "GP",
  "GQ", "GR", "GS", "GT", "GU", "GW", "GY", "HK", "HM", "HN", "HR", "HT", "HU", "ID", "IE", "IL", "IM", "IN", "IO", "IQ", "IR", "IS", "IT", "JE", "JM", "JO", "JP", "KE", "KG",
  "KH", "KI", "KM", "KN", "KP", "KR", "KW", "KY", "KZ", "LA", "LB", "LC", "LI", "LK", "LR", "LS", "LT", "LU", "LV", "LY", "MA", "MC", "MD", "ME", "MF", "MG", "MH", "MK", "ML",
  "MM", "MN", "MO", "MP", "MQ", "MR", "MS", "MT", "MU", "MV", "MW", "MX", "MY", "MZ", "NA", "NC", "NE", "NF", "NG", "NI", "NL", "NO", "NP", "NR", "NU", "NZ", "OM", "PA", "PE",
  "PF", "PG", "PH", "PK", "PL", "PM", "PN", "PR", "PS", "PT", "PW", "PY", "QA", "RE", "RO", "RS", "RU", "RW", "SA", "SB", "SC", "SD", "SE", "SG", "SH", "SI", "SJ", "SK", "SL",
  "SM", "SN", "SO", "SR", "SS", "ST", "SV", "SX", "SY", "SZ", "TC", "TD", "TF", "TG", "TH", "TJ", "TK", "TL", "TM", "TN", "TO", "TR", "TT", "TV", "TW", "TZ", "UA", "UG", "UM",
  "US", "UY", "UZ", "VA", "VC", "VE", "VG", "VI", "VN", "VU", "WF", "WS", "YE", "YT", "ZA", "ZM", "ZW",
] as const;

export type Pais = { codigo: string; nombre: string };

/** Los países ordenados por su nombre en español; los más habituales en exportaciones del Perú van primero. */
export function listarPaises(): Pais[] {
  let nombres: Intl.DisplayNames | null = null;
  try {
    nombres = new Intl.DisplayNames(["es"], { type: "region" });
  } catch {
    nombres = null;
  }
  const todos: Pais[] = CODIGOS_PAIS.map((codigo) => ({ codigo, nombre: nombres?.of(codigo) ?? codigo }));
  const habituales = ["US", "CL", "CO", "EC", "BO", "BR", "MX", "ES", "CN", "CA"];
  const primeros = habituales.map((c) => todos.find((p) => p.codigo === c)).filter((p): p is Pais => p !== undefined);
  const resto = todos.filter((p) => !habituales.includes(p.codigo)).sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
  return [...primeros, ...resto];
}
