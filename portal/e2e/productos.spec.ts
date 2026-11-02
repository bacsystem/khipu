import { expect, test, type Page } from "@playwright/test";

// Los tests corren en paralelo contra el mismo mock en memoria: los que crean un producto usan un código propio y las aserciones van por fila, no por conteos globales.
test.beforeEach(async ({ page }) => {
  await page.goto("/login?next=/comprobantes");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  // Directo al listado, como antes del panel de inicio (#24): `next` lo pide y el login navega del lado del cliente. La URL se ancla sin la consulta: si no,
  // la del propio login (`/login?next=/comprobantes`) ya calzaría antes de entrar.
  await expect(page).toHaveURL(/^[^?]*\/comprobantes$/);
});

const fila = (page: Page, texto: string) => page.getByTestId("producto").filter({ hasText: texto });

const codigoPropio = () => `E2E-${Date.now()}`;

test("#320 la lista trae los productos de la empresa y la búsqueda filtra por descripción o por código, sin tildes", async ({ page }) => {
  await page.goto("/productos");
  await expect(fila(page, "Consultoría tributaria")).toBeVisible();
  await expect(fila(page, "Libro técnico de contabilidad")).toBeVisible();

  await page.getByRole("searchbox", { name: "Buscar productos" }).fill("consultoria");
  await expect(fila(page, "Consultoría tributaria")).toBeVisible();
  await expect(fila(page, "Libro técnico de contabilidad")).toHaveCount(0);

  await page.getByRole("searchbox", { name: "Buscar productos" }).fill("LIB-");
  await expect(fila(page, "Libro técnico de contabilidad")).toBeVisible();

  await page.getByRole("searchbox", { name: "Buscar productos" }).fill("zzzz sin nada");
  await expect(page.getByText("Ningún producto coincide con la búsqueda.")).toBeVisible();
});

test("#320 crea un producto, rechaza el mismo código dos veces, lo edita y lo elimina", async ({ page }) => {
  const codigo = codigoPropio();
  await page.goto("/productos");

  await page.getByTestId("nuevo-producto").click();
  const dialogo = page.getByRole("dialog");
  await dialogo.getByLabel("Descripción").fill("Mantenimiento mensual");
  await dialogo.getByLabel("Unidad de medida").selectOption("ZZ");
  await dialogo.getByLabel("Afectación del IGV").selectOption("10");
  await dialogo.getByLabel(/Precio unit\./).fill("236");
  await dialogo.getByLabel(/Código interno/).fill(codigo);
  await dialogo.getByRole("button", { name: "Guardar producto" }).click();
  await expect(dialogo).toHaveCount(0);
  await page.getByRole("searchbox", { name: "Buscar productos" }).fill(codigo);
  await expect(fila(page, "Mantenimiento mensual")).toContainText("236.00");

  // El mismo código otra vez: el backend contesta 409 y el formulario lo dice sin cerrarse.
  await page.getByTestId("nuevo-producto").click();
  await dialogo.getByLabel("Descripción").fill("Otro producto");
  await dialogo.getByLabel(/Precio unit\./).fill("10");
  await dialogo.getByLabel(/Código interno/).fill(codigo);
  await dialogo.getByRole("button", { name: "Guardar producto" }).click();
  await expect(dialogo.getByRole("alert")).toContainText(/Ya tiene un producto/);
  await dialogo.getByRole("button", { name: "Cancelar" }).click();

  await fila(page, "Mantenimiento mensual").getByTestId("editar-producto").click();
  await page.getByRole("dialog").getByLabel("Descripción").fill("Mantenimiento mensual premium");
  await page.getByRole("dialog").getByRole("button", { name: "Guardar cambios" }).click();
  await expect(fila(page, "Mantenimiento mensual premium")).toBeVisible();

  await fila(page, "Mantenimiento mensual premium").getByRole("button", { name: "Eliminar" }).click();
  await fila(page, "Mantenimiento mensual premium").getByRole("button", { name: "Sí, eliminar" }).click();
  await expect(fila(page, "Mantenimiento mensual premium")).toHaveCount(0);
});

test("#320 «Nuevo comprobante» busca entre los productos guardados y llena la línea", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");

  await dialogo.getByLabel("Buscar un producto guardado").fill("consultoria");
  await dialogo.getByRole("option", { name: /Consultoría tributaria/ }).click();
  await expect(dialogo.getByLabel("Descripción")).toHaveValue("Consultoría tributaria");
  await expect(dialogo.getByLabel(/Precio unit\./)).toHaveValue("118.00");

  // Uno de exportación no sirve en una venta interna: no se agrega y se dice por qué.
  await dialogo.getByLabel("Buscar un producto guardado").fill("licencia");
  await dialogo.getByRole("option", { name: /Licencia de software/ }).click();
  await expect(dialogo.getByTestId("aviso-producto-guardado")).toContainText("es de exportación");
  await expect(dialogo.getByLabel("Descripción")).toHaveCount(1);

  // Otro producto se suma como una línea más, con su propia afectación.
  await dialogo.getByLabel("Buscar un producto guardado").fill("libro");
  await dialogo.getByRole("option", { name: /Libro técnico/ }).click();
  await expect(dialogo.getByLabel("Descripción")).toHaveCount(2);
  await expect(dialogo.getByLabel("Descripción").nth(1)).toHaveValue("Libro técnico de contabilidad");
});

test("#320 emitir con un producto guardado lleva su código y sale", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CLIENTE DE PRODUCTOS S.A.C.");
  await dialogo.getByLabel("Buscar un producto guardado").fill("consultoria");
  await dialogo.getByRole("option", { name: /Consultoría tributaria/ }).click();

  await dialogo.getByRole("button", { name: "Emitir factura" }).click();

  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  await expect(page.getByText("SRV-001")).toBeVisible();
});
