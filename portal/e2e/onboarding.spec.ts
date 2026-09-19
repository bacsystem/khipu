import { expect, test } from "@playwright/test";

test("registro completa el onboarding de 3 pasos y llega a comprobantes", async ({ page }) => {
  const email = `nueva-${Date.now()}@example.com`;

  await page.goto("/registro");
  await page.getByLabel("Nombre de la cuenta").fill("Mi Empresa de Prueba");
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Crear cuenta" }).click();

  await expect(page).toHaveURL(/\/onboarding/);

  // Paso 1: empresa
  await page.getByLabel("RUC").fill("20999999990");
  await page.getByLabel("Razón social").fill("Mi Empresa de Prueba SAC");
  await page.getByRole("button", { name: "Continuar" }).click();

  // Paso 2: certificado y SOL
  await expect(page.getByText("Certificado y SOL")).toBeVisible();
  await page.locator('input[type="file"]').setInputFiles({
    name: "certificado.p12",
    mimeType: "application/x-pkcs12",
    buffer: Buffer.from("contenido-de-prueba"),
  });
  await page.getByLabel("Clave del certificado").fill("clave123");
  await page.getByLabel("Usuario SOL secundario").fill("USOL001");
  await page.getByLabel("Clave SOL").fill("ClaveSol123");
  await page.getByRole("button", { name: "Continuar" }).click();

  // Paso 3: primera serie
  await expect(page.getByLabel("Serie")).toHaveValue("F001");
  await page.getByRole("button", { name: "Terminar" }).click();

  await expect(page).toHaveURL(/\/comprobantes/);
});
