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

/** #261: un correo propio del test, para que los specs en paralelo contra el mismo mock no se bloqueen entre sí. */
test("tras cinco contraseñas erróneas pide esperar 15 minutos en vez de seguir probando", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("bloqueo-login@example.com");
  const password = page.getByLabel("Contraseña");
  const enviar = page.getByRole("button", { name: "Iniciar sesión" });

  for (let i = 0; i < 5; i++) {
    await password.fill(`incorrecta-${i}`);
    await enviar.click();
    await expect(page.getByText("Correo o contraseña incorrectos.")).toBeVisible();
    await expect(enviar).toBeEnabled();
  }

  await password.fill("incorrecta-5");
  await enviar.click();
  await expect(page.getByText("Demasiados intentos fallidos. Por seguridad, espera 15 minutos antes de volver a intentarlo.")).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});
