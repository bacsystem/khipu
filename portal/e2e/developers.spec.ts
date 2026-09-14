import { expect, test } from "@playwright/test";

test("la referencia de API carga sin sesión y muestra un endpoint real", async ({ page }) => {
  const errores: string[] = [];
  page.on("console", (msg) => {
    if (msg.type() === "error") errores.push(msg.text());
  });

  await page.goto("/developers");

  await expect(page.getByText("/v1/facturas").first()).toBeVisible({ timeout: 10_000 });
  expect(errores).toEqual([]);
});
