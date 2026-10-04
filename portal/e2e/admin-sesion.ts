import { expect, type Page } from "@playwright/test";

/** Contraseña y la pantalla de credenciales del backoffice, sin completar el segundo factor (#177). */
export async function credencialesDeAdmin(page: Page, email = "admin@khipu.pe", password = "AdminPass1") {
  await page.goto("/admin/login");
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
}

/** Login completo de un administrador con el segundo factor ya configurado. El mock acepta `123456` como código de la app. */
export async function entrarComoAdmin(page: Page) {
  await credencialesDeAdmin(page);
  await page.getByLabel("Código de 6 dígitos").fill("123456");
  await page.getByRole("button", { name: "Verificar" }).click();
  await expect(page).toHaveURL(/\/admin$/);
}
