const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Lo que llega por la URL no se pega en la llamada al backend sin mirarlo: `../auth/me` o un espacio no son un id. Un UUID en cualquier caja,
 * con sus guiones y nada más.
 */
export function esUuid(valor: string): boolean {
  return UUID.test(valor);
}
