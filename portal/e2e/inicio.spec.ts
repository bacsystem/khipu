import { expect, test, type Page } from "@playwright/test";

/**
 * #24: el panel de inicio, adonde se llega al entrar. El mock (src/mocks/data.ts) siembra cuentas propias con comprobantes del mes en curso, con fechas relativas a hoy:
 *   panel@example.com          5 emitidos este mes (3 aceptados, 1 en camino, 1 rechazado), S/ 418.00 y $ 50.00; el mismo tramo del mes anterior, 1 emitido.
 *   panel-alertas@example.com  certificado que vence en 10 días, sin credenciales SOL y un comprobante fuera de plazo.
 *   panel-vacio@example.com    recién creada: sin certificado, sin SOL, sin series y sin comprobantes.
 *   panel-falla@example.com    su emisión por día responde 500.
 */
async function entrar(page: Page, email: string) {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/inicio$/);
}

function hoyLima(): string {
  return new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" });
}

test("al entrar se llega al panel, que es el primer ítem de la navegación y el destino del logo", async ({ page }) => {
  await entrar(page, "panel@example.com");

  await expect(page).toHaveTitle(/^Inicio · /);
  await expect(page.getByRole("heading", { level: 1, name: "Inicio" })).toBeVisible();
  const navegacion = page.getByRole("navigation", { name: "Navegación principal" });
  await expect(navegacion.getByRole("link").first()).toHaveText("Inicio");
  await expect(navegacion.getByRole("link").first()).toHaveAttribute("aria-current", "page");

  await page.goto("/series");
  await page.locator("aside").getByRole("link").first().click();
  await expect(page).toHaveURL(/\/inicio$/);
});

test("con datos: los indicadores del mes, la comparación con el mes anterior y la emisión por día", async ({ page }) => {
  await entrar(page, "panel@example.com");

  const kpis = page.getByTestId("kpis-del-mes");
  await expect(kpis).toHaveAttribute("data-cargado", "true");
  await expect(page.getByTestId("kpi-facturado")).toHaveText(["S/ 418.00", "$ 50.00"]);
  await expect(page.getByTestId("kpi-facturado-variacion")).toHaveText("Neto, por moneda");
  await expect(page.getByTestId("kpi-emitidos")).toHaveText("5");
  await expect(page.getByTestId("kpi-emitidos-variacion")).toHaveText("+400 % vs. el mes pasado");
  await expect(page.getByTestId("kpi-aceptados")).toHaveText("3");
  await expect(page.getByTestId("kpi-pendientes")).toHaveText("1");
  await expect(page.getByTestId("kpi-atencion")).toHaveText("1");

  const hoy = hoyLima();
  const dias = page.getByTestId("grafico-emision-dia");
  await expect(dias).toHaveCount(Number(hoy.slice(8, 10)));
  await expect(page.getByRole("img", { name: /Emisión por día/ })).toBeVisible();
  const primero = `${hoy.slice(0, 7)}-01`;
  if (primero === hoy) {
    await expect(dias.first()).toHaveAttribute("data-emitidos", "5");
  } else {
    await expect(page.locator(`[data-testid="grafico-emision-dia"][data-fecha="${primero}"]`)).toHaveAttribute("data-emitidos", "3");
    await expect(page.locator(`[data-testid="grafico-emision-dia"][data-fecha="${hoy}"]`)).toHaveAttribute("data-emitidos", "2");
  }
  await expect(page.getByTestId("grafico-emision-maximo")).toHaveText(primero === hoy ? "5" : "3");

  // La misma información como tabla.
  await page.getByText("Ver como tabla").click();
  await expect(page.getByTestId("grafico-emision-tabla").getByRole("columnheader")).toHaveText(["Día", "Emitidos", "Facturado PEN", "Facturado USD"]);
});

test("el aviso del rechazado lleva al listado filtrado por su estado y el mes, y ahí está", async ({ page }) => {
  await entrar(page, "panel@example.com");

  const aviso = page.locator('[data-alerta="rechazados"]');
  await expect(aviso).toContainText("SUNAT rechazó 1 comprobante");
  await aviso.getByRole("link", { name: "Ver rechazados" }).click();

  const hoy = hoyLima();
  await expect(page).toHaveURL(new RegExp(`/comprobantes\\?estado=RECHAZADO&desde=${hoy.slice(0, 7)}-01&hasta=${hoy}$`));
  await expect(page.locator("table").getByText("Rechazado").first()).toBeVisible();
});

test("con alertas: certificado por vencer, credenciales SOL ausentes y un comprobante fuera de plazo, cada uno con su enlace", async ({ page }) => {
  await entrar(page, "panel-alertas@example.com");

  const avisos = page.getByTestId("alerta-del-panel");
  await expect(avisos).toHaveCount(2);
  // Primero los errores, después los avisos.
  await expect(avisos.nth(0)).toHaveAttribute("data-alerta", "fuera-de-plazo");
  await expect(avisos.nth(0)).toContainText("1 comprobante quedó fuera de plazo");
  await expect(avisos.nth(1)).toHaveAttribute("data-alerta", "certificado-por-vencer");
  await expect(avisos.nth(1)).toContainText("El certificado digital vence en 10 días");
  await expect(page.getByTestId("kpi-atencion")).toHaveText("1");
  // Las credenciales SOL que faltan las dice la guía, que es la que lleva los pasos; no se repiten en una tarjeta aparte.
  const guia = page.getByTestId("guia-emitir");
  await expect(guia.locator('[data-paso="sol"]')).toHaveAttribute("data-listo", "false");
  await expect(page.locator('[data-alerta="sol-ausentes"]')).toHaveCount(0);

  await avisos.nth(0).getByRole("link", { name: "Ver fuera de plazo" }).click();
  await expect(page).toHaveURL(/\/comprobantes\?estado=FUERA_DE_PLAZO&/);
  await page.goBack();
  await avisos.nth(1).getByRole("link", { name: "Renovar certificado" }).click();
  await expect(page).toHaveURL(/\/empresa\?seccion=certificado$/);
  await page.goBack();
  await guia.getByRole("link", { name: "Guardar credenciales" }).click();
  await expect(page).toHaveURL(/\/empresa\?seccion=sol$/);
});

test("empresa nueva: la guía para emitir con lo que falta y una invitación a emitir en vez de indicadores en cero", async ({ page }) => {
  await entrar(page, "panel-vacio@example.com");

  await expect(page.getByTestId("guia-emitir")).toBeVisible();
  await expect(page.getByTestId("guia-emitir-avance")).toHaveText("0 de 4 listos");
  // Lo que falta lo dice la guía: ni tarjetas que lo repitan ni un «sin avisos, todo en orden».
  await expect(page.getByTestId("alerta-del-panel")).toHaveCount(0);
  await expect(page.getByTestId("alertas-sin-avisos")).toHaveCount(0);
  await expect(page.getByText("Aún no hay comprobantes este mes")).toBeVisible();
  await expect(page.getByText(/Completa los pasos de «Para empezar a emitir»/)).toBeVisible();
  await expect(page.getByTestId("kpis-del-mes")).toHaveCount(0);
  await expect(page.getByTestId("grafico-emision")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Nuevo comprobante" })).toBeVisible();
});

test("si una lectura falla, el panel sigue con el resto y dice qué no cargó", async ({ page }) => {
  await entrar(page, "panel-falla@example.com");

  await expect(page.getByTestId("grafico-emision")).toHaveAttribute("data-cargado", "false");
  await expect(page.getByTestId("grafico-emision-error")).toContainText("No se pudo cargar la emisión por día");
  await expect(page.getByTestId("kpis-del-mes")).toHaveAttribute("data-cargado", "true");
  await expect(page.getByTestId("kpi-emitidos")).toHaveText("1");
  await expect(page.getByTestId("alertas-sin-avisos")).toBeVisible();
});

test("en un teléfono el panel no se desborda a lo ancho", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 800 });
  await entrar(page, "panel@example.com");

  await expect(page.getByRole("img", { name: /Emisión por día/ })).toBeVisible();
  const desborde = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(desborde).toBeLessThanOrEqual(0);
});
