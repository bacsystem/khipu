import { expect, test } from "@playwright/test";

/**
 * #107: SUNAT rechazó las credenciales SOL de la empresa (sembrada así en el mock, con cuenta propia). Antes el cliente solo veía comprobantes
 * en «error de envío» uno por uno; ahora el panel dice qué pasó, que los envíos esperan, y al guardar credenciales nuevas el aviso se va.
 */
test("con las credenciales SOL rechazadas el panel lo dice, y guardarlas de nuevo lo levanta", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("sol-rechazada@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);

  // Emitir sigue permitido (se firma y queda guardado), pero el diálogo avisa que el envío espera.
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByText("SUNAT rechazó las credenciales SOL de esta empresa")).toBeVisible();
  await expect(dialogo.getByLabel("Serie")).toBeVisible();
  await dialogo.getByRole("link", { name: "Corregirlas" }).click();
  await expect(page).toHaveURL(/\/empresa\?seccion=sol/);

  const aviso = page.getByTestId("credenciales-sol-rechazadas");
  await expect(aviso).toContainText("SUNAT rechazó tus credenciales SOL");
  await expect(aviso).toContainText("0102 - Usuario o contrasena incorrectos");
  await expect(page.getByText("RECHAZADAS", { exact: true })).toBeVisible();

  await page.getByLabel("Usuario SOL secundario").fill("NUEVOUSR");
  await page.getByLabel("Clave SOL").fill("clave-nueva");
  await page.getByRole("button", { name: "Reemplazar credenciales" }).click();

  await expect(aviso).toHaveCount(0);
  await expect(page.getByText("CONFIGURADAS", { exact: true })).toBeVisible();
  // Guardar refresca la página: sigue en la misma pestaña, que ya no está pendiente.
  await expect(page.getByRole("tab", { name: "Credenciales SOL" })).toHaveAttribute("aria-selected", "true");
});
