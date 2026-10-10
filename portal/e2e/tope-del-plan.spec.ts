import { expect, test } from "@playwright/test";

/**
 * #18: una cuenta que ya usó los 30 documentos del mes de su plan Gratis no emite más. «Nuevo comprobante» lo dice antes de que se llene nada, y aunque se
 * pida por la API directamente, el backend responde 429 LIMITE_PLAN sin gastar número.
 */
test("en el tope del plan, «Nuevo comprobante» lo dice y la emisión se rechaza con 429", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("tope@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);

  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByText("Llegaste al tope de documentos del mes")).toBeVisible();
  await expect(dialogo.getByText(/Tu plan Gratis permite 30 documentos al mes y ya los usaste/)).toBeVisible();
  await expect(dialogo.getByLabel("Serie")).toHaveCount(0);

  const respuesta = await page.evaluate(async () => {
    const r = await fetch("/api/proxy/facturas", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ serie: "F001", fecha_emision: "2026-10-09", moneda: "PEN", tipo_operacion: "0101",
        cliente: { tipo_doc: "6", num_doc: "20601234565", razon_social: "CLIENTE SAC" },
        items: [{ descripcion: "Consultoría", unidad: "ZZ", cantidad: 1, precio_unitario: 100, tipo_afectacion_igv: "10" }] }),
    });
    return { status: r.status, cuerpo: await r.json() };
  });
  expect(respuesta.status).toBe(429);
  expect(respuesta.cuerpo.codigo).toBe("LIMITE_PLAN");

  await dialogo.getByRole("link", { name: "Ver plan y consumo" }).click();
  await expect(page).toHaveURL(/\/cuenta\/plan$/);
  await expect(page.getByTestId("consumo-del-mes")).toHaveText("30 / 30");
});
