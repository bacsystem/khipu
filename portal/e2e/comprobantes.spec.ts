import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
});

test("lista comprobantes con su estado y permite ver el detalle", async ({ page }) => {
  const tabla = page.locator("table");
  await expect(page.getByText("F001-00000001")).toBeVisible();
  // Un aceptado con CDR se etiqueta así en la tabla; el filtro de estado tiene un ítem "Aceptado" a secas.
  await expect(tabla.getByText("Aceptado con CDR", { exact: true })).toBeVisible();
  // El segundo comprobante lo muta el test de reenvío (corre en paralelo): se comprueba la fila, no su estado.
  await expect(tabla.getByText("F001-00000002")).toBeVisible();

  await page.getByRole("link", { name: /Ver comprobante F001-00000001/ }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-aceptada/);
  // Forma de pago al crédito (RS 193-2020): neto pendiente y calendario de cuotas en la liquidación.
  const formaPago = page.getByTestId("forma-pago");
  await expect(formaPago.getByText("Crédito")).toBeVisible();
  await expect(formaPago.getByText("Cuota001")).toBeVisible();
  await expect(formaPago.getByText("Cuota002")).toBeVisible();
  // Detracción (SPOT): bien/servicio del catálogo 54, porcentaje y cuenta del Banco de la Nación.
  const detraccion = page.getByTestId("detraccion");
  await expect(detraccion.getByText("Otros servicios empresariales")).toBeVisible();
  await expect(detraccion.getByText("00-000-123456")).toBeVisible();
  // Anticipo regularizado: la factura de anticipo y el importe pagado que se resta del total.
  const anticipos = page.getByTestId("anticipos");
  await expect(anticipos.getByText("F001-90")).toBeVisible();
  await expect(anticipos.getByText(/gravado \(04\)/)).toBeVisible();
  await expect(page.getByText(/^Vence 1 Nov 2026/)).toBeVisible();
  // Documentos relacionados: orden de compra y guía de remisión con su tipo del catálogo 01.
  const referencias = page.getByTestId("referencias");
  await expect(referencias.getByText("OC-2026-0457")).toBeVisible();
  await expect(referencias.getByText(/Guía de remisión remitente/)).toBeVisible();
  // Cargo global sin IGV (46, recargo al consumo): aparece como "Otros cargos" y suma al total a pagar.
  await expect(page.getByText(/Otros cargos sin IGV \(46\)/)).toBeVisible();
  // El detalle abre el XML firmado en una vista previa con su botón de descarga.
  await page.getByRole("button", { name: "Ver XML" }).click();
  await expect(page.getByText("Descargar XML")).toBeVisible();
});

test("filtra por serie y fechas desde la URL y desde los controles (#6)", async ({ page }) => {
  const tabla = page.locator("table");
  // Con los filtros en la URL la tabla llega ya filtrada desde el servidor: solo los emitidos el 2 de setiembre.
  await page.goto("/comprobantes?desde=2026-09-02&hasta=2026-09-02&serie=F001");
  await expect(tabla.getByText("F001-00000004")).toBeVisible();
  await expect(tabla.getByText("F001-00000001")).toHaveCount(0);
  await expect(page.getByLabel("Serie")).toContainText("Serie: F001");
  await expect(page.getByLabel("Desde")).toHaveValue("2026-09-02");

  // Cambiar el filtro por los controles actualiza la URL (compartible) y la lista.
  await page.getByLabel("Hasta").fill("2026-09-01");
  await expect(page).toHaveURL(/hasta=2026-09-01/);
  await page.getByLabel("Desde").fill("2026-09-01");
  await expect(page).toHaveURL(/desde=2026-09-01/);
  await expect(tabla.getByText("F001-00000001")).toBeVisible();
  await expect(tabla.getByText("F001-00000004")).toHaveCount(0);

  await page.getByLabel("Serie").click();
  await page.getByRole("option", { name: "Serie: FC01" }).click();
  await expect(page).toHaveURL(/serie=FC01/);
  await expect(page.getByText("No hay comprobantes con esos filtros.")).toBeVisible();
  await page.getByRole("link", { name: "Quitar filtros" }).click();
  await expect(page).toHaveURL(/\/comprobantes$/);
  await expect(tabla.getByText("F001-00000001")).toBeVisible();
});

test("el detalle muestra el historial de intentos en hora de Lima (#7)", async ({ page }) => {
  await page.goto("/comprobantes/f-error");
  const historial = page.getByTestId("historial");
  await expect(historial.getByText("Historial")).toBeVisible();
  const filas = historial.locator("li");
  await expect(filas).toHaveCount(4);
  // 15:00:01Z es 10:00 en Lima; primero el más antiguo.
  await expect(filas.nth(0)).toContainText("2 Set 2026, 10:00");
  await expect(filas.nth(1)).toContainText("Error de envío");
  await expect(filas.nth(1)).toContainText("SUNAT no disponible (timeout)");
  await expect(filas.nth(2)).toContainText("Enviado a SUNAT (intento 2)");
  await expect(filas.nth(3)).toContainText("SUNAT no respondió a tiempo");
  // Un comprobante sin historial (backend anterior o sin eventos) no rompe la página.
  await page.goto("/comprobantes/f-aceptada");
  await expect(page.getByTestId("historial").getByText("Sin historial.")).toBeVisible();
});

test("reenvía un comprobante en error y queda aceptado", async ({ page }) => {
  await page.goto("/comprobantes/f-error");
  await expect(page.getByText("Error de envío").first()).toBeVisible();

  await page.getByRole("button", { name: "Reenviar", exact: true }).click();

  await expect(page.getByText("Aceptado", { exact: true }).first()).toBeVisible();
});

test("emite una nota de crédito parcial desde la factura y la factura la lista", async ({ page }) => {
  await page.goto("/comprobantes/f-aceptada");
  await page.getByTestId("emitir-nota").click();
  await expect(page).toHaveURL(/\/comprobantes\/f-aceptada\/nota/);
  const form = page.getByTestId("nota-form");
  await expect(form.getByLabel("Serie")).toHaveValue("FC01");
  // Nota parcial (07): la tabla de ítems de la factura con la cantidad a devolver.
  await form.getByLabel(/Motivo/).selectOption("07");
  await expect(form.getByRole("table")).toBeVisible();
  await form.getByLabel("Sustento").fill("Devolución parcial del servicio");
  // Las cantidades arrancan en 0 (antes venían al 100 % de la factura, y sobre esta —con anticipos— el 100 % de
  // los ítems supera el total): el usuario elige qué acredita, y el formulario muestra el importe contra su tope.
  await expect(form.getByRole("button", { name: "Emitir nota de crédito" })).toBeDisabled();
  // 0.1 × 141.60 = 14.16. Cantidades chicas a propósito: los tests de este archivo corren en paralelo sobre la
  // misma factura del mock y cada NC emitida baja el tope de las siguientes; la suma de todas queda lejos de 123.
  await form.getByLabel(/Cantidad de .* en la nota/).fill("0.1");
  await expect(form.getByTestId("nota-importe")).toContainText("Importe de la nota: S/ 14.16");
  await form.getByRole("button", { name: "Emitir nota de crédito" }).click();

  // Aterriza en el detalle de la nota con el bloque "Nota de crédito sobre" y vuelve a la factura, que ya la lista.
  await expect(page).toHaveURL(/\/comprobantes\/n-/);
  const nota = page.getByTestId("nota");
  await expect(nota.getByText("F001-1")).toBeVisible();
  await expect(nota.getByText("Devolución por ítem")).toBeVisible();
  await expect(page.getByText("Nota de crédito electrónica")).toBeVisible();
  await page.goto("/comprobantes/f-aceptada");
  const notas = page.getByTestId("notas");
  await expect(notas.getByText("FC01-1")).toBeVisible();
  await expect(notas.getByText("Devolución por ítem")).toBeVisible();
});

test("una nota de crédito 13 sale sin importe y una nota de débito con su concepto", async ({ page }) => {
  await page.goto("/comprobantes/f-aceptada/nota");
  const form = page.getByTestId("nota-form");
  await form.getByLabel(/Motivo/).selectOption("13");
  await expect(form.getByText(/importe 0/)).toBeVisible();
  await form.getByLabel("Sustento").fill("Reprogramación de cuotas");
  await form.getByRole("button", { name: "Emitir nota de crédito" }).click();
  await expect(page.getByTestId("nota").getByText(/Corrección o modificación/)).toBeVisible();

  await page.goto("/comprobantes/f-aceptada/nota");
  await form.getByLabel("Tipo de nota").selectOption("08");
  await expect(form.getByLabel("Serie")).toHaveValue("FD01");
  await form.getByLabel(/Motivo/).selectOption("01");
  await form.getByLabel("Sustento").fill("Intereses por mora de 30 días");
  await form.getByLabel("Concepto").fill("Intereses por mora");
  await form.getByLabel(/Importe con IGV/).fill("59");
  await form.getByRole("button", { name: "Emitir nota de débito" }).click();
  await expect(page.getByText("Nota de débito electrónica")).toBeVisible();
  await expect(page.getByTestId("nota").getByText(/Intereses por mora de 30 días/)).toBeVisible();
});

/** Nota parcial lista para emitir, con el contador de POSTs a `/api/proxy/notas` armado. */
async function notaLista(page: import("@playwright/test").Page) {
  await page.goto("/comprobantes/f-aceptada/nota");
  const form = page.getByTestId("nota-form");
  await form.getByLabel(/Motivo/).selectOption("07");
  await form.getByLabel("Sustento").fill("Devolución parcial");
  await form.getByLabel(/Cantidad de .* en la nota/).fill("0.1");
  await expect(form.getByRole("button", { name: "Emitir nota de crédito" })).toBeEnabled();
  const posts: string[] = [];
  page.on("request", (r) => {
    if (r.method() === "POST" && r.url().includes("/api/proxy/notas")) posts.push(r.url());
  });
  return { form, posts };
}

test("nota: Enter en cualquier control no emite; solo el botón", async ({ page }) => {
  const { form, posts } = await notaLista(page);
  // Cinco controles, cinco notas reales en la auditoría. Los `<select>` incluidos: Chromium emite desde uno cerrado.
  for (const campo of ["Tipo de nota", "Serie", /Motivo/, "Sustento", /Cantidad de .* en la nota/]) {
    await form.getByLabel(campo).focus();
    await page.waitForTimeout(120);
    await page.keyboard.press("Enter");
  }
  await page.waitForTimeout(400);
  expect(posts).toEqual([]);
  await expect(page).toHaveURL(/\/nota$/);

  await form.getByRole("button", { name: "Emitir nota de crédito" }).focus();
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/\/comprobantes\/n-/);
  expect(posts).toHaveLength(1);
});

test("nota: si la red se corta, avisa y no deja el botón muerto", async ({ page }) => {
  const { form } = await notaLista(page);
  await page.route("**/api/proxy/notas", (r) => r.abort("connectionreset"));
  await form.getByRole("button", { name: "Emitir nota de crédito" }).click();
  const alerta = form.getByRole("alert");
  await expect(alerta).toContainText("Se cortó la conexión");
  await expect(alerta).toContainText("pudo haberse emitido");
  await expect(form.getByRole("button", { name: "Emitir nota de crédito" })).toBeEnabled();
});

test("nota: un segundo clic tras el éxito no emite otra", async ({ page }) => {
  const { form, posts } = await notaLista(page);
  // Respuesta lenta: antes el botón se rehabilitaba en cuanto llegaba y, mientras se navegaba, un re-clic emitía
  // otra nota (medido: 3 notas con 3 clics). Ahora queda deshabilitado hasta desmontarse con la navegación.
  await page.route("**/api/proxy/notas", async (r) => {
    await new Promise((f) => setTimeout(f, 500));
    await r.continue();
  });
  // La ventana real incluye la navegación al detalle (RSC + SUNAT en producción). Con el mock, el detalle carga en
  // milisegundos y el formulario se desmonta antes de que llegue el re-clic: se retrasa también esa carga para
  // que el segundo clic caiga con el formulario todavía montado, que es donde el defecto emitía la nota duplicada.
  await page.route("**/comprobantes/n-*", async (r) => {
    await new Promise((f) => setTimeout(f, 1500));
    await r.continue();
  });
  // Por `type=submit` y no por nombre: al enviar, el texto pasa a «Emitiendo y enviando a SUNAT…».
  const boton = form.locator("button[type=submit]");
  await boton.click();
  await expect(boton).toBeDisabled();
  await page.waitForTimeout(800);
  // Ya respondió el POST y la navegación está en vuelo: acá el botón tiene que seguir deshabilitado.
  await expect(boton).toBeDisabled();
  await boton.click({ force: true }).catch(() => {});
  await boton.click({ force: true }).catch(() => {});
  await expect(page).toHaveURL(/\/comprobantes\/n-/, { timeout: 10_000 });
  expect(posts).toHaveLength(1);
});

test("nota parcial: muestra el importe contra el tope y bloquea si lo supera (3286)", async ({ page }) => {
  await page.goto("/comprobantes/f-aceptada/nota");
  const form = page.getByTestId("nota-form");
  // 07 y no 09: el catálogo 09 del mock solo trae 01/07/13. Para el tope da igual: cualquier motivo parcial.
  await form.getByLabel(/Motivo/).selectOption("07");
  await form.getByLabel("Sustento").fill("Devolución por ítem");
  // f-aceptada: un ítem de 141.60 con anticipo regularizado → total 123.00. El 100 % del ítem supera el tope.
  await form.getByLabel(/Cantidad de .* en la nota/).fill("1");
  const importe = form.getByTestId("nota-importe");
  await expect(importe).toContainText("Importe de la nota: S/ 141.60");
  // El tope exacto depende de cuántas NC emitieron en paralelo los otros tests sobre esta factura; lo que es
  // invariante es que 141.60 lo supera (el máximo posible es 123.00) y que 14.16 no.
  await expect(importe).toContainText("Tope: S/");
  await expect(importe.getByRole("alert")).toContainText("Supera el tope");
  await expect(form.getByRole("button", { name: "Emitir nota de crédito" })).toBeDisabled();
  await form.getByLabel(/Cantidad de .* en la nota/).fill("0.1");
  await expect(importe).toContainText("Importe de la nota: S/ 14.16");
  await expect(importe.getByRole("alert")).toHaveCount(0);
  await expect(form.getByRole("button", { name: "Emitir nota de crédito" })).toBeEnabled();
});

test("nota parcial: las cantidades arrancan en 0 aunque el 100 % quepa en el tope", async ({ page }) => {
  // f-obs: sin anticipos ni descuentos, total 118.00 = el ítem completo. Sobre f-aceptada este caso no discrimina
  // porque el 100 % supera el tope y el botón queda deshabilitado por eso; acá solo lo deshabilita el 0 inicial.
  await page.goto("/comprobantes/f-obs/nota");
  const form = page.getByTestId("nota-form");
  await form.getByLabel(/Motivo/).selectOption("07");
  await form.getByLabel("Sustento").fill("Devolución por ítem");
  await expect(form.getByTestId("nota-importe")).toContainText("Importe de la nota: S/ 0.00");
  await expect(form.getByRole("button", { name: "Emitir nota de crédito" })).toBeDisabled();
  await form.getByLabel(/Cantidad de .* en la nota/).fill("1");
  await expect(form.getByTestId("nota-importe")).toContainText("Importe de la nota: S/ 118.00");
  await expect(form.getByRole("button", { name: "Emitir nota de crédito" })).toBeEnabled();
});

test("da de baja una factura aceptada tras confirmar el motivo y queda anulada", async ({ page }) => {
  await page.goto("/comprobantes/f-obs");
  await page.getByTestId("dar-de-baja").click();
  const confirmacion = page.getByTestId("baja-confirmacion");
  // Sin motivo (mínimo 3 caracteres) no se puede confirmar: la baja es irreversible ante SUNAT.
  await expect(confirmacion.getByRole("button", { name: "Confirmar la baja" })).toBeDisabled();
  await confirmacion.getByLabel(/Motivo/).fill("Error en el RUC del cliente");
  await confirmacion.getByRole("button", { name: "Confirmar la baja" }).click();

  await expect(page.getByTestId("baja")).toContainText("Aceptada: comprobante anulado");
  await expect(page.getByTestId("baja")).toContainText("Error en el RUC del cliente");
  await expect(page.getByText("Anulado", { exact: true }).first()).toBeVisible();
  await expect(page.getByTestId("dar-de-baja")).toHaveCount(0);
});

test("el PDF se abre desde el detalle y el comprobante aceptado se envía por correo al cliente", async ({ page }) => {
  await page.goto("/comprobantes/f-aceptada");
  const pdf = page.getByTestId("ver-pdf");
  await expect(pdf).toHaveAttribute("href", "/api/proxy/facturas/f-aceptada/pdf");
  const res = await page.request.get("/api/proxy/facturas/f-aceptada/pdf");
  expect(res.headers()["content-type"]).toContain("application/pdf");
  expect((await res.body()).subarray(0, 5).toString()).toBe("%PDF-");

  await page.getByTestId("enviar-correo").click();
  const form = page.getByTestId("correo-form");
  await form.getByLabel("Correo del cliente").fill("compras@cliente.pe");
  await form.getByLabel(/Mensaje/).fill("Gracias por su compra.");
  await form.getByRole("button", { name: "Enviar", exact: true }).click();
  await expect(page.getByTestId("correo-enviado")).toHaveText("Enviado a compras@cliente.pe");
});

test("un comprobante firmado sin respuesta de SUNAT no ofrece envío por correo pero sí su PDF", async ({ page }) => {
  await page.goto("/comprobantes/f-firmada");
  await expect(page.getByTestId("ver-pdf")).toBeVisible();
  await expect(page.getByTestId("enviar-correo")).toHaveCount(0);
});
