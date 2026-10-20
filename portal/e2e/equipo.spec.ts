import { expect, test, type Page } from "@playwright/test";

/**
 * #21: un administrador invita a alguien con un rol, el invitado acepta desde el enlace del correo (el mock lo arma con `inv-<correo>`), entra y ve su rol real; lo
 * que su rol no permite no aparece (API keys, Equipo) o queda deshabilitado con el motivo. El mock aplica el rol solo en las rutas del equipo: que el backend rechace
 * el resto (403 ROL_INSUFICIENTE) se prueba en Java (JwtFilterTest, PermisoDeRutaTest, EquipoE2ETest), no aquí.
 */

async function entrar(page: Page, email: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/comprobantes/);
}

async function invitar(page: Page, email: string, rol: string) {
  await page.goto("/cuenta/equipo");
  await page.getByRole("button", { name: "Invitar usuario" }).click();
  const dialogo = page.getByRole("dialog");
  await dialogo.getByLabel("Correo").fill(email);
  await dialogo.getByLabel("Rol").selectOption({ label: rol });
  await dialogo.getByRole("button", { name: "Enviar invitación" }).click();
  await expect(dialogo).toHaveCount(0);
  await expect(page.getByTestId(`invitacion-${email}`)).toBeVisible();
}

async function aceptar(page: Page, token: string, cuentaYRol: RegExp) {
  await page.goto(`/invitacion/${token}`);
  await expect(page.getByTestId("invitacion-detalle")).toHaveText(cuentaYRol);
  await page.getByLabel("Tu contraseña").fill("Clave1234");
  await page.getByRole("button", { name: "Aceptar y crear contraseña" }).click();
  await expect(page.getByRole("status").filter({ hasText: "ya eres parte del equipo" })).toBeVisible();
}

const unico = () => `${Date.now()}-${Math.floor(Math.random() * 1e6)}`;

// Cada prueba recorre dos sesiones y varias páginas que el dev server compila la primera vez: los 30 s por defecto no alcanzan.
// En serie: una prueba suma un segundo administrador a la cuenta demo, y en paralelo la del «único administrador» dejaría de serlo.
test.describe.configure({ mode: "serial", timeout: 120_000 });

test("invitar con solo lectura → aceptar → entra con su rol y no puede escribir", async ({ page, browser }) => {
  const email = `contador-${unico()}@estudio.pe`;
  await entrar(page, "demo@example.com", "Passw0rd1");
  await invitar(page, email, "Solo lectura");

  // El invitado abre el enlace sin sesión, en otro navegador.
  const contexto = await browser.newContext();
  const invitado = await contexto.newPage();
  await aceptar(invitado, `inv-${email}`, /como Solo lectura/);
  await entrar(invitado, email, "Clave1234");

  await invitado.getByRole("button", { name: "Menú de usuario" }).click();
  await expect(invitado.getByRole("menu").getByTestId("rol-del-usuario")).toHaveText("Solo lectura");
  await invitado.keyboard.press("Escape");

  // Lo que no le corresponde no aparece, y lo que escribe queda deshabilitado.
  const lateral = invitado.getByRole("complementary");
  await expect(lateral.getByRole("link", { name: /Comprobantes/ })).toBeVisible();
  await expect(lateral.getByRole("link", { name: /API keys/ })).toHaveCount(0);
  await expect(lateral.getByRole("link", { name: /Equipo/ })).toHaveCount(0);
  await expect(invitado.getByRole("button", { name: /Nuevo comprobante/ })).toBeDisabled();

  // Aunque llegue por la URL, la página del equipo no es suya.
  await invitado.goto("/cuenta/equipo");
  await expect(invitado.getByText("Esta sección es solo para los administradores de la cuenta")).toBeVisible();
  await contexto.close();

  // Para el administrador ya no está pendiente: es un usuario más, con su rol.
  await page.reload();
  await expect(page.getByTestId(`invitacion-${email}`)).toHaveCount(0);
  await expect(page.getByLabel(`Rol de ${email}`)).toHaveValue("LECTURA");
});

test("el enlace sirve una sola vez", async ({ page, browser }) => {
  const email = `cajero-${unico()}@torres.pe`;
  await entrar(page, "demo@example.com", "Passw0rd1");
  await invitar(page, email, "Emisor");

  const contexto = await browser.newContext();
  const invitado = await contexto.newPage();
  await aceptar(invitado, `inv-${email}`, /como Emisor/);
  await invitado.goto(`/invitacion/inv-${email}`);
  await expect(invitado.getByRole("alert").filter({ hasText: "inválido, venció o ya se usó" })).toBeVisible();
  await contexto.close();
});

test("el emisor emite pero no configura, y el administrador le cambia el rol", async ({ page, browser }) => {
  const email = `emisor-${unico()}@torres.pe`;
  await entrar(page, "demo@example.com", "Passw0rd1");
  await invitar(page, email, "Emisor");

  const contexto = await browser.newContext();
  const invitado = await contexto.newPage();
  await aceptar(invitado, `inv-${email}`, /como Emisor/);
  await entrar(invitado, email, "Clave1234");
  await expect(invitado.getByRole("button", { name: /Nuevo comprobante/ })).toBeEnabled();
  await invitado.goto("/series");
  await expect(invitado.getByRole("button", { name: /Nueva serie/ })).toBeDisabled();

  // El administrador lo pasa a solo lectura: vale en su siguiente página, con la misma sesión.
  await page.goto("/cuenta/equipo");
  await page.getByLabel(`Rol de ${email}`).selectOption("LECTURA");
  await expect(page.getByLabel(`Rol de ${email}`)).toHaveValue("LECTURA");
  await invitado.goto("/comprobantes");
  await expect(invitado.getByRole("button", { name: /Nuevo comprobante/ })).toBeDisabled();
  await contexto.close();
});

test("reenviar cambia el enlace y revocar lo deja sin efecto", async ({ page, browser }) => {
  const email = `revocada-${unico()}@torres.pe`;
  await entrar(page, "demo@example.com", "Passw0rd1");
  await invitar(page, email, "Emisor");

  const fila = page.getByTestId(`invitacion-${email}`);
  await fila.getByRole("button", { name: "Reenviar" }).click();
  await expect(fila.getByText(/Enviamos un enlace nuevo/)).toBeVisible();
  await expect(fila.getByText("2 de 5 envíos")).toBeVisible();

  await fila.getByRole("button", { name: "Revocar" }).click();
  await fila.getByRole("button", { name: "Sí, revocar" }).click();
  await expect(fila).toHaveCount(0);

  const contexto = await browser.newContext();
  const invitado = await contexto.newPage();
  await invitado.goto(`/invitacion/inv-${email}-2`);
  await expect(invitado.getByRole("alert").filter({ hasText: "inválido, venció o ya se usó" })).toBeVisible();
  await contexto.close();
});

test("el único administrador no puede quitarse el rol", async ({ page }) => {
  await entrar(page, "demo@example.com", "Passw0rd1");
  await page.goto("/cuenta/equipo");
  const yo = page.getByTestId("miembro-demo@example.com");
  await expect(yo.getByText("Tú")).toBeVisible();
  await page.getByLabel("Rol de demo@example.com").selectOption("EMISOR");
  // H9: bajarse a uno mismo pide confirmación antes de llamar a la API.
  const dialogo = page.getByRole("dialog");
  await expect(dialogo).toContainText("perderás el acceso de administrador");
  await dialogo.getByRole("button", { name: "Sí, dejar de ser administrador" }).click();
  await expect(yo.getByRole("alert")).toHaveText(/al menos un administrador/);
  await page.reload();
  await expect(page.getByLabel("Rol de demo@example.com")).toHaveValue("ADMIN");
});

test("un enlace mal codificado dice que es inválido en vez de romper la página", async ({ page }) => {
  await page.goto("/invitacion/abc%E0%A4%A");
  await expect(page.getByRole("alert").filter({ hasText: "inválido, venció o ya se usó" })).toBeVisible();
});

test("si quien invitó deja de ser administrador, su invitación ya no sirve", async ({ page, browser }) => {
  const email = `huerfana-${unico()}@torres.pe`;
  const segundoAdmin = `admin2-${unico()}@torres.pe`;
  await entrar(page, "demo@example.com", "Passw0rd1");
  await invitar(page, segundoAdmin, "Administrador");
  const contexto = await browser.newContext();
  const otro = await contexto.newPage();
  await aceptar(otro, `inv-${segundoAdmin}`, /como Administrador/);
  await entrar(otro, segundoAdmin, "Clave1234");
  await invitar(otro, email, "Emisor");

  // El primero lo degrada: la invitación que creó el segundo se revoca y su enlace deja de servir.
  await page.goto("/cuenta/equipo");
  await page.getByLabel(`Rol de ${segundoAdmin}`).selectOption("EMISOR");
  await expect(page.getByLabel(`Rol de ${segundoAdmin}`)).toHaveValue("EMISOR");
  await expect(page.getByTestId(`invitacion-${email}`)).toHaveCount(0);
  await otro.goto(`/invitacion/inv-${email}`);
  await expect(otro.getByRole("alert").filter({ hasText: "inválido, venció o ya se usó" })).toBeVisible();
  await contexto.close();
});
