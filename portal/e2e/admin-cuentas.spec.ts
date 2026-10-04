import { expect, test, type Page } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";
import { esperarHidratacion } from "./hidratacion";

// El mock (src/mocks/data.ts) siembra 12 cuentas: 10 en la primera página y, las dos más antiguas, Luna y Ana, en la segunda.

const filas = (page: Page) => page.locator("tbody tr");

test("sin sesión, /admin/cuentas redirige al login del backoffice", async ({ page }) => {
  await page.goto("/admin/cuentas");
  await expect(page).toHaveURL(/\/admin\/login/);
});

/** El listado trae el correo de todos los clientes: una sesión de cliente del portal no debe poder abrirlo. */
test("una sesión de cliente no abre /admin/cuentas", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);

  await page.goto("/admin/cuentas");

  await expect(page).toHaveURL(/\/admin\/login/);
});

test("el administrador llega a Cuentas desde el menú y ve la primera página con su total", async ({ page }) => {
  await entrarComoAdmin(page);

  await page.getByRole("link", { name: "Cuentas" }).click();

  await expect(page).toHaveURL(/\/admin\/cuentas$/);
  await expect(page.getByRole("heading", { name: "Cuentas" })).toBeVisible();
  await expect(filas(page)).toHaveCount(10);
  await expect(page.locator("body")).toContainText(/Mostrando\s*1–10\s*de\s*12/);
  // La cuenta más reciente nunca inició sesión: se dice, no se inventa una fecha.
  await expect(page.getByText("Nunca")).toBeVisible();
  await expect(page.getByText("Último inicio de sesión")).toBeVisible();
});

test("Siguiente lleva a la segunda página con las dos cuentas más antiguas", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/cuentas");

  await page.getByRole("link", { name: /Siguiente/ }).click();

  await expect(page).toHaveURL(/pagina=2/);
  await expect(filas(page)).toHaveCount(2);
  await expect(page.getByText("Panadería Sol")).toBeVisible();
  await expect(page.getByText("Ferretería Luna")).toBeVisible();
  await expect(page.locator("body")).toContainText(/Mostrando\s*11–12\s*de\s*12/);
});

/** Una URL editada a mano o un marcador viejo: antes salía «Mostrando 981–980 de 12» y «Todavía no hay cuentas». */
test("una página pasada de la última lleva a la última en vez de una tabla vacía", async ({ page }) => {
  await entrarComoAdmin(page);

  await page.goto("/admin/cuentas?pagina=99");

  await expect(page).toHaveURL(/pagina=2$/);
  await expect(filas(page)).toHaveCount(2);
  await expect(page.locator("body")).toContainText(/Mostrando\s*11–12\s*de\s*12/);
  await expect(page.getByText("Todavía no hay cuentas.")).toHaveCount(0);
});

test("busca por razón social y encuentra la cuenta aunque esté en la segunda página", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/cuentas");
  await esperarHidratacion(page, "#buscar-cuentas");

  await page.getByRole("searchbox").fill("ferreter");
  await page.getByRole("searchbox").press("Enter");

  await expect(page).toHaveURL(/q=ferreter/);
  await expect(filas(page)).toHaveCount(1);
  await expect(page.getByText("luis@luna.pe")).toBeVisible();
  await expect(page.locator("body")).toContainText(/Mostrando\s*1–1\s*de\s*1/);
});

test("el RUC se busca por prefijo, no por un fragmento interno", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/cuentas");
  await esperarHidratacion(page, "#buscar-cuentas");

  await page.getByRole("searchbox").fill("2010004");
  await page.getByRole("searchbox").press("Enter");
  await expect(page.getByText("ana@sol.pe")).toBeVisible();
  await expect(filas(page)).toHaveCount(1);

  await page.getByRole("searchbox").fill("0047");
  await page.getByRole("searchbox").press("Enter");
  await expect(page.getByText("No hay cuentas con esos filtros.")).toBeVisible();
});

test("sin resultados lo dice y «Quitar filtros» vuelve al listado completo", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/cuentas");
  await esperarHidratacion(page, "#buscar-cuentas");

  await page.getByRole("searchbox").fill("no-existe");
  await page.getByRole("searchbox").press("Enter");
  await expect(page.getByText("No hay cuentas con esos filtros.")).toBeVisible();

  await page.getByRole("link", { name: "Quitar filtros" }).click();

  await expect(page).toHaveURL(/\/admin\/cuentas$/);
  await expect(filas(page)).toHaveCount(10);
  await expect(page.getByRole("searchbox")).toHaveValue("");
});

test("la URL con la búsqueda es compartible: abrirla directo ya muestra lo filtrado", async ({ page }) => {
  await entrarComoAdmin(page);

  await page.goto("/admin/cuentas?q=luna");

  await expect(filas(page)).toHaveCount(1);
  await expect(page.getByText("luis@luna.pe")).toBeVisible();
  await expect(page.getByRole("searchbox")).toHaveValue("luna");
});
