import { expect, type Page } from "@playwright/test";

/** Registro por el formulario: entra al portal con el correo todavía sin verificar (#22). */
export async function registrar(page: Page, { email, nombre = "Mi Empresa de Prueba", celular = "987654321" }: { email: string; nombre?: string; celular?: string }) {
  await page.goto("/registro");
  await page.getByLabel("Nombre de la cuenta").fill(nombre);
  await page.getByLabel(/Celular/).fill(celular);
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Crear cuenta" }).click();
  await expect(page).toHaveURL(/\/onboarding/);
}

/**
 * Abre el enlace del correo de verificación como lo haría el usuario y vuelve al onboarding. El mock arma el enlace con
 * `verif-<correo>` (ver `src/mocks/handlers.ts`).
 */
export async function verificarCorreo(page: Page, email: string) {
  await page.goto(`/verificar/verif-${email}`);
  await page.getByRole("button", { name: "Verificar mi correo" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Tu correo quedó verificado" })).toBeVisible();
  await page.getByRole("link", { name: "Continuar" }).click();
  await expect(page).toHaveURL(/\/onboarding/);
}

export async function registrarYVerificar(page: Page, datos: { email: string; nombre?: string; celular?: string }) {
  await registrar(page, datos);
  await verificarCorreo(page, datos.email);
}
