import { expect, test, type Page } from "@playwright/test";

/**
 * #379: al digitar el RUC del cliente en «Nuevo comprobante», el portal consulta el padrón de SUNAT (`GET /v1/consultas/ruc/{ruc}`, por el proxy) y rellena el
 * nombre y la dirección. Los RUC de ejemplo están en `padronMock` (src/mocks/handlers.ts).
 */
test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
});

/** Cuántas veces el navegador pidió la consulta de RUC. */
function contarConsultas(page: Page) {
  const pedidos: string[] = [];
  page.on("request", (r) => {
    if (r.url().includes("/api/proxy/consultas/ruc/")) pedidos.push(r.url().split("/").pop() ?? "");
  });
  return pedidos;
}

async function abrirFactura(page: Page) {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  return page.getByRole("dialog");
}

async function llenarItem(dialogo: ReturnType<Page["getByRole"]>) {
  await dialogo.getByLabel("Descripción").fill("Consultoría");
  await dialogo.getByLabel("Cantidad").fill("1");
  await dialogo.getByLabel(/Precio unit\./).fill("1000");
}

test("rellena la razón social y la dirección al completar el RUC, con una sola consulta, y se emite", async ({ page }) => {
  const consultas = contarConsultas(page);
  const dialogo = await abrirFactura(page);

  // Dígito a dígito, como se teclea: el pedido sale solo con el RUC completo.
  await dialogo.getByLabel("RUC").pressSequentially("20100070970");

  await expect(dialogo.getByLabel("Razón social")).toHaveValue("TIENDAS DEL SUR S.A.C.");
  await expect(dialogo.getByLabel(/Dirección/)).toHaveValue("AV. REPUBLICA DE PANAMA NRO. 2457");
  await expect(dialogo.getByTestId("consulta-ruc")).toContainText("Encontrado en SUNAT · ACTIVO · HABIDO");
  expect(consultas).toEqual(["20100070970"]);

  // Se puede corregir y se emite con lo que quedó escrito.
  await dialogo.getByLabel("Razón social").fill("TIENDAS DEL SUR SAC");
  await llenarItem(dialogo);
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
});

test("un RUC de baja y no habido avisa 4013 y 4014 antes de emitir, sin bloquear", async ({ page }) => {
  const dialogo = await abrirFactura(page);

  await dialogo.getByLabel("RUC").fill("20160109441");

  await expect(dialogo.getByTestId("aviso-ruc-4013")).toContainText("BAJA DE OFICIO");
  await expect(dialogo.getByTestId("aviso-ruc-4014")).toBeVisible();
  await expect(dialogo.getByLabel("Razón social")).toHaveValue("QUISPE MAMANI JOSE");

  await llenarItem(dialogo);
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
});

test("un RUC activo pero no habido avisa solo 4014", async ({ page }) => {
  const dialogo = await abrirFactura(page);

  await dialogo.getByLabel("RUC").fill("20602160280");

  await expect(dialogo.getByTestId("aviso-ruc-4014")).toBeVisible();
  await expect(dialogo.getByTestId("aviso-ruc-4013")).toHaveCount(0);
});

test("un RUC que el padrón no tiene avisa y se digitan los datos a mano", async ({ page }) => {
  const dialogo = await abrirFactura(page);

  await dialogo.getByLabel("RUC").fill("20554198211");

  await expect(dialogo.getByTestId("consulta-ruc")).toHaveAttribute("data-estado", "no-encontrado");
  await expect(dialogo.getByTestId("consulta-ruc")).toContainText("no figura en el padrón de SUNAT");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");
  await llenarItem(dialogo);
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
});

test("no pisa lo ya escrito y al cambiar de RUC quita solo lo rellenado", async ({ page }) => {
  const dialogo = await abrirFactura(page);
  await dialogo.getByLabel("Razón social").fill("Mi cliente de siempre");

  await dialogo.getByLabel("RUC").fill("20100070970");
  await expect(dialogo.getByRole("button", { name: "Usar este nombre" })).toBeVisible();
  await expect(dialogo.getByLabel("Razón social")).toHaveValue("Mi cliente de siempre");
  await expect(dialogo.getByLabel(/Dirección/)).toHaveValue("AV. REPUBLICA DE PANAMA NRO. 2457");

  await dialogo.getByLabel("RUC").fill("2010007097");
  await expect(dialogo.getByLabel("Razón social")).toHaveValue("Mi cliente de siempre");
  await expect(dialogo.getByLabel(/Dirección/)).toHaveValue("");
});

test("con el cupo agotado avisa y se puede digitar a mano; «Consultar de nuevo» repite el pedido", async ({ page }) => {
  const consultas = contarConsultas(page);
  const dialogo = await abrirFactura(page);

  await dialogo.getByLabel("RUC").fill("20999999990");

  await expect(dialogo.getByTestId("consulta-ruc")).toHaveAttribute("data-estado", "cupo");
  await dialogo.getByLabel("Razón social").fill("CLIENTE A MANO SAC");
  await dialogo.getByRole("button", { name: "Consultar de nuevo" }).click();
  await expect(dialogo.getByTestId("consulta-ruc")).toHaveAttribute("data-estado", "cupo");
  expect(consultas).toEqual(["20999999990", "20999999990"]);
  await expect(dialogo.getByLabel("Razón social")).toHaveValue("CLIENTE A MANO SAC");
});

test("con el padrón sin cargar o la conexión caída avisa y deja digitar a mano", async ({ page }) => {
  const dialogo = await abrirFactura(page);

  await dialogo.getByLabel("RUC").fill("20777777778");
  await expect(dialogo.getByTestId("consulta-ruc")).toHaveAttribute("data-estado", "sin-padron");

  await dialogo.getByLabel("RUC").fill("20888888889");
  await expect(dialogo.getByTestId("consulta-ruc")).toHaveAttribute("data-estado", "error");
  await expect(dialogo.getByRole("button", { name: "Consultar de nuevo" })).toBeVisible();
  await expect(dialogo.getByLabel("Razón social")).toBeEnabled();
});

test("un RUC con el dígito verificador mal no se consulta", async ({ page }) => {
  const consultas = contarConsultas(page);
  const dialogo = await abrirFactura(page);

  await dialogo.getByLabel("RUC").fill("20100070971");

  await expect(dialogo.getByTestId("consulta-ruc")).toHaveCount(0);
  expect(consultas).toEqual([]);
});

test("la boleta consulta el RUC solo si el documento del comprador es RUC", async ({ page }) => {
  const consultas = contarConsultas(page);
  const dialogo = await abrirFactura(page);
  await dialogo.getByRole("button", { name: "Boleta" }).click();

  await dialogo.getByLabel("Número de documento").fill("12345678");
  await expect(dialogo.getByTestId("consulta-ruc")).toHaveCount(0);

  await dialogo.getByLabel("Documento del comprador").selectOption("6");
  await dialogo.getByLabel("Número de documento").fill("20100070970");
  await expect(dialogo.getByLabel("Nombre del comprador")).toHaveValue("TIENDAS DEL SUR S.A.C.");
  expect(consultas).toEqual(["20100070970"]);
});
