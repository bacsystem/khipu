import { expect, test } from "@playwright/test";
import { registrar, verificarCorreo } from "./registro-sesion";

/** #22: el registro exige verificar el correo antes de crear la empresa o emitir. */

test("recién registrado, el onboarding pide revisar el correo en vez de mostrar el formulario de la empresa", async ({ page }) => {
  const email = `sinverificar${Date.now()}@ejemplo.pe`;
  await registrar(page, { email });

  await expect(page.getByTestId("revisa-tu-correo")).toContainText(email);
  await expect(page.getByLabel("RUC")).toHaveCount(0);
});

test("sin verificar, crear la empresa por HTTP directo también se rechaza", async ({ page }) => {
  await registrar(page, { email: `directo${Date.now()}@ejemplo.pe` });

  const estado = await page.evaluate(async () => {
    const res = await fetch("/api/proxy/empresas", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ ruc: "20100066603", razon_social: "Directo SAC", entorno: "BETA" }) });
    return [res.status, (await res.json()).codigo];
  });
  expect(estado).toEqual([403, "CORREO_SIN_VERIFICAR"]);
});

test("se puede pedir otro enlace", async ({ page }) => {
  await registrar(page, { email: `reenvio${Date.now()}@ejemplo.pe` });

  await page.getByRole("button", { name: "Enviarme otro enlace" }).click();

  await expect(page.getByText(/Te enviamos otro enlace/)).toBeVisible();
});

test("con el enlace del correo queda verificado y el onboarding muestra el formulario", async ({ page }) => {
  const email = `verifica${Date.now()}@ejemplo.pe`;
  await registrar(page, { email });

  await verificarCorreo(page, email);

  await expect(page.getByLabel("RUC")).toBeVisible();
  await expect(page.getByTestId("revisa-tu-correo")).toHaveCount(0);
});

test("el enlace sirve una sola vez", async ({ page }) => {
  const email = `unavez${Date.now()}@ejemplo.pe`;
  await registrar(page, { email });
  await verificarCorreo(page, email);

  await page.goto(`/verificar/verif-${email}`);
  await page.getByRole("button", { name: "Verificar mi correo" }).click();

  await expect(page.getByRole("alert").filter({ hasText: "inválido, venció o ya se usó" })).toBeVisible();
});

/** El enlace se puede abrir en el teléfono: «Ya lo verifiqué» vuelve a leer el estado sin tener que salir y entrar. */
test("verificado en otra pestaña, «Ya lo verifiqué» muestra el formulario", async ({ page, context }) => {
  const email = `otrapestana${Date.now()}@ejemplo.pe`;
  await registrar(page, { email });

  const otra = await context.newPage();
  await otra.goto(`/verificar/verif-${email}`);
  await otra.getByRole("button", { name: "Verificar mi correo" }).click();
  await expect(otra.getByText("Tu correo quedó verificado")).toBeVisible();

  await page.getByRole("button", { name: "Ya lo verifiqué" }).click();
  await expect(page.getByLabel("RUC")).toBeVisible();
});
