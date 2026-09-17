import { expect, test } from "@playwright/test";

test("inicia sesión y llega a comprobantes", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();

  await expect(page).toHaveURL(/\/comprobantes/);
  // El selector de empresa muestra la empresa activa (razón social + RUC del mock).
  await expect(page.getByRole("combobox", { name: "Cambiar de empresa" })).toContainText("Demo SAC");
});

test("credenciales inválidas muestran el error sin salir del login", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("clave-incorrecta");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();

  await expect(page.getByText("Correo o contraseña incorrectos.")).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});
