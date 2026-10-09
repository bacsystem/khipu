import { expect, test } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";
import { esperarHidratacion } from "./hidratacion";

// La ficha de un comprobante en el backoffice (#251). Solo lee comprobantes que ninguna spec muta: de la cola de errores, el 201 (envío, 2 intentos) y el 203 (formato, 1033)
// del Cliente 04; de la verificación de integridad, el CDR faltante del 10 de septiembre. Que el backend arme la ficha de verdad lo prueba `ColaDeErroresE2ETest`.
const empresa = (n: number) => `00000000-0000-4000-9000-${String(n).padStart(12, "0")}`;

test.beforeEach(async ({ page }) => {
  await entrarComoAdmin(page);
});

test("desde la cola de errores se abre la ficha: intentos, último error, archivos y enlace a la empresa", async ({ page }) => {
  await page.goto(`/admin/errores?empresa_id=${empresa(4)}`);
  await esperarHidratacion(page, "#errores-q");
  await page.getByRole("link", { name: "20100000400-01-F001-201" }).click();

  await expect(page).toHaveURL(/\/admin\/comprobantes\/[0-9a-f-]{36}$/);
  await expect(page.getByTestId("comprobante-ficha")).toContainText("20100000400-01-F001-201");
  await expect(page.getByTestId("comprobante-intentos")).toHaveText("2");
  await expect(page.getByTestId("comprobante-ultimo-error")).not.toHaveText("Ninguno");
  await expect(page.getByTestId("comprobante-xml")).toHaveText("XML firmado: Guardado");

  await page.getByRole("link", { name: "Ver la empresa" }).click();
  await expect(page).toHaveURL(new RegExp(`/admin/empresas/${empresa(4)}$`));
});

test("un rechazo por formato muestra lo que respondió SUNAT", async ({ page }) => {
  await page.goto(`/admin/errores?empresa_id=${empresa(4)}`);
  await esperarHidratacion(page, "#errores-q");
  await page.getByRole("link", { name: "20100000400-01-F001-203" }).click();

  await expect(page.getByTestId("comprobante-respuesta")).toContainText("1033");
});

test("desde la verificación de integridad se abre la ficha del comprobante con el archivo que falta", async ({ page }) => {
  await page.goto("/admin/integridad");
  await esperarHidratacion(page, "#integridad-desde");
  await page.getByLabel("Desde").fill("2026-09-01");
  await page.getByLabel("Hasta (inclusive)").fill("2026-09-30");
  await page.getByTestId("integridad-verificar").click();

  await page.getByRole("link", { name: "20100047226-03-B001-7" }).click();

  await expect(page).toHaveURL(/\/admin\/comprobantes\/00000000-0000-4000-c000-000000000002$/);
  await expect(page.getByTestId("comprobante-cdr")).toHaveText("CDR de SUNAT: No está");
});

test("un comprobante que no existe es la página de no encontrado", async ({ page }) => {
  const r = await page.goto("/admin/comprobantes/00000000-0000-4000-c000-999999999999");
  expect(r?.status()).toBe(404);
});
