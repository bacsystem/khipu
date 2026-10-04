import { expect, test, type Page } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";

// El mock (src/mocks/data.ts) siembra 12 empresas, de la más reciente a la más antigua: la primera página trae 10 y la segunda, «Ferretería
// Luna» (certificado vencido) y «Panadería Sol» (por vencer en 10 días). Por estado del certificado: 2 vencidas, 2 por vencer, 2 vigentes
// (200 y 30 días justos), 1 sin fecha y 5 sin certificado. Por entorno: 3 en producción.

const filas = (page: Page) => page.locator("tbody tr");

test("sin sesión, /admin/empresas redirige al login del backoffice", async ({ page }) => {
  await page.goto("/admin/empresas");
  await expect(page).toHaveURL(/\/admin\/login/);
});

test("desde el menú, el administrador llega a Empresas y ve la primera página con su total y todas las columnas", async ({ page }) => {
  await entrarComoAdmin(page);

  await page.getByRole("link", { name: "Empresas" }).click();

  await expect(page).toHaveURL(/\/admin\/empresas$/);
  await expect(page.getByRole("heading", { name: "Empresas", level: 1 })).toBeVisible();
  await expect(filas(page)).toHaveCount(10);
  await expect(page.locator("body")).toContainText(/Mostrando\s*1–10\s*de\s*12/);
  for (const columna of ["Empresa", "Cuenta", "Entorno", "Certificado", "Credenciales SOL", "Series", "Comprobantes del mes", "Última emisión"])
    await expect(page.getByRole("columnheader", { name: columna })).toBeVisible();
  // Una empresa de integración no tiene cuenta, y una que nunca emitió lo dice: no se inventa nada.
  await expect(page.getByText("Sin cuenta").first()).toBeVisible();
  await expect(page.getByText("Nunca emitió").first()).toBeVisible();
});

/** El criterio del issue: vencido y por vencer se distinguen a simple vista, sin leer la columna. */
test("las empresas con el certificado vencido o por vencer se distinguen a simple vista", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/empresas?pagina=2");

  const vencida = page.locator('tr[data-certificado="VENCIDO"]');
  const porVencer = page.locator('tr[data-certificado="POR_VENCER"]');
  await expect(vencida).toHaveCount(1);
  await expect(porVencer).toHaveCount(1);
  await expect(vencida).toContainText("FERRETERIA LUNA SAC");
  await expect(vencida).toContainText(/Venció el/);
  await expect(porVencer).toContainText("PANADERIA SOL SAC");
  await expect(porVencer).toContainText(/Vence el .* \(10 días\)/);
  await expect(vencida).toHaveClass(/bg-destructive/);
  await expect(porVencer).toHaveClass(/bg-warning/);
});

test("filtrar por el estado del certificado cambia la URL y deja solo esas empresas", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/empresas");

  await page.getByLabel("Certificado").selectOption("VENCIDO");

  await expect(page).toHaveURL(/certificado=VENCIDO/);
  await expect(filas(page)).toHaveCount(2);
  await expect(page.locator("body")).toContainText(/Mostrando\s*1–2\s*de\s*2/);
  await expect(page.locator('tr[data-certificado="VENCIDO"]')).toHaveCount(2);
});

test("cada estado del certificado trae las empresas que le tocan", async ({ page }) => {
  await entrarComoAdmin(page);
  const esperadas: Array<[string, number]> = [["SIN_CERTIFICADO", 5], ["SIN_FECHA", 1], ["VIGENTE", 2], ["POR_VENCER", 2], ["VENCIDO", 2]];

  for (const [estado, cuantas] of esperadas) {
    await page.goto(`/admin/empresas?certificado=${estado}`);
    await expect(filas(page), estado).toHaveCount(cuantas);
    await expect(page.locator(`tr[data-certificado="${estado}"]`), estado).toHaveCount(cuantas);
  }
});

/** 30 días justos todavía es vigente; 29 ya es «por vencer» (la épica: «< 30 días»). */
test("el borde de los 30 días: «Cliente 08» (30) es vigente y «Cliente 07» (29) está por vencer", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/empresas?certificado=VIGENTE");
  await expect(page.getByText("CLIENTE 08 SAC")).toBeVisible();
  await expect(page.getByText("CLIENTE 07 SAC")).toHaveCount(0);

  await page.goto("/admin/empresas?certificado=POR_VENCER");
  await expect(page.getByText("CLIENTE 07 SAC")).toBeVisible();
  await expect(page.getByText("CLIENTE 08 SAC")).toHaveCount(0);
});

test("filtrar por entorno, y combinado con el certificado", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/empresas");

  await page.getByLabel("Entorno").selectOption("PRODUCCION");
  await expect(page).toHaveURL(/entorno=PRODUCCION/);
  await expect(filas(page)).toHaveCount(3);

  await page.getByLabel("Certificado").selectOption("VENCIDO");
  await expect(page).toHaveURL(/entorno=PRODUCCION&certificado=VENCIDO/);
  await expect(filas(page)).toHaveCount(1);
  await expect(page.getByText("FERRETERIA LUNA SAC")).toBeVisible();
});

test("cambiar un filtro vuelve a la primera página", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/empresas?pagina=2");

  await page.getByLabel("Entorno").selectOption("BETA");

  await expect(page).not.toHaveURL(/pagina=/);
  await expect(page).toHaveURL(/entorno=BETA/);
});

test("«Quitar filtros» vuelve al listado completo", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/empresas?entorno=PRODUCCION&certificado=VENCIDO");
  await expect(filas(page)).toHaveCount(1);

  await page.getByRole("link", { name: "Quitar filtros" }).click();

  await expect(page).toHaveURL(/\/admin\/empresas$/);
  await expect(page.locator("body")).toContainText(/Mostrando\s*1–10\s*de\s*12/);
});

test("la URL con los filtros es compartible: abrirla directo ya muestra lo filtrado y los selectores lo reflejan", async ({ page }) => {
  await entrarComoAdmin(page);

  await page.goto("/admin/empresas?entorno=PRODUCCION&certificado=POR_VENCER");

  await expect(filas(page)).toHaveCount(1);
  await expect(page.getByText("CLIENTE 07 SAC")).toBeVisible();
  await expect(page.getByLabel("Entorno")).toHaveValue("PRODUCCION");
  await expect(page.getByLabel("Certificado")).toHaveValue("POR_VENCER");
});

test("un filtro inventado en la URL se ignora: no rompe el listado", async ({ page }) => {
  await entrarComoAdmin(page);

  await page.goto("/admin/empresas?entorno=STAGING&certificado=CADUCADO");

  await expect(page.getByRole("alert")).toHaveCount(0);
  await expect(page.locator("body")).toContainText(/Mostrando\s*1–10\s*de\s*12/);
});

test("sin resultados lo dice, en vez de una tabla vacía", async ({ page }) => {
  await entrarComoAdmin(page);

  await page.goto("/admin/empresas?entorno=PRODUCCION&certificado=SIN_FECHA");

  await expect(page.getByText("No hay empresas con esos filtros.")).toBeVisible();
});

test("Siguiente lleva a la segunda página con las dos empresas más antiguas, y una página pasada de la última lleva a la última", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/empresas");

  await page.getByRole("link", { name: /Siguiente/ }).click();
  await expect(page).toHaveURL(/pagina=2/);
  await expect(filas(page)).toHaveCount(2);

  await page.goto("/admin/empresas?pagina=9");
  await expect(page).toHaveURL(/pagina=2/);
  await expect(filas(page)).toHaveCount(2);
});

test("la cuenta de una empresa lleva a su detalle", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/empresas?pagina=2");

  await page.getByRole("link", { name: "Panadería Sol" }).click();

  await expect(page).toHaveURL(/\/admin\/cuentas\/00000000-0000-4000-8000-000000000001$/);
  await expect(page.getByRole("heading", { name: "Panadería Sol", level: 1 })).toBeVisible();
});

test("el menú marca «Empresas» como la página actual y la cabecera la ubica bajo «Clientes»", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/empresas");

  const item = page.getByRole("link", { name: "Empresas" });
  await expect(item).toBeVisible();
  await expect(item).toHaveClass(/bg-accent/);
  await expect(page.getByRole("navigation", { name: "Ubicación" })).toContainText("Clientes");
});
