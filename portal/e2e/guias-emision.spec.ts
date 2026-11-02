import { expect, test, type Page } from "@playwright/test";
import { elegirFecha, fechaElegida, fechaEsElegible, sumarDias } from "./fecha";

test.beforeEach(async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill("demo@example.com");
  await page.getByLabel("Contraseña").fill("Passw0rd1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/inicio/);
});

/** Cierra el aviso del formulario y llena lo mínimo en común de una guía (destinatario, peso, punto de llegada y un bien). */
async function llenarLoComun(page: Page) {
  const form = page.getByTestId("nueva-guia-form");
  await form.locator("#destinatario-num").fill("20601234565");
  await form.locator("#destinatario-nombre").fill("PESQUERA PACÍFICO S.A.C.");
  await form.locator("#guia-peso").fill("25,5");
  // Partida: la empresa del mock no cargó su domicilio fiscal, así que parte de una dirección.
  await form.locator("#partida-departamento").selectOption("LIMA");
  await form.locator("#partida-provincia").selectOption("LIMA");
  await form.locator("#partida-distrito").selectOption({ label: "MIRAFLORES" });
  await form.locator("#partida-direccion").fill("Av. Larco 345, Miraflores");
  // Llegada: otra dirección, con el selector de ubigeo del catálogo 13.
  await form.locator("#llegada-departamento").selectOption("LIMA");
  await form.locator("#llegada-provincia").selectOption("LIMA");
  await form.locator("#llegada-distrito").selectOption({ label: "SAN ISIDRO" });
  await form.locator("#llegada-direccion").fill("Calle Los Pinos 450");
  await form.locator("#guia-item-0-descripcion").fill("Laptop 15 pulgadas");
  await form.locator("#guia-item-0-cantidad").fill("2");
}

async function llenarTransportePrivado(page: Page) {
  const form = page.getByTestId("nueva-guia-form");
  await form.locator("#guia-placa-0").fill("abc123");
  await form.locator("#guia-conductor-0-num").fill("45678912");
  await form.locator("#guia-conductor-0-licencia").fill("Q45678912");
  await form.locator("#guia-conductor-0-nombres").fill("JUAN");
  await form.locator("#guia-conductor-0-apellidos").fill("QUISPE MAMANI");
}

test("el listado lleva a «Nueva guía» y el formulario avisa que es experimental", async ({ page }) => {
  await page.goto("/guias");
  await page.getByRole("link", { name: "Nueva guía" }).click();
  await expect(page).toHaveURL(/\/guias\/nueva$/);
  await expect(page.getByRole("note")).toContainText("Función experimental");
  // El envío viene apagado en el servidor: la casilla de enviar al emitir nace sin marcar.
  await expect(page.getByRole("checkbox", { name: /Enviar a SUNAT al emitir/ })).not.toBeChecked();
});

test("una empresa sin domicilio fiscal parte de una dirección: no se le ofrece el 0000, que la API rechazaría", async ({ page }) => {
  await page.goto("/guias/nueva");
  const form = page.getByTestId("nueva-guia-form");
  await expect(form.locator("#partida-departamento")).toBeVisible();
  // El anexo 0002 sí existe: se puede elegir, y la lista no trae el 0000.
  await form.locator("fieldset", { has: page.locator("#partida-departamento") }).getByRole("radio", { name: "Un establecimiento de mi empresa" }).check();
  await expect(form.locator("#partida-establecimiento option")).toHaveText(["0002 · Tienda Miraflores"]);
});

test("los documentos relacionados que son comprobantes piden serie-número y el RUC de quien los emitió", async ({ page }) => {
  await page.goto("/guias/nueva");
  const form = page.getByTestId("nueva-guia-form");
  await form.locator("#guia-motivo").selectOption("01");
  await llenarLoComun(page);
  await llenarTransportePrivado(page);
  await form.getByRole("button", { name: "Agregar" }).last().click();
  await form.locator("#guia-documento-0-numero").fill("F001");
  await form.getByRole("button", { name: "Emitir la guía" }).click();
  await expect(page.getByTestId("guia-error")).toContainText("serie-número");
  await form.locator("#guia-documento-0-numero").fill("F001-123");
  await form.getByRole("button", { name: "Emitir la guía" }).click();
  await expect(page.getByTestId("guia-error")).toContainText("RUC");
});

test("emite una guía remitente en transporte privado: la valida sin numerar, la emite firmada y abre su ficha", async ({ page }) => {
  await page.goto("/guias/nueva");
  const form = page.getByTestId("nueva-guia-form");
  await form.locator("#guia-motivo").selectOption("01");
  await llenarLoComun(page);
  await llenarTransportePrivado(page);

  await form.getByRole("button", { name: "Validar sin emitir" }).click();
  await expect(page.getByTestId("guia-valida")).toContainText("La guía es válida");

  await form.getByRole("button", { name: "Emitir la guía" }).click();
  await expect(page).toHaveURL(/\/guias\/g-nueva-/);
  await expect(page.getByTestId("guia-numero")).toHaveText(/^T001-\d{8}$/);
  await expect(page.getByTestId("guia-estado-ayuda")).toContainText("todavía no se envió a SUNAT");
  await expect(page.getByText("Laptop 15 pulgadas")).toBeVisible();
  await expect(page.getByText("ABC123")).toBeVisible();
  await expect(page.getByText("PESQUERA PACÍFICO S.A.C.", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Enviar a SUNAT" })).toBeVisible();
});

test("en transporte público pide la empresa de transporte y no los vehículos ni los conductores", async ({ page }) => {
  await page.goto("/guias/nueva");
  const form = page.getByTestId("nueva-guia-form");
  await form.locator("#guia-motivo").selectOption("01");
  await llenarLoComun(page);
  await form.getByRole("radio", { name: /Transporte público/ }).check();
  await expect(form.locator("#guia-placa-0")).toHaveCount(0);
  await expect(form.locator("#guia-conductor-0-num")).toHaveCount(0);

  await form.getByRole("button", { name: "Emitir la guía" }).click();
  await expect(page.getByTestId("guia-error")).toContainText("completa el RUC y la razón social del transportista");

  await form.locator("#guia-transportista-ruc").fill("20480072872");
  await form.locator("#guia-transportista-nombre").fill("TRANSPORTES DEL SUR S.A.C.");
  await form.getByRole("button", { name: "Emitir la guía" }).click();
  await expect(page).toHaveURL(/\/guias\/g-nueva-/);
  await expect(page.getByText("TRANSPORTES DEL SUR S.A.C.")).toBeVisible();
});

test("una guía transportista (serie V) pide al remitente y su registro MTC, y solo ofrece direcciones", async ({ page }) => {
  await page.goto("/guias/nueva");
  const form = page.getByTestId("nueva-guia-form");
  await form.locator("#guia-serie").selectOption("V001");
  // La transportista no tiene motivo ni modalidad; sí remitente y registro MTC.
  await expect(form.locator("#guia-motivo")).toHaveCount(0);
  await expect(form.locator("#remitente-num")).toBeVisible();
  await expect(form.getByRole("radio", { name: "Un establecimiento de mi empresa" })).toHaveCount(0);

  await llenarLoComun(page);
  await form.locator("#partida-departamento").selectOption("CALLAO");
  await form.locator("#partida-provincia").selectOption("CALLAO");
  await form.locator("#partida-distrito").selectOption({ label: "CALLAO" });
  await form.locator("#partida-direccion").fill("Av. Contralmirante Mora 100");
  await llenarTransportePrivado(page);
  await form.locator("#remitente-num").fill("20601234565");
  await form.locator("#remitente-nombre").fill("PESQUERA PACÍFICO S.A.C.");

  await form.getByRole("button", { name: "Emitir la guía" }).click();
  await expect(page.getByTestId("guia-error")).toContainText("registro MTC");

  await form.locator("#guia-mtc").fill("1512345CNG");
  await form.getByRole("button", { name: "Emitir la guía" }).click();
  await expect(page).toHaveURL(/\/guias\/g-nueva-/);
  await expect(page.getByTestId("guia-numero")).toHaveText(/^V001-\d{8}$/);
});

test("un formulario incompleto dice qué falta antes de llamar a la API", async ({ page }) => {
  await page.goto("/guias/nueva");
  const form = page.getByTestId("nueva-guia-form");
  await form.getByRole("button", { name: "Emitir la guía" }).click();
  await expect(page.getByTestId("guia-error")).toContainText("Completa el tipo, el número y el nombre del destinatario");

  await form.locator("#destinatario-num").fill("20601234565");
  await form.locator("#destinatario-nombre").fill("PESQUERA PACÍFICO S.A.C.");
  await form.getByRole("button", { name: "Emitir la guía" }).click();
  await expect(page.getByTestId("guia-error")).toContainText("Elige el motivo del traslado");
});

test("con «Enviar a SUNAT al emitir» y el envío apagado en el servidor, la emisión se rechaza y no numera", async ({ page }) => {
  await page.goto("/guias/nueva");
  const form = page.getByTestId("nueva-guia-form");
  await form.locator("#guia-motivo").selectOption("01");
  await llenarLoComun(page);
  await llenarTransportePrivado(page);
  await form.getByRole("checkbox", { name: /Enviar a SUNAT al emitir/ }).check();

  await form.getByRole("button", { name: "Emitir la guía" }).click();
  await expect(page.getByTestId("guia-error")).toContainText("no está habilitado en este servidor");
  await expect(page).toHaveURL(/\/guias\/nueva$/);
});

test("una regla de SUNAT que el formulario no conoce vuelve del backend con su código y se muestra", async ({ page }) => {
  await page.goto("/guias/nueva");
  const form = page.getByTestId("nueva-guia-form");
  await form.locator("#guia-motivo").selectOption("01");
  await llenarLoComun(page);
  await llenarTransportePrivado(page);
  // Un traslado que empieza antes de la emisión: el calendario no ofrece un inicio anterior a la emisión, pero la emisión se puede mover después de elegir el
  // inicio, y el backend es quien manda (3343).
  const emision = form.getByLabel("Fecha de emisión");
  const inicio = form.getByLabel("Inicio del traslado");
  const hoy = await fechaElegida(emision);
  const ayer = sumarDias(hoy, -1);
  expect(await fechaEsElegible(inicio, ayer)).toBe(false);
  await elegirFecha(emision, ayer);
  await elegirFecha(inicio, ayer);
  await elegirFecha(emision, hoy);

  await form.getByRole("button", { name: "Emitir la guía" }).click();
  await expect(page.getByTestId("guia-error")).toContainText("3343");
});
