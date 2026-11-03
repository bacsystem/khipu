import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";

/**
 * Elige una opción de un combo del design system (`SelectCampo`, el `Select` de base-ui): abre la lista con el disparador y hace clic en la
 * opción por su nombre. Es lo que reemplaza a `fireEvent.change(select, { target: { value } })` del `<select>` nativo, que ya no hay.
 *
 * `combo` es el disparador (`getByRole("combobox", { name })` o `getByLabelText(etiqueta)`); `opcion`, el texto visible de la opción.
 */
export async function elegirOpcion(combo: HTMLElement, opcion: string | RegExp) {
  await act(async () => {
    fireEvent.click(combo);
  });
  const lista = await screen.findByRole("listbox");
  const item = within(lista).getByRole("option", { name: opcion });
  // base-ui solo acepta el clic de un mouse que empezó sobre la opción (al abrir, una opción puede quedar bajo el cursor): de ahí el `pointerDown`.
  await act(async () => {
    fireEvent.pointerDown(item, { pointerType: "mouse" });
    fireEvent.click(item);
  });
  // Hasta que la lista se va, base-ui deja el resto de la página oculto a los lectores (`aria-hidden`): la siguiente consulta por rol no lo encontraría.
  await waitFor(() => {
    if (screen.queryByRole("listbox")) throw new Error("la lista del combo sigue abierta");
  });
}

/** Los textos de las opciones de un combo, en orden (abre la lista y la vuelve a cerrar con Escape). */
export async function opcionesDe(combo: HTMLElement): Promise<string[]> {
  await act(async () => {
    fireEvent.click(combo);
  });
  const lista = await screen.findByRole("listbox");
  const textos = within(lista).getAllByRole("option").map((o) => o.textContent?.trim() ?? "");
  await act(async () => {
    fireEvent.keyDown(lista, { key: "Escape" });
  });
  await waitFor(() => {
    if (screen.queryByRole("listbox")) throw new Error("la lista del combo sigue abierta");
  });
  return textos;
}
