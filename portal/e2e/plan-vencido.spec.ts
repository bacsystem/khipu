import { expect, test } from "@playwright/test";

/**
 * #285: una cuenta con el plan vencido (pasados sus días de gracia) no emite, aunque le sobre tope. «Nuevo comprobante» lo dice antes de que se llene nada, y
 * aunque se pida por la API directamente, el backend responde 402 PLAN_VENCIDO sin gastar número.
 */
test("con el plan vencido, «Nuevo comprobante» lo dice y la emisión se rechaza con 402", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("vencido@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);

  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByText("Tu plan venció")).toBeVisible();
  await expect(dialogo.getByText(/Tu plan Emprende venció y terminaron sus días de gracia/)).toBeVisible();
  await expect(dialogo.getByLabel("Serie")).toHaveCount(0);

  const respuesta = await page.evaluate(async () => {
    const r = await fetch("/api/proxy/facturas", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ serie: "F001", fecha_emision: new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" }), moneda: "PEN", tipo_operacion: "0101",
        cliente: { tipo_doc: "6", num_doc: "20601234565", razon_social: "CLIENTE SAC" },
        items: [{ descripcion: "Consultoría", unidad: "ZZ", cantidad: 1, precio_unitario: 100, tipo_afectacion_igv: "10" }] }),
    });
    return { status: r.status, cuerpo: await r.json() };
  });
  expect(respuesta.status).toBe(402);
  expect(respuesta.cuerpo.codigo).toBe("PLAN_VENCIDO");

  const series = await page.evaluate(async () => (await (await fetch("/api/proxy/series")).json()).datos);
  expect(series.find((s: { serie: string }) => s.serie === "F001").ultimo_numero).toBe(4);

  await dialogo.getByRole("link", { name: "Ver plan y consumo" }).click();
  await expect(page).toHaveURL(/\/cuenta\/plan$/);
  await expect(page.getByText("Vencido", { exact: true }).first()).toBeVisible();
});
