import { expect, test } from "@playwright/test";
import { abrirPestana, entrarComoAdmin } from "./admin-sesion";
import { esperarHidratacion } from "./hidratacion";

// El mock (src/mocks/handlers.ts) abre la sesión de soporte con el token del cliente de demostración marcado como soporte, porque su mundo de clientes es otro:
// aquí se prueba el recorrido (el diálogo, la cookie, el aviso permanente, la salida y el historial), no los datos del cliente. Impersonar no muta el mock: solo
// deja cookies en el navegador de la prueba.
const ID_SOL = "00000000-0000-4000-8000-000000000001";
const USUARIO = (n: number) => `00000000-0000-4000-a000-${String(n).padStart(12, "0")}`;
const BETO = USUARIO(9001);
const CARLA = USUARIO(9002); // desactivada

async function abrirComoBeto(page: import("@playwright/test").Page) {
  await entrarComoAdmin(page);
  await page.goto(`/admin/cuentas/${ID_SOL}`);
  await abrirPestana(page, "Usuarios");
  // Un clic antes de que React hidrate el botón se pierde: el diálogo no se abre.
  await esperarHidratacion(page, '[data-testid="impersonar-usuario"]');
  await page.locator("tbody tr", { hasText: "beto@sol.pe" }).getByTestId("impersonar-usuario").click();
  await page.getByTestId("impersonar-usuario-confirmar").click();
}

test("el diálogo dice qué se puede y qué no antes de entrar como el usuario", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/cuentas/${ID_SOL}`);
  await abrirPestana(page, "Usuarios");

  await page.locator("tbody tr", { hasText: "beto@sol.pe" }).getByTestId("impersonar-usuario").click();

  const dialogo = page.getByTestId("impersonar-usuario-dialogo");
  await expect(dialogo).toContainText("Vas a ver el portal tal como lo ve beto@sol.pe");
  await expect(dialogo).toContainText("15 minutos y no se puede renovar");
  await expect(dialogo).toContainText("Solo puedes mirar");
  await expect(dialogo).toContainText("el cliente lo ve en su historial");
});

test("al confirmar se abre el portal del cliente con un aviso permanente de que se está actuando como él", async ({ page }) => {
  await abrirComoBeto(page);

  await expect(page).toHaveURL(/\/comprobantes|\/$/);
  const aviso = page.getByTestId("aviso-de-soporte");
  await expect(aviso).toBeVisible();
  await expect(aviso).toContainText("Modo soporte");
  await expect(aviso).toContainText("beto@sol.pe");
  await expect(aviso).toContainText("Solo puedes mirar: no puedes cambiar nada");
});

test("el aviso sigue en cada página del portal, no solo en la primera", async ({ page }) => {
  await abrirComoBeto(page);
  await expect(page.getByTestId("aviso-de-soporte")).toBeVisible();

  for (const ruta of ["/series", "/api-keys", "/empresa"]) {
    await page.goto(ruta);
    await expect(page.getByTestId("aviso-de-soporte"), ruta).toBeVisible();
  }
});

test("el token de soporte vive en una cookie httpOnly: el JS de la página no lo ve", async ({ page }) => {
  await abrirComoBeto(page);
  await expect(page.getByTestId("aviso-de-soporte")).toBeVisible();

  const visibleParaElJs = await page.evaluate(() => document.cookie);
  const cookies = await page.context().cookies();
  const acceso = cookies.find((c) => c.name === "factura_access");

  expect(visibleParaElJs).not.toContain("factura_access");
  expect(acceso?.httpOnly).toBe(true);
  expect(cookies.find((c) => c.name === "factura_refresh"), "una sesión de soporte no tiene refresh").toBeUndefined();
});

test("salir del modo soporte vuelve a la cuenta en el backoffice, cierra la sesión del cliente y deja la del administrador", async ({ page }) => {
  await abrirComoBeto(page);
  await expect(page.getByTestId("aviso-de-soporte")).toBeVisible();

  // El aviso viene en el HTML del servidor antes de hidratar: un clic en «Salir» en esa ventana se pierde y la página se queda en el portal del cliente.
  await esperarHidratacion(page, '[data-testid="salir-de-soporte"]');
  await page.getByTestId("salir-de-soporte").click();

  await expect(page).toHaveURL(new RegExp(`/admin/cuentas/${ID_SOL}$`));
  await expect(page.getByTestId("cuenta-detalle")).toBeVisible();
  const cookies = await page.context().cookies();
  expect(cookies.find((c) => c.name === "factura_access" && c.value !== "")).toBeUndefined();
  // El portal del cliente ya no se abre: la sesión de soporte terminó.
  await page.goto("/series");
  await expect(page).toHaveURL(/\/login/);
});

test("un usuario desactivado no tiene el botón, y el backend se niega con su código si se pide igual", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/cuentas/${ID_SOL}`);
  await abrirPestana(page, "Usuarios");

  await expect(page.locator("tbody tr", { hasText: "carla@sol.pe" })).toBeVisible();
  await expect(page.locator("tbody tr", { hasText: "carla@sol.pe" }).getByTestId("impersonar-usuario")).toHaveCount(0);
  const res = await page.request.post(`/api/admin/cuentas/${ID_SOL}/usuarios/${CARLA}/impersonar`);
  expect(res.status()).toBe(409);
  expect((await res.json()).codigo).toBe("USUARIO_INACTIVO");
  expect(await page.context().cookies().then((c) => c.find((x) => x.name === "factura_access")), "un intento rechazado no deja ninguna cookie de soporte").toBeUndefined();
});

test("el BFF rechaza ids inválidos, usuarios que no existen y la falta de sesión", async ({ page, request }) => {
  await entrarComoAdmin(page);

  expect((await page.request.post(`/api/admin/cuentas/${ID_SOL}/usuarios/no-es-un-uuid/impersonar`)).status()).toBe(400);
  expect((await page.request.post(`/api/admin/cuentas/no-es-un-uuid/usuarios/${BETO}/impersonar`)).status()).toBe(400);
  expect((await page.request.post(`/api/admin/cuentas/${ID_SOL}/usuarios/${USUARIO(777)}/impersonar`)).status()).toBe(404);
  // El fixture `request` no comparte las cookies de la página: es un cliente sin sesión.
  expect((await request.post(`/api/admin/cuentas/${ID_SOL}/usuarios/${BETO}/impersonar`)).status()).toBe(401);
});

// --- el historial que ve el cliente -------------------------------------------------------------------------------------------------

test("el cliente ve en su portal los accesos de soporte a su cuenta, sin saber qué administrador fue", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);

  await page.getByRole("link", { name: "Accesos de soporte" }).first().click();

  await expect(page).toHaveURL(/\/cuenta\/accesos-de-soporte/);
  await expect(page.getByRole("heading", { name: "Accesos de soporte" })).toBeVisible();
  const tabla = page.getByTestId("accesos-de-soporte");
  await expect(tabla.locator("tbody tr")).toHaveCount(2);
  await expect(tabla.locator("tbody tr").first()).toContainText("demo@example.com");
  // Visible de verdad, no solo presente en el DOM: es lo que el cliente necesita leer.
  await expect(tabla.getByText("demo@example.com")).toBeVisible();
  await expect(tabla.getByText("15 min")).toBeVisible();
  await expect(tabla.locator("tbody tr").first()).toContainText("15 min");
  // El acceso cuyo registro no se entiende se muestra igual, con su fecha.
  await expect(tabla.locator("tbody tr").nth(1)).toContainText("Sin detalle");
  await expect(page.locator("body")).not.toContainText("administrador");
  // Una sesión normal no muestra el aviso de soporte.
  await expect(page.getByTestId("aviso-de-soporte")).toHaveCount(0);
});

test("sin sesión, el historial de accesos redirige al login", async ({ page }) => {
  await page.goto("/cuenta/accesos-de-soporte");

  await expect(page).toHaveURL(/\/login/);
});
