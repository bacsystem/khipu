import { expect, test } from "@playwright/test";
import en from "../src/messages/en.json";
import es from "../src/messages/es.json";

/**
 * #25: el locale sale de la cookie `khipu_locale`, sin prefijo en la URL. Con `en` el login y el panel se ven en inglés de punta a punta
 * (Server Components, Client Components y la etiqueta `<html lang>`); sin cookie, o con un valor desconocido, el portal sigue en castellano
 * aunque el navegador de Playwright esté en inglés.
 */
async function conLocale(context: import("@playwright/test").BrowserContext, baseURL: string | undefined, valor: string) {
  await context.addCookies([{ name: "khipu_locale", value: valor, url: baseURL ?? "http://localhost:3100" }]);
}

test("con khipu_locale=en el login y el panel de inicio se ven en inglés", async ({ page, context, baseURL }) => {
  await conLocale(context, baseURL, "en");
  await page.goto("/login");

  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page).toHaveTitle(`${en.auth.login.titulo} · ${en.app.nombre}`);
  await expect(page.getByRole("heading", { name: en.auth.login.titulo })).toBeVisible();
  await page.getByLabel(en.auth.login.email, { exact: true }).fill("demo@example.com");
  await page.getByLabel(en.auth.login.password, { exact: true }).fill("Passw0rd1");
  await page.getByRole("button", { name: en.auth.login.enviar }).click();

  await expect(page).toHaveURL(/\/inicio$/);
  const menu = page.getByRole("navigation", { name: en.nav.principal });
  await expect(menu.getByRole("link", { name: en.nav.items.comprobantes })).toBeVisible();
  await expect(menu.getByRole("link", { name: en.nav.items.plan })).toBeVisible();
  await expect(page.getByRole("navigation", { name: en.nav.ubicacion })).toContainText(en.nav.items.inicio);
  await expect(page.getByRole("combobox", { name: en.nav.empresas.cambiar })).toContainText("Demo SAC");
  await expect(page.getByTestId("kpis-del-mes")).toBeVisible();
  await expect(page.getByText(es.nav.items.comprobantes, { exact: true })).toHaveCount(0);
});

test("sin cookie el portal está en castellano aunque el navegador esté en inglés", async ({ page }) => {
  await page.goto("/login");
  await expect(page.locator("html")).toHaveAttribute("lang", "es");
  await expect(page.getByRole("heading", { name: es.auth.login.titulo })).toBeVisible();
});

test("un valor de khipu_locale que no se soporta cae en castellano", async ({ page, context, baseURL }) => {
  await conLocale(context, baseURL, "qu");
  await page.goto("/login");
  await expect(page.locator("html")).toHaveAttribute("lang", "es");
  await expect(page.getByRole("button", { name: es.auth.login.enviar })).toBeVisible();
});
