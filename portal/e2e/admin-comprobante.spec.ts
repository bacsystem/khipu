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

/** 273-H2: un fuera de plazo no tiene respuesta de SUNAT (khipu lo decide sin llamarla): el 2108 va como último error, como en el backend. */
test("un fuera de plazo dice que no llegó a SUNAT y el 2108 queda como último error", async ({ page }) => {
  await page.goto(`/admin/errores?empresa_id=${empresa(4)}`);
  await esperarHidratacion(page, "#errores-q");
  await page.getByRole("link", { name: "20100000400-01-F001-204" }).click();

  await expect(page.getByTestId("comprobante-respuesta")).toHaveText("No llegó a SUNAT");
  await expect(page.getByTestId("comprobante-ultimo-error")).toContainText("2108");
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

const FICHA = (n: number) => `/admin/comprobantes/00000000-0000-4000-c000-${String(n).padStart(12, "0")}`;

/** #362 H9: la boleta del mock fue informada fuera de plazo en un resumen diario; la ficha lo muestra con el mismo criterio que la del cliente. */
test("la ficha de una boleta muestra su resumen diario de alta: estado, ticket, fechas y respuesta, con el aviso de fuera de plazo", async ({ page }) => {
  await page.goto(FICHA(2));

  const baja = page.getByTestId("comprobante-baja-RC-20260921-1");
  await expect(baja).toContainText("Resumen diario RC-20260921-1 (alta)");
  await expect(baja.getByTestId("baja-estado")).toHaveText("Aceptado: boleta informada a SUNAT");
  await expect(baja.getByTestId("baja-ticket")).toHaveText("1789768174685");
  await expect(baja.getByTestId("baja-generada")).toHaveText("21 Set 2026");
  await expect(baja.getByTestId("baja-emision")).toHaveText("10 Set 2026");
  await expect(baja.getByTestId("baja-respuesta")).toContainText("El Resumen diario RC-20260921-1, ha sido aceptado");
  // 4443 es lo que la norma prevé, no lo que e-beta devolvió: el aviso no promete la observación.
  await expect(baja.getByTestId("baja-fuera-de-plazo")).toContainText("puede observarla con la 4443");
});

test("la ficha de una factura muestra su comunicación de baja rechazada o en curso, y una sin baja no muestra la sección", async ({ page }) => {
  await page.goto(FICHA(3));
  const rechazada = page.getByTestId("comprobante-baja-RA-20260912-1");
  await expect(rechazada).toContainText("Comunicación de baja RA-20260912-1");
  await expect(rechazada.getByTestId("baja-estado")).toHaveText("Rechazada por SUNAT");
  await expect(rechazada.getByTestId("baja-motivo")).toHaveText("Error en el RUC del cliente"); // en un RA el motivo sí viaja a SUNAT
  await expect(rechazada.getByTestId("baja-respuesta")).toContainText("2323");
  await expect(rechazada.getByTestId("baja-fuera-de-plazo")).toHaveCount(0);

  await page.goto(FICHA(4));
  const enCurso = page.getByTestId("comprobante-baja-RA-20260913-1");
  await expect(enCurso.getByTestId("baja-estado")).toHaveText("Enviada: SUNAT la está procesando");
  await expect(enCurso.getByTestId("baja-respuesta")).toHaveText("SUNAT respondió HTTP 503 (2 intentos)");

  await page.goto(FICHA(1));
  await expect(page.getByTestId("comprobante-ficha")).toBeVisible();
  await expect(page.getByTestId("comprobante-bajas")).toHaveCount(0);
});

test("un comprobante que no existe es la página de no encontrado", async ({ page }) => {
  const r = await page.goto("/admin/comprobantes/00000000-0000-4000-c000-999999999999");
  expect(r?.status()).toBe(404);
});
