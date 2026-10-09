import { expect, test } from "@playwright/test";
import { credencialesDeAdmin, entrarComoAdmin } from "./admin-sesion";

test("sin sesión, /admin redirige al login del backoffice", async ({ page }) => {
  await page.goto("/admin");
  await expect(page).toHaveURL(/\/admin\/login/);
});

test("inicia sesión de administrador con el segundo factor y ve el shell del backoffice", async ({ page }) => {
  await entrarComoAdmin(page);

  await expect(page.getByRole("heading", { name: "Backoffice" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Inicio" })).toBeVisible();
});

/** #177: la contraseña sola no abre el backoffice. */
test("con la contraseña sola no hay sesión: el backoffice sigue pidiendo login", async ({ page }) => {
  await credencialesDeAdmin(page);
  await expect(page.getByRole("heading", { name: "Verificación en dos pasos" })).toBeVisible();

  await page.goto("/admin");
  await expect(page).toHaveURL(/\/admin\/login/);
});

test("credenciales inválidas muestran el error sin salir del login del backoffice", async ({ page }) => {
  await credencialesDeAdmin(page, "admin@khipu.pe", "clave-incorrecta");

  await expect(page.getByText("Correo o contraseña incorrectos.")).toBeVisible();
  await expect(page).toHaveURL(/\/admin\/login/);
});

/** #261: la contraseña del backoffice tampoco se prueba sin freno. Correo propio del test: los specs corren en paralelo contra el mismo mock. */
test("tras cinco contraseñas erróneas el backoffice pide esperar 15 minutos", async ({ page }) => {
  for (let i = 0; i < 5; i++) {
    await credencialesDeAdmin(page, "bloqueo-admin@khipu.pe", `incorrecta-${i}`);
    await expect(page.getByText("Correo o contraseña incorrectos.")).toBeVisible();
  }
  await credencialesDeAdmin(page, "bloqueo-admin@khipu.pe", "incorrecta-5");

  await expect(page.getByText("Demasiados intentos fallidos. Por seguridad, espera 15 minutos antes de volver a intentarlo.")).toBeVisible();
  await expect(page).toHaveURL(/\/admin\/login/);
});

test("un código equivocado se puede reintentar sin volver a la contraseña", async ({ page }) => {
  await credencialesDeAdmin(page);
  await page.getByLabel("Código de 6 dígitos").fill("000000");
  await page.getByRole("button", { name: "Verificar" }).click();
  await expect(page.getByTestId("verificar-segundo-factor").getByRole("alert")).toContainText("El código no es válido");

  await page.getByLabel("Código de 6 dígitos").fill("123456");
  await page.getByRole("button", { name: "Verificar" }).click();
  await expect(page).toHaveURL(/\/admin$/);
});

test("demasiados intentos lo dicen con el tiempo de espera", async ({ page }) => {
  await credencialesDeAdmin(page);
  await page.getByLabel("Código de 6 dígitos").fill("999999");
  await page.getByRole("button", { name: "Verificar" }).click();
  await expect(page.getByTestId("verificar-segundo-factor").getByRole("alert")).toContainText("Espera 15 minutos");
});

test("se puede entrar con un código de recuperación", async ({ page }) => {
  await credencialesDeAdmin(page);
  await page.getByRole("button", { name: "Usar un código de recuperación" }).click();
  await page.getByLabel("Código de recuperación").fill("abcde-fghjk");
  await page.getByRole("button", { name: "Verificar" }).click();
  await expect(page).toHaveURL(/\/admin$/);
});

test("el primer login configura la app con el QR y muestra los códigos de recuperación una vez", async ({ page }) => {
  await credencialesDeAdmin(page, "nuevo@khipu.pe");

  await expect(page.getByRole("heading", { name: "Configura la verificación en dos pasos" })).toBeVisible();
  await expect(page.getByRole("img", { name: /Código QR/ })).toBeVisible();
  await expect(page.getByTestId("secreto")).toHaveText("JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP");
  const activar = page.getByRole("button", { name: "Activar y entrar" });
  await expect(activar).toBeDisabled();

  await page.getByLabel("Código de 6 dígitos").fill("123456");
  await activar.click();

  await expect(page.getByRole("heading", { name: "Guarda tus códigos de recuperación" })).toBeVisible();
  await expect(page.getByRole("list", { name: "Guarda tus códigos de recuperación" }).getByRole("listitem")).toHaveCount(10);
  await expect(page).toHaveURL(/\/admin\/login/);

  await page.getByRole("button", { name: "Ya los guardé, entrar" }).click();
  await expect(page).toHaveURL(/\/admin$/);
});

test("el campo del código solo acepta seis dígitos", async ({ page }) => {
  await credencialesDeAdmin(page);
  const campo = page.getByLabel("Código de 6 dígitos");
  await campo.fill("12ab345678");
  await expect(campo).toHaveValue("123456");
});

test("cerrar sesión del backoffice vuelve a exigir login", async ({ page }) => {
  await entrarComoAdmin(page);

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
