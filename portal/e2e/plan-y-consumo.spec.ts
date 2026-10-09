import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
});

/**
 * C1: el menú mostraba «Plan y consumo — / —» fijo y el cliente no veía su plan en ningún lado. La cuenta demo del mock está en el plan por defecto (Gratis, 30
 * documentos) y lleva 20 este mes.
 */
test("el menú muestra el plan y el consumo del mes, y lleva al detalle", async ({ page }) => {
  const bloque = page.getByRole("link", { name: /^Plan Gratis: 20 \/ 30 documentos este mes/ });
  await expect(bloque).toBeVisible();
  await expect(page.getByText("— / —")).toHaveCount(0);

  await bloque.click();
  await expect(page).toHaveURL(/\/cuenta\/plan$/);
  await expect(page.getByRole("navigation", { name: "Ubicación" })).toContainText("Cuenta");
  await expect(page.getByRole("heading", { name: "Plan Gratis" })).toBeVisible();
  await expect(page.getByText("Al día")).toBeVisible();
  await expect(page.getByText("No vence")).toBeVisible();
  await expect(page.getByTestId("consumo-del-mes")).toHaveText("20 / 30");
  await expect(page.getByRole("meter", { name: "Consumo del tope del mes" })).toHaveAttribute("aria-valuenow", "67");
  await expect(page.getByText("30 documentos al mes · 1 RUC · 1 usuario · 1 API key · 1 año de retención")).toBeVisible();
});

test("«Plan y consumo» también está en el menú, en el grupo de la cuenta", async ({ page }) => {
  await page.getByRole("link", { name: "Plan y consumo" }).click();
  await expect(page).toHaveURL(/\/cuenta\/plan$/);
  await expect(page).toHaveTitle("Plan y consumo · Portal de facturación electrónica");
});

/** C7: el menú de usuario decía «demo» (lo de antes de la @ del correo) en vez del nombre de la cuenta. */
test("el menú de usuario muestra el nombre de la cuenta y sus iniciales", async ({ page }) => {
  const disparador = page.getByRole("button", { name: "Menú de usuario" });
  await expect(disparador).toContainText("Negocio Demo");
  await expect(disparador).toContainText("ND");
  await expect(disparador).toContainText("demo@example.com");
});
