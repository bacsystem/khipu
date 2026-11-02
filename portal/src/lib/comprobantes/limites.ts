/**
 * Tope de líneas de un comprobante (`Comprobante.MAX_LINEAS` en el backend): el número de orden del ítem es de hasta 3 dígitos. SUNAT rechaza más con 2023 en la
 * factura y la boleta y con 2137 en las notas, con el número ya consumido; el backend responde `422 ITEM_INVALIDO` antes de numerar.
 */
export const MAX_LINEAS = 999;
