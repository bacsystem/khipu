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
 * C2: una empresa dada de alta desde el backoffice entra al panel con su serie pero sin certificado ni credenciales SOL. Antes,
 * «Nuevo comprobante» dejaba llenar la factura entera y recién al emitir el backend la rechazaba.
 */
test("sin certificado ni credenciales SOL, «Nuevo comprobante» dice qué falta en vez de mostrar el formulario", async ({ page }) => {
  const sufijo = Date.now();
  await registrarYVerificar(page, { email: `sincert${sufijo}@ejemplo.pe`, nombre: "Sin Cert SAC", celular: "987654324" });
  const ruc = rucUnico();
  const listo = await page.evaluate(async ([r]) => {
    const json = { "content-type": "application/json" };
    const empresa = await (await fetch("/api/proxy/empresas", { method: "POST", headers: json, body: JSON.stringify({ ruc: r, razon_social: "SIN CERT SAC", entorno: "BETA" }) })).json();
    if (!empresa?.datos?.id) return false;
    await fetch("/api/session/empresa", { method: "POST", headers: json, body: JSON.stringify({ empresaId: empresa.datos.id }) });
    const serie = await fetch("/api/proxy/series", { method: "POST", headers: json, body: JSON.stringify({ tipo: "01", serie: "F001", correlativo_inicial: 0 }) });
    return serie.ok;
  }, [ruc]);
  expect(listo).toBe(true);

  await page.goto("/comprobantes");
  await page.getByRole("button", { name: "Nuevo comprobante" }).click();
  const dialogo = page.getByRole("dialog");

  await expect(dialogo.getByText("Esta empresa todavía no puede emitir")).toBeVisible();
  await expect(dialogo.getByText("Cargar el certificado digital (.p12 o .pfx) de la empresa.")).toBeVisible();
  await expect(dialogo.getByText("Guardar el usuario SOL secundario y su clave.")).toBeVisible();
  await expect(dialogo.getByRole("button", { name: "Emitir factura" })).toHaveCount(0);

  await dialogo.getByRole("link", { name: "Ir a Fiscal & certificado" }).click();
  // #276: llega a la pestaña de lo primero que falta, con el formulario de carga a la vista.
  await expect(page).toHaveURL(/\/empresa\?seccion=certificado/);
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByRole("tab", { name: /^Certificado\s*, pendiente$/ })).toHaveAttribute("aria-selected", "true");
  await expect(page.getByRole("tab", { name: /^Credenciales SOL\s*, pendiente$/ })).toBeVisible();

  // C4: con una llave activa, la página de API keys tampoco dice «Listas para emitir por API» si la empresa no puede emitir.
  const creada = await page.evaluate(async () => (await fetch("/api/proxy/empresa/api-keys", { method: "POST" })).status);
  expect(creada).toBe(201);
  await page.goto("/api-keys");
  await expect(page.getByText("Listas para emitir por API")).toHaveCount(0);
  // Sin certificado no firma: ese es el aviso, aunque también falten las credenciales SOL (264-H3).
  await page.getByRole("link", { name: "Falta el certificado digital vigente para emitir" }).click();
  await expect(page).toHaveURL(/\/empresa/);
});
