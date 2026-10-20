import { expect, test } from "@playwright/test";

/**
 * #318: las credenciales API SUNAT (client_id y client_secret de «Credenciales de API SUNAT» en SOL) se guardan en la pestaña de las SOL, nunca se muestran
 * y se pueden quitar. Termina quitándolas: el mock es compartido y la empresa demo queda como estaba.
 */
test("las credenciales API SUNAT se guardan, no se muestran y se quitan", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
  await page.goto("/empresa?seccion=sol");

  const seccion = page.getByTestId("credenciales-api-sunat");
  await expect(seccion).toContainText("Opcionales");

  // Un client_id con espacios internos lo rechaza el backend (mock con la misma regla), y el mensaje se ve.
  await seccion.getByLabel("Id (client_id)").fill("id con espacios");
  await seccion.getByLabel("Clave (client_secret)").fill("secreto-e2e");
  await seccion.getByRole("button", { name: "Guardar credenciales" }).click();
  await expect(seccion.getByText(/solo admite letras/)).toBeVisible();

  await seccion.getByLabel("Id (client_id)").fill("d20b421e-4ace-46d8-ac8c-4fbb44cfde17");
  await seccion.getByLabel("Clave (client_secret)").fill("secreto-e2e");
  await seccion.getByRole("button", { name: "Guardar credenciales" }).click();

  await expect(seccion).toContainText("Configuradas", { timeout: 20_000 });
  await expect(seccion.getByLabel("Clave (client_secret)")).toHaveValue("");
  await expect(page.getByText("secreto-e2e")).toHaveCount(0);

  await seccion.getByRole("button", { name: "Quitar credenciales" }).click();
  // El dev server compila la ruta la primera vez: con la máquina cargada pasa del tope por defecto de 5 s.
  await expect(seccion).toContainText("Opcionales", { timeout: 20_000 });
  await expect(seccion.getByRole("button", { name: "Quitar credenciales" })).toHaveCount(0);
});
