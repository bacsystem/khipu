import { expect, test } from "@playwright/test";

test("sin sesión, /admin redirige al login del backoffice", async ({ page }) => {
  await page.goto("/admin");
  await expect(page).toHaveURL(/\/admin\/login/);
});

test("inicia sesión de administrador y ve el shell del backoffice", async ({ page }) => {
  await page.goto("/admin/login");
  await page.getByLabel("Correo electrónico").fill("admin@khipu.pe");
  await page.getByLabel("Contraseña").fill("AdminPass1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();

  await expect(page).toHaveURL(/\/admin$/);
  await expect(page.getByRole("heading", { name: "Backoffice" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Inicio" })).toBeVisible();
});

test("credenciales inválidas muestran el error sin salir del login del backoffice", async ({ page }) => {
  await page.goto("/admin/login");
  await page.getByLabel("Correo electrónico").fill("admin@khipu.pe");
  await page.getByLabel("Contraseña").fill("clave-incorrecta");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();

  await expect(page.getByText("Correo o contraseña incorrectos.")).toBeVisible();
  await expect(page).toHaveURL(/\/admin\/login/);
});

test("cerrar sesión del backoffice vuelve a exigir login", async ({ page }) => {
  await page.goto("/admin/login");
  await page.getByLabel("Correo electrónico").fill("admin@khipu.pe");
  await page.getByLabel("Contraseña").fill("AdminPass1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/admin$/);

  await page.getByRole("button", { name: "Menú de administrador" }).click();
  await page.getByRole("menuitem", { name: "Cerrar sesión" }).click();
  await expect(page).toHaveURL(/\/admin\/login/);

  await page.goto("/admin");
  await expect(page).toHaveURL(/\/admin\/login/);
});

/** Una sesión de cliente (tenant) no debe abrir el backoffice: el JWT de admin exige el claim tipo=plataforma. */
test("una sesión de cliente no da acceso al backoffice", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);

  await page.goto("/admin");
  await expect(page).toHaveURL(/\/admin\/login/);
});
