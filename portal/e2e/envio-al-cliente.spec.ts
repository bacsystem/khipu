import { expect, test } from "@playwright/test";

// La preferencia es de la empresa del mock, compartida: la segunda prueba no puede correr mientras la primera la tiene activada.
test.describe.configure({ mode: "serial" });

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
});

/**
 * #325: la empresa activa «enviar automáticamente» en su configuración, emite una factura con el correo del cliente y la ficha del comprobante dice que salió a ese correo; el
 * envío a mano propone el mismo correo. Al final se deja como estaba: las demás specs comparten la empresa del mock.
 */
test("con el envío automático activado, la factura emitida con correo llega al cliente y la ficha lo dice", async ({ page }) => {
  // Recorre configuración, emisión y ficha: tres rutas que el servidor de desarrollo compila la primera vez.
  test.slow();
  await page.goto("/empresa?seccion=correo");
  await expect(page.getByRole("tab", { name: "Envío al cliente" })).toHaveAttribute("aria-selected", "true");
  const form = page.getByTestId("form-envio-al-cliente");
  const casilla = form.getByRole("checkbox", { name: /Enviar automáticamente el comprobante al cliente/ });
  await expect(casilla).not.toBeChecked();
  await casilla.check();
  await form.getByRole("button", { name: "Guardar preferencia" }).click();
  await expect(form.getByText("Envío automático activado.")).toBeVisible();
  await expect(page.getByTestId("seccion-envio-al-cliente").getByText("Activado", { exact: true })).toBeVisible();

  try {
    await page.goto("/comprobantes");
    await page.getByRole("button", { name: "Nuevo comprobante" }).click();
    const dialogo = page.getByRole("dialog");
    await dialogo.getByLabel("RUC").fill("20554198211");
    await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");
    await dialogo.getByLabel(/Correo del cliente/).fill("Compras@Andina.pe");
    await dialogo.getByLabel("Descripción").fill("Consultoría");
    await dialogo.getByLabel(/Precio unit\./).fill("1180");
    await dialogo.getByRole("button", { name: "Emitir factura" }).click();

    await expect(page).toHaveURL(/\/comprobantes\/f-/);
    await expect(page.getByTestId("receptor-email")).toHaveText("compras@andina.pe");
    const envio = page.getByTestId("envio-al-cliente");
    await expect(envio).toHaveAttribute("data-estado", "ENVIADO");
    await expect(envio).toContainText("Enviado al cliente");
    await expect(envio).toContainText("compras@andina.pe");

    await page.getByTestId("enviar-correo").click();
    await expect(page.getByLabel("Correo del cliente")).toHaveValue("compras@andina.pe");
  } finally {
    await page.goto("/empresa?seccion=correo");
    const casillaFinal = page.getByTestId("form-envio-al-cliente").getByRole("checkbox", { name: /Enviar automáticamente/ });
    await casillaFinal.uncheck();
    await page.getByTestId("form-envio-al-cliente").getByRole("button", { name: "Guardar preferencia" }).click();
    await expect(page.getByText("Envío automático desactivado.")).toBeVisible();
  }
});

test("sin el envío automático, la factura con correo no muestra un envío y el correo mal escrito no deja emitir", async ({ page }) => {
  test.slow();
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");
  await dialogo.getByLabel("Descripción").fill("Consultoría");
  await dialogo.getByLabel(/Precio unit\./).fill("1180");

  // El navegador frena un correo sin forma de correo antes de mandar nada.
  await dialogo.getByLabel(/Correo del cliente/).fill("compras-sin-arroba");
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(dialogo.getByLabel(/Correo del cliente/)).toHaveJSProperty("validity.valid", false);
  await expect(page).not.toHaveURL(/\/comprobantes\/f-/);

  await dialogo.getByLabel(/Correo del cliente/).fill("compras@andina.pe");
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  await expect(page.getByTestId("receptor-email")).toHaveText("compras@andina.pe");
  await expect(page.getByTestId("envio-al-cliente")).toHaveCount(0);
});
