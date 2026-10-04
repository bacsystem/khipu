import { expect, test, type Page } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";
import { esperarHidratacion } from "./hidratacion";

// El mock (src/mocks/handlers.ts) arma el monitor con un patrón fijo por hora y deja la cola de envíos en «3 + lecturas» pendientes: crece una por cada lectura, así que una
// lectura posterior siempre tiene más que la anterior sin importar qué otra spec leyó entre medio (esta pantalla solo lee: ninguna spec se pisa con otra). La alerta y los
// fallos de lectura se fuerzan interceptando el BFF del navegador (`/api/admin/monitor`). Que el backend arme bien las horas, la tasa y la alerta lo prueban
// `MonitorDeEmisionServiceTest` y `MonitorDeEmisionE2ETest`; acá se prueba la pantalla, su BFF y su refresco.
const BFF = "**/api/admin/monitor";

test.beforeEach(async ({ page }) => {
  // El reloj de la página es controlable pero sigue corriendo solo: así el intervalo del refresco se adelanta a voluntad sin esperar 30 segundos reales.
  await page.clock.install();
  await entrarComoAdmin(page);
});

/** Abre el monitor y espera a que React haya hidratado el panel: antes de eso no hay intervalo de refresco que adelantar. */
async function abrir(page: Page) {
  await page.goto("/admin/monitor");
  await expect(page.getByTestId("monitor-hora")).toHaveCount(24);
  await esperarHidratacion(page, '[data-testid="monitor-horas"]');
}

const pendientes = async (page: Page) => Number(await page.getByTestId("monitor-outbox-pendientes").textContent());

/** Una lectura real del BFF con la cola cambiada: así la prueba no repite el JSON del mock. */
async function conCola(page: Page, cola: Record<string, unknown>) {
  await page.route(BFF, async (route) => {
    const respuesta = await route.fetch();
    const json = await respuesta.json();
    json.datos.outbox = { ...json.datos.outbox, ...cola };
    await route.fulfill({ response: respuesta, json });
  });
}

// --- la pantalla ----------------------------------------------------------------------------------------------------------------------------

test("el menú lleva a Monitor y la miga dice «Operación / Monitor»", async ({ page }) => {
  await page.getByRole("link", { name: "Monitor" }).click();

  await expect(page).toHaveURL(/\/admin\/monitor$/);
  const miga = page.getByRole("navigation", { name: "Ubicación" });
  await expect(miga).toContainText("Operación");
  await expect(miga.locator("[aria-current=page]")).toHaveText("Monitor");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Monitor de emisión");
  await expect(page.getByText(/Se actualiza solo cada 30 segundos/)).toBeVisible();
});

test("muestra las 24 horas, el día de Lima, la cola y SUNAT con la hora de la última lectura", async ({ page }) => {
  await abrir(page);

  await expect(page.getByTestId("monitor-ultima-lectura")).toHaveText(/^Última lectura: \d{2}:\d{2}:\d{2}$/);
  await expect(page.getByTestId("monitor-hora")).toHaveCount(24);
  await expect(page.getByTestId("monitor-hoy-total")).not.toHaveText("0");
  await expect(page.getByTestId("monitor-tasa")).toHaveText(/^\d+\.\d %$/);
  expect(await pendientes(page)).toBeGreaterThanOrEqual(3);
  await expect(page.getByTestId("monitor-alerta")).toHaveCount(0);
  await expect(page.getByTestId("monitor-servicio")).toHaveCount(4);
});

test("cada servicio de SUNAT dice si contesta y la consulta de CDR caída dice por qué", async ({ page }) => {
  await abrir(page);

  const produccion = page.locator('[data-testid="monitor-servicio"][data-servicio="ENVIO_PRODUCCION"]');
  await expect(produccion).toHaveAttribute("data-disponible", "true");
  await expect(produccion).toContainText("Envío (producción)");
  await expect(produccion).toContainText("140 ms");
  const cdr = page.locator('[data-testid="monitor-servicio"][data-servicio="CONSULTA_DE_CDR"]');
  await expect(cdr).toHaveAttribute("data-disponible", "false");
  await expect(cdr).toContainText("No disponible");
  await expect(cdr).toContainText("HTTP 503");
});

test("la tabla por hora trae las 24 horas", async ({ page }) => {
  await abrir(page);

  await page.getByText("Ver los números por hora").click();

  await expect(page.locator("details tbody tr")).toHaveCount(24);
});

// --- el refresco ----------------------------------------------------------------------------------------------------------------------------

test("se actualiza solo a los 30 segundos con una lectura nueva", async ({ page }) => {
  await abrir(page);
  const antes = await pendientes(page);

  await page.clock.fastForward(30_000);

  await expect.poll(() => pendientes(page)).toBeGreaterThan(antes);
});

test("a los 29 segundos todavía no pidió nada", async ({ page }) => {
  await abrir(page);
  let pedidos = 0;
  await page.route(BFF, async (route) => {
    pedidos += 1;
    await route.continue();
  });

  await page.clock.fastForward(29_000);
  await page.waitForTimeout(300);

  expect(pedidos).toBe(0);
});

test("si una lectura falla deja la última a la vista, lo dice y se recupera sola", async ({ page }) => {
  await abrir(page);
  const total = await page.getByTestId("monitor-hoy-total").textContent();
  await page.route(BFF, (route) => route.fulfill({ status: 502, json: { estado: "error", datos: null, mensaje: "El servidor no respondió", codigo: "RED", errores: null } }));

  await page.clock.fastForward(30_000);

  await expect(page.getByTestId("monitor-error")).toContainText("No se pudo actualizar el monitor. Se muestra la última lectura.");
  await expect(page.getByTestId("monitor-hoy-total")).toHaveText(total ?? "");
  await expect(page.getByTestId("monitor-hora")).toHaveCount(24);

  await page.unroute(BFF);
  await page.clock.fastForward(30_000);

  await expect(page.getByTestId("monitor-error")).toHaveCount(0);
});

test("si la sesión terminó lo dice, no ofrece reintentar y deja de pedir", async ({ page }) => {
  await abrir(page);
  let pedidos = 0;
  await page.route(BFF, (route) => {
    pedidos += 1;
    return route.fulfill({ status: 401, json: { estado: "error", datos: null, mensaje: "Sesión de administrador requerida", codigo: "NO_AUTORIZADO", errores: null } });
  });

  await page.clock.fastForward(30_000);
  await expect(page.getByTestId("monitor-error")).toContainText("Tu sesión terminó. Recarga la página para volver a entrar.");
  await expect(page.getByRole("button", { name: "Reintentar" })).toHaveCount(0);
  await page.clock.fastForward(120_000);
  await page.waitForTimeout(300);

  expect(pedidos).toBe(1);
});

// --- la alerta ------------------------------------------------------------------------------------------------------------------------------

test("con envíos vencidos hace más de cinco minutos muestra la alerta con cuántos y desde hace cuánto", async ({ page }) => {
  await abrir(page);
  await conCola(page, { vencidos: 3, vencido_hace_segundos: 3900, alerta: true });

  await page.clock.fastForward(30_000);

  const alerta = page.getByTestId("monitor-alerta");
  await expect(alerta).toContainText("El envío a SUNAT parece detenido");
  await expect(alerta).toContainText("Hay 3 envíos vencidos y el que más espera lleva 1 h 5 min.");
  await expect(page.getByTestId("monitor-outbox-vencidos")).toHaveText("3");
});

test("la alerta se va cuando la cola se normaliza", async ({ page }) => {
  await abrir(page);
  await conCola(page, { vencidos: 1, vencido_hace_segundos: 400, alerta: true });
  await page.clock.fastForward(30_000);
  await expect(page.getByTestId("monitor-alerta")).toBeVisible();

  await page.unroute(BFF);
  await page.clock.fastForward(30_000);

  await expect(page.getByTestId("monitor-alerta")).toHaveCount(0);
});
