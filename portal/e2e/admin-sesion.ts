import { expect, type Page } from "@playwright/test";

/** Contraseña y la pantalla de credenciales del backoffice, sin completar el segundo factor (#177). */
export async function credencialesDeAdmin(page: Page, email = "admin@khipu.pe", password = "AdminPass1") {
  await page.goto("/admin/login");
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
}

/** El menú lateral del backoffice: las tarjetas del Inicio (H1) repiten los nombres de sus enlaces. */
export function menuAdmin(page: Page) {
  return page.getByRole("navigation", { name: "Menú del backoffice" });
}

/** Abre una pestaña del detalle de cuenta o empresa (H19); su nombre puede llevar el contador detrás («Usuarios 4»). */
export async function abrirPestana(page: Page, etiqueta: string) {
  const pestana = page.getByRole("tab", { name: new RegExp(`^${etiqueta}`) });
  // Un clic antes de que React hidrate las pestañas se pierde: se reintenta hasta que quede seleccionada.
  await expect(async () => {
    await pestana.click();
    await expect(pestana).toHaveAttribute("aria-selected", "true", { timeout: 1_000 });
  }).toPass();
}

/** Login completo de un administrador con el segundo factor ya configurado. El mock acepta `123456` como código de la app. */
export async function entrarComoAdmin(page: Page) {
  await credencialesDeAdmin(page);
  await page.getByLabel("Código de 6 dígitos").fill("123456");
  await page.getByRole("button", { name: "Verificar" }).click();
  await expect(page).toHaveURL(/\/admin$/);
}
