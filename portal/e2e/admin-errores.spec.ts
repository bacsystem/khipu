import { expect, test, type Page } from "@playwright/test";
import { entrarComoAdmin, menuAdmin } from "./admin-sesion";
import { esperarHidratacion } from "./hidratacion";

// El mock (src/mocks/handlers.ts) siembra la cola de errores. Las specs de la corrida comparten esa memoria en paralelo, así que cada spec que actúa (reintentar, descartar)
// usa SU comprobante y solo afirma sobre él, y las que cuentan filas usan empresas que nadie muta. Lo sembrado, por empresa:
//   Panadería Sol  (1)  envío 101 (falla otra vez) · 102 (SUNAT lo acepta) · 103 (lo rechaza, 1033) · 105 (se descarta) · 107 (ya lo resolvió otro) · fuera de plazo 502
//   Ferretería Luna (2) envío 104 (se pasó el plazo) · 106 (la spec de validación del motivo) · error de formato 501 (1001)
//   Cliente 04     (4)  solo lectura: envío 201 y 202 (este con un fallo propio, sin código), formato 203, fuera de plazo 204
//   Cliente 07     (7)  solo lectura: 12 errores de envío, para paginar
//   Integrador     (101) solo lectura: envío 401, sin cuenta
// Que el backend junte, clasifique, reintente y descarte de verdad lo prueban `ColaDeErroresE2ETest` y los tests de cada capa; acá se prueba la pantalla y su BFF.
const empresa = (n: number) => `00000000-0000-4000-9000-${String(n).padStart(12, "0")}`;
const EMPRESA_1 = empresa(1);
const EMPRESA_2 = empresa(2);
const EMPRESA_4 = empresa(4);
const EMPRESA_7 = empresa(7);

test.beforeEach(async ({ page }) => {
  await entrarComoAdmin(page);
});

/** Abre la pantalla y espera a que React haya hidratado el buscador: un clic o un campo antes de hidratar se pierde. */
async function abrir(page: Page, ruta: string) {
  await page.goto(ruta);
  await esperarHidratacion(page, "#errores-q");
}

const fila = (page: Page, numero: number, ruc = "20100047226") => page.locator(`[data-testid="errores-fila"][data-comprobante="${ruc}-01-F001-${numero}"]`);
const comprobantes = (page: Page) => page.locator('[data-testid="errores-fila"]').evaluateAll((filas) => filas.map((f) => f.getAttribute("data-comprobante")));
/** Las filas que se ven, esperando a que la navegación termine: un clic cambia la URL y la lista un instante después. */
const esperarFilas = (page: Page, esperadas: string[]) => expect.poll(() => comprobantes(page)).toEqual(esperadas);

// --- la pantalla ----------------------------------------------------------------------------------------------------------------------------

test("el menú lleva a Errores y la miga dice «Operación / Errores»", async ({ page }) => {
  await menuAdmin(page).getByRole("link", { name: "Errores" }).click();

  await expect(page).toHaveURL(/\/admin\/errores$/);
  const miga = page.getByRole("navigation", { name: "Ubicación" });
  await expect(miga).toContainText("Operación");
  await expect(miga.locator("[aria-current=page]")).toHaveText("Errores");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Cola de errores");
});

test("cada fila dice el cliente, el comprobante, el problema, el fault y los intentos, del más antiguo al más reciente", async ({ page }) => {
  await abrir(page, `/admin/errores?empresa_id=${EMPRESA_4}`);

  await esperarFilas(page, ["20100000400-01-F001-204", "20100000400-01-F001-201", "20100000400-01-F001-203", "20100000400-01-F001-202"]);
  const envio = fila(page, 201, "20100000400");
  await expect(envio).toContainText("CLIENTE 04 SAC");
  await expect(envio).toContainText("20100000400");
  await expect(envio).toContainText("Cliente 04");
  await expect(envio).toContainText("Factura · 2 Oct 2026");
  await expect(envio.getByTestId("errores-fault")).toHaveText("0109 - El sistema no puede responder su solicitud");
  await expect(envio.getByTestId("errores-intentos")).toHaveText("2");
  await expect(envio).toContainText("Próximo intento:");
  await expect(fila(page, 202, "20100000400").getByTestId("errores-fault")).toHaveText("INFRA - storage no disponible");
  await expect(fila(page, 202, "20100000400")).toContainText("Sin reintento programado");
});

test("un error de formato y un fuera de plazo son terminales y no ofrecen acciones; un error de envío sí", async ({ page }) => {
  await abrir(page, `/admin/errores?empresa_id=${EMPRESA_4}`);

  for (const numero of [203, 204]) {
    const f = fila(page, numero, "20100000400");
    await expect(f).toContainText("Terminal: no se reintenta");
    await expect(f.getByTestId("errores-reintentar")).toHaveCount(0);
    await expect(f.getByTestId("errores-descartar")).toHaveCount(0);
  }
  await expect(fila(page, 203, "20100000400").getByTestId("errores-fault")).toHaveText("1033 - El comprobante fue registrado previamente con otros datos");
  await expect(fila(page, 201, "20100000400").getByTestId("errores-reintentar")).toBeVisible();
  await expect(fila(page, 201, "20100000400").getByTestId("errores-descartar")).toBeVisible();
});

// --- los filtros ----------------------------------------------------------------------------------------------------------------------------

test("filtrar por tipo de error deja solo ese tipo y lo pone en la URL", async ({ page }) => {
  await abrir(page, `/admin/errores?empresa_id=${EMPRESA_4}`);

  await page.getByRole("navigation", { name: "Tipo de error" }).getByRole("link", { name: "Error de formato" }).click();

  await expect(page).toHaveURL(new RegExp(`clase=ERROR_DE_FORMATO`));
  await esperarFilas(page, ["20100000400-01-F001-203"]);
  await expect(page.getByText("SUNAT lo rechazó con un fault 1000–1999: no cambia por reintentar.")).toBeVisible();

  await page.getByRole("navigation", { name: "Tipo de error" }).getByRole("link", { name: "Fuera de plazo" }).click();
  await esperarFilas(page, ["20100000400-01-F001-204"]);

  await page.getByRole("navigation", { name: "Tipo de error" }).getByRole("link", { name: "Error de envío" }).click();
  await esperarFilas(page, ["20100000400-01-F001-201", "20100000400-01-F001-202"]);
});

test("buscar un cliente por su razón social, sin tildes ni mayúsculas, filtra por él", async ({ page }) => {
  await abrir(page, "/admin/errores?clase=FUERA_DE_PLAZO");

  await page.getByLabel("Buscar cliente").fill("PANADERIA sol");
  await page.getByRole("button", { name: "Buscar" }).click();

  await expect(page).toHaveURL(/q=PANADERIA\+sol|q=PANADERIA%20sol/);
  await expect(page).toHaveURL(/clase=FUERA_DE_PLAZO/);
  await expect(fila(page, 502)).toBeVisible();
  await esperarFilas(page, ["20100047226-01-F001-502"]);
});

test("buscar por RUC lo trae por su prefijo, y un fragmento interno no trae nada", async ({ page }) => {
  await abrir(page, "/admin/errores");

  await page.getByLabel("Buscar cliente").fill("2010000040");
  await page.getByRole("button", { name: "Buscar" }).click();
  await expect(page.getByTestId("errores-fila")).toHaveCount(4);

  await page.getByLabel("Buscar cliente").fill("00004");
  await page.getByRole("button", { name: "Buscar" }).click();
  await expect(page.getByTestId("errores-vacio")).toHaveText("Ningún comprobante con problema coincide con la búsqueda.");
});

test("buscar por el nombre de la cuenta también filtra", async ({ page }) => {
  await abrir(page, "/admin/errores?clase=ERROR_DE_FORMATO");

  await page.getByLabel("Buscar cliente").fill("ferreteria luna");
  await page.getByRole("button", { name: "Buscar" }).click();

  await expect(fila(page, 501, "20100055121")).toBeVisible();
  await expect(page.getByTestId("errores-fila")).toHaveCount(1);
});

test("ver solo una empresa desde una fila filtra por ella y «Ver todas» lo quita", async ({ page }) => {
  await abrir(page, "/admin/errores?empresa_id=" + EMPRESA_4);
  await expect(page.getByTestId("errores-empresa-filtrada")).toContainText("Solo una empresa");
  await expect(fila(page, 201, "20100000400").getByTestId("errores-filtrar-empresa")).toHaveCount(0);

  await page.getByTestId("errores-empresa-filtrada").getByRole("link", { name: "Ver todas" }).click();

  await expect(page).not.toHaveURL(/empresa_id/);
  await expect(page.getByTestId("errores-empresa-filtrada")).toHaveCount(0);
  await esperarHidratacion(page, "#errores-q");
  await page.getByLabel("Buscar cliente").fill("integrador");
  await page.getByRole("button", { name: "Buscar" }).click();
  await fila(page, 401, "20100066611").getByTestId("errores-filtrar-empresa").click();
  await expect(page).toHaveURL(new RegExp(`empresa_id=${empresa(101)}`));
  await esperarFilas(page, ["20100066611-01-F001-401"]);
});

test("una empresa de integración, sin cuenta, igual aparece", async ({ page }) => {
  await abrir(page, `/admin/errores?empresa_id=${empresa(101)}`);

  const f = fila(page, 401, "20100066611");
  await expect(f).toContainText("INTEGRADOR SAC");
  await expect(f).toContainText("20100066611");
});

test("quitar filtros vuelve a la lista completa", async ({ page }) => {
  await abrir(page, `/admin/errores?clase=ERROR_DE_FORMATO&empresa_id=${EMPRESA_4}&q=cliente`);

  await page.getByRole("link", { name: "Quitar filtros" }).click();

  await expect(page).toHaveURL(/\/admin\/errores$/);
  await expect(page.getByRole("link", { name: "Quitar filtros" })).toHaveCount(0);
});

// --- la página ------------------------------------------------------------------------------------------------------------------------------

test("pagina de 10 en 10 y una página pasada de la última lleva a la última", async ({ page }) => {
  await abrir(page, `/admin/errores?empresa_id=${EMPRESA_7}`);
  await expect(page.getByTestId("errores-fila")).toHaveCount(10);

  await page.getByRole("link", { name: "2", exact: true }).click();
  await expect(page).toHaveURL(/pagina=2/);
  await expect(page.getByTestId("errores-fila")).toHaveCount(2);

  await page.goto(`/admin/errores?empresa_id=${EMPRESA_7}&pagina=9`);
  await expect(page).toHaveURL(/pagina=2/);
  await expect(page.getByTestId("errores-fila")).toHaveCount(2);
});

// --- reintentar -----------------------------------------------------------------------------------------------------------------------------

test("reintentar un envío que vuelve a fallar dice el intento y el fault, y la fila sigue con un intento más", async ({ page }) => {
  await abrir(page, `/admin/errores?empresa_id=${EMPRESA_1}`);
  const f = fila(page, 101);
  await expect(f.getByTestId("errores-intentos")).toHaveText("3");

  await f.getByTestId("errores-reintentar").click();
  const dialogo = page.getByTestId("errores-reintentar-dialogo");
  await expect(dialogo).toContainText("Envía 20100047226-01-F001-101 a SUNAT ahora");
  await expect(dialogo).toContainText("con las credenciales de su empresa");
  await page.getByTestId("errores-reintentar-confirmar").click();

  await expect(page.getByTestId("errores-resultado")).toHaveText("20100047226-01-F001-101: SUNAT volvió a fallar (intento 4). 0000 - SUNAT respondió HTTP 503");
  await expect(dialogo).toHaveCount(0);
  await expect(f.getByTestId("errores-intentos")).toHaveText("4");
  await expect(f.getByTestId("errores-fault")).toHaveText("0000 - SUNAT respondió HTTP 503");
});

test("reintentar un envío que SUNAT acepta lo saca de la cola", async ({ page }) => {
  await abrir(page, `/admin/errores?empresa_id=${EMPRESA_1}`);

  await fila(page, 102).getByTestId("errores-reintentar").click();
  await page.getByTestId("errores-reintentar-confirmar").click();

  await expect(page.getByTestId("errores-resultado")).toHaveText("20100047226-01-F001-102: SUNAT lo aceptó.");
  await expect(fila(page, 102)).toHaveCount(0);
});

test("reintentar un envío que SUNAT rechaza con un fault de formato lo pasa a error de formato, ya terminal", async ({ page }) => {
  await abrir(page, `/admin/errores?empresa_id=${EMPRESA_1}`);

  await fila(page, 103).getByTestId("errores-reintentar").click();
  await page.getByTestId("errores-reintentar-confirmar").click();

  await expect(page.getByTestId("errores-resultado")).toHaveText("20100047226-01-F001-103: SUNAT lo rechazó (1033).");
  const f = fila(page, 103);
  await expect(f).toHaveAttribute("data-clase", "ERROR_DE_FORMATO");
  await expect(f).toContainText("Terminal: no se reintenta");
  await expect(f.getByTestId("errores-reintentar")).toHaveCount(0);
});

test("si se pasó el plazo el reintento no envía: lo dice, y la fila pasa a fuera de plazo", async ({ page }) => {
  await abrir(page, `/admin/errores?empresa_id=${EMPRESA_2}`);

  await fila(page, 104, "20100055121").getByTestId("errores-reintentar").click();
  await page.getByTestId("errores-reintentar-confirmar").click();

  // La recarga convierte la fila en terminal (sin botones) y se lleva el diálogo con su alerta: el aviso duradero es el banner de resultados. Asertar sobre la alerta del
  // diálogo era una carrera (a veces la recarga la desmontaba antes de que la prueba la viera).
  await expect(page.getByTestId("errores-resultado")).toContainText("2108");
  await expect(fila(page, 104, "20100055121")).toHaveAttribute("data-clase", "FUERA_DE_PLAZO");
});

test("si otro administrador ya lo resolvió, el reintento lo dice y la fila desaparece al recargar", async ({ page }) => {
  await abrir(page, `/admin/errores?empresa_id=${EMPRESA_1}`);

  await fila(page, 107).getByTestId("errores-reintentar").click();
  await page.getByTestId("errores-reintentar-confirmar").click();

  // La fila desaparece con la recarga y se lleva el diálogo: el aviso queda en el banner de resultados.
  await expect(page.getByTestId("errores-resultado")).toContainText("20100047226-01-F001-107: El comprobante está en estado ACEPTADO");
  await expect(fila(page, 107)).toHaveCount(0);
});

// --- descartar ------------------------------------------------------------------------------------------------------------------------------

test("descartar pide un motivo, lo descarta y lo saca de la cola", async ({ page }) => {
  await abrir(page, `/admin/errores?empresa_id=${EMPRESA_1}`);

  await fila(page, 105).getByTestId("errores-descartar").click();
  const dialogo = page.getByTestId("errores-descartar-dialogo");
  await expect(dialogo).toContainText("No se puede deshacer");
  await page.getByTestId("errores-descartar-confirmar").click();
  await expect(dialogo.getByRole("alert")).toHaveText("Indica por qué se descarta el comprobante");
  await expect(fila(page, 105)).toBeVisible();

  await page.getByTestId("errores-motivo").fill("El cliente lo reemitió con otra serie");
  await page.getByTestId("errores-descartar-confirmar").click();

  await expect(page.getByTestId("errores-resultado")).toHaveText("20100047226-01-F001-105 se descartó.");
  await expect(fila(page, 105)).toHaveCount(0);
});

test("cancelar un descarte no toca nada y el motivo escrito no se arrastra", async ({ page }) => {
  await abrir(page, `/admin/errores?empresa_id=${EMPRESA_2}`);

  await fila(page, 106, "20100055121").getByTestId("errores-descartar").click();
  await page.getByTestId("errores-motivo").fill("algo a medias");
  await page.getByRole("button", { name: "Cancelar" }).click();
  await fila(page, 106, "20100055121").getByTestId("errores-descartar").click();

  await expect(page.getByTestId("errores-motivo")).toHaveValue("");
  await page.getByRole("button", { name: "Cancelar" }).click();
  await expect(fila(page, 106, "20100055121")).toBeVisible();
  await expect(page.getByTestId("errores-resultado")).toHaveCount(0);
});
