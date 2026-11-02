import type { AvisoSunat } from "@/lib/api/types";

const PREFIJO = "khipu:avisos-de-emision:";

/**
 * Los avisos de SUNAT (4013, 4014) llegan solo en la respuesta de la emisión; `GET /v1/facturas/{id}` no los trae. Para que la ficha del comprobante recién emitido
 * los muestre, el formulario los deja aquí antes de navegar. `sessionStorage` vive lo que la pestaña: sobrevive a recargar la ficha y no pasa a otra pestaña ni a otro
 * navegador. Nada de esto es un dato de verdad (el backend no lo guarda), así que cualquier fallo del almacenamiento se ignora: sin aviso la pantalla es la de siempre.
 */
export function guardarAvisosDeEmision(id: string, avisos: AvisoSunat[] | null | undefined): void {
  const validos = (avisos ?? []).filter(esAviso);
  if (validos.length === 0) return;
  try {
    window.sessionStorage.setItem(PREFIJO + id, JSON.stringify(validos));
  } catch {
    // Almacenamiento bloqueado o lleno (ventana privada, cuota): el comprobante se emitió igual, solo no se recuerda el aviso.
  }
}

/** El texto guardado para un comprobante (`""` si no hay o no se puede leer). Es lo que `useSyncExternalStore` compara por valor. */
export function leerAvisosDeEmisionCrudos(id: string): string {
  try {
    return window.sessionStorage.getItem(PREFIJO + id) ?? "";
  } catch {
    return "";
  }
}

export function avisosDeEmision(crudos: string): AvisoSunat[] {
  if (!crudos) return [];
  try {
    const datos: unknown = JSON.parse(crudos);
    return Array.isArray(datos) ? datos.filter(esAviso) : [];
  } catch {
    return [];
  }
}

function esAviso(a: unknown): a is AvisoSunat {
  return typeof a === "object" && a !== null && typeof (a as AvisoSunat).codigo === "string" && typeof (a as AvisoSunat).mensaje === "string";
}
