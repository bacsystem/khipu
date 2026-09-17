import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
});

test("el menú de usuario muestra la cuenta y permite pedir el cambio de contraseña", async ({ page }) => {
  await page.getByRole("button", { name: "Menú de usuario" }).click();
  const menu = page.getByRole("menu");
  await expect(menu.getByText("demo@example.com")).toBeVisible();
  await expect(menu.getByText("Administrador")).toBeVisible();

  await menu.getByRole("menuitem", { name: "Cambiar contraseña" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByText("Cambiar contraseña")).toBeVisible();
  await dialogo.getByRole("button", { name: "Enviar enlace" }).click();
  await expect(dialogo.getByText(/Enlace enviado a/)).toBeVisible();
  await dialogo.getByRole("button", { name: "Entendido" }).click();
  await expect(dialogo).toHaveCount(0);
});

test("cambia el tema desde el menú de usuario", async ({ page }) => {
  await page.getByRole("button", { name: "Menú de usuario" }).click();
  const menu = page.getByRole("menu");
  await menu.getByRole("radio", { name: "Oscuro" }).click();
  await expect(page.locator("html")).toHaveClass(/dark/);
  await menu.getByRole("radio", { name: "Claro" }).click();
  await expect(page.locator("html")).not.toHaveClass(/dark/);
});

test("cierra sesión desde el menú de usuario", async ({ page }) => {
  await page.getByRole("button", { name: "Menú de usuario" }).click();
  await page.getByRole("menuitem", { name: "Cerrar sesión" }).click();
  await expect(page).toHaveURL(/\/login/);
  await page.goto("/comprobantes");
  await expect(page).toHaveURL(/\/login/);
});
