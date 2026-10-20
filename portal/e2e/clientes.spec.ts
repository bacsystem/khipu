import { expect, test, type Page } from "@playwright/test";

// Los tests corren en paralelo contra el mismo mock en memoria: los que crean un cliente usan un documento propio y las aserciones van por fila, no por conteos globales.
test.beforeEach(async ({ page }) => {
  await page.goto("/login?next=/comprobantes");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  // Directo al listado, como antes del panel de inicio (#24): `next` lo pide y el login navega del lado del cliente. La URL se ancla sin la consulta: si no,
  // la del propio login (`/login?next=/comprobantes`) ya calzaría antes de entrar.
  await expect(page).toHaveURL(/^[^?]*\/comprobantes$/);
});

const fila = (page: Page, texto: string) => page.getByTestId("cliente").filter({ hasText: texto });

/** Un DNI propio de cada ejecución: ocho dígitos que no chocan con los clientes de ejemplo ni con los de otro test. */
const dniPropio = () => String(10_000_000 + (Date.now() % 80_000_000));

test("#319 la lista trae los clientes de la empresa y la búsqueda filtra por nombre o por documento, sin tildes", async ({ page }) => {
  await page.goto("/clientes");
  await expect(fila(page, "PESQUERA PACÍFICO S.A.C.")).toBeVisible();
  await expect(fila(page, "ANNA MÜLLER")).toBeVisible();

  await page.getByRole("searchbox", { name: "Buscar clientes" }).fill("pacifico");
  await expect(fila(page, "PESQUERA PACÍFICO S.A.C.")).toBeVisible();
  await expect(fila(page, "ANNA MÜLLER")).toHaveCount(0);

  await page.getByRole("searchbox", { name: "Buscar clientes" }).fill("12345678");
  await expect(fila(page, "JUAN PÉREZ GÓMEZ")).toBeVisible();
  await expect(fila(page, "PESQUERA PACÍFICO S.A.C.")).toHaveCount(0);

  await page.getByRole("searchbox", { name: "Buscar clientes" }).fill("zzzz sin nadie");
  await expect(page.getByText("Ningún cliente coincide con la búsqueda.")).toBeVisible();
});

test("#319 crea un cliente, rechaza el mismo documento dos veces, lo edita y lo elimina", async ({ page }) => {
  const dni = dniPropio();
  await page.goto("/clientes");

  await page.getByTestId("nuevo-cliente").click();
  const dialogo = page.getByRole("dialog");
  await dialogo.getByLabel("Documento").selectOption("1");
  await dialogo.getByLabel("Número").fill(dni);
  await dialogo.getByLabel("Nombre").fill("MARÍA TORRES VEGA");
  await dialogo.getByLabel("Correo").fill("maria@torres.pe");
  await dialogo.getByRole("button", { name: "Guardar cliente" }).click();
  await expect(dialogo).toHaveCount(0);
  await page.getByRole("searchbox", { name: "Buscar clientes" }).fill(dni);
  await expect(fila(page, "MARÍA TORRES VEGA")).toContainText("maria@torres.pe");

  // El mismo documento otra vez: el backend contesta 409 y el formulario lo dice sin cerrarse.
  await page.getByTestId("nuevo-cliente").click();
  await dialogo.getByLabel("Documento").selectOption("1");
  await dialogo.getByLabel("Número").fill(dni);
  await dialogo.getByLabel("Nombre").fill("OTRA PERSONA");
  await dialogo.getByRole("button", { name: "Guardar cliente" }).click();
  await expect(dialogo.getByRole("alert")).toContainText(/Ya tiene un cliente/);
  await dialogo.getByRole("button", { name: "Cancelar" }).click();

  await fila(page, "MARÍA TORRES VEGA").getByTestId("editar-cliente").click();
  await page.getByRole("dialog").getByLabel("Nombre").fill("MARÍA TORRES DE LA VEGA");
  await page.getByRole("dialog").getByRole("button", { name: "Guardar cambios" }).click();
  await expect(fila(page, "MARÍA TORRES DE LA VEGA")).toBeVisible();

  await fila(page, "MARÍA TORRES DE LA VEGA").getByRole("button", { name: "Eliminar" }).click();
  await fila(page, "MARÍA TORRES DE LA VEGA").getByRole("button", { name: "Sí, eliminar" }).click();
  await expect(fila(page, "MARÍA TORRES DE LA VEGA")).toHaveCount(0);
});

test("#319 editar un cliente del exterior conserva su país", async ({ page }) => {
  await page.goto("/clientes");
  await fila(page, "ANNA MÜLLER").getByTestId("editar-cliente").click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("País")).toHaveValue("DE");
  await dialogo.getByLabel("Dirección").fill("Berlín, Alemania");
  await dialogo.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(fila(page, "Berlín, Alemania")).toBeVisible();

  await fila(page, "ANNA MÜLLER").getByTestId("editar-cliente").click();
  await expect(page.getByRole("dialog").getByLabel("País")).toHaveValue("DE");
});

test("#319 «Nuevo comprobante» busca entre los clientes guardados y rellena al comprador", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");

  await dialogo.getByLabel("Buscar un cliente guardado").fill("pacifico");
  await dialogo.getByRole("option", { name: /PESQUERA PACÍFICO/ }).click();
  await expect(dialogo.getByLabel("RUC")).toHaveValue("20601234565");
  await expect(dialogo.getByLabel("Razón social")).toHaveValue("PESQUERA PACÍFICO S.A.C.");
  await expect(dialogo.getByLabel(/Dirección/)).toHaveValue("Av. Argentina 2450, Callao");

  // Un DNI no sirve para una factura: no se aplica y se dice por qué.
  await dialogo.getByLabel("Buscar un cliente guardado").fill("juan");
  await dialogo.getByRole("option", { name: /JUAN PÉREZ/ }).click();
  await expect(dialogo.getByTestId("aviso-cliente-guardado")).toContainText("la factura necesita un cliente con RUC");
  await expect(dialogo.getByLabel("RUC")).toHaveValue("20601234565");

  // En una boleta sí.
  await dialogo.getByRole("button", { name: "Boleta" }).click();
  await dialogo.getByLabel("Buscar un cliente guardado").fill("juan");
  await dialogo.getByRole("option", { name: /JUAN PÉREZ/ }).click();
  await expect(dialogo.getByLabel("Razón social").or(dialogo.getByLabel("Nombre"))).toHaveValue("JUAN PÉREZ GÓMEZ");
});

test("#319 al emitir guarda al comprador nuevo en «Clientes»", async ({ page }) => {
  const nombre = `CLIENTE DE PRUEBA ${Date.now()} S.A.C.`;
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill(nombre);
  await dialogo.getByLabel("Descripción").fill("Consultoría");
  await dialogo.getByLabel("Cantidad").fill("1");
  await dialogo.getByLabel(/Precio unit\./).fill("100");
  await expect(dialogo.getByLabel("Guardar en mis clientes al emitir")).toBeChecked();
  // El guardado es un pedido aparte, después de emitir: se espera su respuesta antes de salir de la página, o la navegación lo abortaría.
  const guardado = page.waitForResponse((r) => r.url().includes("/api/proxy/clientes/documento/6/20554198211") && r.request().method() === "PUT");
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  expect((await guardado).ok()).toBe(true);

  await page.goto("/clientes");
  await page.getByRole("searchbox", { name: "Buscar clientes" }).fill("20554198211");
  await expect(fila(page, "20554198211")).toBeVisible();
});
