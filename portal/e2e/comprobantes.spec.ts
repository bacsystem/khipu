import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
});

test("lista comprobantes con su estado y permite ver el detalle", async ({ page }) => {
  const tabla = page.locator("table");
  await expect(page.getByText("F001-00000001")).toBeVisible();
  // Un aceptado con CDR se etiqueta así en la tabla; el filtro de estado tiene un ítem "Aceptado" a secas.
  await expect(tabla.getByText("Aceptado con CDR", { exact: true })).toBeVisible();
  // El segundo comprobante lo muta el test de reenvío (corre en paralelo): se comprueba la fila, no su estado.
  await expect(tabla.getByText("F001-00000002")).toBeVisible();

  await page.getByRole("link", { name: /Ver comprobante F001-00000001/ }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-aceptada/);
  // Forma de pago al crédito (RS 193-2020): neto pendiente y calendario de cuotas en la liquidación.
  const formaPago = page.getByTestId("forma-pago");
  await expect(formaPago.getByText("Crédito")).toBeVisible();
  await expect(formaPago.getByText("Cuota001")).toBeVisible();
  await expect(formaPago.getByText("Cuota002")).toBeVisible();
  // Detracción (SPOT): bien/servicio del catálogo 54, porcentaje y cuenta del Banco de la Nación.
  const detraccion = page.getByTestId("detraccion");
  await expect(detraccion.getByText("Otros servicios empresariales")).toBeVisible();
  await expect(detraccion.getByText("00-000-123456")).toBeVisible();
  // Anticipo regularizado: la factura de anticipo y el importe pagado que se resta del total.
  const anticipos = page.getByTestId("anticipos");
  await expect(anticipos.getByText("F001-90")).toBeVisible();
  await expect(anticipos.getByText(/gravado \(04\)/)).toBeVisible();
  // Documentos relacionados: orden de compra y guía de remisión con su tipo del catálogo 01.
  const referencias = page.getByTestId("referencias");
  await expect(referencias.getByText("OC-2026-0457")).toBeVisible();
  await expect(referencias.getByText(/Guía de remisión remitente/)).toBeVisible();
  // Cargo global sin IGV (46, recargo al consumo): aparece como "Otros cargos" y suma al total a pagar.
  await expect(page.getByText(/Otros cargos sin IGV \(46\)/)).toBeVisible();
  // El detalle abre el XML firmado en una vista previa con su botón de descarga.
  await page.getByRole("button", { name: "Ver XML" }).click();
  await expect(page.getByText("Descargar XML")).toBeVisible();
});

test("reenvía un comprobante en error y queda aceptado", async ({ page }) => {
  await page.goto("/comprobantes/f-error");
  await expect(page.getByText("Error de envío")).toBeVisible();

  await page.getByRole("button", { name: "Reenviar", exact: true }).click();

  await expect(page.getByText("Aceptado", { exact: true }).first()).toBeVisible();
});
