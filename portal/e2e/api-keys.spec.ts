import { expect, test } from "@playwright/test";

// Los tests corren en paralelo contra el mismo mock en memoria: las aserciones van por fila, no por conteos globales.
function fila(page: import("@playwright/test").Page, prefijo: string) {
  return page.getByRole("row").filter({ hasText: prefijo });
}

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
  await page.goto("/api-keys");
});

test("abre la prueba de emisión con el ejemplo contra la API real", async ({ page }) => {
  await page.getByRole("button", { name: "Prueba de emisión" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByText("Prueba de emisión por API")).toBeVisible();
  await expect(dialogo.getByText(/curl -X POST .*\/v1\/facturas/)).toBeVisible();
  await dialogo.getByRole("button", { name: "Python" }).click();
  await expect(dialogo.getByText(/import os, requests/)).toBeVisible();
});

test("lista las llaves con su prefijo, fechas y estado", async ({ page }) => {
  const revocada = fila(page, "fk_demo000");
  await expect(revocada.getByText("Revocada", { exact: true })).toBeVisible();
  await expect(revocada.getByText("1 Ago 2026, 10:00")).toBeVisible();
  await expect(revocada.getByText("20 Ago 2026, 07:00")).toBeVisible();
  await expect(revocada.getByRole("button", { name: "Revocar", exact: true })).toHaveCount(0);
  await expect(fila(page, "fk_demo001")).toBeVisible();
});

test("crea una llave, la muestra una sola vez y aparece activa en la lista", async ({ page }) => {
  await page.getByRole("button", { name: "Crear API key" }).click();
  await page.getByRole("button", { name: "Generar llave" }).click();

  const nueva = page.getByTestId("api-key-nueva");
  await expect(nueva).toHaveText(/^fk_/);
  const prefijo = (await nueva.textContent())!.slice(0, 10);
  await expect(page.getByText("no volverá a mostrarse")).toBeVisible();

  await page.getByRole("button", { name: "Listo, la guardé" }).click();
  const creada = fila(page, prefijo);
  await expect(creada).toBeVisible();
  await expect(creada.getByText("Activa", { exact: true })).toBeVisible();
  await expect(creada.getByRole("button", { name: "Revocar", exact: true })).toBeVisible();
});

test("revoca una llave activa tras confirmar", async ({ page }) => {
  const activa = fila(page, "fk_demo001");
  await expect(activa.getByText("Activa", { exact: true })).toBeVisible();

  await activa.getByRole("button", { name: "Revocar", exact: true }).click();
  await expect(activa.getByText("¿Revocar de forma permanente?")).toBeVisible();
  await activa.getByRole("button", { name: "Sí, revocar" }).click();

  await expect(activa.getByText("Revocada", { exact: true })).toBeVisible();
  await expect(activa.getByRole("button", { name: "Revocar", exact: true })).toHaveCount(0);
});
