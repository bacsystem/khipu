import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
});

test("lista comprobantes con su estado y permite ver el detalle", async ({ page }) => {
  const tabla = page.locator("table");
  await expect(page.getByText("F001-00000001")).toBeVisible();
  // Escapado a la tabla: el filtro de estado tiene un <option> con el mismo texto.
  await expect(tabla.getByText("Aceptado", { exact: true })).toBeVisible();
  await expect(tabla.getByText("Error de envío")).toBeVisible();

  await page.getByRole("link", { name: /Ver comprobante F001-00000001/ }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-aceptada/);
  await expect(page.getByText("Descargar XML")).toBeVisible();
});

test("reenvía un comprobante en error y queda aceptado", async ({ page }) => {
  await page.goto("/comprobantes/f-error");
  await expect(page.getByText("Error de envío")).toBeVisible();

  await page.getByRole("button", { name: "Reenviar" }).click();

  await expect(page.getByText("Aceptado", { exact: true })).toBeVisible();
});
