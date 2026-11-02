import { act, fireEvent } from "@testing-library/react";
import { vi } from "vitest";
import { ESPERA_DE_BUSQUEDA_MS } from "@/components/compartidos/busqueda-del-listado";

/**
 * Solo para Vitest: escribe en un `BusquedaDelListado` y deja pasar la pausa con la que aplica la búsqueda (no hay botón «Buscar» ni envío de formulario). Usa relojes
 * falsos solo mientras tanto, así no interfiere con lo que espere con relojes reales (`elegirOpcion`, `waitFor`).
 */
export function buscarEnListado(campo: HTMLElement, texto: string) {
  vi.useFakeTimers();
  try {
    fireEvent.change(campo, { target: { value: texto } });
    act(() => void vi.advanceTimersByTime(ESPERA_DE_BUSQUEDA_MS));
  } finally {
    vi.useRealTimers();
  }
}
