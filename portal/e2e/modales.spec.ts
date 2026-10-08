import { expect, test } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";
import { clicFuera } from "./clic-fuera";
import { esperarHidratacion } from "./hidratacion";

/**
 * Regla de producto: ningún modal se cierra con un clic fuera (un clic perdido tiraba lo escrito o lo que se ve una sola vez). La trae
 * `ui/dialog.tsx` por defecto, así que se prueba en modales de distinto origen: uno del backoffice y dos del portal del cliente.
 */

test("«Nuevo plan» del backoffice no se cierra con un clic fuera", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/planes");
  await esperarHidratacion(page, '[data-testid="plan-nuevo"]');
  await page.getByRole("banner").getByTestId("plan-nuevo").click();
  const dialogo = page.getByTestId("plan-formulario");
  await expect(dialogo).toBeVisible();

  await clicFuera(page);

  await expect(dialogo).toBeVisible();
});

test.describe("portal del cliente", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel("Correo electrónico").fill("demo@example.com");
    await page.getByLabel("Contraseña").fill("Passw0rd1");
    await page.getByRole("button", { name: "Iniciar sesión" }).click();
    await expect(page).toHaveURL(/\/comprobantes/);
  });

  test("«Nueva serie» no se cierra con un clic fuera ni pierde lo escrito", async ({ page }) => {
    await page.goto("/series");
    await page.getByRole("button", { name: "Nueva serie" }).click();
    await page.getByLabel("Código de serie").fill("F009");

    await clicFuera(page);

    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(page.getByLabel("Código de serie")).toHaveValue("F009");
  });

  test("el emisor de comprobantes tampoco se cierra con un clic fuera", async ({ page }) => {
    await page.getByRole("button", { name: /Nueva factura|Nuevo comprobante/ }).click();
    await expect(page.getByRole("dialog")).toBeVisible();

    await clicFuera(page);

    await expect(page.getByRole("dialog")).toBeVisible();
  });
});
