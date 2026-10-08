import { expect, test, type Page } from "@playwright/test";
import { entrarComoAdmin, menuAdmin } from "./admin-sesion";
import { esperarHidratacion } from "./hidratacion";

// El mock (src/mocks/handlers.ts) siembra los hallazgos por fecha y esta pantalla solo lee: ninguna spec de la corrida puede pisarse con otra. Si el rango incluye el
// 10 de septiembre de 2026 hay tres problemas (dos comprobantes de «Panadería Sol» y uno de «Ferretería Luna»); si incluye el 15 de agosto, un almacenamiento
// inaccesible; en cualquier otro rango, nada. Revisa 12 comprobantes por día. Que el barrido de verdad detecte un XML alterado lo prueban `VerificarIntegridadServiceTest`
// y los E2E del almacenamiento; acá se prueba la pantalla y su BFF.
const empresa = (n: number) => `00000000-0000-4000-9000-${String(n).padStart(12, "0")}`;

const MESES = ["Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Set", "Oct", "Nov", "Dic"];
const fechaLima = (ms: number) => new Date(ms).toLocaleDateString("en-CA", { timeZone: "America/Lima" });
const hoy = () => fechaLima(Date.now());
const hace = (dias: number) => fechaLima(Date.now() - dias * 86_400_000);
const enPalabras = (iso: string) => {
  const [a, m, d] = iso.split("-").map(Number);
  return `${d} ${MESES[m - 1]} ${a}`;
};

test.beforeEach(async ({ page }) => {
  await entrarComoAdmin(page);
});

/**
 * Abre la pantalla y espera a que React haya hidratado el formulario: un campo rellenado antes vuelve a su valor por defecto al hidratar (el primer test de una corrida,
 * con el servidor todavía compilando, lo hacía a veces).
 */
async function abrir(page: Page) {
  await page.goto("/admin/integridad");
  await esperarHidratacion(page, "#integridad-desde");
}

async function verificar(page: Page, desde: string, hasta: string) {
  await page.getByLabel("Desde").fill(desde);
  await page.getByLabel("Hasta (inclusive)").fill(hasta);
  await page.getByTestId("integridad-verificar").click();
}

// --- la pantalla ----------------------------------------------------------------------------------------------------------------------------

test("el menú lleva a Integridad y la miga dice «Operación / Integridad»", async ({ page }) => {
  await menuAdmin(page).getByRole("link", { name: "Integridad" }).click();

  await expect(page).toHaveURL(/\/admin\/integridad$/);
  const miga = page.getByRole("navigation", { name: "Ubicación" });
  await expect(miga).toContainText("Operación");
  await expect(miga.locator("[aria-current=page]")).toHaveText("Integridad");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Integridad del almacenamiento");
  await expect(page.getByText(/Solo lee: no repara nada/)).toBeVisible();
});

test("el rango arranca en los últimos siete días y no verifica nada hasta que se pide", async ({ page }) => {
  await abrir(page);

  await expect(page.getByLabel("Desde")).toHaveValue(hace(6));
  await expect(page.getByLabel("Hasta (inclusive)")).toHaveValue(hoy());
  await expect(page.getByTestId("integridad-resultado")).toHaveCount(0);
});

// --- el resultado ---------------------------------------------------------------------------------------------------------------------------

test("un rango sin problemas dice cuántos comprobantes se verificaron y que todo está en orden", async ({ page }) => {
  await abrir(page);

  await page.getByTestId("integridad-verificar").click();

  await expect(page.getByTestId("integridad-verificados")).toHaveText(`Se verificaron 84 comprobantes entre el ${enPalabras(hace(6))} y el ${enPalabras(hoy())}.`);
  await expect(page.getByTestId("integridad-hallazgo")).toHaveText("Todo en orden: no se encontró ningún problema.");
  await expect(page.getByTestId("integridad-resultado")).toHaveAttribute("data-limpio", "true");
  await expect(page.getByRole("table")).toHaveCount(0);
});

test("un rango con problemas los lista con su tipo, el comprobante y el detalle", async ({ page }) => {
  await abrir(page);

  await verificar(page, "2026-09-01", "2026-09-30");

  await expect(page.getByTestId("integridad-verificados")).toHaveText("Se verificaron 360 comprobantes entre el 1 Set 2026 y el 30 Set 2026.");
  await expect(page.getByTestId("integridad-hallazgo")).toHaveText("Se encontraron 3 problemas en 3 comprobantes.");
  await expect(page.getByTestId("integridad-resultado")).toHaveAttribute("data-limpio", "false");
  const filas = page.getByTestId("integridad-problema");
  await expect(filas).toHaveCount(3);
  await expect(filas.nth(0)).toHaveAttribute("data-tipo", "XML_CORRUPTO");
  await expect(filas.nth(0)).toContainText("XML corrupto");
  await expect(filas.nth(0)).toContainText("20100047226-01-F001-14");
  await expect(filas.nth(0)).toContainText("el DigestValue registrado");
  await expect(filas.nth(1)).toContainText("CDR faltante");
  await expect(filas.nth(2)).toContainText("XML faltante");
});

test("la leyenda explica solo los tipos que aparecen", async ({ page }) => {
  await abrir(page);

  await verificar(page, "2026-09-01", "2026-09-30");

  const leyenda = page.getByTestId("integridad-leyenda");
  await expect(leyenda.getByRole("term")).toHaveCount(3);
  await expect(leyenda).toContainText("El comprobante está firmado pero su XML ya no está en el almacenamiento.");
  await expect(leyenda).not.toContainText("fallo de conexión");
});

test("cada problema enlaza a su empresa", async ({ page }) => {
  await abrir(page);
  await verificar(page, "2026-09-01", "2026-09-30");

  await expect(page.getByTestId("integridad-problema").nth(0).getByRole("link", { name: "Ver empresa" })).toHaveAttribute("href", `/admin/empresas/${empresa(1)}`);
  await expect(page.getByTestId("integridad-problema").nth(2).getByRole("link", { name: "Ver empresa" })).toHaveAttribute("href", `/admin/empresas/${empresa(2)}`);

  await page.getByTestId("integridad-problema").nth(0).getByRole("link", { name: "Ver empresa" }).click();

  await expect(page).toHaveURL(new RegExp(`/admin/empresas/${empresa(1)}$`));
  await expect(page.getByRole("heading", { name: /PANADERIA SOL SAC/ })).toBeVisible();
});

test("un almacenamiento inaccesible se distingue de un objeto perdido y pide repetir", async ({ page }) => {
  await abrir(page);

  await verificar(page, "2026-08-10", "2026-08-20");

  await expect(page.getByTestId("integridad-hallazgo")).toHaveText("Se encontró 1 problema en 1 comprobante.");
  const fila = page.getByTestId("integridad-problema");
  await expect(fila).toHaveAttribute("data-tipo", "STORAGE_INACCESIBLE");
  await expect(fila).toContainText("Almacenamiento inaccesible");
  await expect(page.getByTestId("integridad-leyenda")).toContainText("Conviene repetir la verificación.");
});

test("otro rango reemplaza el resultado anterior", async ({ page }) => {
  await abrir(page);
  await verificar(page, "2026-09-01", "2026-09-30");
  await expect(page.getByTestId("integridad-problema")).toHaveCount(3);

  await verificar(page, "2026-10-01", "2026-10-07");

  await expect(page.getByTestId("integridad-hallazgo")).toHaveText("Todo en orden: no se encontró ningún problema.");
  await expect(page.getByTestId("integridad-problema")).toHaveCount(0);
  await expect(page.getByTestId("integridad-verificados")).toHaveText(/Se verificaron 84 comprobantes entre el 1 Oct 2026 y el 7 Oct 2026\./);
});

// --- el formulario --------------------------------------------------------------------------------------------------------------------------

test("un rango al revés se rechaza con su error y no verifica nada; al corregirlo el error se va", async ({ page }) => {
  await abrir(page);

  await verificar(page, "2026-09-30", "2026-09-01");

  await expect(page.getByText("El rango no puede terminar antes de empezar.")).toBeVisible();
  await expect(page.getByTestId("integridad-resultado")).toHaveCount(0);
  await page.getByLabel("Hasta (inclusive)").fill("2026-10-15");
  await expect(page.getByText("El rango no puede terminar antes de empezar.")).toHaveCount(0);
});

test("un rango de más de 92 días y las fechas vacías se rechazan antes de enviar", async ({ page }) => {
  await abrir(page);

  await verificar(page, "2026-01-01", "2026-12-31");
  await expect(page.getByText("El rango no puede pasar de 92 días.")).toBeVisible();

  await page.getByLabel("Desde").fill("");
  await page.getByLabel("Hasta (inclusive)").fill("");
  await page.getByTestId("integridad-verificar").click();
  await expect(page.getByText("Indica desde cuándo verificar.")).toBeVisible();
  await expect(page.getByText("Indica hasta cuándo verificar.")).toBeVisible();
  await expect(page.getByTestId("integridad-resultado")).toHaveCount(0);
});

test("justo 92 días sí se verifica", async ({ page }) => {
  await abrir(page);

  await verificar(page, "2026-08-01", "2026-10-31");

  await expect(page.getByTestId("integridad-verificados")).toContainText("Se verificaron 500 comprobantes");
});

// --- el BFF ---------------------------------------------------------------------------------------------------------------------------------

test("el BFF exige la sesión del administrador y valida el cuerpo antes de llamar al backend", async ({ page, request }) => {
  const ok = { desde: "2026-09-01", hasta: "2026-09-30" };

  expect((await request.post("/api/admin/integridad", { data: ok })).status()).toBe(401);
  expect((await page.request.post("/api/admin/integridad", { data: "[1]", headers: { "content-type": "application/json" } })).status()).toBe(400);
  for (const malo of [{}, { desde: "2026-09-01" }, { desde: "2026-02-30", hasta: "2026-03-01" }, { desde: "2026-09-30", hasta: "2026-09-01" }, { desde: "2026-01-01", hasta: "2026-12-31" }, { desde: "2026-09-01&x=1", hasta: "2026-09-30" }]) {
    const res = await page.request.post("/api/admin/integridad", { data: malo });
    expect(res.status(), JSON.stringify(malo)).toBe(400);
    expect((await res.json()).codigo, JSON.stringify(malo)).toBe("PARAMETRO_INVALIDO");
  }
});

test("el BFF devuelve el informe tal cual lo manda el backend y no lo guarda en caché", async ({ page }) => {
  const res = await page.request.post("/api/admin/integridad", { data: { desde: "2026-09-01", hasta: "2026-09-30" } });

  expect(res.status()).toBe(200);
  expect(res.headers()["cache-control"]).toContain("no-store");
  const informe = (await res.json()).datos;
  expect(informe).toMatchObject({ desde: "2026-09-01", hasta: "2026-09-30", verificados: 360 });
  expect(informe.problemas).toHaveLength(3);
  expect(informe.problemas[0]).toMatchObject({ tipo: "XML_CORRUPTO", tenant_id: empresa(1), nombre_archivo: "20100047226-01-F001-14" });
});

test.describe("sin sesión", () => {
  test("la página de integridad lleva al login del backoffice", async ({ browser }) => {
    const contexto = await browser.newContext();
    const pagina = await contexto.newPage();

    await pagina.goto("/admin/integridad");

    await expect(pagina).toHaveURL(/\/admin\/login/);
    await contexto.close();
  });
});
