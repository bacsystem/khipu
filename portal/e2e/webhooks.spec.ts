import { expect, test, type Page } from "@playwright/test";

// Los tests corren en paralelo contra el mismo mock en memoria: cada uno crea sus propias URL y las aserciones van por tarjeta, no por conteos globales.
function tarjeta(page: Page, url: string) {
  return page.getByRole("article", { name: `Webhook ${url}` });
}

// El mock aplica el tope de 5 webhooks por empresa como el backend: los tests de este archivo corren uno tras otro y los que crean uno lo borran al terminar.
test.describe.configure({ mode: "serial" });

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
  await page.goto("/api-keys");
});

async function eliminar(page: Page, url: string) {
  const w = tarjeta(page, url);
  await w.getByRole("button", { name: "Eliminar" }).click();
  await w.getByRole("button", { name: "Sí, eliminar" }).click();
  await expect(tarjeta(page, url)).toHaveCount(0);
}

async function registrar(page: Page, url: string) {
  await page.getByRole("button", { name: "Nuevo webhook" }).click();
  await page.getByLabel("URL del endpoint").fill(url);
  await page.getByRole("button", { name: "Registrar webhook" }).click();
}

test("#19 lista los webhooks con su estado, y uno pausado por khipu explica por qué", async ({ page }) => {
  const activo = tarjeta(page, "https://erp.demo.pe/khipu/webhook");
  await expect(activo.getByText("Activo", { exact: true })).toBeVisible();
  await expect(activo.getByText("ERP producción")).toBeVisible();
  await expect(activo.getByText(/Todos los eventos/)).toBeVisible();

  const pausado = tarjeta(page, "https://viejo.demo.pe/hook");
  await expect(pausado.getByText("Pausado por khipu")).toBeVisible();
  await expect(pausado.getByRole("status")).toContainText("10 entregas fallidas seguidas");
  await expect(pausado.getByRole("button", { name: "Enviar prueba" })).toBeDisabled();
});

test("#19 registra un webhook, muestra el secreto una sola vez y queda en la lista", async ({ page }) => {
  const url = "https://nuevo-" + Date.now() + ".demo.pe/khipu";
  await registrar(page, url);

  const secreto = page.getByTestId("api-key-nueva");
  await expect(secreto).toHaveText(/^whsec_/);
  await expect(page.getByText("no volverá a mostrarse")).toBeVisible();
  await page.getByRole("button", { name: "Listo, lo guardé" }).click();

  const nueva = tarjeta(page, url);
  await expect(nueva).toBeVisible();
  await expect(nueva.getByText("Activo", { exact: true })).toBeVisible();
  await expect(page.getByText(/whsec_/)).toHaveCount(0);
  await eliminar(page, url);
});

test("#19 valida la URL antes de llamar a la API y muestra el rechazo del backend", async ({ page }) => {
  await page.getByRole("button", { name: "Nuevo webhook" }).click();
  await page.getByLabel("URL del endpoint").fill("http://inseguro.demo.pe/h");
  await page.getByRole("button", { name: "Registrar webhook" }).click();
  await expect(page.getByRole("dialog").getByRole("alert")).toContainText("https://");

  await page.getByLabel("URL del endpoint").fill("https://localhost/h");
  await page.getByRole("button", { name: "Registrar webhook" }).click();
  await expect(page.getByRole("dialog").getByRole("alert")).toContainText("servidor público");

  await page.getByLabel("URL del endpoint").fill("https://ok.demo.pe/h");
  for (const caja of await page.getByRole("dialog").getByRole("checkbox").all()) await caja.uncheck();
  await page.getByRole("button", { name: "Registrar webhook" }).click();
  await expect(page.getByRole("dialog").getByRole("alert").filter({ hasText: "al menos un evento" })).toBeVisible();
});

test("#19 envía una prueba y la ve en el log de entregas", async ({ page }) => {
  const url = "https://prueba-" + Date.now() + ".demo.pe/khipu";
  await registrar(page, url);
  await page.getByRole("button", { name: "Listo, lo guardé" }).click();
  const w = tarjeta(page, url);

  await w.getByRole("button", { name: "Enviar prueba" }).click();

  await expect(w.getByRole("status")).toContainText("Prueba en cola");
  const entrega = w.getByTestId("entrega");
  await expect(entrega).toHaveCount(1);
  await expect(entrega.getByText("Prueba", { exact: true })).toBeVisible();
  // El trabajo de entrega la manda a los pocos segundos: el log se vuelve a leer solo, o a mano con el botón de refrescar.
  await w.getByRole("button", { name: "Refrescar entregas" }).click();
  await expect(entrega.getByText("Entregada", { exact: true })).toBeVisible();
  await expect(entrega.getByText("HTTP 200")).toBeVisible();
  await eliminar(page, url);
});

test("#19 el log filtra por estado y reenvía una entrega fallida", async ({ page }) => {
  const w = tarjeta(page, "https://erp.demo.pe/khipu/webhook");
  await w.getByRole("button", { name: "Entregas" }).click();
  await expect(w.getByTestId("entrega").first()).toBeVisible();

  await w.getByLabel("Filtrar entregas por estado").selectOption("FALLIDA");
  const fallida = w.getByTestId("entrega");
  await expect(fallida).toHaveCount(1);
  await expect(fallida.getByText("Fallida", { exact: true })).toBeVisible();
  await expect(fallida.getByText("El servidor respondió HTTP 503")).toBeVisible();

  await fallida.getByRole("button", { name: "Reenviar" }).click();
  await expect(w.getByText("No hay entregas con ese estado.")).toBeVisible();
  await w.getByLabel("Filtrar entregas por estado").selectOption("PENDIENTE");
  await expect(w.getByTestId("entrega").getByText(/reintenta/)).toBeVisible();
  await expect(w.getByTestId("entrega").getByRole("button", { name: "Reenviar" })).toHaveCount(0);
  // Lo reenviado vuelve a la cola, sale, y se ve entre las entregadas.
  await w.getByLabel("Filtrar entregas por estado").selectOption("EXITOSA");
  await expect(w.getByTestId("entrega").filter({ hasText: "HTTP 200" }).first()).toBeVisible();
});

test("#19 apaga y reactiva un webhook, rota su secreto y lo elimina", async ({ page }) => {
  const url = "https://ciclo-" + Date.now() + ".demo.pe/khipu";
  await registrar(page, url);
  await page.getByRole("button", { name: "Listo, lo guardé" }).click();
  const w = tarjeta(page, url);

  await w.getByRole("button", { name: "Apagar" }).click();
  await expect(w.getByText("Apagado", { exact: true })).toBeVisible();
  await expect(w.getByRole("button", { name: "Enviar prueba" })).toBeDisabled();
  await w.getByRole("button", { name: "Activar" }).click();
  await expect(w.getByText("Activo", { exact: true })).toBeVisible();

  await w.getByRole("button", { name: "Rotar secreto" }).click();
  await expect(w.getByText(/El secreto actual deja de valer/)).toBeVisible();
  await w.getByRole("button", { name: "Sí, rotar" }).click();
  const dialogo = page.getByRole("dialog", { name: "Nuevo secreto de firma" });
  await expect(dialogo.getByTestId("api-key-nueva")).toHaveText(/^whsec_/);
  await dialogo.getByRole("button", { name: "Listo, lo guardé" }).click();

  await eliminar(page, url);
});

test("#19 reactivar un webhook pausado por khipu le quita el aviso", async ({ page }) => {
  const pausado = tarjeta(page, "https://viejo2.demo.pe/hook");
  await expect(pausado.getByText("Pausado por khipu")).toBeVisible();

  await pausado.getByRole("button", { name: "Activar" }).click();

  await expect(pausado.getByText("Activo", { exact: true })).toBeVisible();
  await expect(pausado.getByText(/Se apagó solo/)).toHaveCount(0);
  await expect(pausado.getByRole("button", { name: "Enviar prueba" })).toBeEnabled();
});

test("#19 la guía de desarrolladores explica la firma, los eventos y los reintentos", async ({ page }) => {
  await page.goto("/developers/guia#webhooks");
  const seccion = page.locator("#webhooks");
  await expect(seccion.getByRole("heading", { name: "Webhooks", exact: true })).toBeVisible();
  await expect(seccion.getByText("comprobante.rechazado", { exact: true })).toBeVisible();
  await expect(seccion.getByText("t=1792339200,v1=").first()).toBeVisible();
  await expect(seccion.getByText("Node.js", { exact: true })).toBeVisible();
  await expect(seccion.getByText(/10 intentos fallidos seguidos/)).toBeVisible();
});
