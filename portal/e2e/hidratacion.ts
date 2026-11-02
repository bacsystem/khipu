import { expect, type Page } from "@playwright/test";

/**
 * Espera a que React haya hidratado el control. Tras `page.goto` la página ya muestra el HTML del servidor, pero sus
 * `onChange` aún no existen: un `fill`/`selectOption` en esa ventana se pierde, porque al hidratar React restaura el
 * valor del estado. En el dev server (Turbopack) la ventana dura de cientos de ms a un par de segundos.
 *
 * Úsese solo cuando la página no ofrece otra señal de que ya hidrató (p. ej. un control que se habilita al cargar
 * datos en un `useEffect`, como «Motivo» en el formulario de nota): comprueba `__reactProps$…`, una clave interna de
 * React, así que si una versión futura la renombra la espera agota su timeout en vez de fallar con un mensaje claro.
 */
export async function esperarHidratacion(page: Page, selector: string) {
  await expect
    .poll(() =>
      page.evaluate((s) => {
        const el = document.querySelector(s);
        return !!el && Object.keys(el).some((k) => k.startsWith("__reactProps"));
      }, selector),
    )
    .toBe(true);
}
