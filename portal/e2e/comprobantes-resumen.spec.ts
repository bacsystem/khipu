import { expect, test } from "@playwright/test";

// El mock (src/mocks/handlers.ts) calcula el resumen de la empresa de demostración con las mismas reglas que el backend (las prueba `ResumenDeComprobantesE2ETest`).
// Las specs de la corrida comparten ese mock en paralelo y algunas cambian el estado de comprobantes sembrados (reenviar, dar de baja, emitir notas con la fecha de hoy), así
// que lo exacto se afirma sobre días con un solo comprobante que nadie toca, y el mes en curso solo en su forma. Sembrados:
//   2026-08-20  f-anulada       factura ANULADA, S/
//   2026-08-31  f-export-cargo  factura ACEPTADA de exportación, $ 110.00
const metrica = (page: import("@playwright/test").Page, etiqueta: string) => page.getByText(etiqueta, { exact: true }).locator("..");
const tira = (page: import("@playwright/test").Page) => page.getByTestId("resumen-de-comprobantes");

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
});

test("la franja ya no dice «requiere endpoint de resumen»: trae las cuatro métricas con un valor real", async ({ page }) => {
  await expect(tira(page)).toHaveAttribute("data-cargado", "true");
  await expect(tira(page)).not.toContainText("requiere endpoint de resumen");
  await expect(page.getByTestId("metrica-emitidos")).toHaveText(/^\d+$/);
  await expect(page.getByTestId("metrica-aceptados")).toHaveText(/^\d+$/);
  await expect(page.getByTestId("metrica-atencion")).toHaveText(/^\d+$/);
  await expect(page.getByTestId("metrica-facturado").first()).toContainText(/\d/);
});

test("sin filtros de fecha resume el mes en curso y lo dice", async ({ page }) => {
  await expect(metrica(page, "Emitidos en el período")).toContainText("Este mes");
  await expect(metrica(page, "Total facturado")).toContainText("Neto de notas de crédito · Este mes");
});

test("un día con una factura aceptada en dólares dice lo emitido, lo aceptado, el 100 % y lo facturado en su moneda", async ({ page }) => {
  await page.goto("/comprobantes?desde=2026-08-31&hasta=2026-08-31");

  await expect(page.getByTestId("metrica-emitidos")).toHaveText("1");
  await expect(page.getByTestId("metrica-aceptados")).toHaveText("1");
  await expect(metrica(page, "Aceptados con CDR")).toContainText("100 % de lo emitido");
  await expect(page.getByTestId("metrica-atencion")).toHaveText("0");
  await expect(page.getByTestId("metrica-atencion-detalle")).toHaveText("Todo en orden");
  await expect(page.getByTestId("metrica-facturado")).toHaveCount(1);
  await expect(page.getByTestId("metrica-facturado")).toHaveText("$ 110.00");
  await expect(page.getByTestId("metrica-facturado")).toHaveAttribute("data-moneda", "USD");
  await expect(metrica(page, "Emitidos en el período")).toContainText("31 Ago");
});

test("una factura anulada cuenta como emitida pero no como aceptada ni como facturada", async ({ page }) => {
  await page.goto("/comprobantes?desde=2026-08-20&hasta=2026-08-20");

  await expect(page.getByTestId("metrica-emitidos")).toHaveText("1");
  await expect(page.getByTestId("metrica-aceptados")).toHaveText("0");
  await expect(metrica(page, "Aceptados con CDR")).toContainText("0 % de lo emitido");
  await expect(page.getByTestId("metrica-facturado")).toHaveText("S/ 0.00");
  await expect(page.getByTestId("metrica-atencion")).toHaveText("0");
});

test("un período sin comprobantes dice cero y que no hay nada, sin dividir por cero", async ({ page }) => {
  await page.goto("/comprobantes?desde=2020-01-01&hasta=2020-01-31");

  await expect(page.getByTestId("metrica-emitidos")).toHaveText("0");
  await expect(page.getByTestId("metrica-aceptados")).toHaveText("0");
  await expect(metrica(page, "Aceptados con CDR")).toContainText("Sin comprobantes");
  await expect(page.getByTestId("metrica-facturado")).toHaveText("S/ 0.00");
  await expect(page.getByTestId("metrica-atencion-detalle")).toHaveText("Todo en orden");
  await expect(tira(page)).not.toContainText(/NaN|Infinity/);
});

test("un rango con facturas en soles y en dólares las muestra por moneda, soles primero", async ({ page }) => {
  await page.goto("/comprobantes?desde=2026-08-20&hasta=2026-08-31");

  const monedas = await page.getByTestId("metrica-facturado").evaluateAll((els) => els.map((e) => e.getAttribute("data-moneda")));
  expect(monedas).toEqual(["PEN", "USD"]);
  await expect(metrica(page, "Emitidos en el período")).toContainText("20 Ago – 31 Ago");
});

test("la atención requerida es coherente: con cero dice que todo está en orden y con más, de qué clases", async ({ page }) => {
  await page.goto("/comprobantes?desde=2026-09-01&hasta=2026-09-30");

  const total = Number(await page.getByTestId("metrica-atencion").innerText());
  const detalle = await page.getByTestId("metrica-atencion-detalle").innerText();
  if (total === 0) expect(detalle).toBe("Todo en orden");
  else expect(detalle).toMatch(/rechazad|con error de envío|fuera de plazo/);
  expect(Number(await page.getByTestId("metrica-emitidos").innerText())).toBeGreaterThanOrEqual(total);
});

test("el filtro de estado y el de serie no cambian el resumen: solo el rango de fechas lo acota", async ({ page }) => {
  await page.goto("/comprobantes?desde=2026-08-31&hasta=2026-08-31&estado=RECHAZADO");

  await expect(page.getByTestId("metrica-emitidos")).toHaveText("1");
  await page.goto("/comprobantes?desde=2026-08-31&hasta=2026-08-31&serie=B001");
  await expect(page.getByTestId("metrica-emitidos")).toHaveText("1");
});

test("con una sola fecha el período queda abierto del otro lado y se dice", async ({ page }) => {
  await page.goto("/comprobantes?desde=2026-08-31");
  await expect(metrica(page, "Emitidos en el período")).toContainText("Desde el 31 Ago");

  await page.goto("/comprobantes?hasta=2026-08-31");
  await expect(metrica(page, "Emitidos en el período")).toContainText("Hasta el 31 Ago");
});

test("un rango al revés en la URL no rompe la página: se toma solo el «desde»", async ({ page }) => {
  await page.goto("/comprobantes?desde=2026-09-30&hasta=2026-09-01");

  await expect(tira(page)).toHaveAttribute("data-cargado", "true");
  await expect(metrica(page, "Emitidos en el período")).toContainText("Desde el 30 Set");
});

test("el resumen cambia al cambiar el rango de fechas de la lista", async ({ page }) => {
  await page.goto("/comprobantes?desde=2026-08-31&hasta=2026-08-31");
  await expect(page.getByTestId("metrica-emitidos")).toHaveText("1");

  await page.goto("/comprobantes?desde=2020-01-01&hasta=2020-01-31");

  await expect(page.getByTestId("metrica-emitidos")).toHaveText("0");
});
