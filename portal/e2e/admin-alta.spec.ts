import { expect, test, type Page } from "@playwright/test";
import { esperarHidratacion } from "./hidratacion";

async function entrarComoAdmin(page: Page) {
  await page.goto("/admin/login");
  await page.getByLabel("Correo electrónico").fill("admin@khipu.pe");
  await page.getByLabel("Contraseña").fill("AdminPass1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/admin$/);
}

async function abrirAlta(page: Page) {
  await page.goto("/admin/cuentas/nueva");
  await esperarHidratacion(page, "#alta-nombre");
}

async function llenar(page: Page, d: { email?: string; ruc?: string; razon?: string; serie?: string } = {}) {
  await page.getByLabel("Nombre de la cuenta").fill("Comercial Nueva");
  await page.getByLabel("Correo del cliente").fill(d.email ?? "ana@nueva.pe");
  await page.getByLabel("RUC").fill(d.ruc ?? "20100066603");
  await page.getByLabel("Razón social").fill(d.razon ?? "COMERCIAL NUEVA SAC");
  if (d.serie) await page.getByLabel("Serie").fill(d.serie);
}

const darDeAlta = (page: Page) => page.getByRole("button", { name: "Dar de alta" }).click();

test("sin sesión, /admin/cuentas/nueva redirige al login del backoffice", async ({ page }) => {
  await page.goto("/admin/cuentas/nueva");
  await expect(page).toHaveURL(/\/admin\/login/);
});

/** El alta crea una cuenta con acceso a facturar: una sesión de cliente del portal no debe poder abrirla. */
test("una sesión de cliente no abre /admin/cuentas/nueva", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);

  await page.goto("/admin/cuentas/nueva");

  await expect(page).toHaveURL(/\/admin\/login/);
});

test("desde Cuentas, «Nueva cuenta» lleva al alta y la miga dice dónde está", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.getByRole("link", { name: "Cuentas" }).click();

  await page.getByRole("link", { name: "Nueva cuenta" }).click();

  await expect(page).toHaveURL(/\/admin\/cuentas\/nueva$/);
  const miga = page.getByRole("navigation", { name: "Ubicación" });
  await expect(miga).toContainText("Clientes");
  await expect(miga.locator("[aria-current=page]")).toHaveText("Nueva cuenta");
  await expect(page.getByRole("heading", { level: 1 })).toHaveCount(1);
  // En su propia página, la acción no se ofrece a sí misma.
  await expect(page.getByRole("link", { name: "Nueva cuenta" })).toHaveCount(0);
});

test("no pide contraseña: la elige el cliente con la invitación", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);

  await expect(page.getByLabel(/contraseña/i)).toHaveCount(0);
  await expect(page.getByText(/elige su propia contraseña/)).toBeVisible();
});

/** El mock no guarda el alta (ver el handler): que la cuenta aparezca en el listado lo prueba el e2e del backend. */
test("el alta completa deja la API key a la vista una sola vez y ofrece volver a las cuentas", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);
  await llenar(page);

  await darDeAlta(page);

  await expect(page.getByRole("heading", { name: "Cliente dado de alta" })).toBeVisible();
  await expect(page.getByTestId("api-key-nueva")).toHaveText(/^fk_/);
  await expect(page.getByText(/no volverá a mostrarse/)).toBeVisible();
  await expect(page.getByText(/Enviamos la invitación a ana@nueva\.pe/)).toBeVisible();

  await page.getByRole("link", { name: "Ver cuentas" }).click();

  await expect(page).toHaveURL(/\/admin\/cuentas$/);
  await expect(page.getByRole("heading", { name: "Cuentas" })).toBeVisible();
});

test("si el correo no salió lo dice y deja la API key a la vista igual", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);
  await llenar(page, { email: "sin-correo@nueva.pe" });

  await darDeAlta(page);

  await expect(page.getByText(/No pudimos enviar la invitación a sin-correo@nueva\.pe/)).toBeVisible();
  await expect(page.getByText(/¿Olvidaste tu contraseña\?/)).toBeVisible();
  await expect(page.getByTestId("api-key-nueva")).toHaveText(/^fk_/);
});

test("un correo ya registrado muestra el mensaje del backend y no crea nada", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);
  await llenar(page, { email: "ana@sol.pe" });

  await darDeAlta(page);

  await expect(page.getByText("Ya existe una cuenta con ese correo")).toBeVisible();
  await expect(page.getByTestId("api-key-nueva")).toHaveCount(0);
  // El formulario conserva lo escrito para corregirlo.
  await expect(page.getByLabel("Correo del cliente")).toHaveValue("ana@sol.pe");
});

test("un RUC ya registrado dice que es la empresa, no el correo", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);
  await llenar(page, { ruc: "20100047226" });

  await darDeAlta(page);

  await expect(page.getByText("Ya existe una empresa con RUC 20100047226")).toBeVisible();
  await expect(page.getByTestId("api-key-nueva")).toHaveCount(0);
});

test("un RUC con el dígito verificador mal se corrige sin viajar al servidor", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);
  await llenar(page, { ruc: "20100066604" });

  await darDeAlta(page);

  await expect(page.getByText(/El RUC no es válido/)).toBeVisible();
  await expect(page.getByTestId("api-key-nueva")).toHaveCount(0);
});

test("una serie de boleta en una factura se rechaza antes de enviar", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);
  await llenar(page, { serie: "B001" });

  await darDeAlta(page);

  await expect(page.getByText(/empieza con F y la de una boleta con B/)).toBeVisible();
});

test("«Dar de alta a otro cliente» vuelve a un formulario limpio y la API key anterior desaparece", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);
  await llenar(page);
  await darDeAlta(page);
  await expect(page.getByTestId("api-key-nueva")).toBeVisible();

  await page.getByRole("button", { name: "Dar de alta a otro cliente" }).click();

  await expect(page.getByTestId("api-key-nueva")).toHaveCount(0);
  await expect(page.getByLabel("Correo del cliente")).toHaveValue("");
});

/** El correo de la invitación lleva este enlace: el texto cambia, el endpoint es el de restablecer. */
test("el enlace de la invitación pide crear la contraseña; el de restablecer, elegir una nueva", async ({ page }) => {
  await page.goto("/restablecer/un-token?invitacion=1");
  await expect(page.getByRole("heading", { name: "Crea tu contraseña" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Crear contraseña" })).toBeVisible();

  await page.goto("/restablecer/un-token");
  await expect(page.getByRole("heading", { name: "Elige una nueva contraseña" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Restablecer contraseña" })).toBeVisible();
});
