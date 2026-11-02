import { expect, type Page } from "@playwright/test";

/**
 * Hace clic fuera del modal abierto, en el borde de la pantalla. Antes espera a que termine de abrir (base-ui ignora los clics fuera
 * durante la animación de entrada) y después espera más que la animación de salida (150 ms): sin esa espera, un modal que sí se está
 * cerrando todavía se ve, y una aserción de «sigue abierto» pasaba por casualidad (pasó con «Nueva serie» y con el alta de cuenta).
 */
export async function clicFuera(page: Page) {
  await expect(page.locator('[data-slot="dialog-content"]')).not.toHaveAttribute("data-starting-style");
  await page.mouse.click(8, 300);
  await page.waitForTimeout(500);
}
