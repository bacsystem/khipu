import { expect, test, type Page } from "@playwright/test";

// #637: el portal cuenta la inactividad de la persona, avisa 2 minutos antes del corte y cierra la sesión a los N minutos. El mock (src/mocks/handlers.ts) informa N = 30 en `/me`.
// El reloj de la página es controlable: adelantarlo salta el tiempo sin esperarlo. `fastForward` dispara cada temporizador vencido a lo sumo una vez, y el vigilante decide con
// tiempo de pared (compara `Date.now()` con la última actividad), así que un solo salto basta. El servidor, que no ve ese reloj, no corta: lo que se prueba es el corte del navegador.
const N = 30;
const MINUTO = 60_000;

test.beforeEach(async ({ page }) => {
  await page.clock.install();
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/inicio/);
});

const aviso = (page: Page) => page.getByTestId("aviso-de-inactividad");

/** Deja correr hasta el aviso (N − 2 min sin tocar nada) y espera a que se vea. */
async function hastaElAviso(page: Page) {
  await page.clock.fastForward((N - 2) * MINUTO);
  await expect(aviso(page)).toBeVisible();
}

test("sin interacción avisa a los N − 2 minutos y cierra a los N, y el login lo explica", async ({ page }) => {
  await page.clock.fastForward((N - 2) * MINUTO - 5_000);
  await expect(aviso(page)).toHaveCount(0);

  await page.clock.fastForward(5_000);
  await expect(aviso(page)).toBeVisible();
  await expect(aviso(page).getByRole("heading", { name: "Tu sesión está por cerrarse" })).toBeVisible();
  await expect(aviso(page).getByRole("button", { name: "Seguir conectado" })).toBeFocused();

  await page.clock.fastForward(2 * MINUTO);
  await expect(page).toHaveURL(/\/login\?motivo=inactividad/);
  await expect(page.getByText("Cerramos tu sesión por inactividad. Vuelve a ingresar.")).toBeVisible();

  // El logout fue el normal: la sesión ya no sirve.
  await page.goto("/inicio");
  await expect(page).toHaveURL(/\/login/);
});

test("«Seguir conectado» cierra el aviso, renueva la sesión en el servidor y reinicia la cuenta", async ({ page }) => {
  await hastaElAviso(page);
  const renovacion = page.waitForRequest((req) => req.url().endsWith("/api/auth/sesion") && req.method() === "POST");

  await aviso(page).getByRole("button", { name: "Seguir conectado" }).click();

  await renovacion;
  await expect(aviso(page)).toHaveCount(0);
  await page.clock.fastForward(10 * MINUTO);
  await expect(aviso(page)).toHaveCount(0);
  await expect(page).toHaveURL(/\/inicio/);
});

test("«Cerrar sesión» en el aviso sale en el acto, al login sin motivo de inactividad", async ({ page }) => {
  await hastaElAviso(page);

  await aviso(page).getByRole("button", { name: "Cerrar sesión" }).click();

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByText("Cerramos tu sesión por inactividad. Vuelve a ingresar.")).toHaveCount(0);
});

test("una tecla reinicia el contador: pasados N minutos desde el inicio no hay aviso", async ({ page }) => {
  await page.clock.fastForward(20 * MINUTO);
  await page.keyboard.press("a");

  await page.clock.fastForward(20 * MINUTO);

  await expect(aviso(page)).toHaveCount(0);
  await expect(page).toHaveURL(/\/inicio/);
});

test("con interacción constante el keep-alive no pasa de uno cada 5 minutos", async ({ page }) => {
  let keepAlives = 0;
  page.on("request", (req) => {
    if (req.url().endsWith("/api/auth/sesion") && req.method() === "POST") keepAlives += 1;
  });

  for (let minuto = 0; minuto < 12; minuto++) {
    await page.keyboard.press("a");
    await page.clock.fastForward(MINUTO);
  }
  await page.waitForTimeout(300);

  expect(keepAlives).toBeGreaterThanOrEqual(1);
  expect(keepAlives).toBeLessThanOrEqual(Math.ceil(12 / 5));
  await expect(aviso(page)).toHaveCount(0);
});

test("al volver de una suspensión de más de N minutos cierra en el acto, sin esperar los tics", async ({ page }) => {
  await page.clock.setSystemTime(new Date(Date.now() + (N + 1) * MINUTO));

  await page.evaluate(() => document.dispatchEvent(new Event("visibilitychange")));

  await expect(page).toHaveURL(/\/login\?motivo=inactividad/);
});
