import { expect, test, type Page } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";

// El mock (src/mocks/handlers.ts) guarda los pagos y el plan de cada cuenta en memoria, y las specs de la corrida lo comparten en paralelo. Por eso:
//   · Los tests que solo miran usan cuentas sembradas que nadie anota: «Cliente 03» (12 pagos) y «Cuenta Nueva» (sin pagos, plan Gratis).
//   · Los que registran un pago lo hacen en «Ferretería Luna» (2), con una referencia propia por test, y buscan su fila por esa referencia: nunca cuentan filas.
//   · Solo UN test mueve un vencimiento, el de «Panadería Sol» (1): lo extiende hacia adelante, así que sigue «Al día» para las demás specs (consumo, plan).
// Que el pago, la extensión y la bitácora sean una transacción lo prueba `PagosManualesE2ETest`.
const cuenta = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
const PANADERIA = 1;
const FERRETERIA = 2;
const CLIENTE_03 = 3;
const NUEVA = 12;

const MESES = ["Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Set", "Oct", "Nov", "Dic"];
const fechaLima = (ms: number) => new Date(ms).toLocaleDateString("en-CA", { timeZone: "America/Lima" });
const enDias = (dias: number) => fechaLima(Date.now() + dias * 86_400_000);
const HOY = enDias(0);
/** `2026-10-04` como «4 Oct 2026», igual que el portal. */
const enPalabras = (iso: string) => {
  const [a, m, d] = iso.split("-").map(Number);
  return `${d} ${MESES[m - 1]} ${a}`;
};

/** Una referencia propia de esta corrida: nunca choca con la de otra spec ni con una de una corrida anterior. */
const referencia = (base: string) => `${base}-${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;

test.beforeEach(async ({ page }) => {
  await entrarComoAdmin(page);
});

const fila = (page: Page, texto: string) => page.getByTestId("pago-fila").filter({ hasText: texto });

async function abrir(page: Page, n: number) {
  await page.goto(`/admin/cuentas/${cuenta(n)}`);
  await page.getByTestId("registrar-pago").click();
  await expect(page.getByTestId("registrar-pago-dialogo")).toBeVisible();
}

async function llenar(page: Page, v: { hasta: string; monto?: string; medio?: string; referencia?: string; nota?: string; fecha?: string; desde?: string }) {
  if (v.desde) await page.getByLabel("Periodo desde").fill(v.desde);
  await page.getByLabel("Periodo hasta (inclusive)").fill(v.hasta);
  await page.getByLabel("Monto (S/)").fill(v.monto ?? "29.00");
  await page.getByLabel("Medio de pago").selectOption(v.medio ?? "YAPE");
  if (v.fecha) await page.getByLabel("Fecha de pago").fill(v.fecha);
  if (v.referencia) await page.getByLabel("Referencia").fill(v.referencia);
  if (v.nota) await page.getByLabel("Nota").fill(v.nota);
}

// --- lo que se ve ---------------------------------------------------------------------------------------------------------------------------

test("la ficha de una cuenta muestra sus pagos con el periodo, el monto, el medio, la referencia y hasta dónde dejaron pagada la cuenta", async ({ page }) => {
  await page.goto(`/admin/cuentas/${cuenta(PANADERIA)}`);

  await expect(page.getByRole("heading", { name: "Pagos" })).toBeVisible();
  const yape = fila(page, "YP-4411");
  await expect(yape).toContainText("18 Set 2026");
  await expect(yape).toContainText("21 Set 2026 – 20 Oct 2026");
  await expect(yape).toContainText("S/ 29.00");
  await expect(yape).toContainText("Yape");
  await expect(yape).toContainText("Pagada hasta el 20 Oct 2026");
  await expect(fila(page, "BCP-90210")).toContainText("Transferencia");
});

test("el pago más reciente va primero", async ({ page }) => {
  await page.goto(`/admin/cuentas/${cuenta(PANADERIA)}`);

  const textos = await page.getByTestId("pago-fila").allTextContents();
  expect(textos.findIndex((t) => t.includes("YP-4411"))).toBeLessThan(textos.findIndex((t) => t.includes("BCP-90210")));
});

test("un pago sin referencia ni extensión muestra un guion y «Sin cambio»", async ({ page }) => {
  await page.goto(`/admin/cuentas/${cuenta(FERRETERIA)}`);

  const efectivo = page.getByTestId("pago-fila").filter({ hasText: "Efectivo" }).first();
  await expect(efectivo).toContainText("—");
  await expect(efectivo).toContainText("Sin cambio");
});

test("una cuenta con más de diez pagos muestra los diez más recientes y dice cuántos hay", async ({ page }) => {
  await page.goto(`/admin/cuentas/${cuenta(CLIENTE_03)}`);

  await expect(page.getByTestId("pago-fila")).toHaveCount(10);
  await expect(page.getByTestId("pagos-recortados")).toHaveText("Se muestran los 10 más recientes de 12.");
  await expect(fila(page, "T-12")).toBeVisible();
  await expect(fila(page, "T-3")).toHaveCount(1);
  await expect(fila(page, "T-2")).toHaveCount(0);
});

test("una cuenta sin pagos lo dice", async ({ page }) => {
  await page.goto(`/admin/cuentas/${cuenta(NUEVA)}`);

  await expect(page.getByText("Todavía no se registró ningún pago.")).toBeVisible();
  await expect(page.getByTestId("pagos-recortados")).toHaveCount(0);
});

// --- registrar ------------------------------------------------------------------------------------------------------------------------------

test("registrar un pago lo agrega al historial con lo que se escribió, cierra el modal y no mueve el vencimiento si no se pide", async ({ page }) => {
  const ref = referencia("BCP");
  await abrir(page, FERRETERIA);
  await llenar(page, { desde: "2026-09-01", hasta: "2026-09-30", monto: "45.50", medio: "TRANSFERENCIA", fecha: enDias(-1), referencia: ref, nota: "Pagó por el BCP" });

  await page.getByTestId("registrar-pago-confirmar").click();

  await expect(page.getByTestId("registrar-pago-dialogo")).toBeHidden();
  const nueva = fila(page, ref);
  await expect(nueva).toBeVisible();
  await expect(nueva).toContainText("1 Set 2026 – 30 Set 2026");
  await expect(nueva).toContainText("S/ 45.50");
  await expect(nueva).toContainText("Transferencia");
  await expect(nueva).toContainText("Pagó por el BCP");
  await expect(nueva).toContainText("Sin cambio");
  await expect(nueva).toHaveAttribute("data-extendio", "false");
  // «Ferretería Luna» sigue en gracia: no se tocó su vencimiento.
  await expect(page.getByTestId("plan-estado")).toHaveAttribute("data-estado", "EN_GRACIA");
});

test("registrar un pago con extensión mueve el vencimiento del plan y el historial dice hasta dónde", async ({ page }) => {
  const ref = referencia("YP");
  const ultimo = enDias(90);
  await abrir(page, PANADERIA);
  await llenar(page, { hasta: ultimo, referencia: ref });
  await expect(page.getByTestId("registrar-pago-extender")).toBeChecked();
  await expect(page.getByTestId("registrar-pago-extender-nota")).toContainText(`pasaría al ${enPalabras(ultimo)}`);

  await page.getByTestId("registrar-pago-confirmar").click();

  await expect(page.getByTestId("registrar-pago-dialogo")).toBeHidden();
  const nueva = fila(page, ref);
  await expect(nueva).toContainText(`Pagada hasta el ${enPalabras(ultimo)}`);
  await expect(nueva).toHaveAttribute("data-extendio", "true");
  await expect(page.getByTestId("plan-de-cuenta")).toContainText(`Pagado hasta el ${enPalabras(ultimo)}`);
  await expect(page.getByTestId("plan-estado")).toHaveAttribute("data-estado", "VIGENTE");
});

test("el mismo pago repetido (mismo medio y referencia) se rechaza con su motivo y no se anota dos veces", async ({ page }) => {
  const ref = referencia("DUP");
  await abrir(page, FERRETERIA);
  await llenar(page, { hasta: "2026-09-30", desde: "2026-09-01", referencia: ref });
  await page.getByTestId("registrar-pago-confirmar").click();
  await expect(fila(page, ref)).toHaveCount(1);

  await page.getByTestId("registrar-pago").click();
  await llenar(page, { desde: "2026-09-01", hasta: "2026-09-30", referencia: ref.toLowerCase() });
  await page.getByTestId("registrar-pago-confirmar").click();

  await expect(page.getByRole("alert")).toContainText("ya tiene un pago por Yape");
  await expect(page.getByTestId("registrar-pago-dialogo")).toBeVisible();
  await page.getByRole("button", { name: "Cancelar" }).click();
  await expect(fila(page, ref)).toHaveCount(1);
});

test("el mismo número de operación por otro medio sí se anota", async ({ page }) => {
  const ref = referencia("MED");
  await abrir(page, FERRETERIA);
  await llenar(page, { desde: "2026-09-01", hasta: "2026-09-30", medio: "YAPE", referencia: ref });
  await page.getByTestId("registrar-pago-confirmar").click();
  await expect(fila(page, ref)).toHaveCount(1);

  await page.getByTestId("registrar-pago").click();
  await llenar(page, { desde: "2026-09-01", hasta: "2026-09-30", medio: "PLIN", referencia: ref });
  await page.getByTestId("registrar-pago-confirmar").click();

  await expect(fila(page, ref)).toHaveCount(2);
});

// --- el formulario --------------------------------------------------------------------------------------------------------------------------

test("el formulario vacío muestra los errores y no manda nada; al corregir cada campo se quita su error", async ({ page }) => {
  await abrir(page, FERRETERIA);

  await page.getByTestId("registrar-pago-confirmar").click();

  await expect(page.getByText("Indica hasta cuándo cubre el pago.")).toBeVisible();
  await expect(page.getByText("Escribe el monto.")).toBeVisible();
  await expect(page.getByText("Elige el medio de pago.")).toBeVisible();
  await expect(page.getByTestId("registrar-pago-dialogo")).toBeVisible();
  await page.getByLabel("Monto (S/)").fill("29");
  await expect(page.getByText("Escribe el monto.")).toHaveCount(0);
});

test("un monto en cero, con tres decimales o con letras se rechaza antes de enviar", async ({ page }) => {
  await abrir(page, FERRETERIA);
  await llenar(page, { desde: "2026-09-01", hasta: "2026-09-30", monto: "0" });

  for (const malo of ["0", "29.999", "abc", "-5"]) {
    await page.getByLabel("Monto (S/)").fill(malo);
    await page.getByTestId("registrar-pago-confirmar").click();
    await expect(page.getByText("El monto debe ser mayor que cero, con hasta dos decimales.")).toBeVisible();
  }
  await page.getByLabel("Monto (S/)").fill("29,50");
  await expect(page.getByText("El monto debe ser mayor que cero, con hasta dos decimales.")).toHaveCount(0);
});

test("una fecha de pago futura y un periodo al revés se rechazan antes de enviar", async ({ page }) => {
  await abrir(page, FERRETERIA);
  await llenar(page, { desde: "2026-10-31", hasta: "2026-10-01", fecha: HOY });
  await page.getByTestId("registrar-pago-confirmar").click();
  await expect(page.getByText("El periodo no puede terminar antes de empezar.")).toBeVisible();

  await page.getByLabel("Periodo hasta (inclusive)").fill("2026-11-30");
  await page.getByLabel("Fecha de pago").evaluate((el: HTMLInputElement, f) => {
    // El campo ya trae `max=hoy`: se salta para probar la validación del formulario.
    el.removeAttribute("max");
    const asignar = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
    asignar.call(el, f);
    el.dispatchEvent(new Event("input", { bubbles: true }));
  }, enDias(3));
  await page.getByTestId("registrar-pago-confirmar").click();
  await expect(page.getByText("La fecha de pago no puede ser futura.")).toBeVisible();
});

test("cancelar cierra el modal y lo escrito no sobrevive", async ({ page }) => {
  await abrir(page, FERRETERIA);
  await llenar(page, { hasta: "2026-11-30", monto: "99", referencia: "NO-SE-GUARDA" });

  await page.getByRole("button", { name: "Cancelar" }).click();
  await expect(page.getByTestId("registrar-pago-dialogo")).toBeHidden();
  await page.getByTestId("registrar-pago").click();

  await expect(page.getByLabel("Monto (S/)")).toHaveValue("");
  await expect(page.getByLabel("Referencia")).toHaveValue("");
  await expect(fila(page, "NO-SE-GUARDA")).toHaveCount(0);
});

// --- la casilla de extender -----------------------------------------------------------------------------------------------------------------

test("en un plan que no vence la casilla no se puede marcar y lo explica", async ({ page }) => {
  await abrir(page, NUEVA);
  await page.getByLabel("Periodo hasta (inclusive)").fill(enDias(30));

  await expect(page.getByTestId("registrar-pago-extender")).toBeDisabled();
  await expect(page.getByTestId("registrar-pago-extender")).not.toBeChecked();
  await expect(page.getByTestId("registrar-pago-extender-nota")).toHaveText("El plan de esta cuenta no vence: no hay vencimiento que extender.");
});

test("un periodo que no adelanta el vencimiento deshabilita la casilla", async ({ page }) => {
  await abrir(page, FERRETERIA);
  await page.getByLabel("Periodo hasta (inclusive)").fill("2026-01-31");

  await expect(page.getByTestId("registrar-pago-extender")).toBeDisabled();
  await expect(page.getByTestId("registrar-pago-extender-nota")).toContainText("este periodo no adelanta el vencimiento");
});

test("la casilla viene marcada si el periodo adelanta el vencimiento y se puede desmarcar", async ({ page }) => {
  await abrir(page, FERRETERIA);
  await page.getByLabel("Periodo hasta (inclusive)").fill(enDias(60));

  await expect(page.getByTestId("registrar-pago-extender")).toBeEnabled();
  await expect(page.getByTestId("registrar-pago-extender")).toBeChecked();
  await page.getByTestId("registrar-pago-extender").uncheck();
  await expect(page.getByTestId("registrar-pago-extender")).not.toBeChecked();
});

// --- el BFF ---------------------------------------------------------------------------------------------------------------------------------

test("registrar un pago exige la sesión del administrador y valida el id y el cuerpo", async ({ page, request }) => {
  const cuerpo = { periodo_desde: "2026-09-01", periodo_hasta: "2026-09-30", monto: 29, medio: "YAPE", fecha_de_pago: HOY, extender_vencimiento: false };
  const ruta = `/api/admin/cuentas/${cuenta(NUEVA)}/pagos`;

  expect((await request.post(ruta, { data: cuerpo })).status()).toBe(401);
  expect((await page.request.post("/api/admin/cuentas/no-es-un-uuid/pagos", { data: cuerpo })).status()).toBe(400);
  expect((await page.request.post(ruta, { data: "[1]", headers: { "content-type": "application/json" } })).status()).toBe(400);
  expect((await page.request.post(`/api/admin/cuentas/${cuenta(999)}/pagos`, { data: cuerpo })).status()).toBe(404);
});

test("el backend rechaza lo inválido con su código y su estado, y no deja nada", async ({ page }) => {
  const base = { periodo_desde: "2026-09-01", periodo_hasta: "2026-09-30", monto: 29, medio: "YAPE", fecha_de_pago: HOY, extender_vencimiento: false };
  const ruta = `/api/admin/cuentas/${cuenta(NUEVA)}/pagos`;
  const casos: Array<[string, Record<string, unknown>, number]> = [
    ["MONTO_INVALIDO", { ...base, monto: 0 }, 422],
    ["PERIODO_INVALIDO", { ...base, periodo_hasta: "2026-08-01" }, 422],
    ["FECHA_DE_PAGO_FUTURA", { ...base, fecha_de_pago: enDias(5) }, 422],
    ["PLAN_SIN_VENCIMIENTO", { ...base, extender_vencimiento: true }, 409],
  ];

  for (const [codigo, cuerpo, estado] of casos) {
    const res = await page.request.post(ruta, { data: cuerpo });
    expect(res.status(), codigo).toBe(estado);
    expect((await res.json()).codigo, codigo).toBe(codigo);
  }
  await page.goto(`/admin/cuentas/${cuenta(NUEVA)}`);
  await expect(page.getByText("Todavía no se registró ningún pago.")).toBeVisible();
});

test("la respuesta de registrar no se guarda en caché", async ({ page }) => {
  const res = await page.request.post(`/api/admin/cuentas/${cuenta(NUEVA)}/pagos`, { data: { periodo_desde: "2026-09-01", periodo_hasta: "2026-09-30", monto: 0, medio: "YAPE", fecha_de_pago: HOY, extender_vencimiento: false } });

  expect(res.status()).toBe(422);
  expect(res.headers()["content-type"]).toContain("application/json");
});
