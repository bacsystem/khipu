import { expect, type Page } from "@playwright/test";

/**
 * Espera a que React haya hidratado el control. Tras `page.goto` la página ya muestra el HTML del servidor, pero sus
 * `onChange` aún no existen: un `fill`/`selectOption` en esa ventana se pierde, porque al hidratar React restaura el
 * valor del estado. En el dev server (Turbopack) la ventana dura de cientos de ms a un par de segundos.
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
