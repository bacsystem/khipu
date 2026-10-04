import { expect, test, type Page } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";

// El mock (src/mocks/handlers.ts) siembra el consumo de las cuentas con el plan de cada una. Las specs de la corrida comparten ese mock en paralelo (y algunas
// cambian el plan de SU cuenta o crean cuentas nuevas), así que esta spec solo lee y solo afirma sobre cuentas que nadie muta, y nunca sobre totales de la lista sin
// filtro. Lo sembrado, con el mes en curso:
//   Panadería Sol  (1)  Emprende al día, 312 de 300 → 104 %, «En el límite»
//   Ferretería Luna (2) Emprende en gracia (venció hace 2 días, 5 de gracia), 20 de 300
//   Cliente 03      (3) Emprende vencida (venció hace 30 días, 3 de gracia)
//   Cliente 05      (5) Gratis, 25 de 30 → 83 %, «Cerca del límite»
//   Cliente 07      (7) Gratis, 400 de 30 → 1333 %, «En el límite»
//   Cliente 13      (13) de baja: no sale
// Cualquier otro mes cada cuenta lleva 5 documentos. Que la base, el CSV y los filtros digan lo mismo que el dominio lo prueba `ConsumoPorCuentasE2ETest`.
const cuenta = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;

test.beforeEach(async ({ page }) => {
  await entrarComoAdmin(page);
});

const fila = (page: Page, nombre: string) => page.locator("tbody tr", { hasText: nombre });
const nombresEnOrden = (page: Page) => page.locator("tbody tr td:first-child a").allTextContents();

// --- la tabla -----------------------------------------------------------------------------------------------------------------------------

test("la lista dice el consumo, el tope, el uso y el estado del plan de cada cuenta", async ({ page }) => {
  await page.goto("/admin/consumo");

  await expect(page.getByRole("heading", { name: "Consumo" })).toBeVisible();
  const panaderia = fila(page, "Panadería Sol");
  await expect(panaderia).toContainText("Emprende");
  await expect(panaderia).toContainText("312");
  await expect(panaderia).toContainText("300");
  await expect(panaderia).toContainText("104 %");
  await expect(panaderia).toContainText("En el límite");
  await expect(panaderia.locator("[data-estado]")).toHaveAttribute("data-estado", "VIGENTE");
  await expect(panaderia).toContainText(/Hasta el/);
  await expect(panaderia).toHaveAttribute("data-alerta", "true");
  await expect(fila(page, "Ferretería Luna").locator("[data-estado]")).toHaveAttribute("data-estado", "EN_GRACIA");
  await expect(fila(page, "Cliente 03").locator("[data-estado]")).toHaveAttribute("data-estado", "VENCIDA");
  await expect(fila(page, "Cliente 05")).toContainText("83 %");
  await expect(fila(page, "Cliente 05")).toContainText("Cerca del límite");
});

test("se ordena por porcentaje usado: lo más cerca del límite arriba", async ({ page }) => {
  await page.goto("/admin/consumo");

  const nombres = await nombresEnOrden(page);
  expect(nombres.indexOf("Cliente 07")).toBeLessThan(nombres.indexOf("Panadería Sol"));
  expect(nombres.indexOf("Panadería Sol")).toBeLessThan(nombres.indexOf("Cliente 05"));
  expect(nombres.indexOf("Cliente 05")).toBeLessThan(nombres.indexOf("Ferretería Luna"));
});

test("las cuentas dadas de baja no salen", async ({ page }) => {
  await page.goto("/admin/consumo?por_pagina=50");

  await expect(fila(page, "Panadería Sol")).toBeVisible();
  await expect(page.getByText("Cliente 13", { exact: true })).toHaveCount(0);
});

test("el nombre de una cuenta lleva a su detalle", async ({ page }) => {
  await page.goto("/admin/consumo");

  await page.getByRole("link", { name: "Panadería Sol" }).click();

  await expect(page).toHaveURL(new RegExp(`/admin/cuentas/${cuenta(1)}$`));
});

// --- las vistas ---------------------------------------------------------------------------------------------------------------------------

test("«Cerca del límite» trae solo las cuentas en alerta, y el pie cuenta solo esas", async ({ page }) => {
  await page.goto("/admin/consumo");

  await page.getByRole("navigation", { name: "Cuentas a mostrar" }).getByRole("link", { name: "Cerca del límite" }).click();

  await expect(page).toHaveURL(/filtro=CERCA_DEL_LIMITE/);
  await expect(page.locator("tbody tr")).toHaveCount(3);
  expect(await nombresEnOrden(page)).toEqual(["Cliente 07", "Panadería Sol", "Cliente 05"]);
  await expect(page.locator("body")).toContainText(/Mostrando\s*1–3\s*de\s*3/);
  await expect(page.getByRole("link", { name: "Cerca del límite" })).toHaveAttribute("aria-current", "page");
});

test("«Plan vencido» trae las que están en gracia y las vencidas, con su estado a la vista", async ({ page }) => {
  await page.goto("/admin/consumo");

  await page.getByRole("navigation", { name: "Cuentas a mostrar" }).getByRole("link", { name: "Plan vencido" }).click();

  await expect(page).toHaveURL(/filtro=PLAN_VENCIDO/);
  await expect(page.locator("tbody tr")).toHaveCount(2);
  await expect(fila(page, "Ferretería Luna").locator("[data-estado]")).toHaveAttribute("data-estado", "EN_GRACIA");
  await expect(fila(page, "Cliente 03").locator("[data-estado]")).toHaveAttribute("data-estado", "VENCIDA");
  await expect(page.locator("body")).toContainText(/Mostrando\s*1–2\s*de\s*2/);
});

test("con una vista y un orden en la URL, ese estado se ve al abrirla (el enlace se puede compartir)", async ({ page }) => {
  await page.goto("/admin/consumo?filtro=PLAN_VENCIDO&orden=DOCUMENTOS");

  await expect(page.getByRole("link", { name: "Plan vencido" })).toHaveAttribute("aria-current", "page");
  await expect(page.getByLabel("Ordenar por")).toHaveValue("DOCUMENTOS");
  await expect(page.locator("tbody tr")).toHaveCount(2);
});

test("un filtro o un orden raros en la URL no rompen la página: valen los de por defecto", async ({ page }) => {
  await page.goto("/admin/consumo?filtro=cualquiera&orden=nada&mes=octubre");

  await expect(page.getByRole("link", { name: "Todas" })).toHaveAttribute("aria-current", "page");
  await expect(page.getByLabel("Ordenar por")).toHaveValue("PORCENTAJE");
  await expect(fila(page, "Panadería Sol")).toBeVisible();
});

// --- el orden y el mes --------------------------------------------------------------------------------------------------------------------

test("ordenar por documentos pone arriba a quien más consumió", async ({ page }) => {
  await page.goto("/admin/consumo");

  await page.getByLabel("Ordenar por").selectOption("DOCUMENTOS");

  await expect(page).toHaveURL(/orden=DOCUMENTOS/);
  await expect(page.locator("tbody tr").first()).toContainText("Cliente 07");
  const nombres = await nombresEnOrden(page);
  expect(nombres.indexOf("Cliente 07")).toBeLessThan(nombres.indexOf("Panadería Sol"));
  expect(nombres.indexOf("Panadería Sol")).toBeLessThan(nombres.indexOf("Cliente 05"));
});

test("el selector de mes arranca en el mes en curso y otro mes cambia el consumo, no el plan con el que se compara", async ({ page }) => {
  await page.goto("/admin/consumo");
  const mesEnCurso = await page.getByLabel("Mes").inputValue();
  expect(mesEnCurso).toMatch(/^\d{4}-\d{2}$/);

  await page.getByLabel("Mes").fill("2026-08");

  await expect(page).toHaveURL(/mes=2026-08/);
  await expect(page.getByLabel("Mes")).toHaveValue("2026-08");
  const panaderia = fila(page, "Panadería Sol");
  await expect(panaderia).toContainText("Emprende");
  await expect(panaderia).toContainText("300");
  await expect(panaderia).toContainText("1 %");
  await expect(panaderia).toHaveAttribute("data-alerta", "false");
});

test("en un mes sin cuentas cerca del límite, esa vista lo dice", async ({ page }) => {
  await page.goto("/admin/consumo?mes=2026-08&filtro=CERCA_DEL_LIMITE");

  await expect(page.getByText("Ninguna cuenta está cerca de su límite este mes.")).toBeVisible();
  await expect(page.locator("body")).toContainText(/Mostrando\s*0–0\s*de\s*0/);
});

test("el mes enlazado se conserva al cambiar de vista", async ({ page }) => {
  await page.goto("/admin/consumo?mes=2026-08");

  await page.getByRole("link", { name: "Plan vencido" }).click();

  await expect(page).toHaveURL(/mes=2026-08/);
  await expect(page).toHaveURL(/filtro=PLAN_VENCIDO/);
});

// --- paginación ---------------------------------------------------------------------------------------------------------------------------

test("la lista se pagina y conserva la vista y el orden", async ({ page }) => {
  await page.goto("/admin/consumo?orden=DOCUMENTOS");

  await expect(page.locator("tbody tr")).toHaveCount(10);
  await page.getByRole("link", { name: /Siguiente/ }).click();

  await expect(page).toHaveURL(/orden=DOCUMENTOS/);
  await expect(page).toHaveURL(/pagina=2/);
  await expect(page.locator("tbody tr").first()).not.toContainText("Cliente 07");
});

test("una página pasada de la última vuelve a la última, sin una tabla vacía", async ({ page }) => {
  await page.goto("/admin/consumo?filtro=PLAN_VENCIDO&pagina=9");

  await expect(page).toHaveURL(/\/admin\/consumo\?filtro=PLAN_VENCIDO$/);
  await expect(page.locator("tbody tr")).toHaveCount(2);
});

// --- exportación --------------------------------------------------------------------------------------------------------------------------

async function descargar(page: Page): Promise<{ nombre: string; bytes: Buffer }> {
  const descarga = page.waitForEvent("download");
  await page.getByRole("link", { name: "Exportar CSV" }).click();
  const d = await descarga;
  const flujo = await d.createReadStream();
  const partes: Buffer[] = [];
  for await (const parte of flujo) partes.push(Buffer.from(parte));
  return { nombre: d.suggestedFilename(), bytes: Buffer.concat(partes) };
}

test("«Exportar CSV» baja lo que se ve: el mes y la vista, con la marca UTF-8 y una fila por cuenta", async ({ page }) => {
  await page.goto("/admin/consumo?filtro=CERCA_DEL_LIMITE");
  const mes = await page.getByLabel("Mes").inputValue();

  const { nombre, bytes } = await descargar(page);

  expect(nombre).toBe(`consumo-${mes}.csv`);
  expect([...bytes.subarray(0, 3)]).toEqual([0xef, 0xbb, 0xbf]);
  const lineas = bytes.toString("utf-8").slice(1).split("\r\n");
  expect(lineas[0]).toBe("cuenta_id,cuenta,correo,plan,documentos,limite,porcentaje,en_alerta,estado_del_plan,pagado_hasta,se_sirve_hasta");
  expect(lineas.filter((l) => l !== "")).toHaveLength(4);
  expect(lineas[1]).toContain(",Cliente 07,");
  expect(lineas.join("\n")).toContain(`${cuenta(1)},Panadería Sol,`);
  expect(lineas.join("\n")).toContain(",312,300,104,si,VIGENTE,");
  expect(lineas.join("\n")).not.toContain("Ferretería Luna");
});

test("exportar con otro mes y otro orden lo respeta, sin página", async ({ page }) => {
  await page.goto("/admin/consumo?mes=2026-08&orden=DOCUMENTOS&por_pagina=10&pagina=2");

  const { nombre, bytes } = await descargar(page);

  expect(nombre).toBe("consumo-2026-08.csv");
  // Todas las cuentas, no solo la segunda página: la cabecera, las 12 sembradas como mínimo y el salto de línea final.
  expect(bytes.toString("utf-8").split("\r\n").filter((l) => l !== "").length).toBeGreaterThanOrEqual(13);
});

// --- el BFF -------------------------------------------------------------------------------------------------------------------------------

test("la descarga exige la sesión del administrador y rechaza lo mal escrito", async ({ page, request }) => {
  expect((await request.get("/api/admin/consumo/exportacion")).status()).toBe(401);

  const conSesion = await page.request.get("/api/admin/consumo/exportacion?mes=2026-10");
  expect(conSesion.status()).toBe(200);
  expect(conSesion.headers()["content-type"]).toContain("text/csv");
  expect(conSesion.headers()["content-disposition"]).toContain("consumo-2026-10.csv");
  expect(conSesion.headers()["cache-control"]).toContain("no-store");
  for (const malo of ["mes=2026-13", "filtro=TODOS", "orden=NOMBRE"]) {
    expect((await page.request.get(`/api/admin/consumo/exportacion?${malo}`)).status(), malo).toBe(400);
  }
});

// --- navegación ---------------------------------------------------------------------------------------------------------------------------

test("el menú lleva a Consumo y la miga dice «Comercial / Consumo»", async ({ page }) => {
  await page.getByRole("link", { name: "Consumo" }).click();

  await expect(page).toHaveURL(/\/admin\/consumo$/);
  const miga = page.getByRole("navigation", { name: "Ubicación" });
  await expect(miga).toContainText("Comercial");
  await expect(miga.locator("[aria-current=page]")).toHaveText("Consumo");
  await expect(page.getByRole("heading", { level: 1 })).toHaveCount(1);
});

test("la columna de las empresas ya no se llama «Comprobantes del mes»: cuenta todo lo emitido, no lo que consume el plan", async ({ page }) => {
  await page.goto("/admin/empresas");

  const columna = page.getByRole("columnheader", { name: "Emitidos en el mes" });
  await expect(columna).toBeVisible();
  await expect(columna).toHaveAttribute("title", /Lo que consume el plan/);
  await expect(page.getByRole("columnheader", { name: "Comprobantes del mes" })).toHaveCount(0);
});

test.describe("sin sesión", () => {
  test("la página de consumo lleva al login del backoffice", async ({ browser }) => {
    const contexto = await browser.newContext();
    const pagina = await contexto.newPage();

    await pagina.goto("/admin/consumo");

    await expect(pagina).toHaveURL(/\/admin\/login/);
    await contexto.close();
  });
});
