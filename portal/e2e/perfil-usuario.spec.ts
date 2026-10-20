import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/inicio$/);
});

test("el menú de usuario muestra la cuenta y permite pedir el cambio de contraseña", async ({ page }) => {
  await page.getByRole("button", { name: "Menú de usuario" }).click();
  const menu = page.getByRole("menu");
  await expect(menu.getByText("demo@example.com")).toBeVisible();
  await expect(menu.getByText("Administrador")).toBeVisible();

  await menu.getByRole("menuitem", { name: "Cambiar contraseña" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByText("Cambiar contraseña")).toBeVisible();
  // #105: el pedido va al mismo `/api/auth/recuperar` del flujo público, con el correo de la sesión. Si el BFF no recibe un 202 del backend
  // (sin mock de `/v1/auth/recuperar` iba a un backend inexistente), esto falla diciendo el estado, no solo «no apareció el texto».
  const pedido = page.waitForResponse((r) => r.url().endsWith("/api/auth/recuperar") && r.request().method() === "POST");
  await dialogo.getByRole("button", { name: "Enviar enlace" }).click();
  const respuesta = await pedido;
  expect(respuesta.request().postDataJSON()).toEqual({ email: "demo@example.com" });
  expect(respuesta.status()).toBe(202);
  await expect(dialogo.getByRole("alert")).toHaveCount(0);
  await expect(dialogo.getByRole("status").filter({ hasText: "Enlace enviado a" })).toContainText("Enlace enviado a demo@example.com");
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
