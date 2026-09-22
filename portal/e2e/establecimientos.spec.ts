import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
  await page.goto("/establecimientos");
});

test("registra un anexo, lo asigna a una serie nueva y no permite darlo de baja mientras la use", async ({ page }) => {
  // El seed trae el anexo 0002; el 0000 aparece solo cuando hay domicilio fiscal (el seed no lo tiene).
  await expect(page.getByTestId("establecimiento-0002")).toContainText("Tienda Miraflores");

  await page.getByTestId("nuevo-establecimiento").click();
  const form = page.getByTestId("establecimiento-form");
  await form.getByLabel("Código").fill("0003");
  await form.getByLabel("Nombre").fill("Almacén Callao");
  await expect(form.getByLabel("Departamento")).toBeEnabled();
  await form.getByLabel("Departamento").selectOption("LIMA");
  await form.getByLabel("Provincia").selectOption("LIMA");
  await form.getByLabel("Distrito").selectOption({ label: "MIRAFLORES" });
  await form.getByLabel("Dirección").fill("Av. Argentina 2500");
  await form.getByRole("button", { name: "Registrar establecimiento" }).click();

  const fila = page.getByTestId("establecimiento-0003");
  await expect(fila).toContainText("Almacén Callao");
  await expect(fila).toContainText("Av. Argentina 2500");
  await expect(fila).toContainText("150122");

  // Serie nueva asignada al anexo 0003.
  await page.goto("/series");
  await page.getByRole("button", { name: "Nueva serie" }).click();
  await page.getByLabel("Código de serie").fill("F003");
  await page.getByTestId("serie-establecimiento").selectOption("0003");
  await page.getByRole("button", { name: /Guardar/ }).click();
  await expect(page.getByText("F003", { exact: true })).toBeVisible();
  await expect(page.getByRole("row", { name: /F003/ })).toContainText("0003");

  // Con una serie activa el anexo no se puede dar de baja; el que no tiene series, sí.
  await page.goto("/establecimientos");
  await expect(page.getByTestId("establecimiento-0003")).toContainText("F003");
  await expect(page.getByTestId("baja-establecimiento-0003")).toBeDisabled();
  await page.getByTestId("baja-establecimiento-0002").click();
  await page.getByRole("button", { name: "Sí, dar de baja" }).click();
  await expect(page.getByTestId("establecimiento-0002")).toContainText("Dado de baja");
});
