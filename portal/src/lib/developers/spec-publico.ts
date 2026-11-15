/**
 * El OpenAPI con la URL pública de la API como único servidor (#678).
 *
 * springdoc escribe en `servers` la URL por la que le llegó la petición, y el portal lee el spec por la interna
 * (`API_BASE_URL`, red privada). Scalar usa esa entrada antes que su `baseServerURL` (que solo completa URLs relativas),
 * así que «Send» iría a una dirección que el navegador no alcanza. Se reemplaza en el servidor, en una copia, para que
 * la URL interna tampoco llegue al HTML ni al OpenAPI que se descarga desde la referencia.
 */
export function conServidorPublico(spec: Record<string, unknown>, urlPublica: string): Record<string, unknown> {
  return { ...spec, servers: [{ url: urlPublica }] };
}
