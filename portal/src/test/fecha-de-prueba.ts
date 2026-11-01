import { fireEvent, screen, waitFor } from "@testing-library/react";
import { expect } from "vitest";

/**
 * Solo para Vitest: cómo se maneja una `EntradaFecha` del design system desde v0.5.0. Ya no es un `<input type="date">` (con `.value`, `.min`, `.max` y `fireEvent.change`):
 * es un botón que abre un calendario en un popover, con un botón por día (`data-iso`, `disabled` fuera de `min`/`max`, `aria-label` con la fecha en texto) y el día
 * elegido en una celda `aria-selected`. `etiqueta` es la del `<label htmlFor>`, que enfoca al botón.
 */

function dia(iso: string): HTMLButtonElement | null {
  return document.querySelector<HTMLButtonElement>(`button[data-iso="${iso}"]`);
}

/** Abre el calendario de la fecha de esa etiqueta. */
export async function abrirCalendario(etiqueta: string | RegExp): Promise<void> {
  fireEvent.click(screen.getByLabelText(etiqueta));
  await screen.findByRole("grid");
}

/** Cierra el calendario abierto (Escape), para no dejar un popover colgado entre dos comprobaciones. */
export async function cerrarCalendario(): Promise<void> {
  fireEvent.keyDown(document.activeElement ?? document.body, { key: "Escape" });
  await waitFor(() => expect(screen.queryByRole("grid")).toBeNull());
}

/** Lleva el calendario abierto al mes que muestra `iso` (como el usuario, con «Mes anterior» / «Mes siguiente») y devuelve su botón. */
async function irAlDia(iso: string): Promise<HTMLButtonElement> {
  for (let paso = 0; paso < 36 && !dia(iso); paso++) {
    const mostrado = document.querySelector<HTMLButtonElement>("button[data-iso]")?.dataset.iso ?? iso;
    fireEvent.click(screen.getByRole("button", { name: iso < mostrado ? "Mes anterior" : "Mes siguiente" }));
  }
  const boton = dia(iso);
  if (!boton) throw new Error(`el calendario no llega al día ${iso}`);
  return boton;
}

/** Elige ese día como lo haría el usuario: abre el calendario, navega hasta él y lo pulsa. Un día fuera de rango está `disabled` y no se puede elegir. */
export async function elegirFecha(etiqueta: string | RegExp, iso: string): Promise<void> {
  await abrirCalendario(etiqueta);
  const boton = await irAlDia(iso);
  expect(boton, `el día ${iso} está fuera del rango de ${etiqueta}`).toBeEnabled();
  fireEvent.click(boton);
  await waitFor(() => expect(screen.queryByRole("grid")).toBeNull());
}

/** La fecha ISO que muestra la fecha de esa etiqueta: el día `aria-selected` de su calendario, o `null` si no hay ninguno. */
export async function fechaElegida(etiqueta: string | RegExp): Promise<string | null> {
  await abrirCalendario(etiqueta);
  const elegido = document.querySelector<HTMLButtonElement>('[role="gridcell"][aria-selected="true"] button[data-iso]');
  const iso = elegido?.dataset.iso ?? null;
  await cerrarCalendario();
  return iso;
}

/** Si el calendario de esa fecha deja elegir ese día (dentro de `min` y `max`). Lo abre, lo mira y lo cierra. */
export async function fechaEsElegible(etiqueta: string | RegExp, iso: string): Promise<boolean> {
  await abrirCalendario(etiqueta);
  const habilitado = !(await irAlDia(iso)).disabled;
  await cerrarCalendario();
  return habilitado;
}
