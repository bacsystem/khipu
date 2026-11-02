import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/inicio/);
  await page.goto("/series");
});

/** #531: el menú del tipo salía con «20», «31» y «40» antes de «Todos» (las opciones venían de un objeto, y JavaScript ordena primero las claves enteras). */
test("el filtro de tipo lista «Todos» primero y los tipos en orden de código", async ({ page }) => {
  await page.getByRole("combobox").filter({ hasText: "Tipo: Todos" }).click();
  const opciones = (await page.getByRole("option").allTextContents()).map((o) => o.trim());
  expect(opciones[0]).toBe("Tipo: Todos");
  const codigos = opciones.slice(1).map((o) => o.split(" ")[0]);
  expect(codigos).toEqual([...codigos].sort());
});
