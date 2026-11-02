import { expect, test } from "@playwright/test";
import { registrarYVerificar } from "./registro-sesion";

/** Un RUC válido (módulo 11) y distinto en cada corrida: el mock no deja repetir RUC en toda la plataforma. */
function rucUnico(): string {
  const base = `20${String(Date.now()).slice(-6)}${Math.floor(Math.random() * 90 + 10)}`;
  const pesos = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
  const suma = [...base].reduce((s, d, i) => s + Number(d) * pesos[i], 0);
  const r = 11 - (suma % 11);
  return base + (r === 10 ? 0 : r === 11 ? 1 : r);
}

/**
 * #375: la guía «Para empezar a emitir». Empresa nueva → los cuatro pasos pendientes, cada uno con su enlace → certificado y SOL
 * se cargan desde la propia guía y el avance cambia sin recargar a mano → siguen domicilio y series → con todo listo desaparece.
 */
test("la guía acompaña a la empresa nueva hasta que puede emitir y entonces desaparece", async ({ page }) => {
  await registrarYVerificar(page, { email: `guia${Date.now()}@ejemplo.pe`, nombre: "Guía SAC", celular: "987654325" });
  await page.getByLabel("RUC").fill(rucUnico());
  await page.getByLabel("Razón social").fill("GUIA SAC");
  await page.getByRole("button", { name: "Continuar" }).click();
  // El onboarding deja a la empresa en Fiscal y certificado, y la guía ya está ahí con los cuatro pasos pendientes.
  await expect(page).toHaveURL(/\/empresa\?seccion=certificado/);

  const guia = page.getByTestId("guia-emitir");
  await expect(guia.getByTestId("guia-emitir-avance")).toHaveText("0 de 4 listos");
  for (const id of ["certificado", "sol", "domicilio", "series"]) await expect(guia.locator(`[data-paso="${id}"]`)).toHaveAttribute("data-listo", "false");
  await expect(guia.getByRole("link", { name: "Cargar certificado" })).toHaveAttribute("href", "/empresa?seccion=certificado");
  await expect(guia.getByRole("link", { name: "Guardar credenciales" })).toHaveAttribute("href", "/empresa?seccion=sol");
  await expect(guia.getByRole("link", { name: "Completar domicilio" })).toHaveAttribute("href", "/empresa?seccion=datos");
  await expect(guia.getByRole("link", { name: "Crear serie" })).toHaveAttribute("href", "/series");
  await expect(guia.locator('[data-paso="domicilio"]').getByText("recomendado")).toBeVisible();

  // También se ve en Comprobantes, donde el cliente cae cuando entra al portal.
  await page.goto("/comprobantes");
  await expect(page.getByTestId("guia-emitir").getByTestId("guia-emitir-avance")).toHaveText("0 de 4 listos");

  // Certificado: se carga desde el enlace de la guía y el avance sube sin recargar la página.
  await page.getByTestId("guia-emitir").getByRole("link", { name: "Cargar certificado" }).click();
  await expect(page).toHaveURL(/\/empresa\?seccion=certificado/);
  await page.locator('input[type="file"]').setInputFiles({ name: "certificado.p12", mimeType: "application/x-pkcs12", buffer: Buffer.from("p12") });
  await page.getByLabel("Contraseña de la clave privada").fill("clave123");
  await page.getByRole("button", { name: "Cargar certificado digital" }).click();
  await expect(page.getByText("Certificado actualizado.")).toBeVisible();
  await expect(guia.getByTestId("guia-emitir-avance")).toHaveText("1 de 4 listos");
  await expect(guia.locator('[data-paso="certificado"]')).toHaveAttribute("data-listo", "true");

  // SOL: igual, desde su enlace.
  await guia.getByRole("link", { name: "Guardar credenciales" }).click();
  await expect(page).toHaveURL(/\/empresa\?seccion=sol/);
  await page.getByLabel("Usuario SOL secundario").fill("MODDATOS");
  await page.getByLabel("Clave SOL").fill("moddatos");
  // El de las credenciales SOL: la misma pestaña tiene debajo el de las credenciales API SUNAT (#318), con un botón del mismo nombre.
  await page.locator("form").filter({ has: page.getByLabel("Usuario SOL secundario") }).getByRole("button", { name: "Guardar credenciales" }).click();
  await expect(page.getByText("Credenciales SOL actualizadas.")).toBeVisible();
  await expect(guia.getByTestId("guia-emitir-avance")).toHaveText("2 de 4 listos");

  // Quedan el domicilio (recomendado) y las series: con la empresa ya habilitada para firmar y enviar, la guía sigue hasta completarlos.
  const hecho = await page.evaluate(async () => {
    const json = { "content-type": "application/json" };
    const serie = await fetch("/api/proxy/series", { method: "POST", headers: json, body: JSON.stringify({ tipo: "01", serie: "F001", correlativo_inicial: 0 }) });
    return serie.ok;
  });
  expect(hecho).toBe(true);
  await page.goto("/empresa?seccion=datos");
  await expect(page.getByTestId("guia-emitir").getByTestId("guia-emitir-avance")).toHaveText("3 de 4 listos");
  await expect(page.getByTestId("guia-emitir").locator('[data-paso="series"]')).toHaveAttribute("data-listo", "true");

  const form = page.getByTestId("datos-fiscales");
  await expect(form.getByLabel("Departamento")).toBeEnabled();
  await form.getByLabel("Departamento").selectOption("LIMA");
  await form.getByLabel("Provincia").selectOption("LIMA");
  await form.getByLabel("Distrito").selectOption({ label: "MIRAFLORES" });
  await form.getByLabel("Dirección").fill("Av. Larco 345 Of. 12");
  await form.getByRole("button", { name: "Guardar datos fiscales" }).click();
  await expect(form.getByText("Datos fiscales actualizados", { exact: false })).toBeVisible();

  // Con los cuatro pasos hechos la guía no aparece, ni en Fiscal y certificado ni en Comprobantes.
  await expect(page.getByTestId("guia-emitir")).toHaveCount(0);
  await page.goto("/comprobantes");
  await expect(page.getByRole("button", { name: "Nuevo comprobante" })).toBeVisible();
  await expect(page.getByTestId("guia-emitir")).toHaveCount(0);
});
