/** Aviso, dentro del navegador, de que la lista de clientes guardados cambió (alta, cambio o baja): la tabla vuelve a leerla. El botón de «Nuevo cliente» vive en la barra superior, lejos de la tabla. */
export const EVENTO_CLIENTES = "khipu:clientes-cambiaron";

export function avisarQueCambiaronLosClientes() {
  window.dispatchEvent(new Event(EVENTO_CLIENTES));
}

export function alCambiarLosClientes(manejador: () => void): () => void {
  window.addEventListener(EVENTO_CLIENTES, manejador);
  return () => window.removeEventListener(EVENTO_CLIENTES, manejador);
}
