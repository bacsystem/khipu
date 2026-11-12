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

const esKeepAlive = (url: string, metodo: string) => url.endsWith("/api/auth/sesion") && metodo === "POST";

/**
 * `AbortSignal.timeout` del keep-alive sigue al reloj falso de `page.clock`: si `fastForward` salta 60 s con un keep-alive en vuelo, la señal de 30 s vence y el navegador aborta la
 * petición (`TimeoutError`, que el vigilante cuenta como un fallo y reintenta a los 30 s). Con el reloj real no pasa; es una particularidad de saltar el tiempo. Por eso, antes de cada
 * salto se espera a que el keep-alive que haya salido termine. Sale de dos maneras: dentro del manejador de la tecla (síncrono, así que al volver `keyboard.press` ya cuenta) o del tic de 10 s que el
 * propio salto dispara (en vuelo al volver `fastForward`), por eso se espera después de la tecla y después del salto.
 * `contarKeepAlivesEnVuelo` instala el contador (una vez, con la página cargada) y `esperarQueNoHayaKeepAliveEnVuelo` espera a que llegue a cero.
 */
async function contarKeepAlivesEnVuelo(page: Page) {
  await page.evaluate(() => {
    const w = window as unknown as { __keepAlivesEnVuelo?: number };
    w.__keepAlivesEnVuelo = 0;
    const original = window.fetch.bind(window);
    window.fetch = (entrada, init) => {
      const url = typeof entrada === "string" ? entrada : entrada instanceof URL ? entrada.href : entrada.url;
      if (!url.endsWith("/api/auth/sesion")) return original(entrada, init);
      w.__keepAlivesEnVuelo = (w.__keepAlivesEnVuelo ?? 0) + 1;
      return original(entrada, init).finally(() => void (w.__keepAlivesEnVuelo = (w.__keepAlivesEnVuelo ?? 1) - 1));
    };
  });
}

async function esperarQueNoHayaKeepAliveEnVuelo(page: Page) {
  await expect.poll(() => page.evaluate(() => (window as unknown as { __keepAlivesEnVuelo?: number }).__keepAlivesEnVuelo ?? 0)).toBe(0);
}

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
  const renovacion = page.waitForResponse((res) => esKeepAlive(res.url(), res.request().method()));

  await aviso(page).getByRole("button", { name: "Seguir conectado" }).click();

  // La respuesta, no solo la petición: si el reloj salta con ella en vuelo, el navegador la aborta (ver `esperarQueNoHayaKeepAliveEnVuelo`).
  expect((await renovacion).status()).toBe(204);
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
  const peticiones: string[] = [];
  const respuestas: number[] = [];
  page.on("request", (req) => esKeepAlive(req.url(), req.method()) && peticiones.push(req.url()));
  page.on("response", (res) => esKeepAlive(res.url(), res.request().method()) && respuestas.push(res.status()));
  await contarKeepAlivesEnVuelo(page);

  for (let minuto = 0; minuto < 12; minuto++) {
    await page.keyboard.press("a");
    await esperarQueNoHayaKeepAliveEnVuelo(page);
    await page.clock.fastForward(MINUTO);
    await esperarQueNoHayaKeepAliveEnVuelo(page);
  }

  expect(peticiones.length).toBeGreaterThanOrEqual(1);
  expect(peticiones.length).toBeLessThanOrEqual(Math.ceil(12 / 5));
  // Con cada keep-alive terminado antes de saltar el reloj, ninguno se aborta ni se reintenta a los 30 s: cada petición tiene su respuesta 204 y no hay más peticiones que respuestas.
  // (Chrome puede reportar `ERR_ABORTED` DESPUÉS de la respuesta, cuando el siguiente salto vence la señal de 30 s de una petición que ya terminó: es inofensivo, y por eso no se afirma nada sobre `requestfailed`.)
  expect(respuestas).toEqual(peticiones.map(() => 204));
  await expect(aviso(page)).toHaveCount(0);
});

test("al volver de una suspensión de más de N minutos cierra en el acto, sin esperar los tics", async ({ page }) => {
  await page.clock.setSystemTime(new Date(Date.now() + (N + 1) * MINUTO));

  await page.evaluate(() => document.dispatchEvent(new Event("visibilitychange")));

  await expect(page).toHaveURL(/\/login\?motivo=inactividad/);
});
