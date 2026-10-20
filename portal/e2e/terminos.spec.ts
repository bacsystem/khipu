import { expect, test } from "@playwright/test";

/** #342: los textos legales son públicos, el registro exige aceptarlos y quien no aceptó la versión vigente no usa el panel hasta aceptarla. */

test("las páginas de términos y privacidad son públicas, dicen que son un borrador y se enlazan desde el pie", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("contentinfo").getByRole("link", { name: "Términos del servicio", exact: true }).click();

  await expect(page).toHaveURL(/\/terminos$/);
  await expect(page.getByRole("heading", { level: 1, name: "Términos del servicio" })).toBeVisible();
  await expect(page.getByTestId("borrador-legal")).toContainText("Borrador pendiente de revisión legal");

  await page.getByRole("main").getByRole("link", { name: "Política de privacidad", exact: true }).click();
  await expect(page).toHaveURL(/\/privacidad$/);
  await expect(page.getByRole("heading", { level: 1, name: "Política de privacidad" })).toBeVisible();
  await expect(page.getByRole("heading", { name: /Tus derechos/ })).toBeVisible();
});

test("el registro no se envía sin aceptar los términos, y con ellos crea la cuenta", async ({ page }) => {
  const email = `terminos-registro-${Date.now()}@example.com`;
  await page.goto("/registro");
  await page.getByLabel("Nombre de la cuenta").fill("Bodega de Prueba");
  await page.getByLabel(/Celular/).fill("987654321");
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill("Passw0rd1");

  const casilla = page.getByRole("checkbox", { name: /Acepto los términos del servicio y la política de privacidad/ });
  await expect(casilla).not.toBeChecked();
  await expect(page.getByTestId("borrador-legal-casilla")).toContainText("Borrador pendiente de revisión legal");
  await expect(page.getByRole("link", { name: "términos del servicio", exact: true })).toHaveAttribute("href", "/terminos");
  await expect(page.getByRole("link", { name: "política de privacidad", exact: true })).toHaveAttribute("href", "/privacidad");

  await page.getByRole("button", { name: "Crear cuenta" }).click();
  // Filtrado: el anunciador de rutas de Next también es un `role="alert"`.
  await expect(page.getByRole("alert").filter({ hasText: "tienes que aceptar los términos" })).toBeVisible();
  await expect(casilla).toHaveAttribute("aria-invalid", "true");
  await expect(page).toHaveURL(/\/registro/);

  await casilla.check();
  await page.getByRole("button", { name: "Crear cuenta" }).click();
  await expect(page).toHaveURL(/\/onboarding/);
  await expect(page.getByTestId("aceptar-terminos")).toHaveCount(0);
});

test("quien no aceptó la versión vigente no ve el panel hasta aceptarla, y luego sigue donde iba", async ({ page }) => {
  await page.goto("/login?next=/series");
  await page.getByLabel("Correo electrónico").fill("terminos@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();

  await expect(page).toHaveURL(/\/series/);
  const pantalla = page.getByTestId("aceptar-terminos");
  await expect(pantalla).toBeVisible();
  await expect(page.getByRole("heading", { name: "Acepta los términos para continuar" })).toBeVisible();
  await expect(page.getByRole("combobox", { name: "Cambiar de empresa" })).toHaveCount(0);
  await expect(pantalla.getByTestId("borrador-legal-casilla")).toBeVisible();

  // Ir a otra página del panel no la saltea.
  await page.goto("/comprobantes");
  await expect(page.getByTestId("aceptar-terminos")).toBeVisible();

  await page.getByRole("button", { name: "Aceptar y continuar" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "Marca la casilla para aceptar los términos" })).toBeVisible();

  await page.getByRole("checkbox", { name: /Acepto los términos del servicio/ }).check();
  await page.getByRole("button", { name: "Aceptar y continuar" }).click();

  await expect(page.getByTestId("aceptar-terminos")).toHaveCount(0);
  await expect(page).toHaveURL(/\/comprobantes/);
  await expect(page.getByRole("combobox", { name: "Cambiar de empresa" })).toContainText("Bodega Términos SAC");
});
