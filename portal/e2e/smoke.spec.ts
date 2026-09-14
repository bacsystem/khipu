import { expect, test } from "@playwright/test";

test("la página raíz responde", async ({ page }) => {
  const response = await page.goto("/");
  expect(response?.ok()).toBeTruthy();
});
