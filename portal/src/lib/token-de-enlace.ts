/**
 * El token de un enlace de correo (verificar, invitación), tal como llegó en el segmento de la ruta. Un cliente de correo puede haberlo reescrito codificado
 * (`%2D`…), así que se decodifica; pero un `%` suelto (`%E0%A4%A`) hace que `decodeURIComponent` lance `URIError` y la página caía en el error genérico. En ese caso
 * devuelve `null` y la página dice «enlace inválido», como con cualquier token que no sirve.
 */
export function tokenDeEnlace(segmento: string): string | null {
  try {
    return decodeURIComponent(segmento);
  } catch {
    return null;
  }
}
