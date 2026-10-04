import { expect, test } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";

// El mock (src/mocks/data.ts) siembra «Panadería Sol» con un detalle completo: domicilio, tres series (una inactiva), un establecimiento, dos API
// keys (una revocada), PDF con logo, tres comprobantes (aceptado con observaciones, rechazado y sin respuesta de SUNAT), sus cambios de estado y 12
// tareas pendientes en el outbox. «Integrador SAC» es una empresa de integración, sin cuenta y sin nada cargado.
const ID_SOL = "00000000-0000-4000-9000-000000000001";
const ID_INTEGRADOR = "00000000-0000-4000-9000-000000000101";
const ID_CUENTA_SOL = "00000000-0000-4000-8000-000000000001";

test("sin sesión, el detalle redirige al login del backoffice", async ({ page }) => {
  await page.goto(`/admin/empresas/${ID_SOL}`);
  await expect(page).toHaveURL(/\/admin\/login/);
});

test("desde el listado, la razón social lleva al detalle de la empresa", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/empresas?pagina=2");

  await page.getByRole("link", { name: "PANADERIA SOL SAC" }).click();

  await expect(page).toHaveURL(new RegExp(`/admin/empresas/${ID_SOL}$`));
  await expect(page.getByRole("heading", { name: "PANADERIA SOL SAC", level: 1 })).toBeVisible();
});

test("el detalle muestra los datos fiscales, el certificado y las credenciales SOL", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/empresas/${ID_SOL}`);
  const detalle = page.getByTestId("empresa-detalle");

  const fiscales = detalle.getByRole("region", { name: "Datos fiscales" });
  await expect(fiscales).toContainText("LA PANADERIA");
  await expect(fiscales).toContainText("20100047226");
  await expect(fiscales.getByTestId("domicilio")).toContainText("AV. LARCO 345");
  await expect(fiscales.getByTestId("domicilio")).toContainText("MIRAFLORES, LIMA, LIMA");
  await expect(fiscales).toContainText("00-123-456789");

  const conexion = detalle.getByRole("region", { name: "Certificado y credenciales SOL" });
  await expect(conexion.getByText(/Vence el .* \(10 días\)/)).toBeVisible();
  await expect(conexion.getByText("Cargadas")).toBeVisible();
});

test("las series, los establecimientos y las API keys, sin ningún secreto", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/empresas/${ID_SOL}`);
  const detalle = page.getByTestId("empresa-detalle");

  const series = detalle.getByRole("region", { name: "Series" });
  await expect(series.locator("tbody tr")).toHaveCount(3);
  await expect(series.locator("tbody tr", { hasText: "F002" })).toContainText("Inactiva");
  await expect(series.locator("tbody tr", { hasText: "F001" })).toContainText("12");

  const establecimientos = detalle.getByRole("region", { name: "Establecimientos anexos" });
  await expect(establecimientos).toContainText("Tienda Surco");
  await expect(establecimientos).toContainText("SANTIAGO DE SURCO");

  const keys = detalle.getByRole("region", { name: "API keys" });
  await expect(keys.locator("tbody tr")).toHaveCount(2);
  await expect(keys.locator("tbody tr", { hasText: "fk_sol0002" })).toContainText("Vigente");
  await expect(keys.locator("tbody tr", { hasText: "fk_sol0001" })).toContainText("Revocada");
  await expect(keys).toContainText("el secreto nunca se muestra");
});

test("la personalización del PDF dice si hay logo sin mostrar dónde está", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/empresas/${ID_SOL}`);

  const pdf = page.getByTestId("empresa-detalle").getByRole("region", { name: "Personalización del PDF" });

  await expect(pdf).toContainText("MODERNO");
  await expect(pdf).toContainText("#0F766E");
  await expect(pdf.getByText("Cargado")).toBeVisible();
  await expect(pdf).toContainText("Gracias por su compra");
});

test("los comprobantes recientes traen su estado y el detalle del CDR de SUNAT", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/empresas/${ID_SOL}`);

  const comprobantes = page.getByTestId("empresa-detalle").getByRole("region", { name: "Comprobantes recientes" });

  await expect(comprobantes.locator("tbody tr")).toHaveCount(3);
  const aceptado = comprobantes.locator("tbody tr", { hasText: "F001-00000012" });
  await expect(aceptado).toContainText("Aceptado con obs.");
  await expect(aceptado.getByTestId("cdr")).toContainText("La Factura numero F001-12, ha sido aceptada");
  await expect(aceptado.getByTestId("cdr")).toContainText("4287 - El dato ingresado como parte de la dirección");
  const rechazado = comprobantes.locator("tbody tr", { hasText: "F001-00000011" });
  await expect(rechazado).toContainText("Rechazado");
  await expect(rechazado.getByTestId("cdr")).toContainText("2017");
  await expect(rechazado.getByTestId("cdr")).toContainText("Sin observaciones");
  await expect(rechazado).toContainText("Último error: RUC del receptor no existe en SUNAT");
  const sinRespuesta = comprobantes.locator("tbody tr", { hasText: "B001-00000003" });
  await expect(sinRespuesta).toContainText("Firmado");
  await expect(sinRespuesta).toContainText("Sin respuesta de SUNAT");
  await expect(sinRespuesta.getByTestId("cdr")).toHaveCount(0);
});

test("los cambios de estado y el outbox pendiente", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/empresas/${ID_SOL}`);
  const detalle = page.getByTestId("empresa-detalle");

  const eventos = detalle.getByRole("region", { name: "Cambios de estado" });
  await expect(eventos.locator("tbody tr")).toHaveCount(3);
  await expect(eventos.locator("tbody tr", { hasText: "CDR recibido con observaciones" })).toContainText("Enviado → Aceptado con obs.");
  // El primer cambio de un comprobante no tiene estado anterior: se dice «Inicio», no un hueco.
  await expect(eventos.locator("tbody tr", { hasText: "SUNAT rechazó el comprobante" })).toContainText("Inicio → Rechazado");

  const outbox = detalle.getByRole("region", { name: "Outbox pendiente" });
  await expect(outbox.locator("tbody tr")).toHaveCount(2);
  await expect(outbox.getByTestId("outbox-total")).toHaveText("12 tareas pendientes");
  await expect(outbox).toContainText("SUNAT no responde");
});

test("es solo lectura: no ofrece ninguna acción sobre la empresa", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/empresas/${ID_SOL}`);

  await expect(page.getByTestId("empresa-detalle")).toBeVisible();
  await expect(page.getByTestId("empresa-detalle").getByRole("button")).toHaveCount(0);
});

test("la cuenta de la empresa lleva a su detalle", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/empresas/${ID_SOL}`);

  await page.getByTestId("empresa-detalle").getByRole("link", { name: "Panadería Sol" }).click();

  await expect(page).toHaveURL(new RegExp(`/admin/cuentas/${ID_CUENTA_SOL}$`));
});

test("una empresa de integración, sin nada cargado, lo dice en cada sección en vez de dejar huecos", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/empresas/${ID_INTEGRADOR}`);
  const detalle = page.getByTestId("empresa-detalle");

  await expect(detalle).toContainText("Sin cuenta (alta por integración)");
  await expect(detalle.getByText("Todavía no declaró su domicilio fiscal.")).toBeVisible();
  // Ni certificado ni credenciales SOL: las dos cosas dicen «Sin cargar», cada una en su etiqueta.
  const conexion = detalle.getByRole("region", { name: "Certificado y credenciales SOL" });
  await expect(conexion.getByText("Sin cargar")).toHaveCount(2);
  await expect(conexion.locator('[data-estado="ninguno"]')).toHaveCount(1);
  await expect(detalle.getByText("Esta empresa no tiene series.")).toBeVisible();
  await expect(detalle.getByText("No tiene establecimientos anexos.")).toBeVisible();
  await expect(detalle.getByText("Esta empresa no tiene API keys.")).toBeVisible();
  await expect(detalle.getByText("Sin logo")).toBeVisible();
  await expect(detalle.getByText("Todavía no emitió comprobantes.")).toBeVisible();
  await expect(detalle.getByText("Los comprobantes recientes no tienen cambios de estado.")).toBeVisible();
  await expect(detalle.getByText("No tiene tareas pendientes.")).toBeVisible();
});

test("un id que no existe o que no es un UUID da 404, sin llegar al detalle", async ({ page }) => {
  await entrarComoAdmin(page);

  const inexistente = await page.goto("/admin/empresas/00000000-0000-4000-9000-0000000000ff");
  expect(inexistente?.status()).toBe(404);

  const basura = await page.goto("/admin/empresas/no-es-un-uuid");
  expect(basura?.status()).toBe(404);
});
