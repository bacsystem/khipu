import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/inicio/);
});

/**
 * #39: el listado y la ficha de las guías de remisión. Cada test que cambia una guía usa su propia fixture (los specs comparten el mock y corren en paralelo).
 */
test("el menú lleva a las guías, que se listan con su estado y avisan que son experimentales", async ({ page }) => {
  await page.getByRole("navigation").getByRole("link", { name: "Guías de remisión" }).first().click();
  await expect(page).toHaveURL(/\/guias$/);
  await expect(page.getByRole("heading", { name: "Guías de remisión" })).toBeVisible();
  await expect(page.getByRole("note")).toContainText("Función experimental");

  // Las guías que emiten otros specs en el mismo mock van delante (por fecha) y empujan las fixtures a la segunda página de 10: se pide una de 50, como en los specs de admin.
  await page.goto("/guias?por_pagina=50");
  const aceptada = page.getByRole("row", { name: /T001-00000008/ });
  await expect(aceptada).toContainText("Aceptada");
  await expect(aceptada).toContainText("PESQUERA PACÍFICO S.A.C.");
  await expect(page.getByRole("row", { name: /V001-00000001/ })).toContainText("Aceptada con obs.");
  await expect(page.getByRole("row", { name: /T001-00000005/ })).toContainText("Error de envío");
});

test("filtra por estado y por tipo, y los filtros viven en la URL", async ({ page }) => {
  await page.goto("/guias");
  // La barra de filtros del design system (#531): cada filtro se aplica al elegirlo, sin botón «Filtrar».
  await page.getByRole("combobox", { name: "Estado" }).click();
  await page.getByRole("option", { name: "Error de envío" }).click();
  await expect(page).toHaveURL(/estado=ERROR_ENVIO/);
  await expect(page.getByRole("row", { name: /T001-00000005/ })).toBeVisible();
  await expect(page.getByRole("row", { name: /T001-00000008/ })).toHaveCount(0);

  await page.goto("/guias?tipo=31");
  await expect(page.getByRole("row", { name: /V001-00000001/ })).toBeVisible();
  await expect(page.getByRole("row", { name: /T001-00000008/ })).toHaveCount(0);

  await page.goto("/guias?estado=ACEPTADO&serie=V001&desde=2026-10-01");
  await expect(page.getByText("Ninguna guía coincide con los filtros.")).toBeVisible();
  // Una fecha se quita con su ✕ y sale de la URL; los demás filtros quedan.
  await page.getByRole("button", { name: "Quitar la fecha desde" }).click();
  await expect(page).not.toHaveURL(/desde=/);
  await expect(page).toHaveURL(/serie=V001/);
  await page.getByRole("link", { name: "Limpiar" }).click();
  await expect(page).toHaveURL(/\/guias$/);
});

test("la ficha de una guía aceptada muestra su constancia, el traslado, los bienes y las descargas", async ({ page }) => {
  await page.goto("/guias?por_pagina=50");
  await page.getByRole("link", { name: "Ver la guía T001-00000008" }).click();
  await expect(page).toHaveURL(/\/guias\/g-aceptada/);
  await expect(page.getByTestId("guia-numero")).toHaveText("T001-00000008");
  await expect(page.getByTestId("guia-cdr")).toContainText("ha sido aceptada");
  await expect(page.getByText("Laptop 15 pulgadas")).toBeVisible();
  await expect(page.getByText("ABC123")).toBeVisible();
  await expect(page.getByText("F001-123")).toBeVisible();
  const descargas = page.getByTestId("guia-descargas");
  await expect(descargas.getByRole("link", { name: "XML firmado" })).toHaveAttribute("href", "/api/proxy/guias-remision/g-aceptada/xml");
  await expect(descargas.getByRole("link", { name: "CDR (ZIP)" })).toBeVisible();
  // Aceptada: no hay nada más que enviar ni consultar.
  await expect(page.getByTestId("guia-acciones")).toHaveCount(0);
});

test("una guía con error de envío dice qué falló y si khipu la reintenta solo", async ({ page }) => {
  await page.goto("/guias/g-error-envio");
  await expect(page.getByTestId("guia-ultimo-error")).toContainText("CREDENCIALES_API_SUNAT_NO_CARGADAS");
  await expect(page.getByTestId("guia-ultimo-error")).toContainText("khipu no lo reintenta solo");
  await expect(page.getByRole("button", { name: "Reenviar a SUNAT" })).toBeEnabled();
});

test("con el envío apagado en el servidor, enviar una guía firmada lo dice y la guía sigue firmada", async ({ page }) => {
  await page.goto("/guias/g-firmada");
  await expect(page.getByTestId("guia-estado-ayuda")).toContainText("todavía no se envió a SUNAT");
  await page.getByRole("button", { name: "Enviar a SUNAT" }).click();
  await expect(page.getByTestId("guia-acciones").getByRole("alert")).toContainText("no está habilitado en este servidor");
  await expect(page.getByRole("button", { name: "Enviar a SUNAT" })).toBeEnabled();
});

test("una guía firmada se envía, espera su ticket y termina aceptada con su constancia", async ({ page }) => {
  await page.goto("/guias/g-firmada-habilitada");
  await page.getByRole("button", { name: "Enviar a SUNAT" }).click();
  await expect(page.getByRole("button", { name: "Consultar el ticket" })).toBeVisible();
  await expect(page.getByTestId("guia-estado-ayuda")).toContainText("SUNAT recibió la guía");

  await page.getByRole("button", { name: "Consultar el ticket" }).click();
  await expect(page.getByTestId("guia-cdr")).toContainText("ha sido aceptada");
  await expect(page.getByTestId("guia-acciones")).toHaveCount(0);
});

test("un envío que quedó en curso solo se retoma después de confirmar que se revisó en SOL", async ({ page }) => {
  await page.goto("/guias/g-enviando");
  const retomar = page.getByRole("button", { name: "Retomar el envío" });
  await expect(retomar).toBeDisabled();
  await page.getByRole("checkbox", { name: "Revisé en SOL que la guía no llegó" }).check();
  await expect(retomar).toBeEnabled();
  await retomar.click();
  await expect(page.getByTestId("guia-estado-ayuda")).toContainText("SUNAT recibió la guía");
});

test("una guía que no existe da la página de no encontrada", async ({ page }) => {
  const res = await page.goto("/guias/g-no-existe");
  expect(res?.status()).toBe(404);
});
