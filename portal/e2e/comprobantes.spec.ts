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
  // Un aceptado con CDR se etiqueta así en la tabla; el filtro de estado tiene un ítem "Aceptado" a secas.
  await expect(tabla.getByText("Aceptado con CDR", { exact: true })).toBeVisible();
  // El segundo comprobante lo muta el test de reenvío (corre en paralelo): se comprueba la fila, no su estado.
  await expect(tabla.getByText("F001-00000002")).toBeVisible();

  await page.getByRole("link", { name: /Ver comprobante F001-00000001/ }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-aceptada/);
  // El detalle abre el XML firmado en una vista previa con su botón de descarga.
  await page.getByRole("button", { name: "Ver XML" }).click();
  await expect(page.getByText("Descargar XML")).toBeVisible();
});

test("reenvía un comprobante en error y queda aceptado", async ({ page }) => {
  await page.goto("/comprobantes/f-error");
  await expect(page.getByText("Error de envío")).toBeVisible();

  await page.getByRole("button", { name: "Reenviar", exact: true }).click();

  await expect(page.getByText("Aceptado", { exact: true }).first()).toBeVisible();
});
