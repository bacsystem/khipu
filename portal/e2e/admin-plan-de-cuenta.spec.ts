import { expect, test, type Page } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";

// El mock (src/mocks/handlers.ts) guarda el plan de cada cuenta en memoria y las specs de la corrida lo comparten en paralelo: los tests que solo miran usan las
// cuentas sembradas con un plan propio (1 a 4) y los que cambian un plan usan **cada uno su propia cuenta** (5 a 11), que ninguna otra spec mira por su plan.
// Que el cambio quede en la base y en la bitácora lo prueba `CambioDePlanE2ETest`.
const cuenta = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
const plan = (n: number) => `00000000-0000-4000-a000-${String(n).padStart(12, "0")}`;
const GRATIS = plan(1);
const EMPRENDE = plan(2);
const NEGOCIO = plan(3);

/** Una fecha de aquí a `dias` días, `YYYY-MM-DD`: el vencimiento tiene que ser futuro. */
const enDias = (dias: number) => new Date(Date.now() + dias * 86_400_000).toISOString().slice(0, 10);

test.beforeEach(async ({ page }) => {
  await entrarComoAdmin(page);
});

const seccion = (page: Page) => page.getByTestId("plan-de-cuenta");
const elegir = (page: Page, id: string) => page.getByLabel("Plan nuevo").selectOption(id);

async function abrirCambio(page: Page, n: number) {
  await page.goto(`/admin/cuentas/${cuenta(n)}`);
  await page.getByTestId("cambiar-plan").click();
}

async function cambiar(page: Page, n: number, planId: string, hasta?: string, gracia?: string) {
  await abrirCambio(page, n);
  await elegir(page, planId);
  await expect(page.getByTestId("cambiar-plan-confirmar")).toBeEnabled();
  if (hasta) await page.getByLabel("Pagado hasta (inclusive)").fill(hasta);
  if (gracia) await page.getByLabel("Días de gracia").fill(gracia);
  await page.getByTestId("cambiar-plan-confirmar").click();
  await expect(page.getByTestId("cambiar-plan-dialogo")).toHaveCount(0);
}

// --- lo que se ve ---------------------------------------------------------------------------------------------------------------------------

test("la ficha de una cuenta muestra su plan, su estado de pago, hasta qué día está pagada y la gracia", async ({ page }) => {
  await page.goto(`/admin/cuentas/${cuenta(1)}`);

  const s = seccion(page);
  await expect(s).toContainText("Emprende");
  await expect(s).toContainText("S/ 29.00 al mes");
  await expect(page.getByTestId("plan-estado")).toHaveAttribute("data-estado", "VIGENTE");
  await expect(page.getByTestId("plan-estado")).toHaveText("Al día");
  await expect(s).toContainText("Pagado hasta el");
  await expect(s).toContainText("5 días de gracia");
  await expect(s).toContainText("Límites vigentes: 300 documentos al mes");
});

test("una cuenta vencida pero dentro de su gracia se ve «En gracia» y una que ya se pasó, «Vencido»", async ({ page }) => {
  await page.goto(`/admin/cuentas/${cuenta(2)}`);
  await expect(page.getByTestId("plan-estado")).toHaveAttribute("data-estado", "EN_GRACIA");
  await expect(page.getByTestId("plan-estado")).toHaveText("En gracia");

  await page.goto(`/admin/cuentas/${cuenta(3)}`);
  await expect(page.getByTestId("plan-estado")).toHaveAttribute("data-estado", "VENCIDA");
  await expect(page.getByTestId("plan-estado")).toHaveText("Vencido");
});

test("una cuenta en el plan gratis no tiene vencimiento ni gracia", async ({ page }) => {
  await page.goto(`/admin/cuentas/${cuenta(12)}`);

  const s = seccion(page);
  await expect(s).toContainText("Gratis");
  await expect(s).toContainText("Sin vencimiento");
  await expect(s).not.toContainText("Pagado hasta");
  await expect(s).not.toContainText("gracia");
});

test("una bajada que espera el ciclo siguiente se ve aparte y la cuenta sigue con el plan de hoy", async ({ page }) => {
  await page.goto(`/admin/cuentas/${cuenta(4)}`);

  await expect(seccion(page)).toContainText("Negocio");
  const aviso = page.getByTestId("plan-programado");
  await expect(aviso).toContainText("Pasa a Emprende el 1");
  await expect(aviso).toContainText("al inicio del ciclo siguiente. Hasta entonces sigue con Negocio.");
});

// --- previsualizar (sin cambiar nada) -------------------------------------------------------------------------------------------------------

test("al elegir un plan más caro dice que es una subida que entra ahora y cuánto consumió la cuenta este mes", async ({ page }) => {
  await abrirCambio(page, 1);

  await elegir(page, NEGOCIO);

  const previa = page.getByTestId("cambiar-plan-previa");
  await expect(previa).toHaveAttribute("data-direccion", "SUBIDA");
  await expect(previa).toContainText("Subida de plan");
  await expect(previa).toContainText("Entra ahora.");
  await expect(previa).toContainText("la cuenta consumió 312 documentos (solo cuentan los comprobantes aceptados por SUNAT)");
  await expect(previa).toContainText("El plan Negocio permite 1,500 al mes.");
  await expect(page.getByTestId("cambiar-plan-supera")).toHaveCount(0);
});

test("al elegir un plan más barato dice que es una bajada, cuándo entra y que hasta entonces sigue con el plan de hoy", async ({ page }) => {
  await abrirCambio(page, 1);

  await elegir(page, GRATIS);

  const previa = page.getByTestId("cambiar-plan-previa");
  await expect(previa).toHaveAttribute("data-direccion", "BAJADA");
  await expect(previa).toHaveAttribute("data-efecto", "CICLO_SIGUIENTE");
  await expect(previa).toContainText("Bajada de plan");
  await expect(previa).toContainText("al inicio del ciclo siguiente. Hasta entonces la cuenta sigue con Emprende.");
  await expect(previa).toContainText("El plan Gratis permite 30 al mes.");
  // 312 documentos superan los 30 del gratis, pero la bajada no toca este mes.
  await expect(page.getByTestId("cambiar-plan-supera")).toContainText("Este mes no cambia nada");
});

test("elegir el mismo plan es una renovación", async ({ page }) => {
  await abrirCambio(page, 1);

  await elegir(page, EMPRENDE);

  await expect(page.getByTestId("cambiar-plan-previa")).toContainText("Renovación del mismo plan");
});

test("con una bajada esperando, la previsualización dice que renovar la cancela y que otra bajada la reemplaza", async ({ page }) => {
  await abrirCambio(page, 4);

  await elegir(page, NEGOCIO);
  await expect(page.getByTestId("cambiar-plan-descarta")).toContainText("Cancela el paso a Emprende programado para el");

  await elegir(page, GRATIS);
  await expect(page.getByTestId("cambiar-plan-descarta")).toContainText("Reemplaza el paso a Emprende programado para el");
});

test("si el consumo de este mes ya supera el tope del plan nuevo y el cambio es inmediato lo advierte", async ({ page }) => {
  await abrirCambio(page, 7);

  await elegir(page, EMPRENDE);

  await expect(page.getByTestId("cambiar-plan-previa")).toContainText("la cuenta consumió 400 documentos");
  await expect(page.getByTestId("cambiar-plan-supera")).toContainText("Ya supera ese tope: con el cambio inmediato, la cuenta quedaría por encima de su límite de este mes.");
});

test("no se puede confirmar sin ver antes lo que pasaría", async ({ page }) => {
  await abrirCambio(page, 1);

  await expect(page.getByTestId("cambiar-plan-confirmar")).toBeDisabled();
  await elegir(page, NEGOCIO);
  await expect(page.getByTestId("cambiar-plan-confirmar")).toBeEnabled();
  await elegir(page, "");
  await expect(page.getByTestId("cambiar-plan-confirmar")).toBeDisabled();
});

// --- cambiar --------------------------------------------------------------------------------------------------------------------------------

test("subir de plan entra ahora: la ficha muestra el plan nuevo, al día, con su vencimiento y su gracia", async ({ page }) => {
  await cambiar(page, 5, EMPRENDE, enDias(60), "5");

  const s = seccion(page);
  await expect(s).toContainText("Emprende");
  await expect(page.getByTestId("plan-estado")).toHaveAttribute("data-estado", "VIGENTE");
  await expect(s).toContainText("Pagado hasta el");
  await expect(s).toContainText("5 días de gracia");
  await expect(page.getByTestId("plan-programado")).toHaveCount(0);
});

test("bajar de plan lo deja programado y la cuenta sigue con el de hoy; renovar el actual cancela la bajada", async ({ page }) => {
  await cambiar(page, 6, NEGOCIO, enDias(60), "0");
  await expect(seccion(page)).toContainText("Negocio");

  await cambiar(page, 6, EMPRENDE, enDias(90), "3");

  await expect(seccion(page)).toContainText("Negocio");
  await expect(page.getByTestId("plan-programado")).toContainText("Pasa a Emprende el 1");

  await cambiar(page, 6, NEGOCIO, enDias(120), "0");

  await expect(page.getByTestId("plan-programado")).toHaveCount(0);
  await expect(seccion(page)).toContainText("Negocio");
});

test("pasar a un plan gratis no pide fecha", async ({ page }) => {
  await cambiar(page, 9, NEGOCIO, enDias(60), "0");

  await cambiar(page, 9, GRATIS);

  await expect(page.getByTestId("plan-programado")).toContainText("Pasa a Gratis el 1");
});

test("un plan de pago sin fecha, una fecha pasada o una gracia fuera de rango se rechazan antes de enviar", async ({ page }) => {
  await abrirCambio(page, 8);
  await elegir(page, EMPRENDE);
  await expect(page.getByTestId("cambiar-plan-confirmar")).toBeEnabled();

  await page.getByTestId("cambiar-plan-confirmar").click();
  await expect(page.getByTestId("cambiar-plan-dialogo")).toContainText("Un plan de pago necesita la fecha hasta la que está pagado.");

  await page.getByLabel("Pagado hasta (inclusive)").fill("2020-01-01");
  await page.getByLabel("Días de gracia").fill("91");
  await page.getByTestId("cambiar-plan-confirmar").click();
  await expect(page.getByTestId("cambiar-plan-dialogo")).toContainText("La fecha tiene que ser de hoy en adelante.");
  await expect(page.getByTestId("cambiar-plan-dialogo")).toContainText("Los días de gracia van de 0 a 90.");
  await expect(seccion(page)).toContainText("Gratis");
});

test("cerrar el modal sin confirmar no cambia nada", async ({ page }) => {
  await abrirCambio(page, 10);
  await elegir(page, NEGOCIO);

  await page.getByRole("button", { name: "Cancelar" }).click();

  await expect(page.getByTestId("cambiar-plan-dialogo")).toHaveCount(0);
  await expect(seccion(page)).toContainText("Gratis");
});

// --- el BFF ---------------------------------------------------------------------------------------------------------------------------------

test("el BFF rechaza la falta de sesión, los ids inválidos y lo que no existe", async ({ page, request }) => {
  const ruta = `/api/admin/cuentas/${cuenta(11)}/plan`;

  // Sin sesión: el `request` de Playwright no comparte las cookies de `page`.
  expect((await request.post(ruta, { data: { plan_id: GRATIS } })).status()).toBe(401);
  expect((await request.get(`${ruta}/previsualizacion?plan_id=${GRATIS}`)).status()).toBe(401);

  // Con sesión: el id no llega sin validar al backend, y lo que no existe es 404.
  const conSesion = page.request;
  expect((await conSesion.post("/api/admin/cuentas/no-es-un-uuid/plan", { data: { plan_id: GRATIS } })).status()).toBe(400);
  expect((await conSesion.get(`/api/admin/cuentas/no-es-un-uuid/plan/previsualizacion?plan_id=${GRATIS}`)).status()).toBe(400);
  expect((await conSesion.get(`${ruta}/previsualizacion?plan_id=no-es-un-uuid`)).status()).toBe(400);
  expect((await conSesion.get(`${ruta}/previsualizacion`)).status()).toBe(400);
  expect((await conSesion.post(ruta, { data: "no es json", headers: { "content-type": "application/json" } })).status()).toBe(400);
  expect((await conSesion.post(`/api/admin/cuentas/${cuenta(999)}/plan`, { data: { plan_id: GRATIS } })).status()).toBe(404);
  expect((await conSesion.post(ruta, { data: { plan_id: plan(999) } })).status()).toBe(404);
  expect((await conSesion.post(ruta, { data: {} })).status()).toBe(422);
});
