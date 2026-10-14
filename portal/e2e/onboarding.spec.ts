import { expect, test } from "@playwright/test";
import { registrarYVerificar } from "./registro-sesion";

test("el onboarding solo crea la empresa y deja al cliente en el portal para cargar el certificado (#371)", async ({ page }) => {
  await registrarYVerificar(page, { email: `nueva-${Date.now()}@example.com` });

  // El asistente ya no pide certificado, SOL ni serie: eso se configura dentro del portal. Se mira con el formulario ya
  // montado: durante «Cargando tu configuración…» tampoco hay ninguno de esos textos y la comprobación pasaría sola.
  await expect(page.getByLabel("RUC")).toBeVisible();
  await expect(page.getByText("Certificado y SOL")).toHaveCount(0);
  await expect(page.getByText("Primera serie")).toHaveCount(0);
  await expect(page.getByLabel("Usuario SOL secundario")).toHaveCount(0);
  await page.getByLabel("RUC").fill("20999999990");
  await page.getByLabel("Razón social").fill("Mi Empresa de Prueba SAC");
  await page.getByRole("button", { name: "Continuar" }).click();

  // Aterriza en Fiscal y certificado, con el certificado todavía sin cargar.
  await expect(page).toHaveURL(/\/empresa\?seccion=certificado/);
  await expect(page.getByText("Sin certificado").first()).toBeVisible();
});

test("registro: el celular no acepta letras, se corta en 12 y avisa al salir del campo", async ({ page }) => {
  // Reportado por el usuario en el portal desplegado: «907892868sssssssss» se quedaba escrito y solo fallaba al enviar.
  await page.goto("/registro");
  const celular = page.getByLabel(/Celular/);
  await celular.fill("907892868sssssssss");
  await expect(celular).toHaveValue("907892868");
  await celular.fill("+51987654321999");
  await expect(celular).toHaveValue("+51987654321");
  // El error aparece al salir del campo, no recién al enviar, y se anuncia.
  await celular.fill("9078");
  await page.getByLabel("Correo electrónico").click();
  const error = page.getByRole("alert").filter({ hasText: "Celular inválido" });
  await expect(error).toBeVisible();
  await expect(celular).toHaveAttribute("aria-describedby", "telefono-error");
});

test("onboarding: el RUC se valida con el módulo 11 en el cliente y la razón social no admite tabuladores", async ({ page }) => {
  await registrarYVerificar(page, { email: `contrato${Date.now()}@ejemplo.pe`, nombre: "Contrato SAC" });

  const ruc = page.getByLabel("RUC");
  await ruc.fill("20ABCDEF123456");
  // Un pegado pasa primero por `maxLength` (11) y después por el filtro, así que quedan los dígitos de esos 11 caracteres.
  // Lo que importa: nunca entra una letra ni se superan los 11 dígitos.
  await expect(ruc).toHaveValue("20123");
  await ruc.fill("2012345678901234");
  await expect(ruc).toHaveValue("20123456789");
  await ruc.fill("20123456789"); // 11 dígitos, dígito verificador equivocado
  await page.getByLabel("Razón social").fill("Comercial Andina SAC");
  await page.getByRole("button", { name: "Continuar" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "dígito verificador" })).toBeVisible();
  await expect(page).toHaveURL(/\/onboarding/); // no avanzó: el POST no salió

  // La razón social no deja pasar tabuladores (4338): SUNAT rechazaría todos los comprobantes de la empresa.
  await page.getByLabel("Razón social").fill("ACME SAC\tEIRL");
  await expect(page.getByLabel("Razón social")).toHaveValue("ACME SAC EIRL");
  await expect(page.getByText(/Tal como figura en tu ficha RUC/)).toBeVisible();
  // El entorno tampoco se puede cambiar después (no hay endpoint que lo edite) y el wizard ahora lo dice.
  await expect(page.getByText(/no se puede cambiar.*después de crear la empresa/i)).toBeVisible();
});

test("onboarding: si se corta la conexión al crear la empresa, avisa en vez de quedarse mudo", async ({ page }) => {
  await registrarYVerificar(page, { email: `corte${Date.now()}@ejemplo.pe`, nombre: "Corte SAC", celular: "987654322" });

  await page.route("**/api/proxy/empresas", (r) => (r.request().method() === "POST" ? r.abort("connectionreset") : r.continue()));
  await page.getByLabel("RUC").fill("20100066603");
  await page.getByLabel("Razón social").fill("Corte SAC");
  await page.getByRole("button", { name: "Continuar" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "Se cortó la conexión" })).toBeVisible();
});

test("onboarding: con la empresa ya creada, /onboarding lleva al portal en vez de pedir el RUC otra vez", async ({ page }) => {
  const sufijo = Date.now();
  const ruc = `2010006660${(sufijo % 10 === 3 ? 3 : 3)}`; // RUC válido de ejemplo
  await registrarYVerificar(page, { email: `retoma${sufijo}@ejemplo.pe`, nombre: "Retoma SAC", celular: "987654323" });
  // Crea la empresa por HTTP directo (el RUC de ejemplo puede estar tomado por otro worker: lo que importa es retomar).
  const creada = await page.evaluate(async ([r]) => {
    const res = await fetch("/api/proxy/empresas", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ ruc: r, razon_social: "Retoma SAC", entorno: "BETA" }) });
    const cuerpo = await res.json();
    if (cuerpo?.datos?.id) await fetch("/api/session/empresa", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ empresaId: cuerpo.datos.id }) });
    return res.status;
  }, [ruc]);
  test.skip(creada !== 201, "otro worker ya tomó el RUC de ejemplo");

  await page.reload();
  // Antes: volvía al paso 1 vacío y reenviar el mismo RUC daba «Ya existe una cuenta con ese correo».
  await expect(page).toHaveURL(/\/empresa\?seccion=certificado/);
  await expect(page.getByLabel("RUC")).toHaveCount(0);
});
