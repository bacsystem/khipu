/** Aviso, dentro del navegador, de que la lista de productos guardados cambió (alta, cambio o baja): la tabla vuelve a leerla. El botón de «Nuevo producto» vive en la barra superior, lejos de la tabla. */
export const EVENTO_PRODUCTOS = "khipu:productos-cambiaron";

export function avisarQueCambiaronLosProductos() {
  window.dispatchEvent(new Event(EVENTO_PRODUCTOS));
}

export function alCambiarLosProductos(manejador: () => void): () => void {
  window.addEventListener(EVENTO_PRODUCTOS, manejador);
  return () => window.removeEventListener(EVENTO_PRODUCTOS, manejador);
}
