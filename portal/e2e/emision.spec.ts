import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
});

test("emite una factura desde el portal y el total previsualizado es el del comprobante (#17)", async ({ page }) => {
  // La acción principal vive en el top bar, como en el resto del portal: no hay un botón propio en la página.
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");

  // La serie anticipa el correlativo que asignará el backend. El número exacto depende de cuántas emitieron los
  // specs que corren en paralelo sobre el mismo mock, así que se comprueba el formato, no el valor.
  await expect(dialogo.getByLabel("Serie")).toContainText(/F001 · siguiente N\.º \d+/);
  // La factura solo ofrece series F: la B001 de la empresa es de boletas (#20).
  await expect(dialogo.getByLabel("Serie")).not.toContainText("B001");

  // El calendario no deja elegir una fecha que el backend va a rechazar: ni futura ni fuera del plazo de envío.
  const fecha = dialogo.getByLabel("Fecha de emisión");
  const hoy = await fecha.getAttribute("max");
  expect(hoy).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  expect(await fecha.getAttribute("min")).toBe(
    new Date(Date.parse(`${hoy}T00:00:00Z`) - 3 * 86_400_000).toISOString().slice(0, 10),
  );

  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");

  await dialogo.getByLabel("Descripción").fill("Consultoría");
  await dialogo.getByLabel("Cantidad").fill("2");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("1000");

  await dialogo.getByRole("button", { name: "Agregar ítem" }).click();
  await dialogo.getByLabel("Descripción").nth(1).fill("Licencia anual");
  await dialogo.getByLabel("Cantidad").nth(1).fill("1");
  await dialogo.getByLabel("Precio unit. (con IGV)").nth(1).fill("500");

  // H9: un precio con IGV se cobra exacto. 2×1000 + 1×500 da 2500.00, no el 2500.01 de base × tasa por línea.
  await expect(dialogo.getByTestId("total-a-pagar")).toHaveText("S/ 2,500.00");
  const previsualizado = await dialogo.getByTestId("total-a-pagar").innerText();

  await dialogo.getByRole("button", { name: "Emitir factura" }).click();

  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  // Lo que se previsualizó es exactamente lo que quedó emitido.
  await expect(page.getByText(previsualizado, { exact: false }).first()).toBeVisible();
});

/** #20: la boleta sale del mismo diálogo, con su serie y el documento del comprador; sin documento, solo hasta S/ 700. */
test("emite una boleta a un comprador sin documento y no deja pasar una de más de S/ 700", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await dialogo.getByRole("button", { name: "Boleta" }).click();

  await expect(dialogo.getByLabel("Serie")).toContainText(/B001 · siguiente N\.º \d+/);
  // #286: la ayuda de la fecha dice el plazo de la boleta (7 días, el del resumen diario: `PlazoEnvio`), no el de la factura, y el calendario llega hasta ahí.
  await expect(dialogo.getByText("Máx. 7 días")).toBeVisible();
  await expect(dialogo.getByText("Máx. 3 días")).toHaveCount(0);
  const fecha = dialogo.getByLabel("Fecha de emisión");
  const hoy = await fecha.getAttribute("max");
  expect(await fecha.getAttribute("min")).toBe(new Date(Date.parse(`${hoy}T00:00:00Z`) - 7 * 86_400_000).toISOString().slice(0, 10));
  await dialogo.getByLabel("Documento del comprador").selectOption("-");
  await expect(dialogo.getByLabel("Nombre del comprador")).toHaveValue("CLIENTES VARIOS");

  await dialogo.getByLabel("Descripción").fill("Televisor");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("899");
  await expect(dialogo.getByTestId("aviso-sin-documento")).toBeVisible();
  await dialogo.getByRole("button", { name: "Emitir boleta" }).click();
  await expect(dialogo.getByRole("alert")).toContainText("identifica al comprador");

  // Reescribir un monto: `fill` sobre `EntradaMonto` sin foco compite con su `onFocus` (que repone el texto) y concatena; como un usuario, se entra, se selecciona y se escribe.
  const precio = dialogo.getByLabel("Precio unit. (con IGV)");
  await precio.click();
  await precio.press("ControlOrMeta+a");
  await precio.pressSequentially("59");
  await expect(dialogo.getByTestId("aviso-sin-documento")).toHaveCount(0);
  await dialogo.getByRole("button", { name: "Emitir boleta" }).click();

  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  await expect(page.getByText(/B001-\d+/).first()).toBeVisible();
  await expect(page.getByText("Sin documento").first()).toBeVisible();

  // Se anula con el resumen diario (RC), no con una comunicación de baja.
  await page.getByTestId("dar-de-baja").click();
  const confirmacion = page.getByTestId("baja-confirmacion");
  await expect(confirmacion).toContainText("Resumen diario ante SUNAT");
  // #362 H8: el RC no lleva el motivo (queda en khipu) y solo se anula lo no entregado al adquirente (RS 097-2012 art. 14).
  await expect(confirmacion).toContainText("Queda solo en khipu");
  await expect(confirmacion).not.toContainText("lo ve SUNAT");
  await expect(confirmacion.getByTestId("baja-boleta-condicion")).toContainText("no se entregó al adquirente");
  await expect(confirmacion.getByTestId("baja-boleta-condicion")).toContainText("nota de crédito");
  // «[ENVIADA]»: SUNAT responde con ticket y la anulación llega al consultarlo (`GET /v1/bajas/{id}`). La aceptada en el acto ya la cubren la NC sobre
  // boleta y la factura (comprobantes.spec.ts); este camino no tenía test (352-H1).
  await confirmacion.getByLabel("Motivo").fill("[ENVIADA] Se cobró dos veces");
  await confirmacion.getByRole("button", { name: "Confirmar la baja" }).click();
  const panel = page.getByTestId("baja");
  await expect(panel).toContainText(/Resumen diario RC-\d{8}-1/);
  await expect(panel).toContainText("Enviada: SUNAT la está procesando");
  await panel.getByRole("button", { name: "Actualizar estado" }).click();
  await expect(panel).toContainText("Aceptada: comprobante anulado");
  await expect(panel).toContainText(/El Resumen diario RC-\d{8}-1, ha sido aceptado/);
  // #286: el historial también nombra el resumen diario, no una comunicación de baja.
  const historial = page.getByTestId("historial");
  await expect(historial).toContainText("Anulación aceptada por SUNAT en el resumen diario");
  await expect(historial).not.toContainText("Comunicación de baja");
});

test("tras emitir, el diálogo anuncia el correlativo siguiente, no el que acaba de usar (#17)", async ({ page }) => {
  // La opción SELECCIONADA, no el texto del select entero (que concatena todas las opciones).
  const numeroAnunciado = async () => {
    const texto = await page.getByRole("dialog").getByLabel("Serie").locator("option:checked").innerText();
    return Number(texto.match(/siguiente N\.º (\d+)/)?.[1]);
  };

  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  // Serie de uso exclusivo de este test: sobre F001 emiten los demás specs en paralelo y el número avanza por
  // debajo de los pies, con lo que un `>=` toleraba anunciar el correlativo ya consumido. Sobre F002 solo emite
  // este test y se puede afirmar el valor exacto.
  await dialogo.getByLabel("Serie").selectOption("F002");
  const antes = await numeroAnunciado();
  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");
  await dialogo.getByLabel("Descripción").fill("Consultoría");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("100");
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);

  // El número que se acaba de emitir, leído de la ficha: la fuente de verdad contra la que se compara el anuncio.
  const emitido = Number((await page.getByText(/F002-\d{8}/).first().innerText()).match(/F002-(\d{8})/)?.[1]);
  expect(emitido).toBe(antes);

  // El diálogo vive en el top bar y sobrevive a esta navegación: si cacheara las series, seguiría ofreciendo el
  // número que la emisión acaba de consumir; y si anunciara `ultimo_numero` sin el `+1`, ofrecería el ya usado.
  // Con serie propia, el valor es exacto: la recertificación demostró que `>=` no discriminaba bajo `npm run e2e`.
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  await page.getByRole("dialog").getByLabel("Serie").selectOption("F002");
  expect(await numeroAnunciado()).toBe(emitido + 1);
});

test("una línea sin precio no se emite como S/ 0.00, y vaciar la fecha no manda un comprobante sin fecha (#17)", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();

  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");

  // La cantidad ya viene en 1: con solo la descripción, la línea parecía completa y se emitía a precio 0 —una
  // factura que después hay que anular con nota de crédito.
  await dialogo.getByLabel("Descripción").fill("Consultoría");
  await expect(dialogo.getByText("1 ítem incompleto no se emitirá")).toBeVisible();
  await expect(dialogo.getByTestId("total-a-pagar")).toHaveText("S/ 0.00");

  await dialogo.getByLabel("Precio unit. (con IGV)").fill("100");
  await expect(dialogo.getByText("1 ítem incompleto no se emitirá")).toBeHidden();
  // H9: 100 / 1.18 = 84.75 y el IGV es lo que falta para 100 (15.25), no 84.75 × 0.18 = 15.26.
  await expect(dialogo.getByTestId("total-a-pagar")).toHaveText("S/ 100.00");

  // Un input de fecha vaciado emite "", no null: la guarda tiene que devolverlo a hoy y no dejar pasar el vacío.
  const fecha = dialogo.getByLabel("Fecha de emisión");
  const hoy = await fecha.inputValue();
  await fecha.fill("");
  await fecha.blur();
  await expect(fecha).toHaveValue(hoy);

  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
});

test("emite con cantidad fraccionaria: el submit no queda bloqueado por la validación de paso del stepper", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();

  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");
  await dialogo.getByLabel("Descripción").fill("Harina de pescado");
  await dialogo.getByLabel("Cantidad").fill("0.59");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("1208.79");
  // H9: 0.59 × 1208.79 = 713.1861, cobrado exacto a 2 decimales.
  await expect(dialogo.getByTestId("total-a-pagar")).toHaveText("S/ 713.19");

  // Antes: con `step=1` en el input oculto de base-ui, `0.59` caía en `stepMismatch`, el navegador abortaba el
  // submit sin alerta y el botón parecía muerto. Kilos, horas y metros quedaban fuera del portal.
  const posts: string[] = [];
  page.on("request", (r) => {
    if (r.method() === "POST" && r.url().endsWith("/api/proxy/facturas")) posts.push(r.url());
  });
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  expect(posts).toHaveLength(1);
});

test("si la red se corta durante la emisión, avisa y no deja el botón en «Emitiendo…» para siempre", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();

  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");
  await dialogo.getByLabel("Descripción").fill("Consultoría");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("100");

  // `fetch` rechaza (no resuelve) ante un corte: sin `try/catch` el estado `enviando` nunca volvía a false.
  await page.route("**/api/proxy/facturas", (r) => r.abort("connectionreset"));
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();

  const alerta = dialogo.getByRole("alert");
  await expect(alerta).toContainText("Se cortó la conexión");
  // Con la clave de idempotencia (#115) reintentar el mismo contenido es seguro: el mensaje lo dice.
  await expect(alerta).toContainText("Vuelve a emitir sin cambiar nada");
  await expect(dialogo.getByRole("button", { name: "Emitir factura" })).toBeEnabled();
  await expect(page).toHaveURL(/\/comprobantes(\?|$)/);
});

/**
 * #115, el caso que lo motivó: el POST llegó y la factura se emitió, pero la respuesta se cortó antes de llegar al navegador. El
 * usuario vuelve a emitir sin cambiar nada: el reintento lleva la misma clave y termina en la misma factura, no en otra.
 */
test("tras un corte, volver a emitir sin cambiar nada lleva a la misma factura en vez de duplicarla", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();
  await completarMinimo(dialogo);

  const claves: string[] = [];
  let emitida = "";
  await page.route("**/api/proxy/facturas", async (r) => {
    claves.push(r.request().headers()["idempotency-key"]);
    const res = await r.fetch();
    emitida = (await res.json()).datos.id;
    await r.abort("connectionreset");
  });
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(dialogo.getByRole("alert")).toContainText("Vuelve a emitir sin cambiar nada");
  expect(emitida).not.toBe("");

  await page.unroute("**/api/proxy/facturas");
  await page.route("**/api/proxy/facturas", async (r) => {
    claves.push(r.request().headers()["idempotency-key"]);
    await r.continue();
  });
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();

  await expect(page).toHaveURL(new RegExp(`/comprobantes/${emitida}$`));
  expect(claves).toHaveLength(2);
  expect(claves[0]).toMatch(/^[0-9a-f-]{36}$/);
  expect(claves[1]).toBe(claves[0]);
});

test("si después del corte se cambia la factura, el reintento es otra factura con otra clave", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();
  await completarMinimo(dialogo);

  const claves: string[] = [];
  await page.route("**/api/proxy/facturas", async (r) => {
    claves.push(r.request().headers()["idempotency-key"]);
    await r.abort("connectionreset");
  });
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(dialogo.getByRole("alert")).toContainText("Se cortó la conexión");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("200");
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(dialogo.getByRole("alert")).toContainText("Se cortó la conexión");

  expect(claves).toHaveLength(2);
  expect(claves[1]).not.toBe(claves[0]);
});

/**
 * El otro final incierto: la petición llegó y la respuesta no es JSON (el HTML de un 502 de proxy, típico). Acá el POST
 * casi con seguridad se procesó, así que el aviso de no reintentar a ciegas importa más todavía que en el corte.
 */
test("si la respuesta de la emisión no es JSON, avisa que se puede reintentar sin duplicar", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();
  await completarMinimo(dialogo);

  await page.route("**/api/proxy/facturas", (r) =>
    r.fulfill({ status: 502, contentType: "text/html", body: "<html><body>502 Bad Gateway</body></html>" }),
  );
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();

  const alerta = dialogo.getByRole("alert");
  await expect(alerta).toContainText("Vuelve a emitir sin cambiar nada");
  await expect(dialogo.getByRole("button", { name: "Emitir factura" })).toBeEnabled();
});

/** Formulario mínimamente válido: lo que hace falta para que un submit implícito realmente emita. */
async function completarMinimo(dialogo: ReturnType<import("@playwright/test").Page["getByRole"]>) {
  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");
  await dialogo.getByLabel("Descripción").fill("Consultoría");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("100");
}

function contarEmisiones(page: import("@playwright/test").Page) {
  const posts: string[] = [];
  page.on("request", (r) => {
    if (r.method() === "POST" && r.url().endsWith("/api/proxy/facturas")) posts.push(r.url());
  });
  return posts;
}

test("Enter en un campo de texto no emite: la factura solo sale desde el botón", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();
  await completarMinimo(dialogo);
  const posts = contarEmisiones(page);

  // Antes: el envío implícito del navegador convertía «Enter para pasar al siguiente campo» en una factura real.
  // Los selects también: la primera versión los exceptuaba y Chromium emite desde un select cerrado igual —Serie es
  // el primer control del diálogo y Moneda está justo antes del RUC—. La recertificación lo reprodujo.
  await dialogo.getByRole("button", { name: "Más opciones del ítem 1" }).click();
  for (const campo of ["Serie", "Fecha de emisión", "Moneda", "RUC", "Razón social", "Descripción", "Cantidad", "Precio unit. (con IGV)", "Unidad de medida", "Afectación del IGV"]) {
    await dialogo.getByLabel(campo).focus();
    await page.waitForTimeout(120);
    await page.keyboard.press("Enter");
  }
  await page.waitForTimeout(400);
  expect(posts).toEqual([]);
  await expect(dialogo).toBeVisible();

  // Con el foco en el botón, Enter sí emite: es la única vía por teclado.
  await dialogo.getByRole("button", { name: "Emitir factura" }).focus();
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  expect(posts).toHaveLength(1);
});

test("RUC corto y razón social corta se frenan en el cliente, sin viaje al backend", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();
  await completarMinimo(dialogo);
  const posts = contarEmisiones(page);

  // 10 dígitos: `Receptor.esRuc` exige 11. Antes viajaba y volvía como 422 «2017».
  await dialogo.getByLabel("RUC").fill("2055419821");
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(dialogo.getByLabel("RUC")).toHaveJSProperty("validity.valid", false);
  expect(posts).toEqual([]);

  await dialogo.getByLabel("RUC").fill("20554198211");
  // «AB»: el dominio exige de 3 a 1500 caracteres (regla 2022).
  await dialogo.getByLabel("Razón social").fill("AB");
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(dialogo.getByLabel("Razón social")).toHaveJSProperty("validity.valid", false);
  expect(posts).toEqual([]);

  // #303: `Receptor` cuenta sin los espacios de los extremos; `minLength` sola dejaba pasar «  AB  ».
  await dialogo.getByLabel("Razón social").fill("  AB  ");
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(dialogo.getByLabel("Razón social")).toHaveJSProperty("validity.valid", false);
  expect(posts).toEqual([]);

  // Descripción: `Item` admite hasta 500 (regla 2027); el `maxLength` corta antes de que el backend rechace.
  await dialogo.getByLabel("Descripción").fill("x".repeat(501));
  await expect(dialogo.getByLabel("Descripción")).toHaveValue("x".repeat(500));
});

/**
 * #303: la regla 2022 (nombre del adquirente de 3 caracteres o más) es error en Boleta2_0 desde 2027-01-01; khipu ya la exige. El
 * formulario la frena sin viaje, y si igual llega (otro cliente del API), el backend —y el mock— responden con la 2022 del comprador.
 */
test("el nombre corto del comprador de una boleta se frena en el cliente y, si llega, vuelve como 2022", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await dialogo.getByRole("button", { name: "Boleta" }).click();
  await expect(dialogo.getByLabel("Serie")).toContainText(/B001/);
  await dialogo.getByLabel("Número de documento").fill("12345678");
  await dialogo.getByLabel("Descripción").fill("Pan");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("5");
  const posts = contarEmisiones(page);

  const nombre = dialogo.getByLabel("Nombre del comprador");
  await nombre.fill("  JP  ");
  await dialogo.getByRole("button", { name: "Emitir boleta" }).click();
  await expect(nombre).toHaveJSProperty("validity.valid", false);
  expect(posts).toEqual([]);

  // Sin el freno del navegador, el mensaje del dominio llega tal cual.
  await nombre.evaluate((el) => (el as HTMLInputElement).closest("form")?.setAttribute("novalidate", ""));
  await dialogo.getByRole("button", { name: "Emitir boleta" }).click();
  await expect(dialogo.getByRole("alert")).toContainText("2022 - El nombre del comprador tiene de 3 a 1500 caracteres");
  expect(posts).toHaveLength(1);
});

/**
 * #308: el selector ofrece las unidades más usadas del catálogo 03 (no las 2000 de la rec 20) y la elegida viaja; una unidad que no
 * está en el catálogo vuelve 422 ITEM_INVALIDO (2936) con el mismo mensaje del backend.
 */
test("la unidad sale de las más usadas del catálogo 03 y una inexistente vuelve como 2936", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();
  await completarMinimo(dialogo);
  await dialogo.getByRole("button", { name: "Más opciones del ítem 1" }).click();
  const unidad = dialogo.getByLabel("Unidad de medida");
  await expect(unidad.locator("option[value='BX']")).toHaveText("BX · Caja");
  await expect(unidad.locator("option[value='E48']")).toHaveCount(0);
  await unidad.selectOption("KGM");
  const emision = page.waitForRequest((r) => r.method() === "POST" && r.url().endsWith("/api/proxy/facturas"));
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  expect((await emision).postDataJSON().items[0].unidad).toBe("KGM");
  await expect(page).toHaveURL(/\/comprobantes\/f-/);

  const hoy = new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" });
  const r = await page.evaluate(async ([b]) => {
    const res = await fetch("/api/proxy/facturas", { method: "POST", headers: { "content-type": "application/json" }, body: b });
    return { status: res.status, cuerpo: await res.json() };
  }, [JSON.stringify({ serie: "F001", fecha_emision: hoy, moneda: "PEN", cliente: { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C." },
    items: [{ descripcion: "Cable", unidad: "ROL", cantidad: 1, precio_unitario: 10, tipo_afectacion_igv: "10" }] })]);
  expect(r.status).toBe(422);
  expect(r.cuerpo.codigo).toBe("ITEM_INVALIDO");
  expect(r.cuerpo.mensaje).toBe("2936 - La unidad de medida ROL no existe en el catálogo 03 (UN/ECE rec 20; GET /v1/catalogos/03): NIU unidad (bienes), ZZ unidad (servicios), KGM, HUR…");
});

/**
 * #354: las reglas de la línea que SUNAT rechaza con el número ya consumido vuelven 422 ITEM_INVALIDO antes de numerar, con el código SUNAT y el mismo mensaje del backend,
 * en factura y en boleta: ICBPER (cantidad entera de hasta 5 dígitos 2892, unidad NIU 4320, NRUS 3240), precio en cero (2369/2367), valor de venta en 0.00 (2370) y
 * más de 999 líneas (2023). Con bolsas válidas, 999 líneas o un NRUS sin ICBPER sí se emite.
 */
test("las reglas de la línea vuelven como el backend las rechaza: ICBPER, NRUS, precio 0, valor de venta 0.00 y más de 999 líneas (#354)", async ({ page }) => {
  const hoy = new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" });
  type Linea = Record<string, unknown>;
  const emitir = (serie: string, items: Linea[], tipoOperacion?: string) =>
    page.evaluate(async ([b]) => {
      const res = await fetch("/api/proxy/facturas", { method: "POST", headers: { "content-type": "application/json" }, body: b });
      return { status: res.status, cuerpo: await res.json() };
    }, [JSON.stringify({ serie, fecha_emision: hoy, moneda: "PEN", ...(tipoOperacion ? { tipo_operacion: tipoOperacion } : {}),
      cliente: serie.startsWith("B") ? { tipo_doc: "1", num_doc: "12345678", razon_social: "JUAN PEREZ" } : { tipo_doc: "6", num_doc: "20554198211", razon_social: "CORPORACION GRAFICA ANDINA S.A.C." },
      items })]);
  const pan = (precio: number): Linea => ({ descripcion: "Pan", unidad: "NIU", cantidad: 1, precio_unitario: precio, tipo_afectacion_igv: "10" });
  const bolsas = (extra: Linea): Linea => ({ descripcion: "Bolsa", unidad: "NIU", cantidad: 2, precio_unitario: 0.6, tipo_afectacion_igv: "10", icbper: true, ...extra });
  const rechazada = async (serie: string, items: Linea[], mensaje: string, tipoOperacion?: string) => {
    const r = await emitir(serie, items, tipoOperacion);
    expect(r.status, JSON.stringify(r.cuerpo).slice(0, 300)).toBe(422);
    expect(r.cuerpo).toMatchObject({ codigo: "ITEM_INVALIDO", mensaje });
  };

  for (const serie of ["F001", "B001"]) {
    await rechazada(serie, [bolsas({ cantidad: 1.5 })], "2892 - La cantidad de bolsas del ICBPER es un entero de hasta 5 dígitos (1 a 99999); recibida 1.5");
    await rechazada(serie, [bolsas({ cantidad: 100000 })], "2892 - La cantidad de bolsas del ICBPER es un entero de hasta 5 dígitos (1 a 99999); recibida 100000");
    await rechazada(serie, [bolsas({ unidad: "KGM" })], "4320 - Un ítem con ICBPER va en unidades (unidad NIU, una bolsa por unidad); recibida KGM");
    await rechazada(serie, [pan(0)], "2369 - El precio unitario debe ser mayor que cero");
    await rechazada(serie, [{ ...pan(0), tipo_afectacion_igv: "15" }], "2367 - El valor referencial unitario de una operación gratuita debe ser mayor que cero");
    await rechazada(serie, [bolsas({ precio_unitario: 0.5 })], "2370 - El valor de venta de la línea «Bolsa» debe ser mayor que 0.00: el precio_unitario incluye el ICBPER (S/ 0.50 por bolsa) y debe superarlo");
    await rechazada(serie, Array.from({ length: 1000 }, () => pan(1.18)), `2023 - Una ${serie === "B001" ? "boleta" : "factura"} admite hasta 999 líneas (el número de orden del ítem es de hasta 3 dígitos); recibidas 1000: divida la operación en más de un comprobante`);
  }
  // El NRUS no cobra ICBPER (3240) y solo existe en la boleta.
  await rechazada("B001", [bolsas({})], "3240 - El impuesto ICBPER no aplica para el NRUS (tipo de operación 0113): quite el icbper de las líneas o emita con el tipo de operación 0101", "0113");

  expect((await emitir("B001", [bolsas({})])).status).toBe(201);
  expect((await emitir("F001", Array.from({ length: 999 }, () => pan(1.18)))).status).toBe(201);
  expect((await emitir("B001", [pan(10)], "0113")).status).toBe(201);
});

/**
 * #348-H2: la unidad en blanco no llega al dominio: el `@NotBlank` de `ItemDto` responde 422 VALIDACION con el campo, junto con los demás
 * campos en blanco (como `cliente.razonSocial`). El mock respondía 2936 con «La unidad de medida  no existe».
 */
test("la unidad en blanco vuelve como VALIDACION de items[n].unidad, como el @NotBlank del backend", async ({ page }) => {
  const hoy = new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" });
  const emitir = (cliente: string, unidades: Array<string | undefined>) =>
    page.evaluate(async ([b]) => {
      const res = await fetch("/api/proxy/facturas", { method: "POST", headers: { "content-type": "application/json" }, body: b });
      return { status: res.status, cuerpo: await res.json() };
    }, [JSON.stringify({ serie: "F001", fecha_emision: hoy, moneda: "PEN", cliente: { tipo_doc: "6", num_doc: "20554198211", razon_social: cliente },
      items: unidades.map((unidad) => ({ descripcion: "Cable", unidad, cantidad: 1, precio_unitario: 10, tipo_afectacion_igv: "10" })) })]);

  let r = await emitir("CORPORACION GRAFICA ANDINA S.A.C.", ["NIU", " \t", undefined]);
  expect(r.status).toBe(422);
  expect(r.cuerpo).toMatchObject({ codigo: "VALIDACION", errores: { "items[1].unidad": ["no debe estar vacío"], "items[2].unidad": ["no debe estar vacío"] } });
  expect(Object.keys(r.cuerpo.errores)).toEqual(["items[1].unidad", "items[2].unidad"]);

  r = await emitir(" ", [""]);
  expect(r.status).toBe(422);
  expect(r.cuerpo).toMatchObject({ codigo: "VALIDACION", errores: { "cliente.razonSocial": ["no debe estar vacío"], "items[0].unidad": ["no debe estar vacío"] } });
});

/**
 * #347-H2: el `@NotBlank` de `ClienteDto` corre antes que el servicio y que el dominio, así que un nombre en blanco gana a un RUC corto:
 * el backend responde VALIDACION y no 2017. El mock lo comprobaba después del RUC.
 */
test("sin el freno del navegador, la razón social en blanco vuelve como VALIDACION antes que el RUC corto", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();
  await completarMinimo(dialogo);
  const posts = contarEmisiones(page);

  await dialogo.getByLabel("RUC").fill("2055419821");
  await dialogo.getByLabel("Razón social").fill("   ");
  await dialogo.getByLabel("RUC").evaluate((el) => (el as HTMLInputElement).closest("form")?.setAttribute("novalidate", ""));
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(dialogo.getByRole("alert")).toContainText("Validación fallida");
  expect(posts).toHaveLength(1);
});

test("el aviso de ítems incompletos es una región viva y el botón de emitir lo referencia", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();
  await completarMinimo(dialogo);
  await dialogo.getByRole("button", { name: "Agregar ítem" }).click();
  await dialogo.getByLabel("Descripción").nth(1).fill("Sin precio");

  // Un `<span>` pelado no se anuncia: un usuario ciego emitía dos líneas creyendo que iban tres.
  // Un `status` no toma su nombre accesible del contenido, así que se filtra por texto, no por `name`.
  const aviso = dialogo.getByRole("status").filter({ hasText: "1 ítem incompleto no se emitirá" });
  await expect(aviso).toBeVisible();
  await expect(dialogo.getByRole("button", { name: "Emitir factura" })).toHaveAttribute("aria-describedby", "nc-aviso-incompletos");
});

test("tras un error del servidor el foco queda en la alerta, no en <body>", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();
  await completarMinimo(dialogo);
  await page.route("**/api/proxy/facturas", (r) =>
    r.fulfill({ status: 422, contentType: "application/json", body: JSON.stringify({ estado: "error", datos: null, mensaje: "2017 - El RUC del adquirente debe tener 11 dígitos", codigo: "RECEPTOR_INVALIDO", errores: null }) }),
  );
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(dialogo.getByRole("alert")).toContainText("2017");
  // El botón estuvo `disabled` y el navegador soltó el foco: hay que reubicarlo donde está la explicación.
  const activo = await page.evaluate(() => document.activeElement?.closest("[role=dialog]") !== null && document.activeElement?.querySelector("[role=alert]") !== null);
  expect(activo).toBe(true);
});

test("la fecha y la moneda elegidas son las que viajan, y la fecha por defecto es hoy", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();
  await completarMinimo(dialogo);

  // Por defecto, hoy: el `max` del calendario es hoy en Lima, y el valor inicial tiene que coincidir con él.
  const fecha = dialogo.getByLabel("Fecha de emisión");
  const hoy = await fecha.getAttribute("max");
  await expect(fecha).toHaveValue(hoy!);
  const ayer = new Date(Date.parse(`${hoy}T00:00:00Z`) - 86_400_000).toISOString().slice(0, 10);
  await fecha.fill(ayer);
  await dialogo.getByLabel("Moneda").selectOption("USD");
  await dialogo.getByLabel("Cantidad").fill("2.5");

  // Ningún test miraba el cuerpo del POST: `fecha_emision: hoy` y `moneda: "PEN"` hardcodeados pasaban igual.
  const peticion = page.waitForRequest((r) => r.method() === "POST" && r.url().endsWith("/api/proxy/facturas"));
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  const cuerpo = (await peticion).postDataJSON();
  expect(cuerpo).toMatchObject({ fecha_emision: ayer, moneda: "USD" });
  expect(cuerpo.items[0]).toMatchObject({ cantidad: 2.5, precio_unitario: 100, tipo_afectacion_igv: "10" });
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
});

test("sin ningún ítem completo no se emite: avisa en vez de mandar un comprobante vacío", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();
  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");
  // Cliente completo, ítem con cantidad pero sin descripción ni precio: los `required` no lo frenan, la guarda sí.
  const posts = contarEmisiones(page);
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(dialogo.getByRole("alert")).toContainText("Agrega al menos un ítem");
  expect(posts).toEqual([]);
  await expect(dialogo).toBeVisible();
});

test("cancelar cierra el diálogo sin emitir nada (#17)", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();
  // Con el formulario LLENO: cancelar uno vacío no prueba nada, porque los `required` ya bloquean el submit solos.
  // Honestidad sobre el alcance: si «Cancelar» perdiera su `type="button"`, este test NO lo detecta —React
  // desmonta el diálogo durante el clic, antes de la acción por defecto del navegador, así que no llega a haber
  // POST—. Esa regresión es inocua en la práctica; lo que este test sí protege es que cancelar no emita.
  await completarMinimo(dialogo);

  // Se observa la petición y no el estado compartido: otros specs emiten contra el mismo mock en paralelo, así que
  // ni el conteo de filas ni el correlativo ofrecido sirven para afirmar que *este* cancelar no emitió.
  const emisiones = contarEmisiones(page);

  await dialogo.getByRole("button", { name: "Cancelar" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page).toHaveURL(/\/comprobantes(\?|$)/);
  expect(emisiones).toEqual([]);
});

test("una línea sin descripción no entra en el total ni se emite en silencio (#17)", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByLabel("Serie")).toBeVisible();

  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");
  await dialogo.getByLabel("Descripción").fill("Consultoría");
  await dialogo.getByLabel("Cantidad").fill("2");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("1000");

  // Segunda línea con importe pero sin descripción: no debe sumar al total, y el usuario tiene que enterarse.
  await dialogo.getByRole("button", { name: "Agregar ítem" }).click();
  await dialogo.getByLabel("Cantidad").nth(1).fill("2");
  await dialogo.getByLabel("Precio unit. (con IGV)").nth(1).fill("500");

  await expect(dialogo.getByTestId("total-a-pagar")).toHaveText("S/ 2,000.00");
  await expect(dialogo.getByText("1 ítem incompleto no se emitirá")).toBeVisible();

  // Una línea con descripción pero cantidad 0 también queda fuera: el aviso no debe atribuirlo a la descripción.
  await dialogo.getByLabel("Descripción").nth(1).fill("Licencia");
  await dialogo.getByLabel("Cantidad").nth(1).fill("0");
  await expect(dialogo.getByTestId("total-a-pagar")).toHaveText("S/ 2,000.00");
  await expect(dialogo.getByText("1 ítem incompleto no se emitirá")).toBeVisible();

  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  // Lo emitido es lo previsualizado: la línea incompleta quedó fuera de los dos lados.
  await expect(page.getByText("S/ 2,000.00").first()).toBeVisible();
});

test("el redondeo se calcula en el backend, se ve en el pie y queda en el comprobante (#358)", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");
  await dialogo.getByLabel("Descripción").fill("Consultoría");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("100.07");
  await expect(dialogo.getByTestId("total-a-pagar")).toHaveText("S/ 100.07");

  // Los ajustes poco frecuentes viven en «Más opciones del comprobante», cerrado por defecto.
  await dialogo.getByText("Más opciones del comprobante").click();
  await dialogo.getByLabel("Redondeo").fill("-0.07");
  await expect(dialogo.getByTestId("total-a-pagar")).toHaveText("S/ 100.00");
  await expect(dialogo.getByTestId("totales-previsualizados")).toContainText("Redondeo");

  // Fuera de rango el campo lo dice y no deja emitir.
  await dialogo.getByLabel("Redondeo").fill("1.5");
  await expect(dialogo.getByText(/no puede superar 1\.00/).first()).toBeVisible();
  await dialogo.getByLabel("Redondeo").fill("-0.07");
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
});

test("emite una factura al crédito con sus cuotas y la ficha las muestra (#309)", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");
  await dialogo.getByLabel("Descripción").fill("Proyecto");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("1180");

  await dialogo.getByLabel("Forma de pago").selectOption("credito");
  await dialogo.getByRole("button", { name: "Repartir" }).click();
  await expect(dialogo.getByLabel("Cuota 1: monto")).toHaveValue("590.00");
  await expect(dialogo.getByTestId("credito-resumen")).toContainText("suman S/ 1,180.00 de S/ 1,180.00");

  // Unas cuotas que no suman el neto pendiente no dejan emitir: el mock responde con las mismas reglas que el backend (3250).
  await dialogo.getByLabel("Cuota 2: monto").fill("500");
  await expect(dialogo.getByTestId("credito-resumen")).toContainText(/suman 1090\.00 y el neto pendiente es 1180\.00/);
  await dialogo.getByLabel("Cuota 2: monto").fill("590");

  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  await expect(page.getByText("Cuota001").first()).toBeVisible();
  await expect(page.getByText("Cuota002").first()).toBeVisible();
});

test("emite una factura de exportación: afectación 40, cliente del exterior con país e incoterm (#311)", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await dialogo.getByLabel("Tipo de operación").selectOption("0200");
  await expect(dialogo.getByLabel("Moneda")).toHaveValue("USD");

  await dialogo.getByLabel("Número de documento").fill("US123456789");
  await dialogo.getByLabel("Razón social").fill("ACME IMPORTS LLC");
  await dialogo.getByLabel("Descripción").fill("Café verde en grano");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("4500");
  await dialogo.getByLabel("Incoterm").selectOption("FOB");

  // Sin el país del cliente el formulario no deja emitir y lo dice en el momento.
  await expect(dialogo.getByTestId("error-exportacion")).toContainText("país");
  await dialogo.getByLabel("País del cliente").selectOption("US");
  await expect(dialogo.getByTestId("error-exportacion")).toHaveCount(0);

  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  await expect(page.getByText("0200").first()).toBeVisible();
});

test("emite una factura con detracción: bien, porcentaje y cuenta; el pie y la ficha muestran el depósito (#288)", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");
  await dialogo.getByLabel("Descripción").fill("Consultoría");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("1180");

  await dialogo.getByLabel("Operación sujeta a detracción (SPOT)").check();
  await dialogo.getByLabel("Bien o servicio sujeto").selectOption("022");
  await expect(dialogo.getByLabel("Porcentaje")).toHaveValue("12");
  // Sin la cuenta del Banco de la Nación no se emite, y lo dice en el momento.
  await expect(dialogo.getByTestId("detraccion-resumen")).toContainText("cuenta de detracciones");
  await dialogo.getByLabel("Cuenta del Banco de la Nación").fill("00-000-123456");

  // El monto y el neto a cobrar los calcula el backend.
  await expect(dialogo.getByTestId("detraccion-pie")).toContainText("Detracción S/ 142.00");
  await expect(dialogo.getByTestId("detraccion-pie")).toContainText("Neto a cobrar S/ 1,038.00");

  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  await expect(page.getByText("1001").first()).toBeVisible();
  await expect(page.getByText("00-000-123456").first()).toBeVisible();
});

test("emite una factura con percepción: régimen 51, el pie suma el monto y la ficha lo muestra (#297)", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");
  await dialogo.getByLabel("Descripción").fill("Mercadería");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("1180");

  await dialogo.getByLabel("Operación con percepción del IGV").check();
  await dialogo.getByLabel("Régimen de percepción").selectOption("51");
  // El monto lo calcula el backend: 2 % de 1,180.00.
  await expect(dialogo.getByTestId("percepcion-pie")).toContainText("Percepción S/ 23.60");
  await expect(dialogo.getByTestId("percepcion-pie")).toContainText("Total a cobrar S/ 1,203.60");

  // En dólares la percepción no vale (2788) y el formulario lo dice en el momento.
  await dialogo.getByLabel("Moneda").selectOption("USD");
  await expect(dialogo.getByTestId("percepcion-resumen")).toContainText("2788");
  await dialogo.getByLabel("Moneda").selectOption("PEN");

  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  await expect(page.getByTestId("percepcion")).toContainText("Percepción 51");
});

test("emite una boleta NRUS (0113) y no deja agregarle bolsas de plástico (#297)", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await dialogo.getByRole("button", { name: "Boleta" }).click();
  await dialogo.getByLabel("Número de documento").fill("12345678");
  await dialogo.getByLabel("Nombre del comprador").fill("Juan Pérez");
  await dialogo.getByLabel("Descripción").fill("Pan");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("5");
  await dialogo.getByLabel("Tipo de operación").selectOption("0113");

  await dialogo.getByRole("button", { name: "Más opciones del ítem 1" }).click();
  await dialogo.getByLabel("Bolsas de plástico (ICBPER)").check();
  await expect(dialogo.getByTestId("tributos-incompatibles")).toContainText("3240");
  await dialogo.getByLabel("Bolsas de plástico (ICBPER)").uncheck();

  await dialogo.getByRole("button", { name: "Emitir boleta" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  await expect(page.getByText("0113").first()).toBeVisible();
});

test("emite con orden de compra, guía, documento relacionado y observaciones, y la ficha los muestra (#300)", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");
  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");
  await dialogo.getByLabel("Descripción").fill("Mercadería");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("1180");

  await dialogo.getByText(/Datos adicionales: guías/).click();
  await dialogo.getByLabel(/Orden de compra/).fill("OC-2026-0457");
  await dialogo.getByRole("button", { name: "Agregar guía" }).click();
  // Una guía mal escrita se dice en el momento (4006) y la emisión no sale.
  await dialogo.getByLabel("Número de la guía 1").fill("123");
  await expect(dialogo.getByTestId("referencias-error")).toContainText("4006");
  await dialogo.getByLabel("Número de la guía 1").fill("T001-123");
  await expect(dialogo.getByTestId("referencias-error")).toHaveCount(0);
  await dialogo.getByRole("button", { name: "Agregar documento" }).click();
  await dialogo.getByLabel("Tipo del documento 1").selectOption("05");
  await dialogo.getByLabel("Número del documento 1").fill("SCOP-8841203");
  await dialogo.getByLabel(/Observaciones/).fill("Entrega en almacén central.");

  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  const referencias = page.getByTestId("referencias");
  await expect(referencias).toContainText("OC-2026-0457");
  await expect(referencias).toContainText("T001-123");
  await expect(referencias).toContainText("SCOP-8841203");
  await expect(page.getByTestId("observaciones")).toContainText("Entrega en almacén central.");
});

test("emite un anticipo y luego la factura final que lo descuenta: el pie y la ficha muestran lo descontado (#312)", async ({ page }) => {
  const ruc = "20131312955";
  // 1) La factura de anticipo es una factura común; el formulario ayuda a describirla.
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  let dialogo = page.getByRole("dialog");
  await dialogo.getByLabel("RUC").fill(ruc);
  await dialogo.getByLabel("Razón social").fill("SUPERINTENDENCIA NACIONAL DE ADUANAS");
  await dialogo.getByLabel("Descripción").fill("");
  await dialogo.getByText("Anticipos", { exact: true }).click();
  await dialogo.getByLabel("Esta factura es un anticipo").check();
  await expect(dialogo.getByLabel("Descripción")).toHaveValue("Anticipo por ");
  await dialogo.getByLabel("Descripción").fill("Anticipo por obra");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("590");
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);

  // 2) La factura final del mismo cliente elige el anticipo y descuenta su valor sin IGV.
  await page.goto("/comprobantes");
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  dialogo = page.getByRole("dialog");
  await dialogo.getByLabel("RUC").fill(ruc);
  await dialogo.getByLabel("Razón social").fill("SUPERINTENDENCIA NACIONAL DE ADUANAS");
  await dialogo.getByLabel("Descripción").fill("Obra completa");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("1180");
  await dialogo.getByText("Anticipos", { exact: true }).click();
  await dialogo.getByRole("checkbox", { name: /F001-\d+ .*por descontar/ }).first().check();
  await expect(dialogo.getByLabel(/Valor a descontar de F001-/)).toHaveValue("500.00");
  // Los 590.00 del anticipo (500 + IGV) salen del total: 1,180.00 − 590.00.
  await expect(dialogo.getByTestId("total-a-pagar")).toContainText("590.00");
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  await expect(page.getByTestId("anticipos")).toContainText("F001-");
  await expect(page.getByText("Anticipos ya pagados (con IGV)")).toBeVisible();
});
