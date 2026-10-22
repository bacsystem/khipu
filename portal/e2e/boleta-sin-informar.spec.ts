import { expect, test } from "@playwright/test";
import { hoyLima, sumarDias } from "../src/lib/formato";

// #356. El mock (src/mocks/data.ts) siembra tres boletas FIRMADAS sin enviar de la serie B777, con la fecha de emisión relativa a hoy porque el aviso depende del día:
//   b-sin-enviar-4  emitida hace 4 días: todavía no hay aviso
//   b-sin-enviar-5  emitida hace 5 días: el último día del envío individual
//   b-sin-enviar-6  emitida hace 6 días: ya solo va en el resumen diario
// Las specs comparten el mock en paralelo; la que envía una boleta usa la suya, y las demás solo la leen.

test.beforeEach(async ({ page }) => {
  await page.goto("/login?next=/comprobantes");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  // Directo al listado, como antes del panel de inicio (#24): `next` lo pide y el login navega del lado del cliente. La URL se ancla sin la consulta: si no,
  // la del propio login (`/login?next=/comprobantes`) ya calzaría antes de entrar.
  await expect(page).toHaveURL(/^[^?]*\/comprobantes$/);
});

test("a los 4 días una boleta firmada sin enviar todavía no avisa, pero se puede enviar", async ({ page }) => {
  await page.goto("/comprobantes/b-sin-enviar-4");

  await expect(page.getByTestId("aviso-boleta-sin-informar")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Enviar a SUNAT", exact: true })).toBeVisible();
});

test("el 5.º día es el último del envío individual y la ficha lo dice", async ({ page }) => {
  await page.goto("/comprobantes/b-sin-enviar-5");

  const aviso = page.getByTestId("aviso-boleta-sin-informar");
  await expect(aviso).toHaveAttribute("data-fase", "ultimo-dia-individual");
  await expect(aviso).toContainText("Esta boleta todavía no se informó a SUNAT");
  await expect(aviso).toContainText("Hoy es el último día para enviarla sola");
  await expect(page.getByRole("button", { name: "Enviar a SUNAT", exact: true })).toBeVisible();
});

test("al 6.º día solo va en el resumen diario; enviarla la informa y quita el aviso", async ({ page }) => {
  await page.goto("/comprobantes/b-sin-enviar-6");

  const aviso = page.getByTestId("aviso-boleta-sin-informar");
  await expect(aviso).toHaveAttribute("data-fase", "solo-resumen");
  await expect(aviso).toContainText("Ya no se puede enviar sola");
  await expect(aviso).toContainText("queda 1 día");

  await page.getByRole("button", { name: "Enviar a SUNAT", exact: true }).click();

  await expect(page.getByText("Aceptado", { exact: true }).first()).toBeVisible();
  await expect(page.getByTestId("aviso-boleta-sin-informar")).toHaveCount(0);
  await expect(page.getByTestId("baja")).toContainText("(alta)");
  await expect(page.getByRole("button", { name: "Enviar a SUNAT", exact: true })).toHaveCount(0);
});

test("las boletas sin enviar suman a la atención requerida del resumen y se nombran", async ({ page }) => {
  const hoy = hoyLima();
  await page.goto(`/comprobantes?desde=${sumarDias(hoy, -30)}&hasta=${hoy}`);

  await expect(page.getByTestId("resumen-de-comprobantes")).toHaveAttribute("data-cargado", "true");
  // Las de 5 y de 6 días (la de 6 puede estar ya enviada por el test de arriba, que corre en paralelo): al menos una, y se dice con sus palabras.
  await expect(page.getByTestId("metrica-atencion-detalle")).toContainText(/\d+ boletas? sin enviar/);
  expect(Number(await page.getByTestId("metrica-atencion").innerText())).toBeGreaterThanOrEqual(1);
});
