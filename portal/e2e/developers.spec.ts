import { expect, test } from "@playwright/test";

test("la referencia de API carga sin sesión y muestra un endpoint real", async ({ page }) => {
  const errores: string[] = [];
  page.on("console", (msg) => {
    if (msg.type() === "error") errores.push(msg.text());
  });

  await page.goto("/developers");

  await expect(page.getByText("/v1/facturas").first()).toBeVisible({ timeout: 10_000 });
  expect(errores).toEqual([]);
});

test("con sesión, el botón genera una key de prueba", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);

  await page.goto("/developers");
  await page.getByRole("button", { name: "Generar API key de prueba" }).click();

  await expect(page.getByText(/^fk_/)).toBeVisible();
});

test("la guía, los catálogos y los errores se navegan desde la cabecera", async ({ page }) => {
  await page.goto("/developers/guia");
  await expect(page.getByRole("heading", { name: "Guía de emisión de facturas" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Venta gravada al contado (el caso base)" })).toBeVisible();

  await page.getByRole("link", { name: "Catálogos SUNAT" }).first().click();
  await expect(page).toHaveURL(/\/developers\/catalogos/);
  await expect(page.getByText("Gravado - Operación Onerosa")).toBeVisible();
  await page.getByLabel("Buscar en los catálogos").fill("exonerado");
  await expect(page.getByText("Exonerado - Operación Onerosa")).toBeVisible();
  await expect(page.getByText("Gravado - Operación Onerosa")).toHaveCount(0);

  await page.getByRole("link", { name: "Errores y estados" }).first().click();
  await expect(page.getByRole("heading", { name: "Errores y estados" })).toBeVisible();
  await expect(page.getByText("FORMA_PAGO_INVALIDA", { exact: true })).toBeVisible();
});
