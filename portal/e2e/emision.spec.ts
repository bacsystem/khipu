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
  // Boleta existe en el catálogo pero todavía no se puede emitir (#20): se muestra deshabilitada, no oculta.
  await expect(dialogo.getByRole("button", { name: "Boleta" })).toBeDisabled();

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

  // El redondeo va por línea, como exige SUNAT: 2×1000 + 1×500 con IGV da 2500.01, no 2500.00.
  await expect(dialogo.getByTestId("total-a-pagar")).toHaveText("S/ 2,500.01");
  const previsualizado = await dialogo.getByTestId("total-a-pagar").innerText();

  await dialogo.getByRole("button", { name: "Emitir factura" }).click();

  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  // Lo que se previsualizó es exactamente lo que quedó emitido.
  await expect(page.getByText(previsualizado, { exact: false }).first()).toBeVisible();
});

test("tras emitir, el diálogo anuncia el correlativo siguiente, no el que acaba de usar (#17)", async ({ page }) => {
  const numeroAnunciado = async () => {
    const texto = await page.getByRole("dialog").getByLabel("Serie").innerText();
    return Number(texto.match(/siguiente N\.º (\d+)/)?.[1]);
  };

  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const antes = await numeroAnunciado();

  const dialogo = page.getByRole("dialog");
  await dialogo.getByLabel("RUC").fill("20554198211");
  await dialogo.getByLabel("Razón social").fill("CORPORACION GRAFICA ANDINA S.A.C.");
  await dialogo.getByLabel("Descripción").fill("Consultoría");
  await dialogo.getByLabel("Precio unit. (con IGV)").fill("100");
  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);

  // El número que se acaba de emitir, leído de la ficha: la fuente de verdad contra la que se compara el anuncio.
  const emitido = Number((await page.getByText(/F001-\d{8}/).first().innerText()).match(/F001-(\d{8})/)?.[1]);
  expect(emitido).toBeGreaterThanOrEqual(antes);

  // El diálogo vive en el top bar y sobrevive a esta navegación: si cacheara las series, seguiría ofreciendo el
  // número que la emisión acaba de consumir. Se compara con `>=` y no con `===` porque otros specs emiten en
  // paralelo sobre el mismo mock, pero NUNCA puede anunciar el que ya se usó: la cota inferior es `emitido + 1`.
  // (Con `> antes` a secas, anunciar `ultimo_numero` sin el `+1` pasaba igual.)
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  expect(await numeroAnunciado()).toBeGreaterThanOrEqual(emitido + 1);
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
  // 100 / 1.18 = 84.75 y su IGV 15.26: el redondeo por línea da 100.01, no 100.00.
  await expect(dialogo.getByTestId("total-a-pagar")).toHaveText("S/ 100.01");

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
  await expect(dialogo.getByTestId("total-a-pagar")).toHaveText("S/ 713.18");

  // Antes: con `step=1` en el input oculto de base-ui, `0.59` caía en `stepMismatch`, el navegador abortaba el
  // submit sin alerta y el botón parecía muerto. Kilos, horas y metros quedaban fuera del portal.
  const posts: string[] = [];
  page.on("request", (r) => {
    if (r.method() === "POST" && r.url().includes("/api/proxy/facturas")) posts.push(r.url());
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
  // Lo esencial del mensaje: no reintentar a ciegas, porque el POST pudo haber consumido correlativo.
  await expect(alerta).toContainText("pudo haberse emitido");
  await expect(dialogo.getByRole("button", { name: "Emitir factura" })).toBeEnabled();
  await expect(page).toHaveURL(/\/comprobantes(\?|$)/);
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
    if (r.method() === "POST" && r.url().includes("/api/proxy/facturas")) posts.push(r.url());
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
  for (const campo of ["RUC", "Razón social", "Descripción", "Precio unit. (con IGV)"]) {
    await dialogo.getByLabel(campo).focus();
    await page.keyboard.press("Enter");
  }
  await page.waitForTimeout(300);
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
  const peticion = page.waitForRequest((r) => r.method() === "POST" && r.url().includes("/api/proxy/facturas"));
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
  // Si «Cancelar» perdiera su `type="button"` pasaría a ser el submit del form —y emitiría—: esto lo detecta.
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

  await expect(dialogo.getByTestId("total-a-pagar")).toHaveText("S/ 2,000.01");
  await expect(dialogo.getByText("1 ítem incompleto no se emitirá")).toBeVisible();

  // Una línea con descripción pero cantidad 0 también queda fuera: el aviso no debe atribuirlo a la descripción.
  await dialogo.getByLabel("Descripción").nth(1).fill("Licencia");
  await dialogo.getByLabel("Cantidad").nth(1).fill("0");
  await expect(dialogo.getByTestId("total-a-pagar")).toHaveText("S/ 2,000.01");
  await expect(dialogo.getByText("1 ítem incompleto no se emitirá")).toBeVisible();

  await dialogo.getByRole("button", { name: "Emitir factura" }).click();
  await expect(page).toHaveURL(/\/comprobantes\/f-/);
  // Lo emitido es lo previsualizado: la línea incompleta quedó fuera de los dos lados.
  await expect(page.getByText("S/ 2,000.01").first()).toBeVisible();
});
