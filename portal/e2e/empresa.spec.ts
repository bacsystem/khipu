import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
  await page.goto("/empresa");
});

test("guarda el domicilio fiscal eligiendo el ubigeo en cascada y la cuenta de detracciones", async ({ page }) => {
  const form = page.getByTestId("datos-fiscales");
  // El catálogo 13 se carga al abrir; departamento → provincia → distrito.
  await expect(form.getByLabel("Departamento")).toBeEnabled();
  await form.getByLabel("Departamento").selectOption("LIMA");
  await form.getByLabel("Provincia").selectOption("LIMA");
  await form.getByLabel("Distrito").selectOption({ label: "MIRAFLORES" });
  await expect(form.getByText("Ubigeo 150122 (catálogo 13)")).toBeVisible();
  await form.getByLabel("Dirección").fill("Av. Larco 345 Of. 12");
  await form.getByLabel(/Cuenta de detracciones/).fill("00-000-123456");
  await form.getByRole("button", { name: "Guardar datos fiscales" }).click();

  await expect(form.getByText("Datos fiscales actualizados", { exact: false })).toBeVisible();
  await expect(page.getByTestId("domicilio-actual")).toContainText("Av. Larco 345 Of. 12 · MIRAFLORES, LIMA, LIMA (150122)");
  await expect(page.getByText("Cód. local domicilio: 0000")).toBeVisible();
});
