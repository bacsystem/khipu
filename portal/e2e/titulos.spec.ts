import { expect, test } from "@playwright/test";

const APP = "Portal de facturación electrónica";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
});

/** C5: cada pestaña del panel dice en qué página está; antes todas se llamaban igual y no se distinguían entre varias abiertas. */
for (const [ruta, titulo] of [
  ["/comprobantes", "Comprobantes"],
  ["/series", "Series correlativas"],
  ["/empresa", "Fiscal y certificado"],
  ["/establecimientos", "Establecimientos"],
  ["/api-keys", "API keys e integración"],
  ["/cuenta/accesos-de-soporte", "Accesos de soporte"],
] as const) {
  test(`la pestaña de ${ruta} se llama «${titulo}»`, async ({ page }) => {
    await page.goto(ruta);
    await expect(page).toHaveTitle(`${titulo} · ${APP}`);
  });
}

/** C9: «Accesos de soporte» tenía su propio encabezado y ninguna miga, distinto del resto del panel. */
test("«Accesos de soporte» se ubica en la barra superior como las demás páginas, con un solo título", async ({ page }) => {
  await page.goto("/cuenta/accesos-de-soporte");
  const miga = page.getByRole("navigation", { name: "Ubicación" });
  await expect(miga).toContainText("Cuenta");
  await expect(miga.getByRole("heading", { name: "Accesos de soporte", level: 1 })).toBeVisible();
  await expect(page.getByRole("heading", { level: 1 })).toHaveCount(1);
});

test("el detalle de un comprobante y su nota también tienen título propio", async ({ page }) => {
  // Una factura aceptada del mock (`src/mocks/data.ts`): admite nota.
  await page.goto("/comprobantes/f-aceptada");
  await expect(page).toHaveTitle(`Comprobante · ${APP}`);
  // 264-H2: ningún dato «no expuesto todavía» deshabilitado en el detalle.
  await expect(page.getByText("idempotency_key:")).toHaveCount(0);

  await page.goto("/comprobantes/f-aceptada/nota");
  await expect(page).toHaveTitle(`Nota de crédito o débito · ${APP}`);
});
