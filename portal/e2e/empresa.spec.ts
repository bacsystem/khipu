import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
  await page.goto("/empresa");
});

/** #276: una pestaña por tema; la pestaña va en la URL para que los enlaces abran la que corresponde y recargar no la pierda. */
test("las pestañas cambian la sección y la URL; recargar y los enlaces directos abren la pedida", async ({ page }) => {
  await page.getByRole("tab", { name: /Credenciales SOL/ }).click();
  await expect(page).toHaveURL(/\/empresa\?seccion=sol$/);
  await expect(page.getByLabel("Usuario SOL secundario")).toBeVisible();
  await expect(page.getByTestId("datos-fiscales")).toHaveCount(0);

  await page.reload();
  await expect(page.getByRole("tab", { name: /Credenciales SOL/ })).toHaveAttribute("aria-selected", "true");

  await page.goto("/empresa?seccion=pdf");
  await expect(page.getByTestId("personalizacion-pdf")).toBeVisible();
  await expect(page.getByRole("tab", { name: "PDF" })).toHaveAttribute("aria-selected", "true");

  await page.goto("/empresa?seccion=inventada");
  await expect(page.getByRole("tab", { selected: true })).toBeVisible();
});

test("guarda el domicilio fiscal eligiendo el ubigeo en cascada y la cuenta de detracciones", async ({ page }) => {
  await page.getByRole("tab", { name: "Datos fiscales" }).click();
  const form = page.getByTestId("datos-fiscales");
  // El catálogo 13 se carga al abrir; departamento → provincia → distrito.
  await expect(form.getByLabel("Departamento")).toBeEnabled();
  await form.getByLabel("Departamento").selectOption("LIMA");
  await form.getByLabel("Provincia").selectOption("LIMA");
  await form.getByLabel("Distrito").selectOption({ label: "MIRAFLORES" });
  await expect(form.getByText("Ubigeo 150122 (catálogo 13)")).toBeVisible();
  await form.getByLabel("Dirección").fill("Av. Larco 345 Of. 12");
  await form.getByLabel(/Cuenta de detracciones/).fill("00-000-123456");
  await form.getByLabel(/Nombre comercial/).fill("Andina Store");
  await form.getByRole("button", { name: "Guardar datos fiscales" }).click();

  await expect(form.getByText("Datos fiscales actualizados", { exact: false })).toBeVisible();
  await expect(page.getByTestId("domicilio-actual")).toContainText("Av. Larco 345 Of. 12 · MIRAFLORES, LIMA, LIMA (150122)");
  await expect(page.getByText("Cód. local domicilio: 0000")).toBeVisible();
  // El nombre comercial guardado vuelve al formulario tras el refresh.
  await expect(page.getByLabel(/Nombre comercial/)).toHaveValue("Andina Store");
});

test("personaliza el PDF: elige plantilla, ve la vista previa con esa plantilla y guarda", async ({ page }) => {
  await page.goto("/empresa?seccion=pdf");
  const panel = page.getByTestId("personalizacion-pdf");
  await expect(panel.getByTestId("plantilla-clasico")).toHaveAttribute("aria-checked", "true");

  await panel.getByTestId("plantilla-corporativo").click();
  await expect(panel.getByTestId("plantilla-corporativo")).toHaveAttribute("aria-checked", "true");
  await panel.getByLabel("Color primario en hexadecimal").fill("#C8552B");
  await panel.getByLabel("Pie de página").fill("Gracias por su preferencia");
  // Los textos llegan a la vista previa con retardo (un PDF por cambio, no por tecla).
  await expect(panel.getByTestId("vista-previa")).toHaveAttribute("src", /plantilla=corporativo.*color_primario=%23C8552B.*pie_de_pagina=Gracias\+por\+su\+preferencia/);
  // La vista previa es el PDF real del backend con esos parámetros (aquí el mock los refleja).
  const res = await page.request.get("/api/proxy/empresa/personalizacion-pdf/vista-previa?plantilla=corporativo&color_primario=%23C8552B");
  expect(res.headers()["content-type"]).toContain("application/pdf");
  expect((await res.text())).toContain("plantilla=corporativo color=#C8552B");

  await panel.getByRole("button", { name: "Guardar diseño" }).click();
  await expect(panel.getByTestId("diseno-guardado")).toBeVisible();
  await page.reload();
  await expect(page.getByTestId("plantilla-corporativo")).toHaveAttribute("aria-checked", "true");
  await expect(page.getByLabel("Pie de página")).toHaveValue("Gracias por su preferencia");
});

test("sube y quita el logo del PDF", async ({ page }) => {
  await page.goto("/empresa?seccion=pdf");
  const panel = page.getByTestId("personalizacion-pdf");
  await expect(panel.getByTestId("logo-actual")).toHaveCount(0);

  await panel.locator('input[type="file"]').setInputFiles({ name: "logo.png", mimeType: "image/png", buffer: Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0]) });
  await expect(panel.getByTestId("logo-actual")).toBeVisible();
  await expect(panel.getByTestId("vista-previa")).toHaveAttribute("src", /v=1/);

  await panel.locator('input[type="file"]').setInputFiles({ name: "logo.svg", mimeType: "image/svg+xml", buffer: Buffer.from("<svg/>") });
  await expect(panel.getByRole("alert")).toContainText("PNG o JPEG");

  await panel.getByTitle("Quitar logo").click();
  await expect(panel.getByTestId("logo-actual")).toHaveCount(0);
});
