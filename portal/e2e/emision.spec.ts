import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
});

test("emite una factura desde el portal y el total previsualizado es el del comprobante (#17)", async ({ page }) => {
  // La acción principal vive en el top bar, como en el resto del portal: no hay un botón propio en la página.
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");

  // La serie muestra el siguiente correlativo que asignará el backend, no el último usado.
  await expect(dialogo.getByLabel("Serie")).toContainText("F001 · siguiente N.º 3");
  // Boleta existe en el catálogo pero todavía no se puede emitir (#20): se muestra deshabilitada, no oculta.
  await expect(dialogo.getByRole("button", { name: "Boleta" })).toBeDisabled();

  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");

  await dialogo.getByLabel("Descripción").fill("Consultoría");
  await dialogo.getByLabel("Cantidad").fill("2");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("1000");

  await dialogo.getByRole("button", { name: "Agregar ítem" }).click();
  await dialogo.getByLabel("Descripción").nth(1).fill("Licencia anual");
  await dialogo.getByLabel("Cantidad").nth(1).fill("1");
  await dialogo.getByLabel("Precio unit. (con IGV)").nth(1).fill("500");

  // El redondeo va por línea, como exige SUNAT: 2×1000 + 1×500 con IGV da 2500.01, no 2500.00.
  await expect(dialogo.getByTestId("total-a-pagar")).toHaveText("S/ 2,500.01");
  const previsualizado = await dialogo.getByTestId("total-a-pagar").innerText();

  await dialogo.getByRole("button", { name: "Emitir factura" }).click();

  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  await expect(page.getByText("F001-3")).toBeVisible();
  // Lo que se previsualizó es exactamente lo que quedó emitido.
  await expect(page.getByText(previsualizado, { exact: false }).first()).toBeVisible();
});

test("cancelar cierra el diálogo sin emitir nada (#17)", async ({ page }) => {
  const filasAntes = await page.locator("table tbody tr").count();

  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();
  await dialogo.getByRole("button", { name: "Cancelar" }).click();

  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.locator("table tbody tr")).toHaveCount(filasAntes);
});
