import { expect, test } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";

// El mock (src/mocks/handlers.ts) responde a las acciones de #187 sin guardar nada: cambiar el entorno o revocar una key altera lo que cuentan las demás
// specs que corren a la vez (los filtros por entorno, las keys del detalle). Por eso aquí se prueba todo lo que no muta: la prueba de conexión (que es de solo
// lectura), los diálogos, y los rechazos del backend a través del BFF. Que los cambios ocurran de verdad lo prueba `AccionesDeEmpresaE2ETest`.
const empresa = (n: number) => `00000000-0000-4000-9000-${String(n).padStart(12, "0")}`;
const key = (n: number) => `00000000-0000-4000-b000-${String(n).padStart(12, "0")}`;
const SOL = empresa(1); // BETA, con credenciales SOL, doce envíos pendientes, una key vigente y otra revocada
const LUNA = empresa(2); // PRODUCCION, con credenciales SOL; SUNAT le contesta un error definitivo
const CLIENTE_04 = empresa(4); // PRODUCCION, con credenciales SOL; SUNAT no le contesta
const CLIENTE_05 = empresa(5); // sin credenciales SOL

test.beforeEach(async ({ page }) => {
  await entrarComoAdmin(page);
});

// --- probar la conexión ---------------------------------------------------------------------------------------------------------------

test("probar la conexión de una empresa a la que SUNAT contesta con normalidad", async ({ page }) => {
  await page.goto(`/admin/empresas/${SOL}`);

  await page.getByTestId("probar-conexion").click();

  const r = page.getByTestId("resultado-conexion");
  await expect(r).toHaveAttribute("data-resultado", "CONECTADO");
  await expect(r).toContainText("SUNAT contestó con normalidad");
  await expect(r).toContainText("Entorno probado: Beta");
});

test("un error definitivo de SUNAT se muestra con su código y su mensaje, tal cual", async ({ page }) => {
  await page.goto(`/admin/empresas/${LUNA}`);

  await page.getByTestId("probar-conexion").click();

  const r = page.getByTestId("resultado-conexion");
  await expect(r).toHaveAttribute("data-resultado", "RECHAZADO");
  await expect(r).toContainText("SUNAT contestó con un error");
  await expect(r).toContainText("1033");
  await expect(r).toContainText("El ticket no existe");
  await expect(r).toContainText("Entorno probado: Producción");
});

test("sin respuesta útil de SUNAT se distingue de un error de SUNAT", async ({ page }) => {
  await page.goto(`/admin/empresas/${CLIENTE_04}`);

  await page.getByTestId("probar-conexion").click();

  const r = page.getByTestId("resultado-conexion");
  await expect(r).toHaveAttribute("data-resultado", "SIN_RESPUESTA");
  await expect(r).toContainText("No hubo una respuesta útil de SUNAT");
  await expect(r).toContainText("0109");
});

test("sin credenciales SOL la prueba está deshabilitada, y el backend la rechazaría igual", async ({ page }) => {
  await page.goto(`/admin/empresas/${CLIENTE_05}`);

  await expect(page.getByTestId("probar-conexion")).toBeDisabled();
  const res = await page.request.post(`/api/admin/empresas/${CLIENTE_05}/prueba-de-conexion`);
  expect(res.status()).toBe(409);
  expect((await res.json()).codigo).toBe("SOL_NO_CARGADAS");
});

// --- cambiar el entorno ---------------------------------------------------------------------------------------------------------------

test("una empresa en beta ofrece pasar a producción y el diálogo explica las consecuencias", async ({ page }) => {
  await page.goto(`/admin/empresas/${SOL}`);

  await page.getByTestId("cambiar-entorno").click();

  const dialogo = page.getByTestId("cambiar-entorno-dialogo");
  await expect(dialogo).toContainText("PANADERIA SOL SAC pasa de BETA a PRODUCCIÓN");
  await expect(dialogo).toContainText("los comprobantes que emita valen de verdad");
  await expect(dialogo).toContainText("credenciales SOL y el certificado de producción");
  await expect(dialogo).toContainText("No toca los comprobantes ya emitidos");
});

test("una empresa en producción ofrece volver a beta", async ({ page }) => {
  await page.goto(`/admin/empresas/${LUNA}`);

  await page.getByTestId("cambiar-entorno").click();

  await expect(page.getByTestId("cambiar-entorno-dialogo")).toContainText("pasa de PRODUCCIÓN a BETA");
});

/** «Panadería Sol» tiene doce envíos pendientes: el backend se niega, el diálogo lo dice y no se cierra. */
test("con envíos pendientes el cambio se rechaza, el diálogo lo dice y sigue abierto", async ({ page }) => {
  await page.goto(`/admin/empresas/${SOL}`);
  await page.getByTestId("cambiar-entorno").click();

  await page.getByTestId("cambiar-entorno-confirmar").click();

  const dialogo = page.getByTestId("cambiar-entorno-dialogo");
  await expect(dialogo.getByRole("alert")).toContainText("envíos pendientes");
  await expect(dialogo).toBeVisible();
});

test("cancelar el diálogo no envía nada", async ({ page }) => {
  let pedidos = 0;
  await page.route("**/api/admin/empresas/*/entorno", (route) => {
    pedidos++;
    return route.continue();
  });
  await page.goto(`/admin/empresas/${SOL}`);
  await page.getByTestId("cambiar-entorno").click();

  await page.getByTestId("cambiar-entorno-dialogo").getByRole("button", { name: "Cancelar" }).click();

  await expect(page.getByTestId("cambiar-entorno-dialogo")).toBeHidden();
  expect(pedidos).toBe(0);
});

// --- revocar una API key --------------------------------------------------------------------------------------------------------------

test("el diálogo de revocar dice cuál key, que deja de autenticar y que no se deshace", async ({ page }) => {
  await page.goto(`/admin/empresas/${SOL}`);

  await page.getByTestId(`revocar-api-key-${key(2)}`).click();

  const dialogo = page.getByTestId(`revocar-api-key-${key(2)}-dialogo`);
  await expect(dialogo).toContainText("fk_sol0002… de PANADERIA SOL SAC");
  await expect(dialogo).toContainText("Deja de autenticar de inmediato");
  await expect(dialogo).toContainText("No se puede deshacer");
});

test("una key ya revocada no ofrece revocar", async ({ page }) => {
  await page.goto(`/admin/empresas/${SOL}`);

  await expect(page.getByTestId(`revocar-api-key-${key(2)}`)).toBeVisible();
  await expect(page.getByTestId(`revocar-api-key-${key(1)}`)).toHaveCount(0);
});

// --- el BFF, de punta a punta, sin mutar ---------------------------------------------------------------------------------------------------

test("el BFF propaga los conflictos del backend con su código", async ({ page }) => {
  const yaRevocada = await page.request.post(`/api/admin/empresas/${SOL}/api-keys/${key(1)}/revocar`);
  expect(yaRevocada.status()).toBe(409);
  expect((await yaRevocada.json()).codigo).toBe("API_KEY_YA_REVOCADA");

  const mismoEntorno = await page.request.post(`/api/admin/empresas/${SOL}/entorno`, { data: { entorno: "BETA" } });
  expect(mismoEntorno.status()).toBe(409);
  expect((await mismoEntorno.json()).codigo).toBe("ENTORNO_SIN_CAMBIOS");

  const pendientes = await page.request.post(`/api/admin/empresas/${SOL}/entorno`, { data: { entorno: "PRODUCCION" } });
  expect(pendientes.status()).toBe(409);
  expect((await pendientes.json()).codigo).toBe("EMPRESA_CON_ENVIOS_PENDIENTES");
});

test("un entorno inventado es 422 y un id que no es un UUID es 400, sin llegar al backend", async ({ page }) => {
  const entorno = await page.request.post(`/api/admin/empresas/${SOL}/entorno`, { data: { entorno: "PRUEBAS" } });
  expect(entorno.status()).toBe(422);
  expect((await entorno.json()).codigo).toBe("ENTORNO_INVALIDO");

  expect((await page.request.post("/api/admin/empresas/no-es-un-uuid/entorno", { data: { entorno: "BETA" } })).status()).toBe(400);
  expect((await page.request.post(`/api/admin/empresas/${SOL}/api-keys/no-es-un-uuid/revocar`)).status()).toBe(400);
  expect((await page.request.post("/api/admin/empresas/no-es-un-uuid/prueba-de-conexion")).status()).toBe(400);
});

test("una empresa o una key que no existen son 404", async ({ page }) => {
  expect((await page.request.post(`/api/admin/empresas/${empresa(99)}/entorno`, { data: { entorno: "PRODUCCION" } })).status()).toBe(404);
  expect((await page.request.post(`/api/admin/empresas/${SOL}/api-keys/${key(77)}/revocar`)).status()).toBe(404);
  expect((await page.request.post(`/api/admin/empresas/${empresa(99)}/prueba-de-conexion`)).status()).toBe(404);
});

test("sin sesión de administrador el BFF responde 401", async ({ request }) => {
  // El fixture `request` no comparte las cookies de la página: es un cliente sin sesión.
  expect((await request.post(`/api/admin/empresas/${SOL}/entorno`, { data: { entorno: "PRODUCCION" } })).status()).toBe(401);
  expect((await request.post(`/api/admin/empresas/${SOL}/api-keys/${key(2)}/revocar`)).status()).toBe(401);
  expect((await request.post(`/api/admin/empresas/${SOL}/prueba-de-conexion`)).status()).toBe(401);
});
