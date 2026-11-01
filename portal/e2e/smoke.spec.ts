import { expect, test } from "@playwright/test";

test("la página raíz responde", async ({ page }) => {
  const response = await page.goto("/");
  expect(response?.ok()).toBeTruthy();
});

/** En el teléfono el menú del encabezado se oculta: «Iniciar sesión» tiene que seguir a la vista arriba, no solo en el pie de la página. */
test("en un teléfono el encabezado lleva al login", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 800 });
  await page.goto("/");
  const enlace = page.getByRole("banner").getByRole("link", { name: "Iniciar sesión" });
  await expect(enlace).toBeVisible();
  // #525: en el teléfono la marca es solo el ícono (con el nombre, el enlace se partía en dos líneas y el nombre quedaba en «k…»), y el enlace ocupa una línea.
  // Un solo enlace a la portada a la vista, y es el ícono: el logo con nombre no se pinta (cortado a ancho cero seguiría contando como «oculto»).
  const marcas = page.getByRole("banner").locator('a[href="/"]:visible');
  await expect(marcas).toHaveCount(1);
  await expect(marcas).toHaveAccessibleName("khipu.pe");
  await expect(marcas).not.toContainText("khipu");
  const alto = await enlace.evaluate((el) => el.getBoundingClientRect().height);
  const linea = await enlace.evaluate((el) => parseFloat(getComputedStyle(el).lineHeight));
  expect(alto).toBeLessThan(linea * 1.5);
  await enlace.click();
  await expect(page).toHaveURL(/\/login$/);
});

/** Los íconos sobre el fondo de acento suave usan la tinta del acento, no el acento: lima sobre lima no se veía (contraste < 1,5:1). */
test("los íconos de «Cómo funciona» se distinguen de su fondo", async ({ page }) => {
  await page.goto("/");
  const icono = page.locator("section", { hasText: "De tu certificado a la constancia de SUNAT" }).locator("ol li span.bg-acento-suave").first();
  const [color, fondo] = await icono.evaluate((el) => [getComputedStyle(el).color, getComputedStyle(el).backgroundColor]);
  expect(color).not.toBe(fondo);
  // `rgb(47, 58, 7)` o, para un color-mix, `color(srgb 0.94 0.98 0.86)`: canales en 0-255 o en 0-1.
  const luz = (c: string) => {
    const escala = c.startsWith("color(") ? 1 : 255;
    const [r, g, b] = (c.match(/[\d.]+/g) ?? []).slice(0, 3).map(Number).map((v) => v / escala).map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  };
  const [a, b] = [luz(color), luz(fondo)].sort((x, y) => y - x);
  expect((a + 0.05) / (b + 0.05)).toBeGreaterThanOrEqual(3);
});
