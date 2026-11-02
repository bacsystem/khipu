import { expect, test, type Page } from "@playwright/test";
import { entrarComoAdmin, menuAdmin } from "./admin-sesion";
import { esperarHidratacion } from "./hidratacion";

// El mock (src/mocks/handlers.ts) siembra los avisos. Las specs de la corrida comparten esa memoria en paralelo, así que cada spec que avisa usa SU empresa y solo afirma sobre ella;
// las que leen usan empresas que nadie muta. Lo sembrado (certificados: del que vence antes al que vence después):
//   Ferretería Luna (2)  vencido hace 5 días  · se le avisa
//   Integrador (101)     vencido hace 2 días  · sin cuenta, no hay a quién avisarle
//   Cliente 10           vencido hace 1 día   · el correo falla (502)
//   Cliente 04           por vencer en 3      · solo lectura: ya se le avisó hace 2 días
//   Panadería Sol (1)    por vencer en 10     · se le avisa
//   Cliente 05           por vencer en 20     · otro administrador se adelantó (409)
//   Cliente 07           por vencer en 29     · solo lectura
// Credenciales SOL: Cliente 08 (5 atascados, se le avisa) · Integrador (3, sin cuenta) · Cliente 04 (2) · Cliente 07 (1, ya avisada ayer: solo lectura).
// Que el backend deduzca, reserve y mande el correo de verdad lo prueban `AvisosAClientesE2ETest` y los tests de cada capa; acá se prueba la pantalla y su BFF.

test.beforeEach(async ({ page }) => {
  await entrarComoAdmin(page);
});

/** Abre la pantalla y espera a que React haya hidratado los enlaces de las vistas: un clic antes de hidratar navega entero en vez de por el router. */
async function abrir(page: Page, ruta = "/admin/avisos") {
  await page.goto(ruta);
  await esperarHidratacion(page, 'nav[aria-label="Qué avisar"] a');
}

const fila = (page: Page, ruc: string) => page.locator(`[data-testid="avisos-fila"][data-empresa="${ruc}"]`);
const rucs = (page: Page) => page.locator('[data-testid="avisos-fila"]').evaluateAll((filas) => filas.map((f) => f.getAttribute("data-empresa")));

// --- la pantalla ----------------------------------------------------------------------------------------------------------------------------

test("el menú lleva a Avisos y la miga dice «Operación / Avisos»", async ({ page }) => {
  await menuAdmin(page).getByRole("link", { name: "Avisos" }).click();

  await expect(page).toHaveURL(/\/admin\/avisos$/);
  const miga = page.getByRole("navigation", { name: "Ubicación" });
  await expect(miga).toContainText("Operación");
  await expect(miga.locator("[aria-current=page]")).toHaveText("Avisos");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Avisos a clientes");
});

test("los certificados salen del que vence antes al que vence después, vencidos primero", async ({ page }) => {
  await abrir(page);

  await expect.poll(() => rucs(page)).toEqual(["20100055121", "20100066611", "20100001000", "20100000400", "20100047226", "20100000500", "20100000700"]);
  await expect(fila(page, "20100055121")).toContainText("Vencido");
  await expect(fila(page, "20100055121")).toContainText("Venció el");
  await expect(fila(page, "20100055121")).toContainText("(hace 5 días)");
  await expect(fila(page, "20100000700")).toContainText("Por vencer");
  await expect(fila(page, "20100000700")).toContainText("(en 29 días)");
});

test("cada fila dice la empresa, su cuenta y el último aviso", async ({ page }) => {
  await abrir(page);

  const f = fila(page, "20100000400");
  await expect(f).toContainText("CLIENTE 04 SAC");
  await expect(f).toContainText("Cliente 04 · cliente04@negocio.pe");
  await expect(f.getByTestId("avisos-ultimo")).toContainText("Avisado el");
  await expect(f.getByTestId("avisos-ultimo")).toContainText("a cliente04@negocio.pe");
  await expect(fila(page, "20100000700").getByTestId("avisos-ultimo")).toHaveText("Sin avisos");
});

test("cambiar a credenciales SOL muestra los envíos atascados y lo que dijo SUNAT, de la empresa con más a la que menos", async ({ page }) => {
  await abrir(page);

  await page.getByRole("navigation", { name: "Qué avisar" }).getByRole("link", { name: "Credenciales SOL" }).click();

  await expect(page).toHaveURL(/vista=CREDENCIALES_SOL/);
  await expect.poll(() => rucs(page)).toEqual(["20100000800", "20100066611", "20100000400", "20100000700"]);
  const f = fila(page, "20100000800");
  await expect(f).toContainText("5 comprobantes atascados");
  await expect(f.getByTestId("avisos-sunat")).toContainText("SUNAT dijo: 0000 - SUNAT respondió HTTP 401");
  await expect(fila(page, "20100000700")).toContainText("1 comprobante atascado");
  await expect(page.getByRole("navigation", { name: "Qué avisar" }).locator("[aria-current=page]")).toHaveText("Credenciales SOL");
});

test("una página pasada de la última lleva a la primera, con la URL limpia", async ({ page }) => {
  await page.goto("/admin/avisos?pagina=9");

  await expect(page).toHaveURL(/\/admin\/avisos$/);
  await expect(page.getByTestId("avisos-fila")).toHaveCount(7);
});

// --- sin a quién avisar y avisos ya mandados ------------------------------------------------------------------------------------------------

test("una empresa sin cuenta dice que no hay a quién avisarle y no ofrece el botón", async ({ page }) => {
  await abrir(page);

  const f = fila(page, "20100066611");
  await expect(f.getByTestId("avisos-sin-cuenta")).toHaveText("Sin cuenta: no hay a quién avisarle");
  await expect(f.getByTestId("avisos-avisar")).toHaveCount(0);
});

test("a quien ya se avisó esta semana no se le ofrece el botón y se dice desde cuándo se puede repetir", async ({ page }) => {
  await abrir(page);

  const f = fila(page, "20100000400");
  await expect(f.getByTestId("avisos-avisar")).toHaveCount(0);
  await expect(f.getByTestId("avisos-espera")).toContainText("Se puede repetir desde el");

  await page.getByRole("navigation", { name: "Qué avisar" }).getByRole("link", { name: "Credenciales SOL" }).click();
  await expect(fila(page, "20100000700").getByTestId("avisos-espera")).toContainText("Se puede repetir desde el");
  await expect(fila(page, "20100000700").getByTestId("avisos-avisar")).toHaveCount(0);
});

// --- avisar ---------------------------------------------------------------------------------------------------------------------------------

test("avisar de un certificado por vencer pide confirmación, lo manda y deja la fila esperando una semana", async ({ page }) => {
  await abrir(page);
  const f = fila(page, "20100047226");

  await f.getByTestId("avisos-avisar").click();
  const dialogo = page.getByTestId("avisos-avisar-dialogo");
  await expect(dialogo).toContainText("Le manda a PANADERIA SOL SAC un correo de aviso");
  await expect(dialogo).toContainText("Le manda un correo a panaderia@sol.pe: que su certificado digital está por vencer.");
  await expect(dialogo).toContainText("no se repite en una semana");
  await page.getByTestId("avisos-avisar-confirmar").click();

  await expect(page.getByTestId("avisos-resultado")).toHaveText("Se le avisó a panaderia@sol.pe (PANADERIA SOL SAC).");
  await expect(dialogo).toHaveCount(0);
  await expect(f.getByTestId("avisos-avisar")).toHaveCount(0);
  await expect(f.getByTestId("avisos-espera")).toContainText("Se puede repetir desde el");
  await expect(f.getByTestId("avisos-ultimo")).toContainText("a panaderia@sol.pe");
});

test("avisar de un certificado vencido le dice al cliente que ya no puede emitir", async ({ page }) => {
  await abrir(page);

  await fila(page, "20100055121").getByTestId("avisos-avisar").click();
  await expect(page.getByTestId("avisos-avisar-dialogo")).toContainText("que su certificado digital venció y no puede emitir");
  await page.getByTestId("avisos-avisar-confirmar").click();

  await expect(page.getByTestId("avisos-resultado")).toHaveText("Se le avisó a ferreteria@luna.pe (FERRETERIA LUNA SAC).");
  await expect(fila(page, "20100055121").getByTestId("avisos-avisar")).toHaveCount(0);
});

test("avisar de las credenciales SOL le dice al cliente que SUNAT no las acepta", async ({ page }) => {
  await abrir(page, "/admin/avisos?vista=CREDENCIALES_SOL");

  await fila(page, "20100000800").getByTestId("avisos-avisar").click();
  await expect(page.getByTestId("avisos-avisar-dialogo")).toContainText("que SUNAT no acepta sus credenciales SOL");
  await page.getByTestId("avisos-avisar-confirmar").click();

  await expect(page.getByTestId("avisos-resultado")).toHaveText("Se le avisó a cliente08@negocio.pe (CLIENTE 08 SAC).");
  await expect(fila(page, "20100000800").getByTestId("avisos-espera")).toContainText("Se puede repetir desde el");
});

test("cancelar el aviso no manda nada", async ({ page }) => {
  await abrir(page);

  await fila(page, "20100000700").getByTestId("avisos-avisar").click();
  await page.getByRole("button", { name: "Cancelar" }).click();

  await expect(page.getByTestId("avisos-avisar-dialogo")).toHaveCount(0);
  await expect(page.getByTestId("avisos-resultado")).toHaveCount(0);
  await expect(fila(page, "20100000700").getByTestId("avisos-avisar")).toBeVisible();
});

test("si otro administrador se adelantó, lo dice arriba y la fila pasa a esperar", async ({ page }) => {
  await abrir(page);

  await fila(page, "20100000500").getByTestId("avisos-avisar").click();
  await page.getByTestId("avisos-avisar-confirmar").click();

  // La recarga cambia la fila y se lleva el diálogo: el aviso queda en el banner de resultados.
  await expect(page.getByTestId("avisos-resultado")).toContainText("CLIENTE 05 SAC: Ya se avisó lo mismo");
  await expect(fila(page, "20100000500").getByTestId("avisos-avisar")).toHaveCount(0);
  await expect(fila(page, "20100000500").getByTestId("avisos-espera")).toContainText("Se puede repetir desde el");
});

test("si el correo no sale, el diálogo lo dice, sigue abierto y no se da por avisado a nadie", async ({ page }) => {
  await abrir(page);
  const f = fila(page, "20100001000");

  await f.getByTestId("avisos-avisar").click();
  await page.getByTestId("avisos-avisar-confirmar").click();

  await expect(page.getByTestId("avisos-avisar-dialogo").getByRole("alert")).toContainText("No se pudo enviar el aviso");
  await expect(page.getByTestId("avisos-resultado")).toHaveCount(0);
  await page.getByRole("button", { name: "Cancelar" }).click();
  await expect(f.getByTestId("avisos-avisar")).toBeVisible();
  await expect(f.getByTestId("avisos-ultimo")).toHaveText("Sin avisos");
});
