import { expect, test, type Page } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";

// El mock (src/mocks/handlers.ts) siembra en «Panadería Sol» cuatro usuarios: su administrador (verificado), `beto` (activo, sin verificar),
// `carla` (desactivada) y `sin-correo` (activo, pero el servidor no tiene SMTP). El mock no guarda nada, así que estas specs no se pisan.
const ID_SOL = "00000000-0000-4000-8000-000000000001";

const diálogo = (page: Page) => page.getByTestId("acceso-confirmacion");
const fila = (page: Page, correo: string) => page.getByTestId("cuenta-detalle").locator("tbody tr", { hasText: correo });

test.beforeEach(async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/cuentas/${ID_SOL}`);
});

test("restablecer pide confirmación, dice a quién le llega y que el administrador no ve la contraseña", async ({ page }) => {
  await fila(page, "beto@sol.pe").getByTestId("restablecer-usuario").click();

  await expect(diálogo(page)).toBeVisible();
  await expect(diálogo(page)).toContainText("beto@sol.pe");
  await expect(diálogo(page)).toContainText("Tú no ves ni defines la contraseña");
});

test("cancelar cierra el modal sin mandar nada", async ({ page }) => {
  let pedidos = 0;
  await page.route("**/api/admin/cuentas/*/usuarios/*/*", (route) => {
    pedidos++;
    return route.continue();
  });
  await fila(page, "beto@sol.pe").getByTestId("restablecer-usuario").click();

  await diálogo(page).getByRole("button", { name: "Cancelar" }).click();

  await expect(diálogo(page)).toBeHidden();
  expect(pedidos).toBe(0);
});

test("confirmar el restablecimiento dice a quién se le mandó el correo", async ({ page }) => {
  await fila(page, "beto@sol.pe").getByTestId("restablecer-usuario").click();

  await diálogo(page).getByTestId("acceso-confirmar").click();

  await expect(diálogo(page).getByTestId("acceso-hecho")).toHaveText("Correo de restablecimiento enviado a beto@sol.pe.");
  // Ya enviado no se ofrece mandar otro desde el mismo modal: solo cerrar.
  await expect(diálogo(page).getByTestId("acceso-confirmar")).toHaveCount(0);
  await diálogo(page).getByRole("button", { name: "Cerrar" }).first().click();
  await expect(diálogo(page)).toBeHidden();
});

test("reenviar la verificación llega al usuario sin verificar", async ({ page }) => {
  await fila(page, "beto@sol.pe").getByTestId("verificar-usuario").click();
  await expect(diálogo(page)).toContainText("beto@sol.pe");

  await diálogo(page).getByTestId("acceso-confirmar").click();

  await expect(diálogo(page).getByTestId("acceso-hecho")).toHaveText("Correo de verificación enviado a beto@sol.pe.");
});

test("un usuario con el correo ya verificado solo ofrece restablecer", async ({ page }) => {
  const principal = fila(page, "ana@sol.pe");

  await expect(principal.getByTestId("restablecer-usuario")).toBeVisible();
  await expect(principal.getByTestId("verificar-usuario")).toHaveCount(0);
});

test("un usuario desactivado no tiene ninguna acción de acceso", async ({ page }) => {
  await expect(fila(page, "carla@sol.pe").getByRole("button")).toHaveCount(0);
});

test("si el servidor no puede mandar correos, el modal lo dice y no afirma que se envió", async ({ page }) => {
  await fila(page, "sin-correo@sol.pe").getByTestId("restablecer-usuario").click();

  await diálogo(page).getByTestId("acceso-confirmar").click();

  await expect(diálogo(page).getByRole("alert")).toContainText("no se mandó nada");
  await expect(diálogo(page).getByTestId("acceso-hecho")).toHaveCount(0);
  // El error no cierra el modal: el administrador debe verlo.
  await expect(diálogo(page)).toBeVisible();
});

test("dos clics seguidos en Confirmar mandan un solo correo", async ({ page }) => {
  let pedidos = 0;
  await page.route("**/api/admin/cuentas/*/usuarios/*/restablecimiento", async (route) => {
    pedidos++;
    await route.continue();
  });
  await fila(page, "beto@sol.pe").getByTestId("restablecer-usuario").click();

  const confirmar = diálogo(page).getByTestId("acceso-confirmar");
  await confirmar.dblclick();

  await expect(diálogo(page).getByTestId("acceso-hecho")).toBeVisible();
  expect(pedidos).toBe(1);
});

test("el BFF rechaza un id de usuario que no es un UUID sin llegar al backend", async ({ page }) => {
  const res = await page.request.post(`/api/admin/cuentas/${ID_SOL}/usuarios/no-es-un-uuid/restablecimiento`);

  expect(res.status()).toBe(400);
  expect((await res.json()).codigo).toBe("ID_INVALIDO");
});

test("sin sesión de administrador el BFF responde 401", async ({ request }) => {
  // El fixture `request` no comparte las cookies de la página: es un cliente sin sesión.
  const res = await request.post(`/api/admin/cuentas/${ID_SOL}/usuarios/00000000-0000-4000-a000-000000009001/verificacion`);

  expect(res.status()).toBe(401);
});
