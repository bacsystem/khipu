import { expect, type Locator } from "@playwright/test";

/**
 * Cómo se maneja un combo del design system (`SelectCampo`/`Select`, #555). Ya no es un `<select>` con `selectOption`: es un botón `role="combobox"` que abre una
 * lista (`role="listbox"`) con una opción por valor. `combo` es el `getByLabel(...)` o `getByRole("combobox", { name })` del botón; `opcion`, el texto visible.
 */
export async function elegirOpcion(combo: Locator, opcion: string | RegExp): Promise<void> {
  const pagina = combo.page();
  await combo.click();
  const lista = pagina.getByRole("listbox");
  await expect(lista).toBeVisible();
  await lista.getByRole("option", { name: opcion, exact: typeof opcion === "string" }).click();
  await expect(lista).toHaveCount(0);
}

/** Lo que muestra el combo (el texto de la opción elegida), lo que antes era `toHaveValue` del `<select>`. */
export async function esperarOpcion(combo: Locator, texto: string | RegExp): Promise<void> {
  await expect(combo).toHaveText(texto);
}
