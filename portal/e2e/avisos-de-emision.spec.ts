import { expect, test, type Page } from "@playwright/test";

/**
 * #382: `POST /v1/facturas` y `POST /v1/notas` devuelven `avisos` (4013 RUC no activo, 4014 RUC no habido) cuando SUNAT va a observar el comprobante por el comprador, y el portal los
 * muestra en la ficha del comprobante recién emitido. Los RUC de ejemplo están en `padronMock` (src/mocks/handlers.ts): 20160109441 de baja y no habido, 20602160280 activo y no habido,
 * 20100070970 activo y habido.
 */
test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
});

async function emitirFactura(page: Page, ruc: string) {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await dialogo.getByLabel("RUC").fill(ruc);
  // La consulta del padrón (#379) rellena el nombre; se espera a que lo haga para no competir con el autorrelleno.
  await expect(dialogo.getByLabel("Razón social")).not.toHaveValue("");
  await dialogo.getByLabel("Descripción").fill("Consultoría");
  await dialogo.getByLabel("Cantidad").fill("1");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("1000");
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
}

test("una factura a un RUC de baja y no habido se emite y su ficha muestra los avisos 4013 y 4014", async ({ page }) => {
  await emitirFactura(page, "20160109441");

  await expect(page.getByText("Factura electrónica")).toBeVisible();
  await expect(page.getByTestId("aviso-emision-4013")).toContainText("BAJA DE OFICIO");
  await expect(page.getByTestId("aviso-emision-4014")).toContainText("no habido");

  // Sobrevive a recargar la ficha: el backend no guarda el aviso, así que lo guarda la pestaña.
  await page.reload();
  await expect(page.getByTestId("aviso-emision-4013")).toBeVisible();
  await expect(page.getByTestId("aviso-emision-4014")).toBeVisible();
});

test("un RUC activo pero no habido avisa solo 4014", async ({ page }) => {
  await emitirFactura(page, "20602160280");

  await expect(page.getByTestId("aviso-emision-4014")).toBeVisible();
  await expect(page.getByTestId("aviso-emision-4013")).toHaveCount(0);
});

test("un RUC activo y habido no trae avisos: la ficha es la de siempre", async ({ page }) => {
  await emitirFactura(page, "20100070970");

  await expect(page.getByText("Factura electrónica")).toBeVisible();
  await expect(page.getByTestId("avisos-de-emision")).toHaveCount(0);
});

test("la nota de crédito sobre esa factura también muestra los avisos en su ficha", async ({ page }) => {
  await emitirFactura(page, "20160109441");
  await page.goto(`${page.url()}/nota`);

  const form = page.getByTestId("nota-form");
  await form.getByLabel(/Motivo/).selectOption("01");
  await form.getByLabel("Sustento").fill("Anulación de la operación");
  await form.getByRole("button", { name: "Emitir nota de crédito" }).click();

  await expect(page).toHaveURL(/\/comprobantes\/n-/);
  await expect(page.getByText("Nota de crédito electrónica")).toBeVisible();
  await expect(page.getByTestId("aviso-emision-4013")).toBeVisible();
  await expect(page.getByTestId("aviso-emision-4014")).toBeVisible();
});
