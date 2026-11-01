import { expect, type Locator } from "@playwright/test";

/**
 * Cómo se maneja una `EntradaFecha` del design system desde v0.5.0 (#500). Ya no es un `<input type="date">` con `value`, `min`, `max` y `fill`: es un botón que abre un calendario
 * en un popover, con un botón por día (`data-iso`, `disabled` fuera de `min`/`max`) y el día elegido en una celda `aria-selected`. `fecha` es el `getByLabel(...)` del botón.
 * El popover también es un `role="dialog"`: los tests que miran `getByRole("dialog")` lo cierran antes de seguir.
 */

/** Abre el calendario de esa fecha. */
export async function abrirCalendario(fecha: Locator): Promise<void> {
  await fecha.click();
  await expect(fecha.page().getByRole("grid")).toBeVisible();
}

/** Cierra el calendario abierto con Escape (cierra el popover y deja el diálogo que lo contiene). */
export async function cerrarCalendario(fecha: Locator): Promise<void> {
  const pagina = fecha.page();
  await pagina.keyboard.press("Escape");
  await expect(pagina.getByRole("grid")).toHaveCount(0);
}

/** Lleva el calendario abierto al mes que muestra `iso`, como el usuario, y devuelve el botón de ese día. */
async function irAlDia(fecha: Locator, iso: string): Promise<Locator> {
  const pagina = fecha.page();
  const dia = pagina.locator(`button[data-iso="${iso}"]`);
  for (let paso = 0; paso < 36 && (await dia.count()) === 0; paso++) {
    const mostrado = (await pagina.locator("button[data-iso]").first().getAttribute("data-iso")) ?? iso;
    await pagina.getByRole("button", { name: iso < mostrado ? "Mes anterior" : "Mes siguiente" }).click();
  }
  await expect(dia).toHaveCount(1);
  return dia;
}

/** La fecha ISO que muestra: el día `aria-selected` de su calendario. */
export async function fechaElegida(fecha: Locator): Promise<string> {
  await abrirCalendario(fecha);
  const iso = await fecha.page().locator('[role="gridcell"][aria-selected="true"] button[data-iso]').getAttribute("data-iso");
  await cerrarCalendario(fecha);
  expect(iso, "el calendario no tiene ningún día elegido").toMatch(/^\d{4}-\d{2}-\d{2}$/);
  return iso as string;
}

/** Si el calendario deja elegir ese día (dentro de `min` y `max`). */
export async function fechaEsElegible(fecha: Locator, iso: string): Promise<boolean> {
  await abrirCalendario(fecha);
  const habilitado = await (await irAlDia(fecha, iso)).isEnabled();
  await cerrarCalendario(fecha);
  return habilitado;
}

/** Elige ese día como el usuario: abre el calendario, navega hasta él y lo pulsa (el popover se cierra solo). */
export async function elegirFecha(fecha: Locator, iso: string): Promise<void> {
  await abrirCalendario(fecha);
  const dia = await irAlDia(fecha, iso);
  await expect(dia, `el día ${iso} está fuera del rango de la fecha`).toBeEnabled();
  await dia.click();
  // Un día de otro mes que asoma en la grilla (el 31 de octubre en la vista de noviembre) no se elige con el primer clic: el foco lleva el calendario a su mes y la
  // grilla se rehace antes de que llegue el clic (v0.5.0). Como el usuario, se vuelve a pulsar cuando el día ya está en su mes.
  const grilla = fecha.page().getByRole("grid");
  if ((await grilla.count()) > 0) {
    await expect(dia).toHaveCount(1);
    await dia.click();
  }
  await expect(grilla).toHaveCount(0);
}

/** Suma días a una fecha ISO (UTC, sin desfase de zona). */
export function sumarDias(iso: string, dias: number): string {
  return new Date(Date.parse(`${iso}T00:00:00Z`) + dias * 86_400_000).toISOString().slice(0, 10);
}
