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
  // Se afirma sobre lo que el portal ENVÍA, no solo sobre la pantalla que el mock devuelve: antes una nota con
  // fecha 2020, sin ítems o por 10× la factura pasaba igual.
  const peticion = page.waitForRequest((r) => r.method() === "POST" && r.url().includes("/api/proxy/notas"));
  await form.getByRole("button", { name: "Emitir nota de crédito" }).click();
  const cuerpo = (await peticion).postDataJSON();
  expect(cuerpo).toMatchObject({ tipo: "07", serie: "FC01", motivo: "07", documento_afectado: { serie: "F001", numero: 1 } });
  expect(cuerpo.fecha_emision).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  expect(cuerpo.fecha_emision >= "2026-09-01").toBe(true);
  expect(cuerpo.items).toHaveLength(1);
  expect(cuerpo.items[0]).toMatchObject({ cantidad: 0.1, precio_unitario: 141.6, tipo_afectacion_igv: "10" });
  expect(cuerpo.forma_pago).toBeUndefined();

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
  // La NC 13 sin `forma_pago` es un 422 (3257) en el backend real; antes el mock la sintetizaba y este test pasaba
  // aunque el portal no la enviara.
  const peticion13 = page.waitForRequest((r) => r.method() === "POST" && r.url().includes("/api/proxy/notas"));
  await form.getByRole("button", { name: "Emitir nota de crédito" }).click();
  const cuerpo13 = (await peticion13).postDataJSON();
  expect(cuerpo13.forma_pago).toMatchObject({ tipo: "credito", monto_pendiente: 123 });
  expect(cuerpo13.forma_pago.cuotas).toHaveLength(2);
  expect(cuerpo13.items).toBeUndefined();
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

test("nota: los motivos que no aplican a la factura no se ofrecen (11 exportación, 12 IVAP, 13 solo al crédito)", async ({ page }) => {
  // Con el catálogo completo, «11» o «12» sobre una factura interna salían numeradas y SUNAT las rechazaba
  // (2642/3107) con el correlativo consumido; «13» sobre una factura al contado viola 3260.
  await page.goto("/comprobantes/f-obs/nota"); // interna, al contado
  const form = page.getByTestId("nota-form");
  const codigos = async () => (await form.getByLabel(/Motivo/).locator("option").allTextContents()).map((t) => t.slice(0, 2)).filter((c) => /^\d\d$/.test(c));
  await expect(form.getByLabel(/Motivo/)).toBeEnabled();
  expect(await codigos()).toEqual(["01", "07"]);
  await form.getByLabel("Tipo de nota").selectOption("08");
  expect(await codigos()).toEqual(["01", "13"]); // el 13 de la ND (penalidades) no depende de la forma de pago

  await page.goto("/comprobantes/f-aceptada/nota"); // interna, al crédito
  await expect(form.getByLabel(/Motivo/)).toBeEnabled();
  expect(await codigos()).toEqual(["01", "07", "13"]);

  await page.goto("/comprobantes/f-export/nota"); // exportación 0200
  await expect(form.getByLabel(/Motivo/)).toBeEnabled();
  expect(await codigos()).toEqual(["01", "07", "11"]);
  // ND 13 sobre exportación no tiene salida: la penalidad va inafecta (3507) y la exportación exige 40 (2642).
  await form.getByLabel("Tipo de nota").selectOption("08");
  expect(await codigos()).toEqual(["01", "11"]);

  await page.goto("/comprobantes/f-ivap/nota"); // IVAP (afectación 17)
  await expect(form.getByLabel(/Motivo/)).toBeEnabled();
  expect(await codigos()).toEqual(["01", "07", "12"]);
  // ND sobre IVAP: la línea sale con 17 y SUNAT exige el motivo 12 (3230); el 13 escapa porque va con 30.
  await form.getByLabel("Tipo de nota").selectOption("08");
  expect(await codigos()).toEqual(["12", "13"]);
});

test("nota de débito 12 sobre una factura IVAP: la línea sale con afectación 17", async ({ page }) => {
  await page.goto("/comprobantes/f-ivap/nota");
  const form = page.getByTestId("nota-form");
  await form.getByLabel("Tipo de nota").selectOption("08");
  await form.getByLabel(/Motivo/).selectOption("12");
  await form.getByLabel("Sustento").fill("Ajuste del precio del arroz");
  await form.getByLabel("Concepto").fill("Diferencia de precio");
  await form.getByLabel(/Importe con IGV/).fill("10.4");
  const peticion = page.waitForRequest((r) => r.method() === "POST" && r.url().includes("/api/proxy/notas"));
  await form.getByRole("button", { name: "Emitir nota de débito" }).click();
  expect((await peticion).postDataJSON().items[0]).toMatchObject({ tipo_afectacion_igv: "17", precio_unitario: 10.4 });
  await expect(page).toHaveURL(/\/comprobantes\/n-/);
});

test("el mock de POST /v1/notas rechaza los ítems que el backend rechaza: 3230, 2642, 2025 y 3286", async ({ page }) => {
  await page.goto("/comprobantes/f-cargos");
  const hoy = new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" });
  const post = (body: Record<string, unknown>) =>
    page.evaluate(async ([b]) => {
      const r = await fetch("/api/proxy/notas", { method: "POST", headers: { "content-type": "application/json" }, body: b as string });
      return { status: r.status, texto: await r.text() };
    }, [JSON.stringify({ fecha_emision: hoy, descripcion: "Prueba de contrato", ...body })]);
  const linea = (afectacion: string, cantidad = 1) => [{ descripcion: "Línea", unidad: "ZZ", cantidad, precio_unitario: 10, tipo_afectacion_igv: afectacion }];

  let r = await post({ tipo: "08", serie: "FD01", documento_afectado: { serie: "F001", numero: 8 }, motivo: "01", items: linea("17") });
  expect(r.status, r.texto).toBe(422);
  expect(r.texto).toContain("3230");

  r = await post({ tipo: "08", serie: "FD01", documento_afectado: { serie: "F001", numero: 5 }, motivo: "13", items: linea("30") });
  expect(r.status, r.texto).toBe(422);
  expect(r.texto).toContain("2642");

  r = await post({ tipo: "07", serie: "FC01", documento_afectado: { serie: "F001", numero: 7 }, motivo: "07", items: linea("10", 0.12345678901) });
  expect(r.status, r.texto).toBe(422);
  expect(r.texto).toContain("2025");

  // 3286: una NC por más que la factura (1353). Este chequeo del mock no tenía test; su mutación sobrevivía.
  r = await post({ tipo: "07", serie: "FC01", documento_afectado: { serie: "F001", numero: 7 }, motivo: "07", items: linea("10", 200) });
  expect(r.status, r.texto).toBe(422);
  expect(r.texto).toContain("3286");
});

test("nota de débito sobre una exportación: la línea sale con afectación 40, no con 10 fijo", async ({ page }) => {
  await page.goto("/comprobantes/f-export/nota");
  const form = page.getByTestId("nota-form");
  await form.getByLabel("Tipo de nota").selectOption("08");
  await form.getByLabel(/Motivo/).selectOption("01");
  await form.getByLabel("Sustento").fill("Intereses por mora de 30 días");
  await form.getByLabel("Concepto").fill("Intereses por mora");
  await form.getByLabel(/Importe con IGV/).fill("59");
  // Con «10» fijo el dominio rechazaba (2642): ninguna ND sobre exportaciones podía salir del portal.
  const peticion = page.waitForRequest((r) => r.method() === "POST" && r.url().includes("/api/proxy/notas"));
  await form.getByRole("button", { name: "Emitir nota de débito" }).click();
  expect((await peticion).postDataJSON().items[0]).toMatchObject({ tipo_afectacion_igv: "40", precio_unitario: 59 });
});

test("NC 13: una cuota en blanco o vencida antes de la factura no deja emitir", async ({ page }) => {
  await page.goto("/comprobantes/f-aceptada/nota"); // al crédito, emitida el 2026-09-01
  const form = page.getByTestId("nota-form");
  await form.getByLabel(/Motivo/).selectOption("13");
  await form.getByLabel("Sustento").fill("Reprogramación de cuotas");
  const boton = form.getByRole("button", { name: "Emitir nota de crédito" });
  await expect(boton).toBeEnabled(); // las cuotas de la factura vienen precargadas y son válidas

  // Antes «listo» solo pedía que hubiera una cuota: una en blanco viajaba como monto 0 y vencimiento vacío.
  await form.getByRole("button", { name: "Añadir cuota" }).click();
  await expect(boton).toBeDisabled();
  const n = await form.getByLabel(/Monto de la cuota/).count();
  await form.getByLabel(`Monto de la cuota ${n}`).fill("10");
  await form.getByLabel(`Vencimiento de la cuota ${n}`).fill("2026-08-01"); // anterior a la factura (3321)
  await expect(boton).toBeDisabled();
  await expect(form.getByLabel(`Vencimiento de la cuota ${n}`)).toHaveAttribute("min", "2026-09-02");
  await form.getByLabel(`Vencimiento de la cuota ${n}`).fill("2026-12-01");
  // Fecha ya válida, pero 61.5 + 61.5 + 10 = 133 supera el total de la factura (123): 3320 sigue bloqueando.
  await expect(boton).toBeDisabled();
  await form.getByLabel("Monto de la cuota 1").fill("51.5");
  await expect(boton).toBeEnabled();
});

test("nota: si los catálogos no cargan por red, avisa y se puede reintentar", async ({ page }) => {
  let caido = true;
  await page.route("**/api/proxy/catalogos/**", (r) => (caido ? r.abort("failed") : r.continue()));
  await page.goto("/comprobantes/f-aceptada/nota");
  const form = page.getByTestId("nota-form");
  // Antes: `fetch` rechazado → el motivo quedaba en «Cargando…» deshabilitado para siempre, sin mensaje.
  await expect(form.getByRole("alert")).toContainText("No se pudieron cargar los motivos");
  await expect(form.getByLabel(/Motivo/)).toBeDisabled();
  caido = false;
  await form.getByRole("button", { name: "Reintentar" }).click();
  await expect(form.getByLabel(/Motivo/)).toBeEnabled();
  await expect(form.getByRole("alert")).toHaveCount(0);
});

test("nota: una factura anulada no ofrece «Emitir nota» y /nota redirige a la ficha (2120)", async ({ page }) => {
  // `admiteNotas` es la única puerta al flujo; la auditoría la mutó a `return true` y 40/40 e2e siguieron verdes,
  // porque el mock no tenía ninguna factura ANULADO.
  await page.goto("/comprobantes/f-anulada");
  await expect(page.getByText("F001-00000006").first()).toBeVisible();
  await expect(page.getByText(/Anulad/i).first()).toBeVisible();
  await expect(page.getByTestId("emitir-nota")).toHaveCount(0);
  await page.goto("/comprobantes/f-anulada/nota");
  await expect(page).toHaveURL(/\/comprobantes\/f-anulada$/);
  await expect(page.getByTestId("nota-form")).toHaveCount(0);
});

test("nota: el sustento se corta a 500 caracteres en el cliente (2135)", async ({ page }) => {
  await page.goto("/comprobantes/f-aceptada/nota");
  const form = page.getByTestId("nota-form");
  await form.getByLabel("Sustento").fill("x".repeat(501));
  await expect(form.getByLabel("Sustento")).toHaveValue("x".repeat(500));
});

test("nota de débito 13 (penalidades): la línea sale inafecta, 30 (SUNAT 3507)", async ({ page }) => {
  await page.goto("/comprobantes/f-obs/nota");
  const form = page.getByTestId("nota-form");
  await form.getByLabel("Tipo de nota").selectOption("08");
  await form.getByLabel(/Motivo/).selectOption("13");
  await form.getByLabel("Sustento").fill("Penalidad por entrega tardía");
  await form.getByLabel("Concepto").fill("Penalidad contractual");
  // La etiqueta ya no promete «con IGV»: las penalidades son inafectas.
  await form.getByLabel(/Importe inafecto, sin IGV/).fill("100");
  // Antes salía con afectación 10 (o 40 en exportación): numerada, firmada y rechazada por SUNAT con el
  // correlativo consumido. El mock ahora replica 3507, así que si volviera a salir gravada esto ni navega.
  const peticion = page.waitForRequest((r) => r.method() === "POST" && r.url().includes("/api/proxy/notas"));
  await form.getByRole("button", { name: "Emitir nota de débito" }).click();
  expect((await peticion).postDataJSON().items[0]).toMatchObject({ tipo_afectacion_igv: "30", precio_unitario: 100 });
  await expect(page).toHaveURL(/\/comprobantes\/n-/);
});

test("nota parcial y total sobre exportación: el importe no es 0.00 y el tope bloquea también la nota total", async ({ page }) => {
  await page.goto("/comprobantes/f-export/nota");
  const form = page.getByTestId("nota-form");
  await form.getByLabel(/Motivo/).selectOption("07");
  await form.getByLabel("Sustento").fill("Devolución por ítem");
  await form.getByLabel(/Cantidad de .* en la nota/).fill("1");
  // Con `calcularTotales` (que no entiende la afectación 40) acá decía «$ 0.00» y el tope era inerte.
  await expect(form.getByTestId("nota-importe")).toContainText("Importe de la nota: $ 100.00");
  // El tope arranca en el total: la NC `n-rechazada` (RECHAZADO, 100) sobre esta factura NO se descuenta, como en
  // `acreditadoPorNotas` del backend. Este filtro no tenía test; su mutación sobrevivía.
  await expect(form.getByTestId("nota-importe")).toContainText("Tope: $ 100.00");

  // Se acredita la mitad por HTTP directo (con la cookie de sesión), para que el estado no dependa del orden de
  // los tests: si el server ya venía con f-export acreditada, el POST falla por 3286 y el tope igual queda < 100.
  await page.evaluate(() =>
    fetch("/api/proxy/notas", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        // Fecha de Lima, no UTC: pasada la medianoche UTC el mock la tomaría como futura y rechazaría el POST.
        tipo: "07", serie: "FC01", fecha_emision: new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" }), documento_afectado: { serie: "F001", numero: 5 },
        motivo: "07", descripcion: "Media devolución",
        items: [{ descripcion: "Servicio de diseño para el exterior", unidad: "ZZ", cantidad: 0.5, precio_unitario: 100, tipo_afectacion_igv: "40" }],
      }),
    }).catch(() => null),
  );
  await page.goto("/comprobantes/f-export/nota");
  await form.getByLabel(/Motivo/).selectOption("07");
  await form.getByLabel("Sustento").fill("Devolución por ítem");
  await form.getByLabel(/Cantidad de .* en la nota/).fill("1");
  await expect(form.getByTestId("nota-importe").getByRole("alert")).toContainText("Supera el tope");
  await expect(form.getByRole("button", { name: "Emitir nota de crédito" })).toBeDisabled();

  // Nota TOTAL (01) sobre una factura ya acreditada: antes decía «copia la factura ($ 100.00)» y dejaba emitir.
  await form.getByLabel(/Motivo/).selectOption("01");
  const total = form.getByTestId("nota-total");
  await expect(total).toContainText("ya acreditado");
  await expect(total.getByRole("alert")).toContainText("Supera el tope");
  await expect(form.getByRole("button", { name: "Emitir nota de crédito" })).toBeDisabled();
});

test("NC 13: el neto pendiente viaja redondeado a 2 decimales, no en punto flotante (3250/3319)", async ({ page }) => {
  await page.goto("/comprobantes/f-aceptada/nota");
  const form = page.getByTestId("nota-form");
  await form.getByLabel(/Motivo/).selectOption("13");
  await form.getByLabel("Sustento").fill("Reprogramación de cuotas");
  // 10.1 + 20.2 = 30.299999999999997 en JS; el backend real exige hasta 2 decimales y que la suma sea el pendiente.
  await form.getByLabel("Monto de la cuota 1").fill("10.1");
  await form.getByLabel("Monto de la cuota 2").fill("20.2");
  const peticion = page.waitForRequest((r) => r.method() === "POST" && r.url().includes("/api/proxy/notas"));
  await form.getByRole("button", { name: "Emitir nota de crédito" }).click();
  const fp = (await peticion).postDataJSON().forma_pago;
  expect(fp.monto_pendiente).toBe(30.3);
  expect(fp.cuotas.map((q: { monto: number }) => q.monto)).toEqual([10.1, 20.2]);
});

test("nota: Enter sobre «Cancelar» (un enlace) sigue navegando; la guarda solo frena el envío implícito", async ({ page }) => {
  const { form, posts } = await notaLista(page);
  await form.getByRole("link", { name: "Cancelar" }).focus();
  await page.waitForTimeout(150);
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/\/comprobantes\/f-aceptada$/);
  expect(posts).toEqual([]);
});

test("NC 13: una cuota con más de 2 decimales no deja emitir (3253)", async ({ page }) => {
  await page.goto("/comprobantes/f-aceptada/nota");
  const form = page.getByTestId("nota-form");
  await form.getByLabel(/Motivo/).selectOption("13");
  await form.getByLabel("Sustento").fill("Reprogramación de cuotas");
  const boton = form.getByRole("button", { name: "Emitir nota de crédito" });
  await expect(boton).toBeEnabled();
  // `step=0.01` no frena lo tipeado, solo las flechas: `10.123` pasaba el formulario y el backend lo rechazaba.
  await form.getByLabel("Monto de la cuota 1").fill("10.123");
  await expect(boton).toBeDisabled();
  await form.getByLabel("Monto de la cuota 1").fill("10.12");
  await form.getByLabel("Monto de la cuota 2").fill("20.20");
  await expect(boton).toBeEnabled();
});

test("nota: un tabulador pegado en el sustento viaja como espacio (2135)", async ({ page }) => {
  const { form } = await notaLista(page);
  // `fill` mete el tab tal cual, como un pegado desde Excel; un input de una línea no lo bloquea.
  await form.getByLabel("Sustento").fill("linea1\tcon tab");
  const peticion = page.waitForRequest((r) => r.method() === "POST" && r.url().includes("/api/proxy/notas"));
  await form.getByRole("button", { name: "Emitir nota de crédito" }).click();
  expect((await peticion).postDataJSON().descripcion).toBe("linea1 con tab");
  await expect(page).toHaveURL(/\/comprobantes\/n-/);
});

test("el mock de POST /v1/notas rechaza lo que el backend rechaza: tab (2135), 3 decimales (3253), suma ≠ pendiente (3319), cuerpo vacío (400)", async ({ page }) => {
  // Red de seguridad de la suite: si el mock aceptara esto, un e2e verde no diría nada sobre producción. Se prueba
  // por HTTP directo con la cookie de sesión, porque el formulario (correctamente) ya no deja construir estos casos.
  await page.goto("/comprobantes/f-aceptada");
  const hoy = new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" });
  const post = (body: unknown) =>
    page.evaluate(async ([b]) => {
      const r = await fetch("/api/proxy/notas", { method: "POST", headers: { "content-type": "application/json" }, body: b as string });
      return { status: r.status, texto: await r.text() };
    }, [typeof body === "string" ? body : JSON.stringify(body)]);
  const base = { tipo: "07", serie: "FC01", fecha_emision: hoy, documento_afectado: { serie: "F001", numero: 1 }, motivo: "13", descripcion: "Reprogramación" };
  const cuotas = (montos: number[], pendiente: number) => ({ ...base, forma_pago: { tipo: "credito", monto_pendiente: pendiente, cuotas: montos.map((m) => ({ monto: m, vencimiento: "2026-12-01" })) } });

  let r = await post({ ...base, motivo: "07", descripcion: "con\ttab", items: [{ descripcion: "x", unidad: "ZZ", cantidad: 0.1, precio_unitario: 141.6, tipo_afectacion_igv: "10" }] });
  expect(r.status, r.texto).toBe(422);
  expect(r.texto).toContain("2135");

  // Pendiente a 2 decimales para pasar 3250 y que el rechazo sea el de la cuota (3253), no el del pendiente.
  r = await post(cuotas([10.123, 20.12], 30.24));
  expect(r.status, r.texto).toBe(422);
  expect(r.texto).toContain("3253");

  r = await post(cuotas([10, 20], 40));
  expect(r.status, r.texto).toBe(422);
  expect(r.texto).toContain("3319");

  r = await post("");
  expect(r.status, r.texto).toBe(400);
  expect(r.texto).toContain("JSON_INVALIDO");
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

test("NC parcial: los cargos de línea y el ISC de la factura viajan con la forma que exige el backend y entran en el importe", async ({ page }) => {
  // Recertificación #2: la NC omitía los cargos (acreditaba de menos) y mandaba el ISC como {sistema, tasa}, que el
  // dominio rechaza en los sistemas 02 (exige monto_unitario) y 03 (exige base_pvp). Un solo test sobre f-cargos, y
  // por menos del total, para no gastar el tope 3286 que comparten los workers.
  await page.goto("/comprobantes/f-cargos/nota");
  const form = page.getByTestId("nota-form");
  await form.getByLabel(/Motivo/).selectOption("07");
  await form.getByLabel("Sustento").fill("Devolución parcial con flete");
  const importe = page.getByTestId("nota-importe");
  // Con la cantidad facturada el importe es el precio de venta del backend, flete incluido (no 1180 de precio × cantidad).
  await form.getByLabel("Cantidad de Mesa de trabajo en la nota").fill("10");
  await expect(importe).toContainText("Importe de la nota: S/ 1,298.00");
  // La NC `n-anulada` (100, ANULADO) sobre esta factura no acreditó nada: el tope sigue siendo el total, sin
  // «ya acreditado». Este filtro no tenía test y su mutación sobrevivía.
  await expect(importe).toContainText("Tope: S/ 1,353.00");
  await expect(importe).not.toContainText("ya acreditado");
  // Con la mitad: 5 × 118 = 590 más la mitad del flete (50) con su IGV (59) = 649, porque el porcentaje acompaña a la línea.
  await form.getByLabel("Cantidad de Mesa de trabajo en la nota").fill("5");
  await expect(importe).toContainText("Importe de la nota: S/ 649.00");
  await form.getByLabel("Cantidad de Cerveza artesanal 330 ml en la nota").fill("2");
  await form.getByLabel("Cantidad de Gaseosa 500 ml en la nota").fill("3");
  await expect(importe).toContainText("Importe de la nota: S/ 704.00");
  const peticion = page.waitForRequest((r) => r.method() === "POST" && r.url().includes("/api/proxy/notas"));
  await form.getByRole("button", { name: "Emitir nota de crédito" }).click();
  const items = (await peticion).postDataJSON().items;
  expect(items).toHaveLength(3);
  expect(items[0]).toMatchObject({ cantidad: 5, cargos: [{ porcentaje: 10, afecta_base_igv: true }] });
  expect(items[0].cargos[0].tipo).toBeUndefined();
  expect(items[1].isc).toEqual({ sistema: "02", monto_unitario: 2.25 });
  expect(items[2].isc).toEqual({ sistema: "03", tasa: 17, base_pvp: 3.5 });
  await expect(page).toHaveURL(/\/comprobantes\/n-/);
});

test("el mock de POST /v1/notas rechaza el ISC y los cargos que el dominio rechaza", async ({ page }) => {
  await page.goto("/comprobantes/f-cargos");
  const hoy = new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" });
  const post = (items: unknown) =>
    page.evaluate(async ([b]) => {
      const r = await fetch("/api/proxy/notas", { method: "POST", headers: { "content-type": "application/json" }, body: b as string });
      return { status: r.status, texto: await r.text() };
    }, [JSON.stringify({ tipo: "07", serie: "FC01", fecha_emision: hoy, documento_afectado: { serie: "F001", numero: 7 }, motivo: "07", descripcion: "Prueba de contrato", items })]);
  const linea = { descripcion: "Cerveza", unidad: "NIU", cantidad: 1, precio_unitario: 20, tipo_afectacion_igv: "10" };

  let r = await post([{ ...linea, isc: { sistema: "02", tasa: 15.31 } }]);
  expect(r.status, r.texto).toBe(422);
  expect(r.texto).toContain("ISC_INVALIDO");

  r = await post([{ ...linea, isc: { sistema: "03", tasa: 17 } }]);
  expect(r.status, r.texto).toBe(422);
  expect(r.texto).toContain("base_pvp");

  r = await post([{ ...linea, cargos: [{ tipo: "PORCENTAJE", valor: 10, afecta_base_igv: true }] }]);
  expect(r.status, r.texto).toBe(422);
  expect(r.texto).toContain("CARGO_INVALIDO");
});

test("NC total (01) sobre una factura con anticipos se pide por ítems: copiar la factura iría por el bruto (3286)", async ({ page }) => {
  // f-aceptada regularizó un anticipo (bruto 141.60, neto 123). La guarda `!conAnticipos` existía sin test.
  await page.goto("/comprobantes/f-aceptada/nota");
  const form = page.getByTestId("nota-form");
  await form.getByLabel(/Motivo/).selectOption("01");
  await expect(form.getByTestId("nota-total")).toHaveCount(0);
  await expect(form.getByText(/regularizó anticipos \(neto S\/ 123\.00\)/)).toBeVisible();
  await expect(form.getByLabel(/Cantidad de .* en la nota/)).toBeVisible();
});

test("nota: si falla solo un catálogo, la otra pestaña funciona y «Reintentar» sigue disponible para recargarlo", async ({ page }) => {
  let caido = true;
  await page.route("**/api/proxy/catalogos/09", (r) => (caido ? r.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ estado: "error", datos: null, mensaje: "Boom", codigo: "ERROR_INTERNO", errores: null }) }) : r.continue()));
  await page.goto("/comprobantes/f-aceptada/nota");
  const form = page.getByTestId("nota-form");
  await expect(form.getByRole("alert")).toContainText("No se pudieron cargar los motivos");
  await form.getByLabel("Tipo de nota").selectOption("08");
  await expect(form.getByLabel(/Motivo/)).toBeEnabled(); // el 10 sí cargó
  // Antes: en esta pestaña la alerta quedaba pegada y «Reintentar» desaparecía (su condición miraba solo la pestaña actual).
  caido = false;
  await form.getByRole("button", { name: "Reintentar" }).click();
  await expect(form.getByRole("alert")).toHaveCount(0);
  await form.getByLabel("Tipo de nota").selectOption("07");
  await expect(form.getByLabel(/Motivo/)).toBeEnabled();
});

test("NC parcial: la cantidad se limita a 10 decimales (2025)", async ({ page }) => {
  // Sobre f-cargos (sin POST): f-obs la anula el test de baja que corre en paralelo y /nota redirige a la ficha.
  await page.goto("/comprobantes/f-cargos/nota");
  const form = page.getByTestId("nota-form");
  await form.getByLabel(/Motivo/).selectOption("07");
  const cantidad = form.getByLabel("Cantidad de Gaseosa 500 ml en la nota");
  await cantidad.fill("0.12345678901");
  await expect(cantidad).toHaveValue("0.123456789");
});

test("nota: tras un error el foco va al mensaje", async ({ page }) => {
  await page.route("**/api/proxy/notas", (r) => r.abort("failed"));
  const { form } = await notaLista(page);
  await form.getByRole("button", { name: "Emitir nota de crédito" }).click();
  const alerta = form.getByRole("alert");
  await expect(alerta).toContainText("Se cortó la conexión");
  await expect(alerta).toBeFocused();
});
