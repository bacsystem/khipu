import { expect, test } from "@playwright/test";

test("la referencia de API carga sin sesión y muestra un endpoint real", async ({ page }) => {
  const errores: string[] = [];
  page.on("console", (msg) => {
    if (msg.type() === "error") errores.push(msg.text());
  });

  await page.goto("/developers");

  await expect(page.getByText("/v1/facturas").first()).toBeVisible({ timeout: 10_000 });
  expect(errores).toEqual([]);
});

test("con sesión, el botón genera una key de prueba", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);

  await page.goto("/developers");
  await page.getByRole("button", { name: "Generar API key de prueba" }).click();

  await expect(page.getByText(/^fk_/)).toBeVisible();
});

test("la guía, los catálogos y los errores se navegan desde la cabecera", async ({ page }) => {
  await page.goto("/developers/guia");
  await expect(page.getByRole("heading", { name: "Guía de emisión de facturas" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Venta gravada al contado (el caso base)" })).toBeVisible();
  // #293: el caso de las notas sobre una boleta, con sus motivos no admitidos.
  const notasDeBoleta = page.locator("#notas-boleta");
  await expect(notasDeBoleta.getByRole("heading", { name: "Notas de crédito y débito sobre una boleta" })).toBeVisible();
  await expect(notasDeBoleta.getByText(/RCP art\. 10 num\. 1\.4/)).toBeVisible();

  await page.getByRole("link", { name: "Catálogos SUNAT" }).first().click();
  await expect(page).toHaveURL(/\/developers\/catalogos/);
  await expect(page.getByText("Gravado - Operación Onerosa")).toBeVisible();
  await page.getByLabel("Buscar en los catálogos").fill("exonerado");
  await expect(page.getByText("Exonerado - Operación Onerosa")).toBeVisible();
  await expect(page.getByText("Gravado - Operación Onerosa")).toHaveCount(0);

  await page.getByRole("link", { name: "Errores y estados" }).first().click();
  await expect(page.getByRole("heading", { name: "Errores y estados" })).toBeVisible();
  await expect(page.getByText("FORMA_PAGO_INVALIDA", { exact: true })).toBeVisible();
  // #293: los plazos y las reglas de las notas sobre una boleta también están en la tabla de errores.
  await expect(page.getByText(/nota de boleta, como la boleta/).first()).toBeVisible();
  await expect(page.getByText(/NC 04, 05 u 08 sobre una boleta \(4367/)).toBeVisible();
});

/**
 * #348-H3/H4: desde #308 la unidad se valida contra el catálogo 03 antes de numerar (2936), y en blanco la frena el `@NotBlank` de `ItemDto`
 * (VALIDACION), no el dominio: la 2883 no llega por la API. El catálogo seguía diciendo que la API acepta cualquier código y que SUNAT
 * rechaza los inexistentes.
 */
test("el catálogo 03 y la tabla de errores describen la validación de la unidad", async ({ page }) => {
  await page.goto("/developers/catalogos");
  const unidades = page.locator("#cat-03");
  await expect(unidades.getByText(/fuera del catálogo responde/)).toBeVisible();
  await expect(unidades.getByText(/SUNAT rechaza los inexistentes/)).toHaveCount(0);

  await page.goto("/developers/errores");
  await expect(page.getByText(/unidad de medida que no existe en el catálogo 03 \(2936; en blanco responde VALIDACION\)/)).toBeVisible();
  await expect(page.getByText(/unidad de medida ausente \(2883\)/)).toHaveCount(0);
});
