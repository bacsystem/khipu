import { expect, test, type Page } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";
import { esperarHidratacion } from "./hidratacion";

/** El alta es un modal de tres pasos que se abre desde la cabecera de Cuentas. */
async function abrirAlta(page: Page) {
  await page.goto("/admin/cuentas");
  await esperarHidratacion(page, '[data-testid="nueva-cuenta"]');
  await page.getByRole("button", { name: "Nueva cuenta" }).click();
  await expect(page.getByTestId("alta-dialogo")).toBeVisible();
}

/** Los campos se buscan dentro del modal: detrás está el buscador de Cuentas, cuya etiqueta también nombra el RUC y la razón social. */
const dialogo = (page: Page) => page.getByTestId("alta-dialogo");
const siguiente = (page: Page) => page.getByRole("button", { name: "Siguiente" }).click();
const pasoActual = (page: Page) => page.getByTestId("alta-dialogo").locator('[aria-current="step"]');

async function llenarCuenta(page: Page, email = "ana@nueva.pe") {
  await dialogo(page).getByLabel("Nombre de la cuenta", { exact: true }).fill("Comercial Nueva");
  await dialogo(page).getByLabel("Correo del cliente", { exact: true }).fill(email);
}

async function llenarEmpresa(page: Page, d: { ruc?: string; razon?: string } = {}) {
  await dialogo(page).getByLabel("RUC", { exact: true }).fill(d.ruc ?? "20100066603");
  await dialogo(page).getByLabel("Razón social", { exact: true }).fill(d.razon ?? "COMERCIAL NUEVA SAC");
}

/** Pasa los tres pasos con datos válidos (salvo lo que se pida) y deja el asistente en el último, listo para «Dar de alta». */
async function llenar(page: Page, d: { email?: string; ruc?: string; razon?: string; serie?: string } = {}) {
  await llenarCuenta(page, d.email);
  await siguiente(page);
  await expect(pasoActual(page)).toHaveText("2");
  await llenarEmpresa(page, d);
  await siguiente(page);
  await expect(pasoActual(page)).toHaveText("3");
  if (d.serie) await dialogo(page).getByLabel("Serie", { exact: true }).fill(d.serie);
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

test("la ruta vieja del alta lleva a Cuentas, donde está el botón que abre el modal", async ({ page }) => {
  await entrarComoAdmin(page);

  await page.goto("/admin/cuentas/nueva");

  await expect(page).toHaveURL(/\/admin\/cuentas$/);
  await expect(page.getByRole("button", { name: "Nueva cuenta" })).toBeVisible();
});

test("desde Cuentas, «Nueva cuenta» abre el alta en un modal de tres pasos, sin salir de la página", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.getByRole("link", { name: "Cuentas" }).click();

  await abrirAlta(page);

  await expect(page).toHaveURL(/\/admin\/cuentas$/);
  await expect(pasoActual(page)).toHaveText("1");
  await expect(page.getByTestId("alta-dialogo").getByRole("listitem")).toHaveCount(3);
});

test("un paso con los obligatorios vacíos no deja seguir; «Atrás» vuelve sin perder lo escrito", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);

  await siguiente(page);
  await expect(page.getByText("Ingresa el nombre de la cuenta")).toBeVisible();
  await expect(pasoActual(page)).toHaveText("1");

  await llenarCuenta(page);
  await siguiente(page);
  await expect(pasoActual(page)).toHaveText("2");
  await page.getByRole("button", { name: "Atrás" }).click();

  await expect(pasoActual(page)).toHaveText("1");
  await expect(dialogo(page).getByLabel("Correo del cliente", { exact: true })).toHaveValue("ana@nueva.pe");
});

test("un clic fuera del modal no lo cierra ni pierde lo escrito", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);
  await llenarCuenta(page);

  await page.mouse.click(10, 400);

  await expect(dialogo(page)).toBeVisible();
  await expect(dialogo(page).getByLabel("Correo del cliente", { exact: true })).toHaveValue("ana@nueva.pe");
});

test("no pide contraseña: la elige el cliente con la invitación", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);

  await expect(page.getByLabel(/contraseña/i)).toHaveCount(0);
  await expect(page.getByText(/elige su propia contraseña/)).toBeVisible();
});

/** El mock no guarda el alta (ver el handler): que la cuenta aparezca en el listado lo prueba el e2e del backend. */
test("el alta completa deja la API key a la vista una sola vez y «Listo» cierra el modal en Cuentas", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);
  await llenar(page);

  await darDeAlta(page);

  await expect(page.getByRole("heading", { name: "Cliente dado de alta" })).toBeVisible();
  await expect(page.getByTestId("api-key-nueva")).toHaveText(/^fk_/);
  await expect(page.getByText(/no volverá a mostrarse/)).toBeVisible();
  await expect(page.getByText(/Enviamos la invitación a ana@nueva\.pe/)).toBeVisible();

  await page.getByRole("button", { name: "Listo" }).click();

  await expect(page.getByTestId("alta-dialogo")).toHaveCount(0);
  await expect(page).toHaveURL(/\/admin\/cuentas$/);
  await expect(page.getByRole("heading", { name: "Cuentas" })).toBeVisible();
});

/**
 * #219, el caso del issue: el alta llegó y se hizo, pero la respuesta —con la API key que solo se muestra una vez— se cortó. Reenviar
 * sin cambiar nada muestra la misma API key en vez de un «ya existe una cuenta con ese correo».
 */
test("tras un corte, reenviar sin cambiar nada muestra la misma API key del alta ya hecha", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);
  await llenar(page, { email: "corte@nueva.pe" });

  const claves: string[] = [];
  let apiKey = "";
  await page.route("**/api/admin/cuentas", async (r) => {
    claves.push(r.request().headers()["idempotency-key"]);
    const res = await r.fetch();
    apiKey = (await res.json()).datos.api_key;
    await r.abort("connectionreset");
  });
  await darDeAlta(page);
  await expect(page.getByText(/Vuelve a enviar sin cambiar nada/)).toBeVisible();
  expect(apiKey).toMatch(/^fk_/);

  await page.unroute("**/api/admin/cuentas");
  await page.route("**/api/admin/cuentas", async (r) => {
    claves.push(r.request().headers()["idempotency-key"]);
    await r.continue();
  });
  await darDeAlta(page);

  await expect(page.getByTestId("api-key-nueva")).toHaveText(apiKey);
  expect(claves[1]).toBe(claves[0]);
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
  // El formulario conserva lo escrito para corregirlo: volviendo al primer paso está el correo.
  await page.getByRole("button", { name: "Atrás" }).click();
  await page.getByRole("button", { name: "Atrás" }).click();
  await expect(dialogo(page).getByLabel("Correo del cliente", { exact: true })).toHaveValue("ana@sol.pe");
});

test("un RUC ya registrado dice que es la empresa, no el correo", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);
  await llenar(page, { ruc: "20100047226" });

  await darDeAlta(page);

  await expect(page.getByText("Ya existe una empresa con RUC 20100047226")).toBeVisible();
  await expect(page.getByTestId("api-key-nueva")).toHaveCount(0);
});

test("un RUC con el dígito verificador mal no deja pasar del paso de la empresa", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);
  await llenarCuenta(page);
  await siguiente(page);
  await llenarEmpresa(page, { ruc: "20100066604" });

  await siguiente(page);

  await expect(page.getByText(/El RUC no es válido/)).toBeVisible();
  await expect(pasoActual(page)).toHaveText("2");
  await expect(page.getByTestId("api-key-nueva")).toHaveCount(0);
});

test("una serie de boleta en una factura se rechaza antes de enviar", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);
  await llenar(page, { serie: "B001" });

  await darDeAlta(page);

  await expect(page.getByText(/empieza con F y la de una boleta con B/)).toBeVisible();
});

test("«Dar de alta a otro cliente» vuelve al primer paso limpio y la API key anterior desaparece", async ({ page }) => {
  await entrarComoAdmin(page);
  await abrirAlta(page);
  await llenar(page);
  await darDeAlta(page);
  await expect(page.getByTestId("api-key-nueva")).toBeVisible();

  await page.getByRole("button", { name: "Dar de alta a otro cliente" }).click();

  await expect(page.getByTestId("api-key-nueva")).toHaveCount(0);
  await expect(pasoActual(page)).toHaveText("1");
  await expect(dialogo(page).getByLabel("Correo del cliente", { exact: true })).toHaveValue("");
});

/** El correo de la invitación lleva este enlace: el texto cambia, el endpoint es el de restablecer. */
test("el enlace de la invitación pide crear la contraseña; el de restablecer, elegir una nueva", async ({ page }) => {
  await page.goto("/restablecer/un-token?invitacion=1");
  await expect(page.getByRole("heading", { name: "Crea tu contraseña" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Crear contraseña" })).toBeVisible();
  // La pestaña dice lo mismo que la página: es lo primero que ve el cliente invitado.
  await expect(page).toHaveTitle(/^Crea tu contraseña/);

  await page.goto("/restablecer/un-token");
  await expect(page.getByRole("heading", { name: "Elige una nueva contraseña" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Restablecer contraseña" })).toBeVisible();
  await expect(page).toHaveTitle(/^Elige una nueva contraseña/);
});
