import { expect, test, type Page } from "@playwright/test";
import { abrirPestana, entrarComoAdmin } from "./admin-sesion";

// El mock (src/mocks/data.ts) siembra «Cliente 13» de baja, con una empresa, y nadie la muta: dar de baja o reponer una cuenta cambia lo que cuentan
// las demás specs que corren a la vez (12 cuentas, 12 empresas), así que estas specs no mutan. El ciclo completo, con la bitácora, lo prueba el
// e2e real del backend (`BajaDeClienteE2ETest`) y el de los componentes.
const cuenta = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
const empresa = (n: number) => `00000000-0000-4000-9000-${String(n).padStart(12, "0")}`;
const DE_BAJA = cuenta(13);
const EN_SERVICIO = cuenta(4);

const filas = (page: Page) => page.locator("tbody tr");

test.beforeEach(async ({ page }) => {
  await entrarComoAdmin(page);
});

// --- listado de cuentas ---------------------------------------------------------------------------------------------------------------

test("por defecto las cuentas dadas de baja no salen, y el total las descuenta", async ({ page }) => {
  await page.goto("/admin/cuentas");

  await expect(page.locator("body")).toContainText(/Mostrando\s*1–10\s*de\s*12/);
  await expect(page.getByText("Cliente 13", { exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Cuentas dadas de baja")).toHaveValue("");
});

test("con «Solo las dadas de baja» salen únicamente esas, marcadas «De baja» y enlazadas a su detalle", async ({ page }) => {
  await page.goto("/admin/cuentas?bajas=SOLO");

  await expect(filas(page)).toHaveCount(1);
  const fila = page.locator("tr", { hasText: "Cliente 13" });
  await expect(fila).toHaveAttribute("data-estado-cuenta", "BAJA");
  await expect(fila.getByText("De baja")).toBeVisible();
  await expect(page.getByLabel("Cuentas dadas de baja")).toHaveValue("SOLO");
  await fila.getByRole("link", { name: "Cliente 13" }).click();
  await expect(page).toHaveURL(new RegExp(`/admin/cuentas/${DE_BAJA}$`));
});

test("con «Incluir» salen las 13, mezcladas", async ({ page }) => {
  await page.goto("/admin/cuentas?bajas=INCLUIDAS");

  await expect(page.locator("body")).toContainText(/Mostrando\s*1–10\s*de\s*13/);
});

test("el selector cambia la URL y la lista sin perder lo que ya se había elegido", async ({ page }) => {
  await page.goto("/admin/cuentas?por_pagina=20");

  await page.getByLabel("Cuentas dadas de baja").selectOption("SOLO");

  await expect(page).toHaveURL(/bajas=SOLO/);
  await expect(page).toHaveURL(/por_pagina=20/);
  await expect(filas(page)).toHaveCount(1);
  await page.getByLabel("Cuentas dadas de baja").selectOption("");
  await expect(page).not.toHaveURL(/bajas=/);
  await expect(page.locator("body")).toContainText(/de\s*12/);
});

test("la búsqueda no encuentra una cuenta de baja salvo que se pida verla", async ({ page }) => {
  await page.goto("/admin/cuentas?q=Cliente 13");
  await expect(page.getByText("No hay cuentas con esos filtros.")).toBeVisible();

  await page.getByLabel("Cuentas dadas de baja").selectOption("INCLUIDAS");

  await expect(page).toHaveURL(/q=Cliente\+13/);
  await expect(filas(page)).toHaveCount(1);
  await expect(page.getByRole("link", { name: "Cliente 13" })).toBeVisible();
});

test("un valor de bajas que no existe se ignora en vez de romper el listado", async ({ page }) => {
  await page.goto("/admin/cuentas?bajas=TODAS");

  await expect(page.locator("body")).toContainText(/de\s*12/);
  await expect(page.getByLabel("Cuentas dadas de baja")).toHaveValue("");
});

// --- listado de empresas --------------------------------------------------------------------------------------------------------------

test("las empresas de una cuenta de baja tampoco salen por defecto", async ({ page }) => {
  await page.goto("/admin/empresas");

  await expect(page.locator("body")).toContainText(/Mostrando\s*1–10\s*de\s*12/);
  await expect(page.getByText("CLIENTE 13 SAC")).toHaveCount(0);
});

test("con «Solo las dadas de baja» sale la empresa, con la marca «De baja» junto a su cuenta", async ({ page }) => {
  await page.goto("/admin/empresas?bajas=SOLO");

  await expect(filas(page)).toHaveCount(1);
  const fila = page.locator("tr", { hasText: "CLIENTE 13 SAC" });
  await expect(fila.getByText("De baja")).toBeVisible();
  await expect(fila.getByRole("link", { name: "Cliente 13", exact: true })).toBeVisible();
});

test("«Incluir» suma esa empresa a las demás", async ({ page }) => {
  await page.goto("/admin/empresas?bajas=INCLUIDAS");

  await expect(page.locator("body")).toContainText(/Mostrando\s*1–10\s*de\s*13/);
});

/** Lo que la ley obliga a conservar sigue consultable: la empresa de una cuenta de baja se abre igual. */
test("la empresa de una cuenta de baja se abre igual", async ({ page }) => {
  await page.goto(`/admin/empresas/${empresa(13)}`);

  await expect(page.getByRole("heading", { name: "CLIENTE 13 SAC", level: 1 })).toBeVisible();
});

// --- detalle de la cuenta -------------------------------------------------------------------------------------------------------------

test("el detalle de una cuenta de baja se abre, dice desde cuándo y ofrece reponerla o suspenderla", async ({ page }) => {
  await page.goto(`/admin/cuentas/${DE_BAJA}`);

  const detalle = page.getByTestId("cuenta-detalle");
  await expect(detalle.getByText("De baja", { exact: true })).toBeVisible();
  await expect(detalle.getByTestId("baja-desde")).toContainText("De baja desde");
  await expect(detalle.getByTestId("reponer-cuenta")).toBeVisible();
  await expect(detalle.getByTestId("dar-de-baja-cuenta")).toHaveCount(0);
  // H16: la baja no corta el acceso; suspenderla es lo que corta el servicio, así que se sigue ofreciendo.
  await expect(detalle.getByTestId("suspender-cuenta")).toBeVisible();
  await expect(detalle.getByTestId("reactivar-cuenta")).toHaveCount(0);
  // Se conserva todo: sus empresas siguen ahí.
  await abrirPestana(page, "Empresas");
  await expect(detalle.getByRole("region", { name: "Empresas" }).getByText("CLIENTE 13 SAC")).toBeVisible();
});

test("una cuenta en servicio ofrece dar de baja junto a suspender, y el diálogo dice que no corta el acceso", async ({ page }) => {
  await page.goto(`/admin/cuentas/${EN_SERVICIO}`);
  const detalle = page.getByTestId("cuenta-detalle");
  await expect(detalle.getByTestId("suspender-cuenta")).toBeVisible();

  await detalle.getByTestId("dar-de-baja-cuenta").click();

  const dialogo = page.getByTestId("baja-confirmacion");
  await expect(dialogo).toContainText("No corta el acceso");
  await expect(dialogo).toContainText("Los comprobantes, XML y CDR se conservan");
  await expect(dialogo).toContainText("su RUC sigue ocupado");
});

test("cancelar el diálogo no envía nada", async ({ page }) => {
  let pedidos = 0;
  await page.route("**/api/admin/cuentas/*/baja", (route) => {
    pedidos++;
    return route.continue();
  });
  await page.goto(`/admin/cuentas/${EN_SERVICIO}`);
  await page.getByTestId("dar-de-baja-cuenta").click();

  await page.getByTestId("baja-confirmacion").getByRole("button", { name: "Cancelar" }).click();

  await expect(page.getByTestId("baja-confirmacion")).toBeHidden();
  expect(pedidos).toBe(0);
});

test("el diálogo de reponer dice que no toca la suspensión", async ({ page }) => {
  await page.goto(`/admin/cuentas/${DE_BAJA}`);

  await page.getByTestId("reponer-cuenta").click();

  await expect(page.getByTestId("baja-confirmacion")).toContainText("si estaba suspendida, sigue suspendida");
});

// --- el BFF, de punta a punta, sin mutar ------------------------------------------------------------------------------------------------

test("dar de baja una cuenta que ya lo está es 409 con su código propio, y reponer una que no lo está también", async ({ page }) => {
  const yaDeBaja = await page.request.post(`/api/admin/cuentas/${DE_BAJA}/baja`);
  expect(yaDeBaja.status()).toBe(409);
  expect((await yaDeBaja.json()).codigo).toBe("CUENTA_YA_DE_BAJA");

  const noDeBaja = await page.request.post(`/api/admin/cuentas/${EN_SERVICIO}/reponer`);
  expect(noDeBaja.status()).toBe(409);
  expect((await noDeBaja.json()).codigo).toBe("CUENTA_NO_DE_BAJA");
});

test("un id que no es un UUID es 400 sin llegar al backend, y una cuenta que no existe es 404", async ({ page }) => {
  const basura = await page.request.post("/api/admin/cuentas/no-es-un-uuid/baja");
  expect(basura.status()).toBe(400);
  expect((await basura.json()).codigo).toBe("ID_INVALIDO");

  const inexistente = await page.request.post("/api/admin/cuentas/00000000-0000-4000-8000-0000000000ff/reponer");
  expect(inexistente.status()).toBe(404);
});

test("un motivo de más de 200 caracteres es 422 y no da de baja", async ({ page }) => {
  const res = await page.request.post(`/api/admin/cuentas/${EN_SERVICIO}/baja`, { data: { motivo: "x".repeat(201) } });

  expect(res.status()).toBe(422);
  expect((await res.json()).codigo).toBe("MOTIVO_INVALIDO");
});

test("sin sesión de administrador el BFF responde 401", async ({ request }) => {
  // El fixture `request` no comparte las cookies de la página: es un cliente sin sesión.
  expect((await request.post(`/api/admin/cuentas/${EN_SERVICIO}/baja`)).status()).toBe(401);
  expect((await request.post(`/api/admin/cuentas/${DE_BAJA}/reponer`)).status()).toBe(401);
});
