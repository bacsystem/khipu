import { expect, test, type Page } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";

const miga = (page: Page) => page.getByRole("navigation", { name: "Ubicación" });

test("el inicio del backoffice muestra su miga y ninguna acción de crear cuentas", async ({ page }) => {
  await entrarComoAdmin(page);

  await expect(miga(page)).toContainText("Backoffice");
  await expect(miga(page).locator("[aria-current=page]")).toHaveText("Inicio");
  await expect(page.getByRole("button", { name: "Nueva cuenta" })).toHaveCount(0);
});

test("en Cuentas la miga dice «Clientes / Cuentas» y «Nueva cuenta» está en la cabecera", async ({ page }) => {
  await entrarComoAdmin(page);

  await page.getByRole("link", { name: "Cuentas" }).click();

  await expect(page).toHaveURL(/\/admin\/cuentas$/);
  await expect(miga(page)).toContainText("Clientes");
  await expect(miga(page).locator("[aria-current=page]")).toHaveText("Cuentas");
  await expect(page.getByRole("banner").getByRole("button", { name: "Nueva cuenta" })).toBeVisible();
  // La miga no compite con el título de la página: sigue habiendo un solo h1.
  await expect(page.getByRole("heading", { level: 1 })).toHaveCount(1);
});

test("en Planes «Nuevo plan» está en la cabecera, como «Nueva cuenta» en Cuentas, y abre el formulario", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/planes");

  const cabecera = page.getByRole("banner");
  await expect(miga(page).locator("[aria-current=page]")).toHaveText("Planes");
  await expect(cabecera.getByTestId("plan-nuevo")).toHaveText("Nuevo plan");
  await expect(page.getByTestId("plan-nuevo")).toHaveCount(1);
  await cabecera.getByTestId("plan-nuevo").click();
  await expect(page.getByTestId("plan-formulario")).toBeVisible();
});

test.describe("en móvil", () => {
  test.use({ viewport: { width: 390, height: 844 } });

  /** Antes el sidebar solo existía desde `md` y en móvil el backoffice no tenía ninguna navegación. */
  test("el menú abre la navegación, lleva a Cuentas y se cierra solo", async ({ page }) => {
    await entrarComoAdmin(page);
    await expect(page.getByRole("link", { name: "Cuentas" })).toBeHidden();

    await page.getByRole("button", { name: "Abrir menú" }).click();
    await page.getByRole("link", { name: "Cuentas" }).click();

    await expect(page).toHaveURL(/\/admin\/cuentas$/);
    await expect(page.getByRole("heading", { name: "Cuentas" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Cuentas" })).toBeHidden();
  });
});
