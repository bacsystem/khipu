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
  await expect(page.getByText(/^Vence 1 Nov 2026/)).toBeVisible();
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

test("emite una nota de crédito parcial desde la factura y la factura la lista", async ({ page }) => {
  await page.goto("/comprobantes/f-aceptada");
  await page.getByTestId("emitir-nota").click();
  await expect(page).toHaveURL(/\/comprobantes\/f-aceptada\/nota/);
  const form = page.getByTestId("nota-form");
  await expect(form.getByLabel("Serie")).toHaveValue("FC01");
  // Nota parcial (07): la tabla de ítems de la factura con la cantidad a devolver.
  await form.getByLabel(/Motivo/).selectOption("07");
  await expect(form.getByRole("table")).toBeVisible();
  await form.getByLabel("Sustento").fill("Devolución parcial del servicio");
  await form.getByRole("button", { name: "Emitir nota de crédito" }).click();

  // Aterriza en el detalle de la nota con el bloque "Nota de crédito sobre" y vuelve a la factura, que ya la lista.
  await expect(page).toHaveURL(/\/comprobantes\/n-/);
  const nota = page.getByTestId("nota");
  await expect(nota.getByText("F001-1")).toBeVisible();
  await expect(nota.getByText("Devolución por ítem")).toBeVisible();
  await expect(page.getByText("Nota de crédito electrónica")).toBeVisible();
  await page.goto("/comprobantes/f-aceptada");
  const notas = page.getByTestId("notas");
  await expect(notas.getByText("FC01-1")).toBeVisible();
  await expect(notas.getByText("Devolución por ítem")).toBeVisible();
});

test("una nota de crédito 13 sale sin importe y una nota de débito con su concepto", async ({ page }) => {
  await page.goto("/comprobantes/f-aceptada/nota");
  const form = page.getByTestId("nota-form");
  await form.getByLabel(/Motivo/).selectOption("13");
  await expect(form.getByText(/importe 0/)).toBeVisible();
  await form.getByLabel("Sustento").fill("Reprogramación de cuotas");
  await form.getByRole("button", { name: "Emitir nota de crédito" }).click();
  await expect(page.getByTestId("nota").getByText(/Corrección o modificación/)).toBeVisible();

  await page.goto("/comprobantes/f-aceptada/nota");
  await form.getByLabel("Tipo de nota").selectOption("08");
  await expect(form.getByLabel("Serie")).toHaveValue("FD01");
  await form.getByLabel(/Motivo/).selectOption("01");
  await form.getByLabel("Sustento").fill("Intereses por mora de 30 días");
  await form.getByLabel("Concepto").fill("Intereses por mora");
  await form.getByLabel(/Importe con IGV/).fill("59");
  await form.getByRole("button", { name: "Emitir nota de débito" }).click();
  await expect(page.getByText("Nota de débito electrónica")).toBeVisible();
  await expect(page.getByTestId("nota").getByText(/Intereses por mora de 30 días/)).toBeVisible();
});

test("da de baja una factura aceptada tras confirmar el motivo y queda anulada", async ({ page }) => {
  await page.goto("/comprobantes/f-obs");
  await page.getByTestId("dar-de-baja").click();
  const confirmacion = page.getByTestId("baja-confirmacion");
  // Sin motivo (mínimo 3 caracteres) no se puede confirmar: la baja es irreversible ante SUNAT.
  await expect(confirmacion.getByRole("button", { name: "Confirmar la baja" })).toBeDisabled();
  await confirmacion.getByLabel(/Motivo/).fill("Error en el RUC del cliente");
  await confirmacion.getByRole("button", { name: "Confirmar la baja" }).click();

  await expect(page.getByTestId("baja")).toContainText("Aceptada: comprobante anulado");
  await expect(page.getByTestId("baja")).toContainText("Error en el RUC del cliente");
  await expect(page.getByText("Anulado", { exact: true }).first()).toBeVisible();
  await expect(page.getByTestId("dar-de-baja")).toHaveCount(0);
});
