import { expect, test } from "@playwright/test";
import { entrarComoAdmin } from "./admin-sesion";

// El mock (src/mocks/handlers.ts) guarda los planes en memoria y las specs de la corrida lo comparten en paralelo: cada test de aquí crea SU plan con un nombre
// propio y lo borra al terminar, y los cuatro sembrados solo se leen (o se intenta tocarlos para ver el rechazo, que no cambia nada). Que los cambios queden en
// la base y en la bitácora lo prueba `PlanesAdminE2ETest`.
const plan = (n: number) => `00000000-0000-4000-a000-${String(n).padStart(12, "0")}`;
const GRATIS = plan(1); // el de las cuentas nuevas, 12 cuentas
const EMPRENDE = plan(2); // 3 cuentas
const NEGOCIO = plan(3); // con un cambio de límites ya programado
const PRO = plan(4); // sin cuentas hoy, pero con historial: no se puede borrar

test.beforeEach(async ({ page }) => {
  await entrarComoAdmin(page);
});

const fila = (page: import("@playwright/test").Page, id: string) => page.getByTestId(`plan-fila-${id}`);

/** Un nombre propio de esta corrida: nunca choca con el de otra spec ni con uno que haya quedado de una corrida anterior. */
const nombreUnico = (base: string) => `${base} ${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;

async function llenarFormulario(page: import("@playwright/test").Page, v: { nombre: string; precio: string; documentos: string; rucs: string; usuarios: string; keys: string; retencion: string }) {
  await page.getByLabel("Nombre", { exact: true }).fill(v.nombre);
  await page.getByLabel("Precio mensual (S/)").fill(v.precio);
  await page.getByLabel("Documentos por mes").fill(v.documentos);
  await page.getByLabel("RUC", { exact: true }).fill(v.rucs);
  await page.getByLabel("Usuarios", { exact: true }).fill(v.usuarios);
  await page.getByLabel("API keys", { exact: true }).fill(v.keys);
  await page.getByLabel("Retención de XML y CDR (años)").fill(v.retencion);
}

async function crearPlan(page: import("@playwright/test").Page, nombre: string, documentos = "800") {
  await page.getByTestId("plan-nuevo").click();
  await llenarFormulario(page, { nombre, precio: "49.90", documentos, rucs: "2", usuarios: "3", keys: "5", retencion: "6" });
  await page.getByTestId("plan-guardar").click();
  const nuevo = page.getByRole("row", { name: new RegExp(nombre) });
  await expect(nuevo).toBeVisible();
  return nuevo;
}

async function borrarPlan(page: import("@playwright/test").Page, nombre: string) {
  const f = page.getByRole("row", { name: new RegExp(nombre) });
  if ((await f.count()) === 0) return;
  await f.getByRole("button", { name: "Borrar" }).click();
  await page.getByRole("button", { name: "Borrar plan" }).click();
  await expect(f).toHaveCount(0);
}

// --- el listado -----------------------------------------------------------------------------------------------------------------------------

test("el listado muestra los cuatro planes con su precio, sus límites y cuántas cuentas los tienen", async ({ page }) => {
  await page.goto("/admin/planes");

  await expect(page.getByRole("heading", { name: "Planes" })).toBeVisible();
  const gratis = fila(page, GRATIS);
  await expect(gratis).toContainText("Gratis");
  await expect(gratis).toContainText("Para cuentas nuevas");
  const emprende = await fila(page, EMPRENDE).getByRole("cell").allTextContents();
  expect(emprende[1]).toBe("S/ 29.00");
  expect(emprende[2]).toBe("300");
  expect(emprende[6]).toBe("5 años");
  expect(emprende[7]).toBe("3");
  const pro = await fila(page, PRO).getByRole("cell").allTextContents();
  expect(pro[2]).toBe("Ilimitados");
  expect(pro[3]).toBe("10");
});

test("el menú lleva a los planes y la miga dice «Comercial / Planes»", async ({ page }) => {
  await page.goto("/admin");

  await page.getByRole("link", { name: "Planes" }).click();

  await expect(page).toHaveURL(/\/admin\/planes$/);
  await expect(page.getByRole("navigation", { name: "Ubicación" })).toContainText("Comercial");
  await expect(page.getByRole("navigation", { name: "Ubicación" })).toContainText("Planes");
});

test("un cambio de límites ya programado se muestra aparte, con qué cambia y desde cuándo, sin tocar lo vigente", async ({ page }) => {
  await page.goto("/admin/planes");

  const negocio = fila(page, NEGOCIO);
  await expect((await negocio.getByRole("cell").allTextContents())[2]).toBe("1,500");
  const aviso = page.getByTestId(`plan-programado-${NEGOCIO}`);
  await expect(aviso).toContainText("Desde el 1");
  await expect(aviso).toContainText("Documentos / mes: 1,500 → 2,000");
});

// --- crear ----------------------------------------------------------------------------------------------------------------------------------

test("crear un plan lo deja en el listado, activo, con sus límites y sin cuentas", async ({ page }) => {
  const nombre = nombreUnico("Estudio");
  await page.goto("/admin/planes");
  try {
    const nuevo = await crearPlan(page, nombre);

    const celdas = await nuevo.getByRole("cell").allTextContents();
    expect(celdas[1]).toBe("S/ 49.90");
    expect(celdas[2]).toBe("800");
    expect(celdas[3]).toBe("2");
    expect(celdas[6]).toBe("6 años");
    expect(celdas[7]).toBe("0");
    await expect(nuevo).toHaveAttribute("data-estado", "ACTIVO");
  } finally {
    await borrarPlan(page, nombre);
  }
});

test("el formulario vacío muestra todos los errores y no envía nada", async ({ page }) => {
  await page.goto("/admin/planes");
  await page.getByTestId("plan-nuevo").click();

  await page.getByTestId("plan-guardar").click();

  const dialogo = page.getByTestId("plan-formulario");
  await expect(dialogo).toContainText("Escribe el nombre del plan.");
  await expect(dialogo).toContainText("Escribe un precio de cero o más");
  await expect(dialogo).toContainText("Escribe un número entero mayor que cero, o marca «Ilimitado».");
  await expect(dialogo.locator("[aria-invalid='true']")).toHaveCount(7);
});

test("un nombre repetido (sin importar mayúsculas) lo rechaza el backend y el formulario sigue abierto", async ({ page }) => {
  await page.goto("/admin/planes");
  await page.getByTestId("plan-nuevo").click();
  await llenarFormulario(page, { nombre: "EMPRENDE", precio: "10", documentos: "10", rucs: "1", usuarios: "1", keys: "1", retencion: "1" });

  await page.getByTestId("plan-guardar").click();

  const dialogo = page.getByTestId("plan-formulario");
  await expect(dialogo.getByRole("alert")).toContainText("Ya existe un plan llamado «EMPRENDE»");
  await expect(page.getByLabel("Nombre", { exact: true })).toHaveAttribute("aria-invalid", "true");
});

test("un límite marcado como ilimitado se guarda sin tope y se lee «Ilimitados»", async ({ page }) => {
  const nombre = nombreUnico("Libre");
  await page.goto("/admin/planes");
  try {
    await page.getByTestId("plan-nuevo").click();
    await llenarFormulario(page, { nombre, precio: "0", documentos: "", rucs: "1", usuarios: "1", keys: "1", retencion: "1" });
    await page.getByLabel("Ilimitado").first().check();

    await page.getByTestId("plan-guardar").click();

    const nuevo = page.getByRole("row", { name: new RegExp(nombre) });
    await expect(nuevo).toBeVisible();
    const celdas = await nuevo.getByRole("cell").allTextContents();
    expect(celdas[1]).toBe("Gratis");
    expect(celdas[2]).toBe("Ilimitados");
  } finally {
    await borrarPlan(page, nombre);
  }
});

// --- editar ---------------------------------------------------------------------------------------------------------------------------------

test("editar cambia el precio al instante pero los límites quedan programados para el ciclo siguiente", async ({ page }) => {
  const nombre = nombreUnico("Taller");
  await page.goto("/admin/planes");
  try {
    const nuevo = await crearPlan(page, nombre, "800");
    await nuevo.getByRole("button", { name: "Editar" }).click();
    const dialogo = page.getByTestId("plan-formulario");
    await expect(dialogo).toContainText("El nombre y el precio cambian al instante.");
    await expect(dialogo).toContainText("Los límites nuevos entran el 1");
    await page.getByLabel("Precio mensual (S/)").fill("59.90");
    await page.getByLabel("Documentos por mes").fill("2000");

    await page.getByTestId("plan-guardar").click();

    const editado = page.getByRole("row", { name: new RegExp(nombre) });
    // Primero se espera a que la tabla se vuelva a pintar con el cambio; leer las celdas antes puede agarrar la fila vieja o ninguna.
    await expect(editado).toContainText("Documentos / mes: 800 → 2,000");
    const celdas = await editado.getByRole("cell").allTextContents();
    expect(celdas[1]).toBe("S/ 59.90");
    expect(celdas[2], "los límites vigentes no cambian").toBe("800");
  } finally {
    await borrarPlan(page, nombre);
  }
});

test("volver a poner los límites vigentes cancela el cambio programado", async ({ page }) => {
  const nombre = nombreUnico("Cancelo");
  await page.goto("/admin/planes");
  try {
    const nuevo = await crearPlan(page, nombre, "800");
    await nuevo.getByRole("button", { name: "Editar" }).click();
    await page.getByLabel("Documentos por mes").fill("2000");
    await page.getByTestId("plan-guardar").click();
    await expect(page.getByRole("row", { name: new RegExp(nombre) })).toContainText("800 → 2,000");

    await page.getByRole("row", { name: new RegExp(nombre) }).getByRole("button", { name: "Editar" }).click();
    await expect(page.getByTestId("plan-hay-programado")).toContainText("se cancela");
    await page.getByLabel("Documentos por mes").fill("800");
    await page.getByTestId("plan-guardar").click();

    await expect(page.getByRole("row", { name: new RegExp(nombre) })).not.toContainText("→");
  } finally {
    await borrarPlan(page, nombre);
  }
});

// --- desactivar y borrar ----------------------------------------------------------------------------------------------------------------------

test("desactivar un plan lo saca de la oferta sin borrarlo, y se puede volver a ofrecer", async ({ page }) => {
  const nombre = nombreUnico("Pausa");
  await page.goto("/admin/planes");
  try {
    const nuevo = await crearPlan(page, nombre);
    await nuevo.getByRole("button", { name: "Desactivar" }).click();
    const dialogo = page.getByRole("dialog");
    await expect(dialogo).toContainText("Ninguna cuenta lo tiene hoy.");
    await expect(dialogo).toContainText("no se podrá asignar a cuentas nuevas");

    await dialogo.getByRole("button", { name: "Desactivar plan" }).click();

    const inactivo = page.getByRole("row", { name: new RegExp(nombre) });
    await expect(inactivo).toHaveAttribute("data-estado", "INACTIVO");
    await expect(inactivo).toContainText("Fuera de la oferta");
    await inactivo.getByRole("button", { name: "Activar" }).click();
    await page.getByRole("button", { name: "Activar plan" }).click();
    await expect(page.getByRole("row", { name: new RegExp(nombre) })).toHaveAttribute("data-estado", "ACTIVO");
  } finally {
    await borrarPlan(page, nombre);
  }
});

test("un plan sin cuentas se borra; el diálogo avisa que es para siempre", async ({ page }) => {
  const nombre = nombreUnico("Efimero");
  await page.goto("/admin/planes");
  const nuevo = await crearPlan(page, nombre);

  await nuevo.getByRole("button", { name: "Borrar" }).click();
  await expect(page.getByRole("dialog")).toContainText("se borra para siempre");
  await page.getByRole("button", { name: "Borrar plan" }).click();

  await expect(page.getByRole("row", { name: new RegExp(nombre) })).toHaveCount(0);
});

test("un plan con cuentas no ofrece borrar, solo desactivar", async ({ page }) => {
  await page.goto("/admin/planes");

  const emprende = fila(page, EMPRENDE);
  await expect(emprende.getByRole("button", { name: "Desactivar" })).toBeVisible();
  await expect(emprende.getByRole("button", { name: "Borrar" })).toHaveCount(0);
});

test("el plan de las cuentas nuevas solo se edita: ni desactivar ni borrar", async ({ page }) => {
  await page.goto("/admin/planes");

  const gratis = fila(page, GRATIS);
  await expect(gratis.getByRole("button", { name: "Editar" })).toBeVisible();
  await expect(gratis.getByRole("button", { name: "Desactivar" })).toHaveCount(0);
  await expect(gratis.getByRole("button", { name: "Borrar" })).toHaveCount(0);
});

/** Pro hoy no tiene cuentas pero las tuvo: se ofrece borrar, el backend lo rechaza con su motivo y nada cambia. */
test("un plan que tuvo cuentas se ofrece borrar pero el backend lo rechaza y explica que hay que desactivarlo", async ({ page }) => {
  await page.goto("/admin/planes");

  await fila(page, PRO).getByRole("button", { name: "Borrar" }).click();
  await page.getByRole("button", { name: "Borrar plan" }).click();

  await expect(page.getByRole("dialog").getByRole("alert")).toContainText("historial de suscripciones");
  await page.getByRole("button", { name: "Cancelar" }).click();
  await expect(fila(page, PRO)).toBeVisible();
});

// --- el BFF ---------------------------------------------------------------------------------------------------------------------------------

test("el BFF rechaza ids inválidos, planes inexistentes y la falta de sesión", async ({ page, request }) => {
  // Sin sesión: una petición a la API sin la cookie del administrador (el `request` de Playwright no comparte las cookies de `page`).
  expect((await request.post("/api/admin/planes", { data: { nombre: "x" } })).status()).toBe(401);
  expect((await request.put(`/api/admin/planes/${GRATIS}`, { data: { nombre: "x" } })).status()).toBe(401);
  expect((await request.delete(`/api/admin/planes/${GRATIS}`)).status()).toBe(401);

  // Con sesión: el id no llega sin validar al backend, y lo que no existe es 404.
  const conSesion = page.request;
  expect((await conSesion.delete("/api/admin/planes/no-es-un-uuid")).status()).toBe(400);
  expect((await conSesion.put("/api/admin/planes/no-es-un-uuid", { data: {} })).status()).toBe(400);
  expect((await conSesion.post("/api/admin/planes/no-es-un-uuid/desactivar")).status()).toBe(400);
  expect((await conSesion.delete(`/api/admin/planes/${plan(999)}`)).status()).toBe(404);
  expect((await conSesion.post("/api/admin/planes", { data: "no es json", headers: { "content-type": "application/json" } })).status()).toBe(400);
});

test("el token del administrador nunca llega al JS: la cookie es httpOnly", async ({ page }) => {
  await page.goto("/admin/planes");

  const visibles = await page.evaluate(() => document.cookie);
  expect(visibles).not.toContain("khipu_admin_access");
});
