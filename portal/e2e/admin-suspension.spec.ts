import { expect, test, type Page } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";

// El mock (src/mocks/data.ts) siembra «Cliente 06» ya suspendida y nadie la muta. «Cliente 09» y «Cliente 10» son las que suspenden y
// reactivan estos tests: cada una vive en UN solo test (con `fullyParallel` los tests de un archivo corren a la vez) y se reactiva al final.
// «Cliente 04» es una cuenta activa que nadie toca.
const cuenta = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
const SUSPENDIDA = cuenta(6);
const ACTIVA = cuenta(4);
const PARA_ACCION = cuenta(9);
const PARA_CONFLICTO = cuenta(10);

const diálogo = (page: Page) => page.getByTestId("suspension-confirmacion");

/** Deja la cuenta activa pase lo que pase en el test: una aserción que falla a mitad no debe dejarla suspendida para los demás. */
async function reactivarSiHaceFalta(page: Page, id: string) {
  await page.request.post(`/api/admin/cuentas/${id}/reactivar`).catch(() => undefined);
}

test("el listado dice qué cuentas están suspendidas y las distingue de las activas", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto("/admin/cuentas");

  const suspendida = page.locator("tr", { hasText: "Cliente 06" });
  await expect(suspendida).toHaveAttribute("data-estado-cuenta", "SUSPENDIDA");
  await expect(suspendida.getByText("Suspendida")).toBeVisible();
  await expect(suspendida).toHaveClass(/bg-destructive/);
  const activa = page.locator("tr", { hasText: "Cliente 04" });
  await expect(activa).toHaveAttribute("data-estado-cuenta", "ACTIVA");
  await expect(activa.getByText("Activa")).toBeVisible();
  await expect(activa).not.toHaveClass(/bg-destructive/);
});

test("una cuenta suspendida sigue apareciendo en la búsqueda, para poder reactivarla", async ({ page }) => {
  await entrarComoAdmin(page);

  await page.goto("/admin/cuentas?q=Cliente 06");

  await expect(page.locator("tbody tr")).toHaveCount(1);
  await expect(page.getByRole("link", { name: "Cliente 06" })).toBeVisible();
});

test("el detalle de una cuenta suspendida dice desde cuándo y ofrece reactivar, no suspender", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/cuentas/${SUSPENDIDA}`);

  const detalle = page.getByTestId("cuenta-detalle");
  await expect(detalle.getByText("Suspendida", { exact: true })).toBeVisible();
  await expect(detalle.getByTestId("suspendida-desde")).toContainText("Suspendida desde");
  await expect(detalle.getByTestId("reactivar-cuenta")).toBeVisible();
  await expect(detalle.getByTestId("suspender-cuenta")).toHaveCount(0);
});

test("el detalle de una cuenta activa ofrece suspender, no reactivar, y no dice nada de una suspensión", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/cuentas/${ACTIVA}`);

  const detalle = page.getByTestId("cuenta-detalle");
  await expect(detalle.getByText("Activa", { exact: true })).toBeVisible();
  await expect(detalle.getByTestId("suspender-cuenta")).toBeVisible();
  await expect(detalle.getByTestId("reactivar-cuenta")).toHaveCount(0);
  await expect(detalle.getByTestId("suspendida-desde")).toHaveCount(0);
});

test("suspender y reactivar una cuenta: pide confirmación, dice el efecto y cambia el estado en el detalle y en el listado", async ({ page }) => {
  await entrarComoAdmin(page);
  try {
    await page.goto(`/admin/cuentas/${PARA_ACCION}`);
    const detalle = page.getByTestId("cuenta-detalle");
    await expect(detalle.getByText("Activa", { exact: true })).toBeVisible();

    // Abrir el diálogo no cambia nada, y cancelar tampoco.
    await detalle.getByTestId("suspender-cuenta").click();
    await expect(diálogo(page)).toContainText("Suspender la cuenta");
    await expect(diálogo(page).getByTestId("suspension-alcance")).toHaveText("Sin empresas todavía");
    await expect(diálogo(page)).toContainText("ni siquiera con una sesión ya abierta");
    await expect(diálogo(page)).toContainText("seguirán enviándose a SUNAT");
    await expect(diálogo(page)).toContainText("lo devuelve todo a como estaba");
    await diálogo(page).getByRole("button", { name: "Cancelar" }).click();
    await expect(diálogo(page)).toHaveCount(0);
    await expect(detalle.getByText("Activa", { exact: true })).toBeVisible();

    // Confirmar, con motivo: la página pasa a «Suspendida» y ofrece reactivar.
    await detalle.getByTestId("suspender-cuenta").click();
    await diálogo(page).getByLabel(/Motivo/).fill("factura de septiembre sin pagar");
    await diálogo(page).getByTestId("suspension-confirmar").click();
    await expect(diálogo(page)).toHaveCount(0);
    await expect(detalle.getByText("Suspendida", { exact: true })).toBeVisible();
    await expect(detalle.getByTestId("suspendida-desde")).toContainText("Suspendida desde");
    await expect(detalle.getByTestId("reactivar-cuenta")).toBeVisible();
    await expect(detalle.getByTestId("suspender-cuenta")).toHaveCount(0);

    // El listado lo refleja.
    await page.goto("/admin/cuentas?q=Cliente 09");
    await expect(page.locator("tbody tr")).toHaveAttribute("data-estado-cuenta", "SUSPENDIDA");

    // Reactivar: otra confirmación, y todo vuelve.
    await page.goto(`/admin/cuentas/${PARA_ACCION}`);
    await detalle.getByTestId("reactivar-cuenta").click();
    await expect(diálogo(page)).toContainText("Reactivar la cuenta");
    await expect(diálogo(page)).toContainText("con las mismas API keys");
    await expect(diálogo(page).getByLabel(/Motivo/)).toHaveCount(0);
    await diálogo(page).getByTestId("suspension-confirmar").click();
    await expect(diálogo(page)).toHaveCount(0);
    await expect(detalle.getByText("Activa", { exact: true })).toBeVisible();
    await expect(detalle.getByTestId("suspender-cuenta")).toBeVisible();
    await expect(detalle.getByTestId("suspendida-desde")).toHaveCount(0);

    await page.goto("/admin/cuentas?q=Cliente 09");
    await expect(page.locator("tbody tr")).toHaveAttribute("data-estado-cuenta", "ACTIVA");
  } finally {
    await reactivarSiHaceFalta(page, PARA_ACCION);
  }
});

/** Si otro administrador ya la suspendió, el botón de esta página quedó viejo: se dice, y la página muestra el estado real. */
test("si otro administrador ya la suspendió, lo dice y la página se actualiza", async ({ page }) => {
  await entrarComoAdmin(page);
  try {
    await page.goto(`/admin/cuentas/${PARA_CONFLICTO}`);
    const detalle = page.getByTestId("cuenta-detalle");
    await detalle.getByTestId("suspender-cuenta").click();
    // Mientras tanto, otro administrador la suspende.
    const otro = await page.request.post(`/api/admin/cuentas/${PARA_CONFLICTO}/suspender`, { data: { motivo: "lo hizo otro" } });
    expect(otro.status()).toBe(200);

    await diálogo(page).getByTestId("suspension-confirmar").click();

    await expect(diálogo(page).getByRole("alert")).toContainText("ya está suspendida");
    await diálogo(page).getByRole("button", { name: "Cancelar" }).click();
    await expect(detalle.getByText("Suspendida", { exact: true })).toBeVisible();
    await expect(detalle.getByTestId("reactivar-cuenta")).toBeVisible();
  } finally {
    await reactivarSiHaceFalta(page, PARA_CONFLICTO);
  }
});

test("un motivo de más de 200 caracteres no cabe en el campo", async ({ page }) => {
  await entrarComoAdmin(page);
  await page.goto(`/admin/cuentas/${ACTIVA}`);

  await page.getByTestId("suspender-cuenta").click();
  // «Cliente 04» tiene una empresa: el diálogo dice a cuántas alcanza la suspensión.
  await expect(diálogo(page).getByTestId("suspension-alcance")).toHaveText("1 empresa");
  await diálogo(page).getByLabel(/Motivo/).fill("x".repeat(250));

  await expect(diálogo(page).getByLabel(/Motivo/)).toHaveValue("x".repeat(200));
  await expect(diálogo(page)).toContainText("Llegaste al máximo de 200");
});

/** El BFF protege la acción: sin la sesión del administrador no hay forma de suspender, y la cuenta sigue como estaba. */
test("sin sesión de administrador no se puede suspender ni reactivar", async ({ request }) => {
  const suspender = await request.post(`/api/admin/cuentas/${ACTIVA}/suspender`, { data: { motivo: "x" } });
  const reactivar = await request.post(`/api/admin/cuentas/${SUSPENDIDA}/reactivar`);

  expect(suspender.status()).toBe(401);
  expect(reactivar.status()).toBe(401);
});

test("un id que no es un UUID se rechaza antes de llegar al backend", async ({ page }) => {
  await entrarComoAdmin(page);

  const r = await page.request.post("/api/admin/cuentas/no-es-un-uuid/suspender");

  expect(r.status()).toBe(400);
  expect((await r.json()).codigo).toBe("ID_INVALIDO");
});

// --- lo que ve el cliente suspendido ---------------------------------------------------------------------------------------------

test("un cliente con la cuenta suspendida no entra y se le dice por qué", async ({ page }) => {
  await page.goto("/login");

  await page.getByLabel("Correo electrónico").fill("suspendida@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();

  await expect(page.getByText("Tu cuenta está suspendida")).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});

test("la página de cuenta suspendida explica que no se borró nada y deja cerrar la sesión", async ({ page }) => {
  await page.goto("/cuenta-suspendida");

  await expect(page.getByRole("heading", { name: "Tu cuenta está suspendida" })).toBeVisible();
  await expect(page.getByTestId("cuenta-suspendida")).toContainText("No se borró nada");
  await expect(page.getByTestId("cuenta-suspendida")).toContainText("contacta a soporte");

  await page.getByRole("button", { name: /Cerrar sesión/ }).click();

  await expect(page).toHaveURL(/\/login/);
});
